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

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import io.rover.sdk.core.logging.log
import io.rover.sdk.experiences.appscreens.network.AppScreenDocument
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Where a prewarming WebView lives while its document loads and the runtime boots.
 *
 * A detached Android WebView still loads HTML and executes JavaScript (only `requestAnimationFrame`
 * is frozen while detached, and the `loaded` handshake is not rAF-gated), so [Detached] is the
 * default: it boots the runtime with zero window/layout cost. [DecorAttached] is the hedge — it
 * attaches the WebView at 1×1px inside a container added to the current Activity's decor view and
 * translated offscreen, then detaches on completion — for the case where a detached WebView proves
 * unreliable on some platform WebView build.
 */
internal enum class PrewarmAttachStrategy {
    Detached,
    DecorAttached
}

/**
 * Pure planning for prewarm hints: given the candidate template paths from a `links` hint (already
 * resolved and classified as App Screen URLs, in DOM order), returns the subset to enqueue —
 * dropping any that are already known (warm/on-stack), already queued/in-flight, or exhausted
 * (failed their allowed attempts), and de-duplicating while preserving order.
 *
 * Kept side-effect free so the queue/dedupe policy can be unit-tested off-device.
 */
internal object PrewarmPlanner {
    fun plan(
        candidateTemplatePaths: List<String>,
        isKnown: (String) -> Boolean,
        isSkippable: (String) -> Boolean
    ): List<String> {
        val result = LinkedHashSet<String>()
        for (path in candidateTemplatePaths) {
            if (isKnown(path) || isSkippable(path)) continue
            result.add(path)
        }
        return result.toList()
    }
}

/**
 * Boots App Screen runtimes ahead of a tap, from the `links` prefetch hints the runtime posts after
 * each render. Each prewarm is document + runtime boot ONLY (never a `.json` fetch); on success the
 * session joins the warm pool exactly like a popped warm session, so the first navigation to that
 * template resolves to a warm [AppScreensDecisions.SessionSelection.Reuse] with no document fetch.
 *
 * Prewarms are launched [STAGGER] apart to avoid a thundering herd. A prewarm that does not report
 * `loaded` within [LOADED_TIMEOUT] is destroyed and discarded silently; the template may be retried
 * once from a later hint ([MAX_ATTEMPTS]). All work is driven on the navigator's main-thread scope;
 * [cancelAll] tears everything down (navigator reset/teardown) and destroys any in-flight WebViews.
 *
 * The navigator wires this class up with the handles it needs; the pure enqueue policy lives in
 * [PrewarmPlanner].
 */
internal class AppScreenPrewarmer(
    private val scope: CoroutineScope,
    private val createSession: (templatePath: String) -> AppScreenSession,
    private val loadDocument: suspend (Uri) -> AppScreenDocument,
    private val loadShell: (session: AppScreenSession, href: String, html: String) -> Unit,
    private val registerWarm: (templatePath: String, session: AppScreenSession) -> Boolean,
    private val isTemplateKnown: (templatePath: String) -> Boolean,
    private val decorViewProvider: () -> ViewGroup?,
    private val strategy: PrewarmAttachStrategy = DEFAULT_STRATEGY
) {
    private data class Request(val templatePath: String, val href: String)

    /** Template paths queued but not yet dispatched, in DOM order across hints. */
    private val queue = ArrayDeque<Request>()

    /** Template paths that are queued OR actively prewarming (dedupe guard). */
    private val active = HashSet<String>()

    /** Attempts spent per template path (initial + retries), to bound retries at [MAX_ATTEMPTS]. */
    private val attempts = HashMap<String, Int>()

    /** The staggering worker that dispatches the queue; null when idle. */
    private var worker: Job? = null

    /** In-flight prewarm jobs, tracked so [cancelAll] can cancel them all. */
    private val prewarmJobs = HashSet<Job>()

    /**
     * In-flight prewarm sessions mapped to their job, so the navigator can recognise a renderer
     * death on a still-prewarming session and cancel + discard exactly it. Main-thread only.
     */
    private val prewarmSessions = HashMap<AppScreenSession, Job>()

    /**
     * Consume a `links` hint. [candidates] are (templatePath, href) pairs already resolved against
     * the current document and classified as App Screen URLs, in DOM order. Enqueues the plannable
     * remainder and ensures the staggering worker is running.
     */
    fun hint(candidates: List<Pair<String, String>>) {
        val byPath = candidates.associate { it.first to it.second }
        val toEnqueue = PrewarmPlanner.plan(
            candidateTemplatePaths = candidates.map { it.first },
            isKnown = { isTemplateKnown(it) },
            isSkippable = { it in active || (attempts[it] ?: 0) >= MAX_ATTEMPTS }
        )
        if (toEnqueue.isEmpty()) return
        for (path in toEnqueue) {
            active.add(path)
            queue.addLast(Request(path, byPath.getValue(path)))
            log.i("App Screen prewarm queued template=$path")
        }
        ensureWorker()
    }

    private fun ensureWorker() {
        if (worker?.isActive == true) return
        worker = scope.launch {
            while (queue.isNotEmpty()) {
                val request = queue.removeFirst()
                launchPrewarm(request)
                delay(STAGGER)
            }
        }
    }

    private fun launchPrewarm(request: Request) {
        attempts[request.templatePath] = (attempts[request.templatePath] ?: 0) + 1
        // Create the session up front (main thread) so it can be tracked for renderer-death
        // classification while its document/runtime boot is still in flight.
        val session = createSession(request.templatePath)
        session.currentHref = request.href
        lateinit var job: Job
        job = scope.launch {
            try {
                runPrewarm(request, session)
            } finally {
                prewarmJobs.remove(job)
                prewarmSessions.remove(session)
                active.remove(request.templatePath)
            }
        }
        prewarmJobs.add(job)
        prewarmSessions[session] = job
    }

    private suspend fun runPrewarm(request: Request, session: AppScreenSession) {
        val templatePath = request.templatePath
        log.i("App Screen prewarm started template=$templatePath")
        session.state = AppScreenSession.State.LoadingDocument
        var offscreen: FrameLayout? = null
        try {
            val document = withTimeout(DOCUMENT_TIMEOUT) { loadDocument(Uri.parse(request.href)) }
            session.documentETag = document.etag
            session.dataScope = AppScreensDecisions.effectiveScope(document.dataScope)
            session.state = AppScreenSession.State.AwaitingRuntime

            offscreen = attachOffscreenIfNeeded(session)
            loadShell(session, request.href, document.html)
            withTimeout(LOADED_TIMEOUT) { session.bridge.awaitLoaded() }
            session.runtimeLoaded = true
            detachOffscreen(session, offscreen)
            offscreen = null

            if (registerWarm(templatePath, session)) {
                log.i("App Screen prewarm ready template=$templatePath (joined warm pool)")
            } else {
                // A navigation already created a session for this template; the prewarm is redundant.
                log.i("App Screen prewarm redundant template=$templatePath (session already exists); discarding")
                session.webView.destroy()
            }
        } catch (cancellation: CancellationException) {
            detachOffscreen(session, offscreen)
            session.webView.destroy()
            throw cancellation
        } catch (error: Exception) {
            detachOffscreen(session, offscreen)
            session.webView.destroy()
            val spent = attempts[templatePath] ?: 0
            val retryNote = if (spent < MAX_ATTEMPTS) "; may retry on a later hint" else "; no retries left"
            log.w("App Screen prewarm failed template=$templatePath: $error$retryNote")
        }
    }

    private fun attachOffscreenIfNeeded(session: AppScreenSession): FrameLayout? {
        if (strategy != PrewarmAttachStrategy.DecorAttached) return null
        val decor = decorViewProvider() ?: run {
            log.w("App Screen prewarm: DecorAttached strategy but no decor view available; loading detached")
            return null
        }
        val holder = FrameLayout(decor.context).apply {
            layoutParams = ViewGroup.LayoutParams(1, 1)
            // Park it fully offscreen so it never paints over the UI.
            translationX = -10_000f
            addView(
                session.webView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
        }
        decor.addView(holder)
        return holder
    }

    private fun detachOffscreen(session: AppScreenSession, holder: FrameLayout?) {
        if (holder == null) return
        (session.webView.parent as? ViewGroup)?.removeView(session.webView)
        (holder.parent as? ViewGroup)?.removeView(holder)
    }

    /** Whether [session] is one currently being prewarmed (still booting, not yet in the warm pool). */
    fun ownsSession(session: AppScreenSession): Boolean = prewarmSessions.containsKey(session)

    /**
     * Discard a still-prewarming [session] whose renderer just died: cancel its job, which
     * resumes the suspended boot with a [kotlinx.coroutines.CancellationException] so
     * [runPrewarm]'s handler destroys the dead WebView and clears the bookkeeping. No-op if it is
     * not (or no longer) a prewarm session.
     */
    fun discard(session: AppScreenSession) {
        val job = prewarmSessions[session] ?: return
        log.i("App Screen prewarm discarded on renderer-gone template=${session.templateKey}")
        job.cancel()
    }

    /** Cancel every prewarm (worker + in-flight jobs), destroying any in-flight WebViews, and reset. */
    fun cancelAll() {
        worker?.cancel()
        worker = null
        prewarmJobs.toList().forEach { it.cancel() }
        prewarmJobs.clear()
        prewarmSessions.clear()
        queue.clear()
        active.clear()
        // Attempt bookkeeping intentionally persists across resets so a chronically failing template
        // is not retried forever within a process.
    }

    companion object {
        val DEFAULT_STRATEGY = PrewarmAttachStrategy.Detached
        val STAGGER: Duration = 300.milliseconds
        val LOADED_TIMEOUT: Duration = 10.seconds
        val DOCUMENT_TIMEOUT: Duration = 12.seconds
        const val MAX_ATTEMPTS = 2
    }
}
