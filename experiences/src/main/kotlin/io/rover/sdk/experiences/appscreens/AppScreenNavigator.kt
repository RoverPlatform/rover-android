/*
 * Copyright (c) 2023, Rover Labs, Inc. All rights reserved.
 * You are hereby granted a non-exclusive, worldwide, royalty-free license to use,
 * copy, modify, and distribute this software in source code or binary form for use
 * in connection with the web services and APIs provided by Rover.
 *
 * This copyright notice shall be included in all copies or substantial portions of
 * the software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package io.rover.sdk.experiences.appscreens

import android.content.Context
import android.content.MutableContextWrapper
import android.net.Uri
import android.os.SystemClock
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import io.rover.sdk.core.logging.log
import io.rover.sdk.experiences.appscreens.network.AppScreenDocument
import io.rover.sdk.experiences.appscreens.network.AppScreensDocumentClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds

/**
 * Owns the App Screens navigation session model: the warm-session pool, the root/sheet stacks, and
 * the per-screen load pipelines. Registered as a process singleton and driven entirely from the main
 * thread (all public methods, and the pipelines, run on [Dispatchers.Main]); network legs suspend
 * onto IO inside the clients and resume back on main.
 *
 * The pure stack/selection algorithm lives in [NavigatorModel]; this class supplies the real session
 * factory (WebView + bridge), reacts to each navigation outcome by launching the appropriate
 * pipeline (cold for create/ephemeral, warm-reuse for reuse), and reconciles pops back from the
 * NavHosts — cancelling in-flight per-entry work and destroying only ephemeral WebViews while
 * leaving popped template sessions warm for later reuse.
 *
 * An ephemeral's WebView is NOT destroyed at pop-commit but deferred to composition disposal: the
 * popped host keeps rendering through the ~300ms pop-exit slide, so destroying its WebView eagerly
 * would blank the exiting screen and flash the window background. [releaseEntries] parks ephemerals in
 * the model's pending-disposal map and [onEntryDisposed] reclaims and destroys them when Compose
 * disposes the exiting host. A belt-and-braces sweep in [establish]/[teardown] drains any straggler.
 */
internal class AppScreenNavigator(
    private val appContext: Context,
    private val loader: AppScreenLoader,
    private val scopeRegistry: AppScreenScopeRegistry
) {
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val model = NavigatorModel<AppScreenSession> { templatePath -> createSession(templatePath) }

    /** In-flight pipeline job per entry, so a pop can cancel exactly that screen's work. */
    private val jobs = HashMap<String, Job>()

    /** Origin(s) the bridge trusts; set from the entry URL in [start] (single associated host). */
    private var allowedOrigins: Set<String> = emptySet()

    /**
     * Whether prewarming from `links` hints is active. Defaults on; disabled per-presentation by a
     * `roverPrewarm=off` query parameter on the entry URL (stripped before use) — an internal,
     * bench/adb-drivable switch used to measure cold-vs-warm first-tap latency. No public API.
     */
    private var prewarmEnabled: Boolean = true

    /** Weakly-held current host decor view, for the [PrewarmAttachStrategy.DecorAttached] hedge. */
    private var currentDecorView: java.lang.ref.WeakReference<ViewGroup> =
        java.lang.ref.WeakReference(null)

    /** Boots runtimes ahead of a tap from the `links` hints the runtime posts after each render. */
    private val prewarmer = AppScreenPrewarmer(
        scope = scope,
        createSession = { templatePath -> createSession(templatePath) },
        loadDocument = { url -> loader.loadDocument(url) },
        loadShell = { session, href, html -> loadShell(session, href, html) },
        registerWarm = { templatePath, session ->
            // Claim guard at prewarm promotion: a surface change or appearance flip during the boot
            // cancels in-flight prewarms (establish / onActiveAppearanceChanged both cancelAll), but
            // settle any race by refusing to pool a session whose scheme no longer matches the active
            // surface. Returning false makes the prewarmer destroy the redundant session.
            if (session.forcedDark != activeForcedDark) {
                false
            } else {
                model.registerWarm(templatePath, session)
            }
        },
        isTemplateKnown = { templatePath -> model.isTemplateKnown(templatePath) },
        decorViewProvider = { currentDecorView.get() }
    )

    val rootStack: SnapshotStateList<NavigatorModel.Entry<AppScreenSession>> get() = model.rootStack
    val sheetStack: SnapshotStateList<NavigatorModel.Entry<AppScreenSession>> get() = model.sheetStack

    fun sessionFor(entryId: String): AppScreenSession? = model.sessionFor(entryId)

    /**
     * The live App Screens surfaces (host composition trees), oldest first, newest (the current
     * presentation) last. Because the navigator is a process singleton, more than one surface can be
     * composed at once — e.g. a standalone [android.content.Intent.ACTION_VIEW] presentation opened
     * over a still-composed Hub home tab. Each surface is identified by an opaque token supplied by
     * its [AppScreen] composable. The topmost surface owns the presentation (and the shared warm
     * WebView); when it is dismissed the navigator re-establishes the surface beneath it (see
     * [release]).
     */
    private val surfaces = ArrayDeque<Surface>()

    /**
     * A registered App Screens surface: an opaque [token] identifying a host tree, its [entryUrl],
     * and the HOST-PROVIDED colour-scheme override for that surface ([forcedDark]; null = follow the
     * device). The override is supplied by the host per surface (Hub-only policy — the Hub passes its
     * config colorScheme, standalone presentations pass null) and is mutable so a live change to a
     * composed surface (a Hub config flip) can be recorded via [updateSurfaceAppearance].
     */
    private class Surface(val token: Any, val entryUrl: Uri, var forcedDark: Boolean?)

    /**
     * The forced-dark scheme of the surface that currently owns the presentation (the topmost), or
     * null when nothing is presented or that surface follows the device. This is the appearance every
     * session created for — or claimed by — the active presentation must carry. It is HOST-provided
     * per surface (see [Surface.forcedDark]); App Screens no longer reads the remote config for it.
     */
    private val activeForcedDark: Boolean?
        get() = surfaces.lastOrNull()?.forcedDark

    /**
     * The token of the surface that currently owns the presentation. Compose state, read by each
     * [AppScreensRoot] to decide whether it is the active surface: only the active one binds the
     * shared WebView into its host, so a covered surface never contends for it (which would thrash the
     * WebView between two host slots and stall the runtime). Null when nothing is presented.
     */
    val activeSurfaceToken: State<Any?> get() = activeSurfaceTokenState
    private val activeSurfaceTokenState = mutableStateOf<Any?>(null)

    // region public navigation surface (main thread)

    /**
     * Register (or, for a known [token], re-assert) the surface identified by [token] as the current
     * presentation and establish the App Screens view rooted at [entryUrl]. The later presentation
     * steals: an already-registered surface is moved to the top and its WebView re-bound to it.
     *
     * iOS v1 parity for the warm multi-surface case: presenting the same template in a second surface
     * hands the shared warm session's WebView to the NEW host (see [establish]/[stealForNewHost]); the
     * covered surface shows a neutral state until it re-steals on reappear ([release] re-establishes it).
     *
     * [forcedDark] is the host's colour-scheme override for THIS surface (Hub-only policy; null =
     * follow the device). It becomes the active appearance while this surface is topmost and is
     * carried into every session the presentation builds.
     */
    fun present(token: Any, entryUrl: Uri, forcedDark: Boolean?) {
        surfaces.removeAll { it.token === token }
        surfaces.addLast(Surface(token, entryUrl, forcedDark))
        activeSurfaceTokenState.value = token
        establish(entryUrl)
    }

    /**
     * A composed surface's HOST-provided colour-scheme override changed — the Hub flipped its config
     * colorScheme while its embedded App Screen stayed composed (standalone surfaces are fixed to the
     * device and never call this with a new value). Record the new per-surface [forcedDark]; no-op if
     * unchanged (this is called on every recomposition of the surface's appearance effect, including
     * the first, where present() already carried the value).
     *
     * When [token] is the ACTIVE (topmost) surface, re-resolve every live session to the new scheme
     * via [onActiveAppearanceChanged] — Chromium only re-evaluates `prefers-color-scheme` on an
     * attach/configuration event, so a silent context swap would not repaint a composed page and the
     * sessions must be rebuilt. A background (covered) surface just stores the value; it takes effect
     * when that surface is next re-established (see [release]).
     */
    fun updateSurfaceAppearance(token: Any, forcedDark: Boolean?) {
        val surface = surfaces.firstOrNull { it.token === token } ?: return
        if (surface.forcedDark == forcedDark) return
        surface.forcedDark = forcedDark
        if (surfaces.lastOrNull()?.token === token) {
            onActiveAppearanceChanged()
        }
    }

    /**
     * Unregister the surface identified by [token] (its host tree left composition). If it was the
     * current (topmost) presentation and another surface remains, the navigator re-establishes that
     * surface — re-stealing the shared warm session's WebView back to the reappearing host rather than
     * leaving it blank. If no surface remains, the presentation is fully torn down.
     */
    fun release(token: Any) {
        val wasTop = surfaces.lastOrNull()?.token === token
        surfaces.removeAll { it.token === token }
        val next = surfaces.lastOrNull()
        when {
            !wasTop -> return // A background surface left; the current presentation is unaffected.
            next != null -> {
                log.i("App Screen surface released; re-establishing covered surface entryUrl=${next.entryUrl}")
                activeSurfaceTokenState.value = next.token
                establish(next.entryUrl)
            }
            else -> teardown()
        }
    }

    /**
     * Reset any prior presentation and establish the App Screens view rooted at [entryUrl]. Warm
     * sessions survive the reset and may be reused for the new master; when the master session is one
     * reused across surfaces its WebView is stolen to the new host and it is dropped to the loading
     * phase (see [stealForNewHost]).
     */
    private fun establish(entryUrl: Uri) {
        // Belt-and-braces: destroy any ephemeral parked for deferred disposal whose host never
        // disposed (an unexpected leak) before starting fresh. The idempotent guards below make this
        // harmless when the host did dispose normally.
        drainPendingDisposals(reason = "establish")
        // Reset any prior presentation (keeps the warm pool, defers ephemeral destruction, cancels jobs).
        prewarmer.cancelAll()
        releaseEntries(model.teardown(), reason = "reset")

        // Internal, bench/adb-drivable switch: `roverPrewarm=off` disables prewarming for this
        // presentation. Stripped from the URL so it affects neither the template identity nor the
        // network requests.
        val effectiveUrl = consumePrewarmSwitch(entryUrl)

        allowedOrigins = originOf(effectiveUrl)
        val templatePath = AppScreensDecisions.templatePath(effectiveUrl)
        val outcome = model.start(templatePath, effectiveUrl.toString(), optimisticDataJson = null)
        val session = outcome.entry.session
        stealForNewHost(session)
        logPushed(outcome)

        // Claim guard (surface crossing): warm sessions survive across surfaces, so the master this
        // presentation just reused may carry a DIFFERENT surface's forced scheme than the one now
        // active (e.g. the still-composed dark Hub master reused for a light standalone presentation).
        // It is now the composed master, so rebuild it in place to the active scheme through the
        // existing recovery machinery and cold-load it — a warm reuse would show the wrong appearance,
        // and its runtime is gone after the rebuild so it cannot be morphed in place.
        if (outcome.selection == AppScreensDecisions.SessionSelection.Reuse &&
            session.forcedDark != activeForcedDark
        ) {
            log.i(
                "App Screen establish: reused master scheme mismatch " +
                    "(was ${session.forcedDark}, active $activeForcedDark); rebuilding for this surface"
            )
            session.updateForcedDark(activeForcedDark)
            rebuildWebView(session)
            jobs.remove(outcome.entry.entryId)?.cancel()
            jobs[outcome.entry.entryId] = scope.launch {
                runColdPipeline(session, outcome.entry.href, outcome.entry.optimisticDataJson)
            }
            return
        }

        launchPipeline(outcome)
    }

    /**
     * Prepare [session] to be (re)bound to the freshly-presented surface's host: detach its WebView
     * from any previous surface's slot so the new host attaches it cleanly (a warm session reused
     * across surfaces is still parented to the covered host's FrameLayout), and drop it back to the
     * loading phase so the new host shows a neutral skeleton until its show resolves and reveals. A
     * cold/first master has no parent and is already loading, so this is a no-op there.
     */
    private fun stealForNewHost(session: AppScreenSession) {
        (session.webView.parent as? ViewGroup)?.removeView(session.webView)
        session.phase.value = AppScreenPhase.Loading
    }

    /**
     * Reads and strips the internal `roverPrewarm` query parameter, updating [prewarmEnabled].
     * Returns the URL with that parameter removed (all others preserved in order).
     */
    private fun consumePrewarmSwitch(entryUrl: Uri): Uri {
        val names = try {
            entryUrl.queryParameterNames
        } catch (e: UnsupportedOperationException) {
            emptySet<String>()
        }
        if (SWITCH_PARAM !in names) {
            prewarmEnabled = true
            return entryUrl
        }
        prewarmEnabled = entryUrl.getQueryParameter(SWITCH_PARAM) != SWITCH_OFF
        log.i("App Screen prewarm ${if (prewarmEnabled) "enabled" else "DISABLED"} via $SWITCH_PARAM")
        val rebuilt = entryUrl.buildUpon().clearQuery()
        for (name in names) {
            if (name == SWITCH_PARAM) continue
            for (value in entryUrl.getQueryParameters(name)) {
                rebuilt.appendQueryParameter(name, value)
            }
        }
        return rebuilt.build()
    }

    /** Handle a `navigate` bridge message that arrived from [fromSession]'s runtime. */
    fun onBridgeNavigate(fromSession: AppScreenSession, message: BridgeMessage.Navigate) {
        // Timestamp the tap the instant it lands, to measure tap→painted latency.
        val tapAt = SystemClock.uptimeMillis()
        val base = Uri.parse(fromSession.currentHref)
        val resolved = AppScreensDecisions.resolveHref(base, message.href)
        if (resolved == null) {
            log.w("App Screen navigate: unresolvable href '${message.href}' from ${fromSession.currentHref}")
            return
        }
        val templatePath = AppScreensDecisions.templatePath(resolved)
        // Claim guard (surface crossing): a warm-idle session pooled under a different surface's
        // scheme must not be reused for the active surface — its WebView carries the wrong appearance.
        // Discard it here so this navigation falls through to a fresh cold load against the active
        // scheme (rebuild-in-place is only wired for composed sessions; an idle one is cheapest as a
        // cold load). No-op when the pooled session already matches or none exists.
        discardMismatchedWarmIdle(templatePath)
        val fromSheet = sheetStack.any { it.session === fromSession }
        val outcome = model.navigate(
            templatePath = templatePath,
            href = resolved.toString(),
            optimisticDataJson = message.optimisticData,
            transition = message.transition,
            fromSheet = fromSheet
        )
        logPushed(outcome)
        launchPipeline(outcome, tapAt = tapAt)
    }

    /** Handle a `links` prefetch-hint message that arrived from [fromSession]'s runtime. */
    fun onBridgeLinks(fromSession: AppScreenSession, message: BridgeMessage.Links) {
        if (!prewarmEnabled) {
            log.i("App Screen links hint (${message.hrefs.size}) ignored: prewarm disabled")
            return
        }
        val base = Uri.parse(fromSession.currentHref)
        val candidates = message.hrefs.mapNotNull { href ->
            val resolved = AppScreensDecisions.resolveHref(base, href) ?: return@mapNotNull null
            if (!AppScreensDecisions.isAppScreenUrl(resolved)) return@mapNotNull null
            AppScreensDecisions.templatePath(resolved) to resolved.toString()
        }
        if (candidates.isEmpty()) return
        prewarmer.hint(candidates)
    }

    /** Record the current host's decor view (for the DecorAttached prewarm strategy hedge). */
    fun setCurrentDecorView(decorView: ViewGroup?) {
        if (currentDecorView.get() !== decorView) {
            currentDecorView = java.lang.ref.WeakReference(decorView)
        }
    }

    /** Reconcile a NavHost-committed root pop: release the removed entries. */
    fun onPopped(entryIds: List<String>) {
        releaseEntries(model.onPopped(entryIds), reason = "popped")
    }

    /** Reconcile an in-sheet pop. */
    fun onSheetPopped(entryIds: List<String>) {
        releaseEntries(model.onSheetPopped(entryIds), reason = "sheet-popped")
    }

    /** The sheet was dismissed (swipe/scrim/back at its root): release its whole stack. */
    fun onSheetDismissed() {
        val removed = model.onSheetDismissed()
        log.i("App Screen sheet dismissed, releasing ${removed.size} entr${if (removed.size == 1) "y" else "ies"}")
        releaseEntries(removed, reason = "sheet-dismissed")
    }

    /**
     * Retry a failed screen by re-running its cold pipeline from scratch. Clears the M6 one-attempt
     * recovery flag (so a fresh crash-and-recover cycle is allowed) and the dead flag. When the
     * failure came from a renderer crash the WebView was already rebuilt live at classification
     * time, so the cold pipeline reloads into a healthy WebView.
     */
    fun retry(entryId: String) {
        val entry = rootStack.firstOrNull { it.entryId == entryId }
            ?: sheetStack.firstOrNull { it.entryId == entryId }
            ?: return
        jobs.remove(entryId)?.cancel()
        entry.session.didAttemptRecovery = false
        entry.session.dead.value = false
        entry.session.phase.value = AppScreenPhase.Loading
        jobs[entryId] = scope.launch { runColdPipeline(entry.session, entry.href, entry.optimisticDataJson) }
    }

    /** The last surface left composition: clear stacks and surfaces. Warm sessions are preserved. */
    private fun teardown() {
        surfaces.clear()
        activeSurfaceTokenState.value = null
        prewarmer.cancelAll()
        releaseEntries(model.teardown(), reason = "teardown")
        // Belt-and-braces: destroy any ephemeral parked for deferred disposal whose host never
        // disposed (idempotent guards make this harmless when disposal already fired).
        drainPendingDisposals(reason = "teardown")
    }

    // endregion

    private fun launchPipeline(
        outcome: NavigatorModel.NavOutcome<AppScreenSession>,
        tapAt: Long? = null
    ) {
        val entry = outcome.entry
        val session = entry.session
        val selection = outcome.selection
        val job = scope.launch {
            when (selection) {
                AppScreensDecisions.SessionSelection.Reuse ->
                    runWarmReuse(session, entry.href, entry.optimisticDataJson, tapAt, selection)
                AppScreensDecisions.SessionSelection.Create,
                AppScreensDecisions.SessionSelection.Ephemeral ->
                    runColdPipeline(session, entry.href, entry.optimisticDataJson, tapAt, selection)
            }
        }
        jobs[entry.entryId] = job
    }

    /**
     * One tap→painted telemetry line per navigation, emitted when the pushed screen's first `show`
     * resolves (the reveal). No-op for [tapAt] == null (a [start], which is not a tap).
     */
    private fun logTapToPainted(
        tapAt: Long?,
        selection: AppScreensDecisions.SessionSelection,
        templatePath: String
    ) {
        if (tapAt == null) return
        val elapsed = SystemClock.uptimeMillis() - tapAt
        log.i("App Screen tap→painted ${elapsed}ms (selection=$selection, template=$templatePath)")
    }

    private fun releaseEntries(entries: List<NavigatorModel.Entry<AppScreenSession>>, reason: String) {
        for (entry in entries) {
            jobs.remove(entry.entryId)?.cancel()
            if (entry.isEphemeral) {
                // Defer WebView destruction to composition disposal ([onEntryDisposed]): the popped
                // host keeps rendering through the ~300ms pop-exit slide, so destroying now would
                // blank the exiting screen and flash the window background. Ephemerals were never in
                // the warm pool, so there is nothing to forget here.
                log.i("App Screen $reason: deferring ephemeral session destruction to disposal entry=${entry.entryId} template=${entry.templatePath}")
                model.deferDisposal(entry)
            } else {
                log.i("App Screen $reason: entry=${entry.entryId} template=${entry.templatePath} left warm")
            }
        }
    }

    /**
     * Reclaim and destroy an ephemeral session parked by [releaseEntries], invoked when Compose
     * disposes the exiting host (the pop-exit transition finished) — the teardown signal, with no
     * timers. No-op when [entryId] was never deferred or was already reclaimed (the model's
     * [NavigatorModel.takeDisposed] returns it exactly once). Idempotent against a concurrent
     * renderer-gone [discardWarm] of the same session: both guard on [AppScreenSession.isDestroyed],
     * so whichever runs second is a no-op.
     */
    fun onEntryDisposed(entryId: String) {
        val entry = model.takeDisposed(entryId) ?: return
        val session = entry.session
        if (session.isDestroyed) {
            log.i("App Screen disposal: ephemeral session already destroyed entry=$entryId template=${entry.templatePath}")
            return
        }
        session.isDestroyed = true
        session.bridge.fail(RenderProcessGoneException())
        val dead = session.webView
        (dead.parent as? ViewGroup)?.removeView(dead)
        dead.destroy()
        log.i("App Screen disposal: destroyed deferred ephemeral session entry=$entryId template=${entry.templatePath}")
    }

    /**
     * Drain and destroy every ephemeral still parked for deferred disposal — the belt-and-braces
     * sweep run at [establish]/[teardown] for any host that never disposed. Skips sessions already
     * destroyed by a normal [onEntryDisposed] or a renderer-gone [discardWarm], so double-invocation
     * is harmless.
     */
    private fun drainPendingDisposals(reason: String) {
        for (entry in model.drainPendingDisposal()) {
            val session = entry.session
            if (session.isDestroyed) continue
            session.isDestroyed = true
            session.bridge.fail(RenderProcessGoneException())
            val dead = session.webView
            (dead.parent as? ViewGroup)?.removeView(dead)
            dead.destroy()
            log.i("App Screen $reason sweep: destroyed stranded ephemeral session entry=${entry.entryId} template=${entry.templatePath}")
        }
    }

    // region session factory

    private fun createSession(templatePath: String): AppScreenSession {
        // Resolve the forced scheme at build time (covers prewarm, which uses the same factory): the
        // construction context is what the WebView resolves prefers-color-scheme through until its
        // first attach (prewarm, warm pool, initial load), so the active surface's HOST-provided
        // override must be applied now for the page to boot in the right scheme.
        val resolvedForcedDark = activeForcedDark
        val (webView, bridge) = buildWebView(resolvedForcedDark)
        // The factory constructs the WebView over a MutableContextWrapper whose base is a neutral
        // (Activity-free) themed context; capture it so the host can swap back to it on detach.
        val themed = (webView.context as MutableContextWrapper).baseContext
        val session = AppScreenSession(
            templatePath = templatePath,
            initialWebView = webView,
            initialBridge = bridge,
            initialAppThemedContext = themed,
            initialForcedDark = resolvedForcedDark
        )
        wireSession(session)
        return session
    }

    /**
     * Build a fresh WebView (factory-installed clients) with its construction context forced to
     * [forcedDark] (null = follow the device), and the bridge installed on it.
     */
    private fun buildWebView(forcedDark: Boolean?): Pair<WebView, AppScreenBridge> {
        val webView = AppScreenWebViewFactory.create(appContext, forcedDark)
        val bridge = AppScreenBridge.install(webView, allowedOrigins)
            ?: throw IllegalStateException("WebView message-listener feature unavailable")
        return webView to bridge
    }

    /**
     * Wire the current WebView's bridge and renderer-death client to route to [session]. Re-run
     * after a recovery swap so the fresh WebView reports back to the same session (M6).
     */
    private fun wireSession(session: AppScreenSession) {
        session.bridge.onNavigate = { message -> onBridgeNavigate(session, message) }
        session.bridge.onLinks = { message -> onBridgeLinks(session, message) }
        (session.webView.webViewClient as? AppScreenWebViewClient)?.onRenderProcessGone =
            { view, didCrash -> onRenderProcessGone(session, view, didCrash) }
    }

    // endregion

    // region renderer-death recovery (M6)

    /**
     * A WebView renderer died — routed here (main thread) per affected session, so this runs once
     * per live session on a shared-renderer crash (the "thundering herd"). Classifies [deadSession]
     * and applies the fixed recovery policy. Idempotent: a stale callback for an already-replaced or
     * already-destroyed WebView is dropped via the [view] identity / [AppScreenSession.isDestroyed]
     * guards.
     */
    private fun onRenderProcessGone(
        deadSession: AppScreenSession,
        view: WebView,
        didCrash: Boolean
    ) {
        log.w("App Screen renderer gone (didCrash=$didCrash) template=${deadSession.templatePath}")

        if (deadSession.isDestroyed) {
            log.i("App Screen renderer-gone ignored: session already destroyed (template=${deadSession.templatePath})")
            return
        }
        if (view !== deadSession.webView) {
            log.i("App Screen renderer-gone ignored: stale callback for an already-replaced WebView (template=${deadSession.templatePath})")
            return
        }

        // A still-prewarming session isn't on any stack yet and has no user-facing surface: cancel
        // its boot and discard it. A later links hint may re-prewarm the template.
        if (prewarmer.ownsSession(deadSession)) {
            log.i("App Screen renderer-gone: classify=Prewarming → discard (template=${deadSession.templatePath})")
            prewarmer.discard(deadSession)
            return
        }

        val liveness = model.livenessOf(deadSession)
        val action = AppScreensDecisions.recoveryAction(liveness, deadSession.didAttemptRecovery)
        log.i("App Screen renderer-gone: classify=$liveness attempted=${deadSession.didAttemptRecovery} → $action (template=${deadSession.templatePath})")
        when (action) {
            AppScreensDecisions.RecoveryAction.RecoverNow -> {
                val entry = entryFor(deadSession) ?: return
                recoverSession(entry, alreadyRebuilt = false)
            }
            AppScreensDecisions.RecoveryAction.ShowError -> {
                // One attempt already burned: rebuild so Retry has a live WebView, then error out.
                rebuildWebView(deadSession)
                setError(deadSession, RenderProcessGoneException(didCrash))
            }
            AppScreensDecisions.RecoveryAction.MarkDead -> markDead(deadSession)
            AppScreensDecisions.RecoveryAction.Discard -> discardWarm(deadSession)
        }
    }

    /**
     * A live-but-broken page rejected a `show` (M2's [ShowRejectedException]). Treated as the same
     * liveness signal as a renderer crash and funnelled through the one-attempt recovery policy,
     * rather than going straight to the error state.
     */
    private fun onShowRejected(session: AppScreenSession, cause: ShowRejectedException) {
        val liveness = model.livenessOf(session)
        val action = AppScreensDecisions.recoveryAction(liveness, session.didAttemptRecovery)
        log.w("App Screen show rejected: classify=$liveness attempted=${session.didAttemptRecovery} → $action (template=${session.templatePath})")
        when (action) {
            AppScreensDecisions.RecoveryAction.RecoverNow -> {
                val entry = entryFor(session) ?: run { setError(session, cause); return }
                recoverSession(entry, alreadyRebuilt = false)
            }
            AppScreensDecisions.RecoveryAction.ShowError -> setError(session, cause)
            // A show only runs for an active (visible-bound) push, so the off-screen classifications
            // are not expected here; fail safe to the error state rather than silently dropping.
            AppScreensDecisions.RecoveryAction.MarkDead,
            AppScreensDecisions.RecoveryAction.Discard -> setError(session, cause)
        }
    }

    /**
     * Recover [entry]'s session: (re)build its WebView unless [alreadyRebuilt], reset to the loading
     * phase, and launch the recovery pipeline (replay of [AppScreenSession.lastShowPayload], or a
     * fresh cold load when nothing was ever shown). Sets the one-attempt flag; a clean run through
     * reveal clears it.
     */
    private fun recoverSession(entry: NavigatorModel.Entry<AppScreenSession>, alreadyRebuilt: Boolean) {
        val session = entry.session
        session.didAttemptRecovery = true
        session.dead.value = false
        if (!alreadyRebuilt) rebuildWebView(session)
        session.phase.value = AppScreenPhase.Loading
        session.state = AppScreenSession.State.LoadingDocument
        jobs.remove(entry.entryId)?.cancel()
        log.i("App Screen recovery starting template=${session.templatePath} href=${entry.href} replay=${session.lastShowPayload != null}")
        jobs[entry.entryId] = scope.launch {
            if (session.lastShowPayload != null) {
                runRecoveryPipeline(session, entry)
            } else {
                // Nothing was ever shown (crash during the very first load): a full cold load is the
                // recovery. Reveal clears didAttemptRecovery like any clean pipeline.
                runColdPipeline(session, entry.href, entry.optimisticDataJson)
            }
        }
    }

    /**
     * The recovery load pipeline: refetch the document (the OkHttp cache typically serves it),
     * reload the shell into the freshly-built WebView, await the runtime, re-inject insets, then
     * replay the last successful show in one call (repainting the full pre-crash content) and
     * re-reveal. If the retained payload's templateHash no longer matches the refetched document
     * (a rebake since the crash), the normal one-shot revalidation handshake runs first.
     */
    private suspend fun runRecoveryPipeline(
        session: AppScreenSession,
        entry: NavigatorModel.Entry<AppScreenSession>
    ) {
        val payload = session.lastShowPayload
        val href = payload?.href ?: entry.href
        try {
            session.currentHref = href
            val url = Uri.parse(href)

            var document = withTimeout(DOCUMENT_TIMEOUT) { loader.loadDocument(url) }
            if (payload?.templateHash != null &&
                AppScreensDecisions.handshake(document.etag, payload.templateHash, alreadyRetried = false)
                is AppScreensDecisions.HandshakeDecision.RefetchDocumentOnce
            ) {
                log.i("App Screen recovery: template rebaked since crash (etag=${document.etag} hash=${payload.templateHash}); revalidating document")
                document = withTimeout(DOCUMENT_TIMEOUT) {
                    loader.loadDocument(url, AppScreensDocumentClient.DocumentCachePolicy.ForceRevalidate)
                }
            }
            session.documentETag = document.etag
            session.dataScope = AppScreensDecisions.effectiveScope(document.dataScope)
            session.state = AppScreenSession.State.AwaitingRuntime

            loadShell(session, href, document.html)
            withTimeout(LOADED_TIMEOUT) { session.bridge.awaitLoaded() }
            session.runtimeLoaded = true
            session.reinjectInsets()

            val replayArgs = (payload ?: error("recovery pipeline requires a payload")).toArgs()
            val result = session.bridge.call("show", replayArgs, SHOW_TIMEOUT)
            log.i("App Screen recovery replay show resolved: hydrateMs=${result.opt("hydrateMs")}")
            session.webView.scrollTo(0, 0)
            session.state = AppScreenSession.State.Ready
            session.phase.value = AppScreenPhase.Revealed
            session.didAttemptRecovery = false
            log.i("App Screen recovery SUCCEEDED template=${session.templatePath}")
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            log.e("App Screen recovery FAILED template=${session.templatePath}: $error")
            session.state = AppScreenSession.State.Failed
            session.phase.value = AppScreenPhase.Error(error)
        }
    }

    /**
     * Fail the dead bridge's pending calls, detach + destroy the dead WebView, and install a fresh
     * WebView + bridge on [session] (a dead WebView cannot be reloaded on Android). Re-wires the new
     * bridge/renderer-client to the session. Main thread.
     */
    private fun rebuildWebView(session: AppScreenSession) {
        session.bridge.fail(RenderProcessGoneException())
        val dead = session.webView
        (dead.parent as? ViewGroup)?.removeView(dead)
        dead.destroy()
        // Rebuild over the session's current forced scheme: unchanged for a crash recovery, and the
        // already-updated new scheme when a host appearance change drove the rebuild
        // ([onActiveAppearanceChanged], or the surface-crossing claim guard in [establish]).
        val (webView, bridge) = buildWebView(session.forcedDark)
        session.replaceWebView(webView, bridge)
        wireSession(session)
        session.runtimeLoaded = false
        session.state = AppScreenSession.State.LoadingDocument
        // The fresh document will have no injected inline safe-area props; let the host re-inject.
        session.lastInjectedInsets = null
    }

    /**
     * Mark an off-screen stack session dead: rebuild its (crashed) WebView now so its slot holds a
     * valid blank view, drop it back to the loading phase, and flag it so the host lazy-recovers the
     * moment it becomes visible again (a pop-to / sheet dismissal re-composes it). [lastShowPayload]
     * is preserved for that replay.
     */
    private fun markDead(session: AppScreenSession) {
        rebuildWebView(session)
        session.phase.value = AppScreenPhase.Loading
        session.dead.value = true
        log.i("App Screen marked DEAD (off-screen) template=${session.templatePath}; will lazy-recover on reveal")
    }

    /**
     * Claim guard for a warm-idle session about to be reused by the active surface. Warm sessions
     * survive across surfaces (a standalone presentation over the still-composed Hub), so the session
     * pooled for [templatePath] may carry a DIFFERENT surface's forced scheme than the one now active.
     * A warm-idle WebView carrying the wrong appearance can't be re-themed in place (that machinery is
     * wired only for composed sessions), so discard it — destroy + forget — and let the caller's
     * navigation fall through to a fresh cold load against the active scheme. No-op when the pooled
     * session already matches the active scheme (the common single-surface case) or none is idle.
     */
    private fun discardMismatchedWarmIdle(templatePath: String) {
        val warm = model.warmIdleSessionFor(templatePath) ?: return
        if (warm.forcedDark == activeForcedDark) return
        log.i(
            "App Screen claim guard: discarding warm-idle session template=$templatePath " +
                "(scheme ${warm.forcedDark} != active $activeForcedDark) so it cold-loads for this surface"
        )
        discardWarm(warm)
    }

    /** Discard a warm-idle session whose renderer died: detach, destroy, and forget it. */
    private fun discardWarm(session: AppScreenSession) {
        session.isDestroyed = true
        session.bridge.fail(RenderProcessGoneException())
        model.forgetWarm(session)
        val dead = session.webView
        (dead.parent as? ViewGroup)?.removeView(dead)
        dead.destroy()
        log.i("App Screen discarded warm-idle session on renderer-gone template=${session.templatePath}")
    }

    private fun setError(session: AppScreenSession, cause: Throwable) {
        session.state = AppScreenSession.State.Failed
        session.phase.value = AppScreenPhase.Error(cause)
    }

    /** The stack entry (root or sheet) currently backed by [session], or null if it is off-stack. */
    private fun entryFor(session: AppScreenSession): NavigatorModel.Entry<AppScreenSession>? =
        rootStack.firstOrNull { it.session === session }
            ?: sheetStack.firstOrNull { it.session === session }

    /**
     * Run the deferred recovery for a session marked dead while off-screen, invoked by the host when
     * it becomes visible again (M6). No-op if the entry is gone or the session is no longer dead
     * (already recovered / re-entered), making repeated host calls safe.
     */
    fun lazyRecover(entryId: String) {
        val entry = rootStack.firstOrNull { it.entryId == entryId }
            ?: sheetStack.firstOrNull { it.entryId == entryId }
            ?: return
        if (!entry.session.dead.value) return
        log.i("App Screen lazy recovery at reveal template=${entry.session.templatePath}")
        recoverSession(entry, alreadyRebuilt = true)
    }

    // endregion

    // region host-driven appearance change

    /**
     * The active surface's HOST-provided colour-scheme override changed (the Hub flipped its config
     * colorScheme while its embedded App Screen stayed composed; standalone surfaces are fixed to the
     * device and never trigger this). Every existing WebView resolves the OLD scheme through its
     * current contexts, and swapping those contexts silently would not make Chromium re-evaluate
     * `prefers-color-scheme` (it re-evaluates on attach/configuration events, not on a wrapper base
     * swap) — so re-resolve the whole session model to the new scheme instead.
     * Warm-idle sessions (no surface) are simply discarded — a later navigation
     * recreates them against the new scheme through the normal factory. Composed (on-stack) sessions
     * are rebuilt in place through the EXISTING renderer-recovery machinery ([recoverSession] →
     * [rebuildWebView] → replay/cold pipeline): the same session object is kept, so its recorded
     * scheme is updated first and the rebuild reconstructs the WebView over the new forced uiMode,
     * reloads the shell, re-injects insets, and replays the last show. In-flight prewarms booted
     * against the old scheme are cancelled; fresh hints re-prewarm against the new one.
     *
     * Config flips (the override's upstream in the Hub) are rare, so the rebuild cost is acceptable
     * and keeps App Screens in lockstep with the Hub's Material theme flip. Main thread.
     */
    private fun onActiveAppearanceChanged() {
        val newForcedDark = activeForcedDark
        log.i("App Screen active surface appearance changed → forcedDark=$newForcedDark; re-resolving sessions")

        // Cancel prewarms whose WebViews carry the old scheme (they are not yet in the warm pool).
        prewarmer.cancelAll()

        // Discard warm-idle sessions (built for the old scheme, no surface): destroy + forget.
        for (session in model.warmIdleSessions()) {
            discardWarm(session)
        }

        // Rebuild the composed sessions (root + sheet) in place to the new scheme. Distinct by
        // identity: a session is on at most one stack, but guard against ever rebuilding one twice.
        val composedEntries = (rootStack + sheetStack).distinctBy { it.session }
        for (entry in composedEntries) {
            if (entry.session.isDestroyed) continue
            entry.session.updateForcedDark(newForcedDark)
            recoverSession(entry, alreadyRebuilt = false)
        }
    }

    // endregion

    // region pipelines

    /**
     * Cold pipeline: document → load → loaded → show(href[,optimisticData]) → reveal → json → handshake →
     * show(morph). If the scope is already known (session or registry), the `.json` fetch is fired
     * concurrently with the document load; otherwise it waits until the scope is read off the
     * document.
     */
    private suspend fun runColdPipeline(
        session: AppScreenSession,
        href: String,
        optimisticDataJson: String?,
        tapAt: Long? = null,
        selection: AppScreensDecisions.SessionSelection = AppScreensDecisions.SessionSelection.Create
    ) {
        try {
            session.currentHref = href
            session.phase.value = AppScreenPhase.Loading
            session.state = AppScreenSession.State.LoadingDocument
            val url = Uri.parse(href)

            coroutineScope {
                val knownScope = session.dataScope ?: scopeRegistry.scopeFor(session.templatePath)
                val eagerData: Deferred<io.rover.sdk.experiences.appscreens.network.AppScreenDataEnvelope>? =
                    if (knownScope != null) {
                        log.i("App Screen cold: scope known ($knownScope) — firing .json concurrently for $href")
                        async { withTimeout(JSON_TIMEOUT) { loader.loadScreenData(url, knownScope) } }
                    } else {
                        null
                    }

                // Document channel (always anonymous); records scope in the registry.
                val document = withTimeout(DOCUMENT_TIMEOUT) { loader.loadDocument(url) }
                val effectiveScope = AppScreensDecisions.effectiveScope(document.dataScope)
                session.documentETag = document.etag
                session.dataScope = effectiveScope
                session.state = AppScreenSession.State.AwaitingRuntime

                // Load the shell and await the runtime boot.
                loadShell(session, href, document.html)
                withTimeout(LOADED_TIMEOUT) { session.bridge.awaitLoaded() }
                session.runtimeLoaded = true
                // A reload wiped the injected inline safe-area properties; re-apply the last known.
                session.reinjectInsets()

                val dataDeferred = eagerData ?: async {
                    withTimeout(JSON_TIMEOUT) { loader.loadScreenData(url, effectiveScope) }
                }

                // Optimistic paint / hydrate gates the reveal.
                val hydrateArgs = AppScreenShowArgs.build(href, optimisticDataJson = optimisticDataJson)
                val hydrateResult = session.bridge.call("show", hydrateArgs, SHOW_TIMEOUT)
                session.lastShowPayload = ShowPayload(href, optimisticDataJson, responseJson = null, templateHash = null)
                log.i("App Screen hydrate show resolved (optimisticData=${optimisticDataJson != null}): hydrateMs=${hydrateResult.opt("hydrateMs")}")
                session.webView.scrollTo(0, 0)
                session.state = AppScreenSession.State.Ready
                session.phase.value = AppScreenPhase.Revealed
                session.didAttemptRecovery = false
                logTapToPainted(tapAt, selection, session.templatePath)

                // Morph show updates in place once data has landed and reconciled.
                val envelope = dataDeferred.await()
                val reconciled = loader.reconcileScreenData(envelope, session.documentETag) {
                    refetchDocument(session, href)
                }
                session.documentETag = reconciled.documentETag
                val morphArgs = AppScreenShowArgs.build(href, optimisticDataJson, reconciled.envelope.rawJson)
                val morphResult = session.bridge.call("show", morphArgs, SHOW_TIMEOUT)
                session.lastShowPayload = ShowPayload(
                    href, optimisticDataJson, reconciled.envelope.rawJson, reconciled.envelope.templateHash
                )
                log.i("App Screen morph show resolved: hydrateMs=${morphResult.opt("hydrateMs")}")
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (rejected: ShowRejectedException) {
            log.w("App Screen cold load: show rejected for $href — routing to liveness recovery")
            onShowRejected(session, rejected)
        } catch (error: Exception) {
            log.e("App Screen cold load failed for $href (terminal, showing error state): $error")
            session.state = AppScreenSession.State.Failed
            session.phase.value = AppScreenPhase.Error(error)
        }
    }

    /**
     * Warm-reuse pipeline: the runtime is already loaded, so there is NO document fetch. The `.json`
     * is fired concurrently, `show(href[,optimisticData])` re-hydrates immediately (near-instant reveal),
     * scroll is reset, then the morph lands when the data arrives.
     */
    private suspend fun runWarmReuse(
        session: AppScreenSession,
        href: String,
        optimisticDataJson: String?,
        tapAt: Long? = null,
        selection: AppScreensDecisions.SessionSelection = AppScreensDecisions.SessionSelection.Reuse
    ) {
        try {
            session.currentHref = href
            val url = Uri.parse(href)
            val scopeForData = session.dataScope
                ?: scopeRegistry.scopeFor(session.templatePath)
                ?: AppScreensDecisions.effectiveScope(null)

            coroutineScope {
                log.i("App Screen warm-reuse: firing .json concurrently ($scopeForData) for $href (no document fetch)")
                val dataDeferred = async { withTimeout(JSON_TIMEOUT) { loader.loadScreenData(url, scopeForData) } }

                val hydrateArgs = AppScreenShowArgs.build(href, optimisticDataJson = optimisticDataJson)
                val hydrateResult = session.bridge.call("show", hydrateArgs, SHOW_TIMEOUT)
                session.lastShowPayload = ShowPayload(href, optimisticDataJson, responseJson = null, templateHash = null)
                log.i("App Screen warm hydrate show resolved (optimisticData=${optimisticDataJson != null}): hydrateMs=${hydrateResult.opt("hydrateMs")}")
                session.webView.scrollTo(0, 0)
                session.state = AppScreenSession.State.Ready
                session.phase.value = AppScreenPhase.Revealed
                session.didAttemptRecovery = false
                logTapToPainted(tapAt, selection, session.templatePath)

                val envelope = dataDeferred.await()
                val reconciled = loader.reconcileScreenData(envelope, session.documentETag) {
                    refetchDocument(session, href)
                }
                session.documentETag = reconciled.documentETag
                val morphArgs = AppScreenShowArgs.build(href, optimisticDataJson, reconciled.envelope.rawJson)
                val morphResult = session.bridge.call("show", morphArgs, SHOW_TIMEOUT)
                session.lastShowPayload = ShowPayload(
                    href, optimisticDataJson, reconciled.envelope.rawJson, reconciled.envelope.templateHash
                )
                log.i("App Screen warm morph show resolved: hydrateMs=${morphResult.opt("hydrateMs")}")
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (rejected: ShowRejectedException) {
            log.w("App Screen warm reuse: show rejected for $href — routing to liveness recovery")
            onShowRejected(session, rejected)
        } catch (error: Exception) {
            log.e("App Screen warm reuse failed for $href (terminal, showing error state): $error")
            session.state = AppScreenSession.State.Failed
            session.phase.value = AppScreenPhase.Error(error)
        }
    }

    private fun loadShell(session: AppScreenSession, href: String, html: String) {
        session.bridge.rearmLoaded()
        // Base URL = the document URL so relative script srcs resolve through the WebView's stack.
        session.webView.loadDataWithBaseURL(href, html, "text/html", "utf-8", null)
    }

    private suspend fun refetchDocument(session: AppScreenSession, href: String): AppScreenDocument {
        val refetched = withTimeout(DOCUMENT_TIMEOUT) {
            loader.loadDocument(Uri.parse(href), AppScreensDocumentClient.DocumentCachePolicy.ForceRevalidate)
        }
        loadShell(session, href, refetched.html)
        withTimeout(LOADED_TIMEOUT) { session.bridge.awaitLoaded() }
        session.runtimeLoaded = true
        session.reinjectInsets()
        return refetched
    }

    // endregion

    private fun logPushed(outcome: NavigatorModel.NavOutcome<AppScreenSession>) {
        if (outcome.presentedNewSheet) {
            log.i("App Screen sheet presented: href=${outcome.entry.href}")
        }
        log.i(
            "App Screen pushed entry=${outcome.entry.entryId} template=${outcome.entry.templatePath} " +
                "selection=${outcome.selection} stack=${if (outcome.toSheet) "sheet" else "root"} " +
                "optimisticData=${outcome.entry.optimisticDataJson != null}"
        )
    }

    private fun originOf(url: Uri): Set<String> {
        val origin = buildString {
            append(url.scheme)
            append("://")
            append(url.host)
            if (url.port != -1) {
                append(":")
                append(url.port)
            }
        }
        return setOf(origin)
    }

    companion object {
        private val DOCUMENT_TIMEOUT = 12.seconds
        private val LOADED_TIMEOUT = 10.seconds
        private val SHOW_TIMEOUT = 12.seconds
        private val JSON_TIMEOUT = 12.seconds

        /** Internal debug switch (query param) to disable prewarming for a presentation. */
        private const val SWITCH_PARAM = "roverPrewarm"
        private const val SWITCH_OFF = "off"
    }
}
