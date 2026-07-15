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

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList

/**
 * The pure navigation core: it owns the root and sheet stacks, the warm-session pool (keyed by
 * template path), and the session-selection / placement decisions — with NO WebView, coroutine, or
 * Android-framework dependency. [AppScreenNavigator] wraps it, supplies the real session factory,
 * and performs the side effects (pipelines, WebView destruction) implied by each result. That split
 * lets the whole stack/selection algorithm be unit-tested over fake sessions.
 *
 * Sessions are opaque to the model ([S]); it only ever compares them by identity. The stacks are
 * [SnapshotStateList]s so the navigator can expose them directly as the Compose-observable source of
 * truth for its NavHosts.
 */
internal class NavigatorModel<S : Any>(
    private val createSession: (templatePath: String) -> S
) {
    /** One pushed screen. [entryId] is unique per push; [session] may be shared across pushes (reuse). */
    data class Entry<S>(
        val entryId: String,
        val href: String,
        val templatePath: String,
        val optimisticDataJson: String?,
        /**
         * See [AppScreensDecisions.SessionSelection.Ephemeral].
         */
        val isEphemeral: Boolean,
        val session: S
    )

    /** The result of a push: what was placed, how the session was obtained, and where it went. */
    data class NavOutcome<S>(
        val entry: Entry<S>,
        val selection: AppScreensDecisions.SessionSelection,
        val toSheet: Boolean,
        val presentedNewSheet: Boolean
    )

    val rootStack: SnapshotStateList<Entry<S>> = mutableStateListOf()
    val sheetStack: SnapshotStateList<Entry<S>> = mutableStateListOf()

    private val warmSessions = HashMap<String, S>()

    /**
     * Ephemeral entries whose WebView destruction has been deferred from pop-commit to composition
     * disposal (keyed by [Entry.entryId]). An ephemeral is popped off the stack immediately, but its
     * host keeps rendering through the ~300ms pop-exit slide; destroying its WebView at pop-commit
     * would blank the exiting screen and flash the window background. The navigator parks the entry
     * here at pop and reclaims it when Compose disposes the exiting host (see
     * [AppScreenNavigator.onEntryDisposed]). Purely a holding map — the model performs no side effects.
     */
    private val pendingDisposal = HashMap<String, Entry<S>>()

    private var entryCounter = 0

    private fun nextEntryId(): String = "as-${entryCounter++}"

    private fun isOnAnyStack(session: S): Boolean =
        rootStack.any { it.session === session } || sheetStack.any { it.session === session }

    fun sessionFor(entryId: String): S? =
        (rootStack.firstOrNull { it.entryId == entryId }
            ?: sheetStack.firstOrNull { it.entryId == entryId })?.session

    /**
     * Establish the root master entry. The caller is expected to have [teardown]-ed any prior stacks
     * first; the warm pool is preserved, so a master for a template already warm is reused.
     */
    fun start(templatePath: String, href: String, optimisticDataJson: String?): NavOutcome<S> {
        val selection = selectionFor(templatePath)
        val session = resolveSession(templatePath, selection)
        val entry = Entry(nextEntryId(), href, templatePath, optimisticDataJson, isEphemeral = false, session = session)
        rootStack.add(entry)
        return NavOutcome(entry, selection, toSheet = false, presentedNewSheet = false)
    }

    /**
     * Push a navigation. Placement: a message from inside the sheet always pushes within the sheet
     * (a `sheet` transition from a sheet is a plain in-sheet push, never sheet-on-sheet); otherwise a
     * `sheet` transition presents a new sheet; anything else pushes on the root stack.
     */
    fun navigate(
        templatePath: String,
        href: String,
        optimisticDataJson: String?,
        transition: String?,
        fromSheet: Boolean
    ): NavOutcome<S> {
        val selection = selectionFor(templatePath)
        val session = resolveSession(templatePath, selection)
        val isEphemeral = selection == AppScreensDecisions.SessionSelection.Ephemeral
        val entry = Entry(nextEntryId(), href, templatePath, optimisticDataJson, isEphemeral, session)

        val toSheet = fromSheet || transition == TRANSITION_SHEET
        val presentedNewSheet: Boolean
        if (toSheet) {
            presentedNewSheet = !fromSheet && sheetStack.isEmpty()
            sheetStack.add(entry)
        } else {
            presentedNewSheet = false
            rootStack.add(entry)
        }
        return NavOutcome(entry, selection, toSheet, presentedNewSheet)
    }

    private fun selectionFor(templatePath: String): AppScreensDecisions.SessionSelection {
        val warm = warmSessions[templatePath]
        return AppScreensDecisions.selectSession(
            hasWarmSession = warm != null,
            warmSessionOnStack = warm != null && isOnAnyStack(warm)
        )
    }

    private fun resolveSession(
        templatePath: String,
        selection: AppScreensDecisions.SessionSelection
    ): S = when (selection) {
        AppScreensDecisions.SessionSelection.Reuse -> warmSessions.getValue(templatePath)
        AppScreensDecisions.SessionSelection.Create -> createSession(templatePath).also { warmSessions[templatePath] = it }
        AppScreensDecisions.SessionSelection.Ephemeral -> createSession(templatePath)
    }

    /** Remove the given entries from the root stack (a NavHost-committed pop). Returns them. */
    fun onPopped(entryIds: Collection<String>): List<Entry<S>> {
        val ids = entryIds.toSet()
        val removed = rootStack.filter { it.entryId in ids }
        rootStack.removeAll(removed.toSet())
        return removed
    }

    /** Remove the given entries from the sheet stack (an in-sheet pop). Returns them. */
    fun onSheetPopped(entryIds: Collection<String>): List<Entry<S>> {
        val ids = entryIds.toSet()
        val removed = sheetStack.filter { it.entryId in ids }
        sheetStack.removeAll(removed.toSet())
        return removed
    }

    /** Release the entire sheet stack (dismissal). Returns the removed entries. */
    fun onSheetDismissed(): List<Entry<S>> {
        val removed = sheetStack.toList()
        sheetStack.clear()
        return removed
    }

    /** Clear both stacks (reset / view left composition). The warm pool is preserved. */
    fun teardown(): List<Entry<S>> {
        val removed = rootStack.toList() + sheetStack.toList()
        rootStack.clear()
        sheetStack.clear()
        return removed
    }

    /**
     * Register a [session] as the warm session for [templatePath] WITHOUT placing it on any stack
     * (a prewarmed session joining the pool). No-op returning false if a warm session already exists
     * for the path — the caller (prewarmer) should then discard the redundant session. A prewarmed
     * session registered here is selected exactly like a popped warm session: a later navigate to
     * the same template resolves to [AppScreensDecisions.SessionSelection.Reuse].
     */
    fun registerWarm(templatePath: String, session: S): Boolean {
        if (warmSessions.containsKey(templatePath)) return false
        warmSessions[templatePath] = session
        return true
    }

    /** Whether any session (warm, on-stack, or ephemeral duplicate) exists for [templatePath]. */
    fun isTemplateKnown(templatePath: String): Boolean =
        warmSessions.containsKey(templatePath) ||
            rootStack.any { it.templatePath == templatePath } ||
            sheetStack.any { it.templatePath == templatePath }

    /** Forget [session] from the warm pool (called when its WebView is destroyed). */
    fun forgetWarm(session: S) {
        val iterator = warmSessions.entries.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().value === session) iterator.remove()
        }
    }

    /** Test/diagnostic: whether a warm session is registered for [templatePath]. */
    fun hasWarmSession(templatePath: String): Boolean = warmSessions.containsKey(templatePath)

    /**
     * The warm-idle sessions: pooled sessions that are not currently on any stack (so not composed).
     * Used by a live config-scheme change to discard the sessions whose WebViews were built for the
     * old scheme — they carry no user-facing surface, so destroying them is invisible and a later
     * navigation simply recreates them against the new scheme. A warm session that IS on a stack is
     * excluded here; the navigator rebuilds those in place instead. Compared by identity.
     */
    fun warmIdleSessions(): List<S> =
        warmSessions.values.filterNot { isOnAnyStack(it) }

    /**
     * The warm session pooled for [templatePath] if one exists AND it is not currently on any stack
     * (so it is idle and would be selected for [AppScreensDecisions.SessionSelection.Reuse]), else
     * null. Used by the navigator's surface-crossing claim guard to discard a pooled session whose
     * WebView carries a different surface's forced scheme before it is reused. A warm session that IS
     * on a stack yields an ephemeral one-off instead of a reuse, so it is intentionally excluded here.
     */
    fun warmIdleSessionFor(templatePath: String): S? =
        warmSessions[templatePath]?.takeUnless { isOnAnyStack(it) }

    /**
     * Park an ephemeral [entry] whose WebView destruction is deferred until its host leaves
     * composition (the pop-exit transition completes). Idempotent by [Entry.entryId]: re-deferring
     * the same entry simply overwrites the (identical) parked reference.
     */
    fun deferDisposal(entry: Entry<S>) {
        pendingDisposal[entry.entryId] = entry
    }

    /**
     * Reclaim a parked entry for destruction, removing it from the pending map. Returns the entry
     * exactly once — a second call for the same [entryId] (or one never deferred) returns null — so
     * the navigator's destroy path is naturally single-shot even if disposal signals arrive twice.
     */
    fun takeDisposed(entryId: String): Entry<S>? = pendingDisposal.remove(entryId)

    /**
     * Drain every still-parked entry, emptying the pending map. Used by the navigator as a
     * belt-and-braces sweep at teardown/establish so an entry whose host never disposed (an
     * unexpected leak) is not stranded holding a live WebView. Returns the drained entries.
     */
    fun drainPendingDisposal(): List<Entry<S>> {
        val drained = pendingDisposal.values.toList()
        pendingDisposal.clear()
        return drained
    }

    /**
     * Classify where [session] sits relative to the visible surfaces (renderer-death recovery).
     *
     * The visible session is the top of the sheet stack when a sheet is presented, else the top of
     * the root stack. A session on a stack that is not that visible one — including the root top
     * while a sheet covers it — is [AppScreensDecisions.SessionLiveness.OnStackHidden]. A session on
     * no stack is [AppScreensDecisions.SessionLiveness.WarmIdle] (the navigator screens out
     * prewarming sessions before calling this). Compared by identity, matching the rest of the model.
     */
    fun livenessOf(session: S): AppScreensDecisions.SessionLiveness {
        val onAnyStack = isOnAnyStack(session)
        if (!onAnyStack) return AppScreensDecisions.SessionLiveness.WarmIdle
        val visible = sheetStack.lastOrNull()?.session ?: rootStack.lastOrNull()?.session
        return if (session === visible) {
            AppScreensDecisions.SessionLiveness.Visible
        } else {
            AppScreensDecisions.SessionLiveness.OnStackHidden
        }
    }

    companion object {
        const val TRANSITION_SHEET = "sheet"
    }
}
