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
import java.net.URI

/**
 * Pure decision functions governing App Screens (Experiences V3) routing, data-URL derivation,
 * ETag/handshake reconciliation, data-scope handling, and href resolution.
 *
 * Everything here is deterministic and side-effect free (no I/O, no logging, no Android framework
 * types other than [android.net.Uri]). These semantics mirror the proven iOS implementation and
 * are exercised directly by unit tests; callers layer logging and I/O on top.
 */
internal object AppScreensDecisions {

    /**
     * Returns true iff [url] is an App Screen URL: its first path segment is exactly `"a"` and
     * there is at least one further non-empty segment.
     *
     * Matching is component-based (via [Uri.getPathSegments]), never string-prefix based, so
     * `/a/home` and `/a/x/y` match while `/a`, `/a/`, `/about`, and `/apple/x` do not. Query and
     * fragment are irrelevant.
     */
    fun isAppScreenUrl(url: Uri): Boolean {
        val segments = url.pathSegments
        return segments.size >= 2 && segments.first() == "a"
    }

    /**
     * Rewrites [url]'s scheme to `https`, preserving every other component.
     *
     * Experience deep links arrive with the app's custom scheme on an associated-domain host
     * (`rv-myapp://myapp.rover.io/a/home`) and are meant to open the same screen as the https
     * universal-link form. App Screens consumes its entry URL LITERALLY — the bridge origin rule
     * (Chromium rejects non-http(s) `allowedOriginRules` with a fatal IllegalArgumentException),
     * the `.json` data fetch, and href resolution against the base — so the scheme must be
     * normalized before any of that machinery sees the URL. Unconditional, mirroring both the
     * V1/V2 fetch pipeline (Experience.kt) and iOS
     * (`ExperienceViewController.loadAppScreensExperience`): http is upgraded too.
     */
    fun normalizeScheme(url: Uri): Uri {
        return url.buildUpon().scheme("https").build()
    }

    /**
     * The URL path with query and fragment stripped, e.g.
     * `https://testbench.rover.io/a/player-detail?id=12` → `/a/player-detail`.
     *
     * This is the template identity used for warm-session lookups and the scope registry.
     */
    fun templatePath(url: Uri): String {
        return url.path ?: ""
    }

    /**
     * Derives the data (document) URL for an App Screen by inserting `.json` at the end of the
     * path, before the query, preserving all query parameters and their order.
     *
     * `/a/home?x=1&y=2` → `/a/home.json?x=1&y=2`; `/a/standings` → `/a/standings.json`.
     */
    fun dataUrl(screenUrl: Uri): Uri {
        val encodedPath = screenUrl.encodedPath ?: ""
        return screenUrl.buildUpon()
            .encodedPath(encodedPath + ".json")
            .build()
    }

    /**
     * Normalizes an HTTP ETag to its bare value for comparison against a template hash.
     *
     * Trims surrounding whitespace, then strips a weak-validator prefix `W/` (case-sensitive per
     * RFC 7232), then strips a single pair of surrounding double quotes: `W/"abc"` → `abc`,
     * `"abc"` → `abc`, `abc` → `abc`, null → null.
     */
    fun normalizeETag(raw: String?): String? {
        if (raw == null) return null
        var value = raw.trim()
        if (value.startsWith("W/")) {
            value = value.substring(2)
        }
        if (value.length >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length - 1)
        }
        return value
    }

    /**
     * The effective data scope to apply for a document. Fails safe: a missing or unknown scope
     * (null) is treated as [AppScreenDataScope.PERSONALIZED].
     */
    fun effectiveScope(scope: AppScreenDataScope?): AppScreenDataScope {
        return scope ?: AppScreenDataScope.PERSONALIZED
    }

    /**
     * The outcome of reconciling the freshly-fetched document ETag against the template hash a
     * warm/prefetched shell was built from.
     */
    sealed interface HandshakeDecision {
        /** The document and template agree (or we fail open): render immediately. */
        object Render : HandshakeDecision

        /** The document is stale relative to the template: refetch the document exactly once. */
        object RefetchDocumentOnce : HandshakeDecision
    }

    /**
     * Decides whether the current document is consistent with the template the shell was built
     * from.
     *
     * Fails open in every ambiguous case so a screen always renders: a null/blank [templateHash]
     * (caller should log a warning) or a null [documentETag] yields [HandshakeDecision.Render].
     * On a match (after [normalizeETag]) it renders. On a mismatch it requests exactly one refetch
     * ([HandshakeDecision.RefetchDocumentOnce]); if [alreadyRetried] is set it renders anyway to
     * avoid looping (caller should log).
     */
    fun handshake(
        documentETag: String?,
        templateHash: String?,
        alreadyRetried: Boolean
    ): HandshakeDecision {
        if (templateHash.isNullOrBlank()) return HandshakeDecision.Render
        if (documentETag == null) return HandshakeDecision.Render
        if (normalizeETag(documentETag) == templateHash) return HandshakeDecision.Render
        return if (alreadyRetried) {
            HandshakeDecision.Render
        } else {
            HandshakeDecision.RefetchDocumentOnce
        }
    }

    /**
     * Whether to perform the one-shot retry that re-requests the document with identifiers
     * attached. This is true ONLY when a [AppScreenDataScope.PUBLIC] request came back as
     * [AppScreenDataScope.PERSONALIZED] — the single retry direction. The reverse flip, matching
     * scopes, or a null response scope all return false.
     */
    fun shouldRefetchWithIdentifiers(
        requestedScope: AppScreenDataScope,
        responseScope: AppScreenDataScope?
    ): Boolean {
        return requestedScope == AppScreenDataScope.PUBLIC &&
            responseScope == AppScreenDataScope.PERSONALIZED
    }

    /**
     * Resolves a tapped [href] against the current document URL [base].
     *
     * Absolute URLs (with a scheme) pass through unchanged; absolute-path hrefs such as `/a/x`
     * adopt the base's scheme and authority; relative hrefs resolve against the base path using
     * [java.net.URI] semantics. Returns null (rather than throwing) for unresolvable, opaque
     * (e.g. `mailto:`), or malformed inputs.
     */
    fun resolveHref(base: Uri, href: String): Uri? {
        val trimmed = href.trim()
        if (trimmed.isEmpty()) return null
        return try {
            val hrefUri = URI(trimmed)
            if (hrefUri.isAbsolute) {
                // A fully-qualified URL. Opaque URIs (e.g. mailto:foo) are not navigable App
                // Screen documents, so reject them.
                if (hrefUri.isOpaque) return null
                Uri.parse(trimmed)
            } else {
                val resolved = URI(base.toString()).resolve(hrefUri)
                Uri.parse(resolved.toString())
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * The navigation session-selection outcome for a tapped App Screen link (M4).
     *
     * A warm session for a template path is one whose runtime is already loaded and which is not
     * currently attached to any navigation stack.
     */
    enum class SessionSelection {
        /** Reuse the template's warm session in place (morph it with the new href/optimisticData). */
        Reuse,

        /**
         * The template's session is currently on a stack, so a one-off ephemeral session must be
         * created (cold pipeline) and destroyed when it is popped.
         */
        Ephemeral,

        /** No session exists for the template yet; create one (cold) and register it as warm. */
        Create
    }

    /**
     * Decide how to obtain the session for a navigation to a template path.
     *
     * - No warm session ([hasWarmSession] false) → [SessionSelection.Create].
     * - A warm session that is currently on a stack ([warmSessionOnStack] true) → the same WebView
     *   may never be attached twice, so create an [SessionSelection.Ephemeral] one-off.
     * - A warm session that is free (not on any stack) → [SessionSelection.Reuse].
     *
     * The "on a stack" input must span BOTH the root and sheet stacks (see the navigator).
     */
    fun selectSession(
        hasWarmSession: Boolean,
        warmSessionOnStack: Boolean
    ): SessionSelection {
        return when {
            !hasWarmSession -> SessionSelection.Create
            warmSessionOnStack -> SessionSelection.Ephemeral
            else -> SessionSelection.Reuse
        }
    }

    /**
     * How a session sits relative to the visible surfaces at the instant its renderer died (M6).
     *
     * A session is [Visible] when it is the top of the sheet stack (sheet presented) or the top of
     * the root stack (no sheet). Anything else on a stack — below the top, or on the root stack while
     * a sheet covers it — is [OnStackHidden]. A session in the warm pool but on no stack is
     * [WarmIdle]; one still booting inside the prewarmer is [Prewarming].
     */
    enum class SessionLiveness {
        Visible,
        OnStackHidden,
        WarmIdle,
        Prewarming
    }

    /**
     * What to do with a session whose WebView renderer just died (or whose `show` was rejected —
     * treated identically as a liveness signal). See [recoveryAction].
     */
    enum class RecoveryAction {
        /** Rebuild the WebView and replay the last successful show immediately. */
        RecoverNow,

        /** The one recovery attempt is already spent: surface the error/retry state. */
        ShowError,

        /** Keep the entry and its [lastShowPayload]; lazy-recover when it next becomes visible. */
        MarkDead,

        /** Destroy and forget the session silently (no user-facing surface to recover). */
        Discard
    }

    /**
     * The recovery policy, decided per session at the instant its renderer dies (M6).
     *
     * The one-attempt rule is encoded here: a [SessionLiveness.Visible] session recovers immediately
     * only if it has not already burned its single attempt ([didAttemptRecovery] false), otherwise it
     * lands in the error state — so a crash-loop settles into error after exactly one retry.
     * Off-screen stack members are marked dead for lazy recovery, and pooled/prewarming sessions are
     * discarded (a later `links` hint may re-prewarm them).
     */
    fun recoveryAction(
        liveness: SessionLiveness,
        didAttemptRecovery: Boolean
    ): RecoveryAction = when (liveness) {
        SessionLiveness.Visible ->
            if (didAttemptRecovery) RecoveryAction.ShowError else RecoveryAction.RecoverNow
        SessionLiveness.OnStackHidden -> RecoveryAction.MarkDead
        SessionLiveness.WarmIdle -> RecoveryAction.Discard
        SessionLiveness.Prewarming -> RecoveryAction.Discard
    }

    /**
     * How to schedule the shell load and the data (document) fetch relative to one another.
     */
    enum class LoadOrdering {
        /** Load the document first, then the shell (the scope is not yet known). */
        Sequential,

        /** Load the shell and document concurrently (the scope is already known). */
        Concurrent
    }

    /**
     * Chooses the load ordering. A null [knownScope] means this is a cold load where the scope
     * must first come off the document response, so the fetches must be [LoadOrdering.Sequential].
     * A known scope permits a [LoadOrdering.Concurrent] load.
     */
    fun loadOrdering(knownScope: AppScreenDataScope?): LoadOrdering {
        return if (knownScope == null) LoadOrdering.Sequential else LoadOrdering.Concurrent
    }
}
