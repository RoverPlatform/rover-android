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
     * The normalized security origin of [url] as `scheme://host[:port]`, with scheme and host
     * lower-cased (both are case-insensitive) and the port included ONLY when it was explicit in the
     * URL (an absent/default port is omitted, matching [android.net.Uri.getPort] returning -1).
     *
     * This is the single origin-normalization helper: it defines both the App Screen bridge's
     * `allowedOriginRules` (the origin Chromium restricts each session's bridge to) and the origin
     * half of [templateKey]. Keeping the two in lockstep is what lets a session's bridge origin be
     * recovered from its template key.
     */
    fun originOf(url: Uri): String {
        return buildString {
            append(url.scheme?.lowercase())
            append("://")
            append(url.host?.lowercase())
            if (url.port != -1) {
                append(":")
                append(url.port)
            }
        }
    }

    /**
     * The origin-qualified template identity used for warm-session lookups and the scope registry:
     * the normalized [originOf] the URL followed by its path (query and fragment stripped), e.g.
     * `https://testbench.rover.io/a/player-detail?id=12` → `https://testbench.rover.io/a/player-detail`.
     *
     * The origin is included so that the SAME path served from two associated domains
     * (`https://one.example/a/home` vs `https://two.example/a/home`) yields DISTINCT keys and never
     * shares a warm session, an HTML shell, a scope record, or a bridge restricted to the wrong
     * origin. The path is preserved verbatim (it is case-sensitive); only scheme and host are
     * lower-cased (via [originOf]). Because the key embeds the normalized origin, the origin can be
     * parsed back out of it (re-parse with [android.net.Uri] and apply [originOf] again).
     */
    fun templateKey(url: Uri): String {
        return originOf(url) + (url.path ?: "")
    }

    /**
     * Derives the data URL for an App Screen by inserting `.json` at the end of the
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
     * Whether an eager `.json` fetch — started during the cold pipeline against a scope known
     * up front ([eagerScope], from the session or the scope registry) — must be cancelled and
     * restarted once the document lands and its [effectiveScope] is known.
     *
     * The eager fetch is a latency optimization fired before the document is read, so its scope
     * can be stale when a template's scope has changed between loads. Restart on ANY mismatch, in
     * either direction:
     * - stale PUBLIC → now PERSONALIZED: the eager request omitted the identifiers/auth the
     *   personalized screen needs. (The response-side retry in [AppScreensDataClient] only covers
     *   this when the server advertises the response scope; an absent header leaves public data on
     *   a personalized screen, so the document-side reconcile is the reliable guard.)
     * - stale PERSONALIZED → now PUBLIC: the eager request sent deviceIdentifier/userID + auth to a
     *   now-public endpoint; the response-side retry never corrects this direction.
     *
     * A null [eagerScope] means no eager fetch was started (the scope was unknown up front, so the
     * pipeline waits and fetches under [effectiveScope] anyway) — there is nothing to reconcile, so
     * this returns false. Matching scopes keep the in-flight eager fetch (false).
     */
    fun shouldRestartEagerFetch(
        eagerScope: AppScreenDataScope?,
        effectiveScope: AppScreenDataScope
    ): Boolean {
        if (eagerScope == null) return false
        return eagerScope != effectiveScope
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
     * Interprets an `openURL`/`presentWebsite` [href] the way a browser interprets an `<a href>`:
     * resolution against the posting document's URL [base] per RFC 3986 (via [java.net.URI]),
     * equivalent to browser href resolution for well-formed hrefs.
     *
     * An absolute URI passes through as written — and unlike [resolveHref]'s `navigate` targets,
     * opaque absolute URIs (`mailto:`, `tel:`, `myapp://…`) are valid external targets here, because
     * deep links are the point. A relative, protocol-relative, or query/fragment href resolves
     * against [base], which is what lets `openURL` reach *other* experiences by path. Leading and
     * trailing whitespace is trimmed. Deliberately NO address-bar-style host guessing: a scheme-less
     * `www.example.com` is a relative path per the URL standard and resolves onto the document's
     * domain. A blank or malformed href returns null for the caller to log and drop — including
     * hrefs that rely on WHATWG-only leniencies (e.g. backslashes as path separators, unencoded
     * spaces), which [java.net.URI] treats as malformed. Mirrors the iOS SDK's
     * `externalURL(from:against:)`.
     */
    fun resolveExternalHref(base: Uri, href: String): Uri? {
        val trimmed = href.trim()
        if (trimmed.isEmpty()) return null
        return try {
            val hrefUri = URI(trimmed)
            if (hrefUri.isAbsolute) {
                Uri.parse(trimmed)
            } else {
                Uri.parse(URI(base.toString()).resolve(hrefUri).toString())
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Coerces a [resolved] external target into a URL a browser tab can present, or null when it
     * cannot be presented in one.
     *
     * A scheme of `http`/`https` (case-insensitive) is already presentable and returns unchanged. A
     * hierarchical non-http(s) URI WITH a non-null host (e.g. `rv-app://example.com/x`) has its
     * scheme replaced by `https` (via [Uri.buildUpon]). An opaque URI (`mailto:…`) or a hostless one
     * cannot be presented in a tab and returns null.
     *
     * Custom Tabs require http(s), so this coercion is what lets a hierarchical deep-link-style URL
     * still open in the in-app browser. The behaviour mirrors the iOS SDK's handling so a link
     * authored once behaves identically on both platforms.
     */
    fun coerceWebPresentationUrl(resolved: Uri): Uri? {
        val scheme = resolved.scheme?.lowercase()
        if (scheme == "http" || scheme == "https") {
            // An opaque (https:foo) or hostless http(s) URI cannot be presented in a tab; mirrors
            // the iOS safariPresentableURL host guard.
            return if (!resolved.isOpaque && !resolved.host.isNullOrEmpty()) resolved else null
        }
        if (resolved.isOpaque) return null
        val host = resolved.host ?: return null
        if (host.isEmpty()) return null
        return resolved.buildUpon().scheme("https").build()
    }

    /**
     * Authorizes a resolved bridge-navigation target before the navigator will fetch and render it,
     * returning the https-normalized URL when the target is safe and null when it must be refused.
     *
     * A target is authorized iff ALL hold:
     * - its scheme is `http` or `https` (custom/opaque schemes are refused; other kinds never reach
     *   an App Screen render or the identifier-bearing `.json` fetch);
     * - after upgrading http→https via [normalizeScheme], it is an App Screen URL ([isAppScreenUrl] —
     *   a `/a/...` template path); and
     * - its host is one of [associatedDomains], compared case-insensitively (the host is lower-cased
     *   here; callers pass an already-lower-cased set, matching PresentExperienceRoute). Membership is
     *   EXACT host equality — `evil.example` and `sub.myapp.rover.io` do NOT match `myapp.rover.io` —
     *   mirroring the route handler's `associatedDomains.contains(host.lowercase())` semantics.
     *
     * Why this gate exists: a screen's runtime posts `navigate`/`links` hrefs, and [resolveHref] lets
     * a fully-qualified absolute href through unchanged. Without this check a hostile screen could
     * steer navigation to an arbitrary origin, which the navigator would then (a) fetch a document
     * from and RENDER IN-APP with native chrome, and (b) issue a personalized `.json` fetch to that
     * appends the device's `deviceIdentifier`/`userID` to the attacker host. Gating navigate + prewarm
     * on associated domains is what keeps both the render and the identifier-bearing fetch on trusted
     * origins. Mirrors the iOS `authorizedTarget` decision.
     */
    fun authorizeNavigationTarget(resolved: Uri, associatedDomains: Set<String>): Uri? {
        val scheme = resolved.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return null
        val normalized = normalizeScheme(resolved)
        if (!isAppScreenUrl(normalized)) return null
        val host = normalized.host?.lowercase() ?: return null
        if (host !in associatedDomains) return null
        return normalized
    }

    /**
     * The navigation session-selection outcome for a tapped App Screen link.
     *
     * A warm session for a template key is one that is pooled for that key and not currently
     * attached to any navigation stack. This pure model is opaque over session state, so it does not
     * (and cannot) require the runtime to have finished loading — the navigator enforces that
     * requirement at pipeline launch, downgrading a [Reuse] to a cold load when the pooled session's
     * runtime never booted (e.g. it was popped mid-load).
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
     * Decide how to obtain the session for a navigation to a template key.
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
     * How a session sits relative to the visible surfaces at the instant its renderer died.
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
     * The recovery policy, decided per session at the instant its renderer dies.
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
     * True iff [payload] is complete enough for [AppScreenNavigator]'s recovery pipeline to replay
     * it and fully restore the page: it must carry the morph's [ShowPayload.responseJson].
     *
     * The recovery pipeline refetches only the DOCUMENT and reissues a single `show` from the
     * payload; it never refetches the `.json` data. So a hydrate-only payload (recorded in the
     * hydrate→morph window, [ShowPayload.responseJson] == null) is NOT replayable — replaying it
     * would reissue only the hydrate show and leave the page permanently unhydrated (SSR/optimistic
     * content only, no morph ever arriving). A null payload (nothing was ever shown) is likewise not
     * replayable. Both cases must instead recover through the full cold pipeline, which redoes the
     * whole document → hydrate → `.json` → morph sequence.
     */
    fun isReplayablePayload(payload: ShowPayload?): Boolean = payload?.responseJson != null

    /**
     * How to schedule the `.json` data fetch relative to the document fetch.
     */
    enum class LoadOrdering {
        /** Fetch the document first (its response declares the scope), then the data. */
        Sequential,

        /** Fire the data fetch concurrently with the document fetch (the scope is already known). */
        Concurrent
    }

    /**
     * Chooses the load ordering. A null [knownScope] means this is a cold load where the scope
     * must first come off the document response, so the fetches must be [LoadOrdering.Sequential].
     * A known scope permits a [LoadOrdering.Concurrent] eager data fetch.
     */
    fun loadOrdering(knownScope: AppScreenDataScope?): LoadOrdering {
        return if (knownScope == null) LoadOrdering.Sequential else LoadOrdering.Concurrent
    }
}
