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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// android.net.Uri is an Android framework class with no JVM implementation, so these pure-function
// tests run under Robolectric (already a testImplementation dependency of this module) to get a
// real Uri parser. No Android UI or context is touched.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppScreensDecisionsTest {

    // region isAppScreenUrl

    @Test
    fun `isAppScreenUrl true for a home`() {
        assertTrue(AppScreensDecisions.isAppScreenUrl(Uri.parse("https://testbench.rover.io/a/home")))
    }

    @Test
    fun `isAppScreenUrl false for bare a`() {
        assertFalse(AppScreensDecisions.isAppScreenUrl(Uri.parse("https://testbench.rover.io/a")))
    }

    @Test
    fun `isAppScreenUrl false for a trailing slash`() {
        assertFalse(AppScreensDecisions.isAppScreenUrl(Uri.parse("https://testbench.rover.io/a/")))
    }

    @Test
    fun `isAppScreenUrl false for about`() {
        assertFalse(AppScreensDecisions.isAppScreenUrl(Uri.parse("https://testbench.rover.io/about")))
    }

    @Test
    fun `isAppScreenUrl false for apple x (no prefix matching)`() {
        assertFalse(AppScreensDecisions.isAppScreenUrl(Uri.parse("https://testbench.rover.io/apple/x")))
    }

    @Test
    fun `isAppScreenUrl true for nested segments`() {
        assertTrue(AppScreensDecisions.isAppScreenUrl(Uri.parse("https://testbench.rover.io/a/x/y")))
    }

    @Test
    fun `isAppScreenUrl query and fragment irrelevant`() {
        assertTrue(
            AppScreensDecisions.isAppScreenUrl(Uri.parse("https://testbench.rover.io/a/home?x=1#frag"))
        )
    }

    // endregion

    // region normalizeScheme

    @Test
    fun `normalizeScheme rewrites a custom deep-link scheme to https preserving everything else`() {
        assertEquals(
            Uri.parse("https://testbench.rover.io/a/home?x=1&y=2#frag"),
            AppScreensDecisions.normalizeScheme(
                Uri.parse("rv-developer://testbench.rover.io/a/home?x=1&y=2#frag")
            )
        )
    }

    @Test
    fun `normalizeScheme upgrades http to https`() {
        assertEquals(
            Uri.parse("https://testbench.rover.io/a/home"),
            AppScreensDecisions.normalizeScheme(Uri.parse("http://testbench.rover.io/a/home"))
        )
    }

    @Test
    fun `normalizeScheme leaves an https URL equal`() {
        assertEquals(
            Uri.parse("https://testbench.rover.io/a/home?id=12"),
            AppScreensDecisions.normalizeScheme(Uri.parse("https://testbench.rover.io/a/home?id=12"))
        )
    }

    @Test
    fun `normalizeScheme preserves an explicit port`() {
        assertEquals(
            Uri.parse("https://testbench.rover.io:8443/a/home"),
            AppScreensDecisions.normalizeScheme(Uri.parse("rv-developer://testbench.rover.io:8443/a/home"))
        )
    }

    // endregion

    // region templateKey

    @Test
    fun `templateKey composes origin and path`() {
        assertEquals(
            "https://testbench.rover.io/a/standings",
            AppScreensDecisions.templateKey(Uri.parse("https://testbench.rover.io/a/standings"))
        )
    }

    @Test
    fun `templateKey excludes query and fragment`() {
        assertEquals(
            "https://testbench.rover.io/a/player-detail",
            AppScreensDecisions.templateKey(Uri.parse("https://testbench.rover.io/a/player-detail?id=12#top"))
        )
    }

    @Test
    fun `templateKey lower-cases scheme and host`() {
        assertEquals(
            "https://testbench.rover.io/a/home",
            AppScreensDecisions.templateKey(Uri.parse("HTTPS://TestBench.Rover.IO/a/home"))
        )
    }

    @Test
    fun `templateKey preserves the path case verbatim`() {
        assertEquals(
            "https://testbench.rover.io/a/Player-Detail",
            AppScreensDecisions.templateKey(Uri.parse("https://testbench.rover.io/a/Player-Detail"))
        )
    }

    @Test
    fun `templateKey preserves an explicit port`() {
        assertEquals(
            "https://testbench.rover.io:8443/a/home",
            AppScreensDecisions.templateKey(Uri.parse("https://testbench.rover.io:8443/a/home"))
        )
    }

    @Test
    fun `templateKey omits an absent (default) port`() {
        assertEquals(
            "https://testbench.rover.io/a/home",
            AppScreensDecisions.templateKey(Uri.parse("https://testbench.rover.io/a/home"))
        )
    }

    @Test
    fun `templateKey distinguishes the same path on different hosts`() {
        val one = AppScreensDecisions.templateKey(Uri.parse("https://one.example/a/home"))
        val two = AppScreensDecisions.templateKey(Uri.parse("https://two.example/a/home"))
        assertEquals("https://one.example/a/home", one)
        assertEquals("https://two.example/a/home", two)
        assertNotEquals(one, two)
    }

    // endregion

    // region originOf

    @Test
    fun `originOf normalizes scheme and host and omits default port`() {
        assertEquals(
            "https://testbench.rover.io",
            AppScreensDecisions.originOf(Uri.parse("HTTPS://TestBench.Rover.IO/a/home?id=12"))
        )
    }

    @Test
    fun `originOf keeps an explicit port`() {
        assertEquals(
            "https://testbench.rover.io:8443",
            AppScreensDecisions.originOf(Uri.parse("https://testbench.rover.io:8443/a/home"))
        )
    }

    // endregion

    // region dataUrl

    @Test
    fun `dataUrl without query`() {
        assertEquals(
            "https://testbench.rover.io/a/standings.json",
            AppScreensDecisions.dataUrl(Uri.parse("https://testbench.rover.io/a/standings")).toString()
        )
    }

    @Test
    fun `dataUrl preserves query and param order`() {
        assertEquals(
            "https://testbench.rover.io/a/home.json?x=1&y=2",
            AppScreensDecisions.dataUrl(Uri.parse("https://testbench.rover.io/a/home?x=1&y=2")).toString()
        )
    }

    // endregion

    // region normalizeETag

    @Test
    fun `normalizeETag weak validator`() {
        assertEquals("abc", AppScreensDecisions.normalizeETag("W/\"abc\""))
    }

    @Test
    fun `normalizeETag quoted`() {
        assertEquals("abc", AppScreensDecisions.normalizeETag("\"abc\""))
    }

    @Test
    fun `normalizeETag bare`() {
        assertEquals("abc", AppScreensDecisions.normalizeETag("abc"))
    }

    @Test
    fun `normalizeETag surrounding whitespace`() {
        assertEquals("abc", AppScreensDecisions.normalizeETag("  W/\"abc\"  "))
    }

    @Test
    fun `normalizeETag null`() {
        assertNull(AppScreensDecisions.normalizeETag(null))
    }

    // endregion

    // region effectiveScope

    @Test
    fun `effectiveScope null fails safe to personalized`() {
        assertEquals(AppScreenDataScope.PERSONALIZED, AppScreensDecisions.effectiveScope(null))
    }

    @Test
    fun `effectiveScope passes through public`() {
        assertEquals(
            AppScreenDataScope.PUBLIC,
            AppScreensDecisions.effectiveScope(AppScreenDataScope.PUBLIC)
        )
    }

    // endregion

    // region handshake

    @Test
    fun `handshake match renders`() {
        assertSame(
            AppScreensDecisions.HandshakeDecision.Render,
            AppScreensDecisions.handshake(documentETag = "\"hash\"", templateHash = "hash", alreadyRetried = false)
        )
    }

    @Test
    fun `handshake mismatch first time refetches`() {
        assertSame(
            AppScreensDecisions.HandshakeDecision.RefetchDocumentOnce,
            AppScreensDecisions.handshake(documentETag = "\"other\"", templateHash = "hash", alreadyRetried = false)
        )
    }

    @Test
    fun `handshake mismatch after retry renders`() {
        assertSame(
            AppScreensDecisions.HandshakeDecision.Render,
            AppScreensDecisions.handshake(documentETag = "\"other\"", templateHash = "hash", alreadyRetried = true)
        )
    }

    @Test
    fun `handshake null hash fails open`() {
        assertSame(
            AppScreensDecisions.HandshakeDecision.Render,
            AppScreensDecisions.handshake(documentETag = "\"hash\"", templateHash = null, alreadyRetried = false)
        )
    }

    @Test
    fun `handshake blank hash fails open`() {
        assertSame(
            AppScreensDecisions.HandshakeDecision.Render,
            AppScreensDecisions.handshake(documentETag = "\"hash\"", templateHash = "   ", alreadyRetried = false)
        )
    }

    @Test
    fun `handshake null etag renders`() {
        assertSame(
            AppScreensDecisions.HandshakeDecision.Render,
            AppScreensDecisions.handshake(documentETag = null, templateHash = "hash", alreadyRetried = false)
        )
    }

    // endregion

    // region shouldRefetchWithIdentifiers

    @Test
    fun `shouldRefetchWithIdentifiers public to personalized`() {
        assertTrue(
            AppScreensDecisions.shouldRefetchWithIdentifiers(
                requestedScope = AppScreenDataScope.PUBLIC,
                responseScope = AppScreenDataScope.PERSONALIZED
            )
        )
    }

    @Test
    fun `shouldRefetchWithIdentifiers personalized to public is false`() {
        assertFalse(
            AppScreensDecisions.shouldRefetchWithIdentifiers(
                requestedScope = AppScreenDataScope.PERSONALIZED,
                responseScope = AppScreenDataScope.PUBLIC
            )
        )
    }

    @Test
    fun `shouldRefetchWithIdentifiers public to public is false`() {
        assertFalse(
            AppScreensDecisions.shouldRefetchWithIdentifiers(
                requestedScope = AppScreenDataScope.PUBLIC,
                responseScope = AppScreenDataScope.PUBLIC
            )
        )
    }

    @Test
    fun `shouldRefetchWithIdentifiers null response is false`() {
        assertFalse(
            AppScreensDecisions.shouldRefetchWithIdentifiers(
                requestedScope = AppScreenDataScope.PUBLIC,
                responseScope = null
            )
        )
    }

    // endregion

    // region shouldRestartEagerFetch

    @Test
    fun `shouldRestartEagerFetch matching scopes keep`() {
        assertFalse(
            AppScreensDecisions.shouldRestartEagerFetch(
                eagerScope = AppScreenDataScope.PUBLIC,
                effectiveScope = AppScreenDataScope.PUBLIC
            )
        )
        assertFalse(
            AppScreensDecisions.shouldRestartEagerFetch(
                eagerScope = AppScreenDataScope.PERSONALIZED,
                effectiveScope = AppScreenDataScope.PERSONALIZED
            )
        )
    }

    @Test
    fun `shouldRestartEagerFetch stale public to personalized restarts`() {
        assertTrue(
            AppScreensDecisions.shouldRestartEagerFetch(
                eagerScope = AppScreenDataScope.PUBLIC,
                effectiveScope = AppScreenDataScope.PERSONALIZED
            )
        )
    }

    @Test
    fun `shouldRestartEagerFetch stale personalized to public restarts`() {
        assertTrue(
            AppScreensDecisions.shouldRestartEagerFetch(
                eagerScope = AppScreenDataScope.PERSONALIZED,
                effectiveScope = AppScreenDataScope.PUBLIC
            )
        )
    }

    @Test
    fun `shouldRestartEagerFetch null eager scope has nothing to reconcile`() {
        // No eager fetch was ever started (scope unknown up front), so neither effective scope
        // triggers a restart.
        assertFalse(
            AppScreensDecisions.shouldRestartEagerFetch(
                eagerScope = null,
                effectiveScope = AppScreenDataScope.PERSONALIZED
            )
        )
        assertFalse(
            AppScreensDecisions.shouldRestartEagerFetch(
                eagerScope = null,
                effectiveScope = AppScreenDataScope.PUBLIC
            )
        )
    }

    // endregion

    // region resolveHref

    @Test
    fun `resolveHref absolute url passthrough`() {
        assertEquals(
            "https://other.example.com/a/x",
            AppScreensDecisions.resolveHref(
                Uri.parse("https://testbench.rover.io/a/home"),
                "https://other.example.com/a/x"
            ).toString()
        )
    }

    @Test
    fun `resolveHref absolute path adopts base authority`() {
        assertEquals(
            "https://testbench.rover.io/a/standings",
            AppScreensDecisions.resolveHref(
                Uri.parse("https://testbench.rover.io/a/home"),
                "/a/standings"
            ).toString()
        )
    }

    @Test
    fun `resolveHref relative resolves against base path`() {
        assertEquals(
            "https://testbench.rover.io/a/standings",
            AppScreensDecisions.resolveHref(
                Uri.parse("https://testbench.rover.io/a/home"),
                "standings"
            ).toString()
        )
    }

    @Test
    fun `resolveHref garbage returns null`() {
        assertNull(
            AppScreensDecisions.resolveHref(
                Uri.parse("https://testbench.rover.io/a/home"),
                "ht tp://\\bad url"
            )
        )
    }

    @Test
    fun `resolveHref opaque returns null`() {
        assertNull(
            AppScreensDecisions.resolveHref(
                Uri.parse("https://testbench.rover.io/a/home"),
                "mailto:foo@example.com"
            )
        )
    }

    // endregion

    // region resolveExternalHref

    private val externalBase = Uri.parse("https://testbench.rover.io/a/home")

    @Test
    fun `resolveExternalHref absolute http passes through`() {
        assertEquals(
            "https://other.example.com/page",
            AppScreensDecisions.resolveExternalHref(externalBase, "https://other.example.com/page")
                .toString()
        )
    }

    @Test
    fun `resolveExternalHref preserves an opaque mailto`() {
        // Unlike resolveHref, opaque absolute URIs survive: openURL/presentWebsite target them.
        assertEquals(
            "mailto:x@y.com",
            AppScreensDecisions.resolveExternalHref(externalBase, "mailto:x@y.com").toString()
        )
    }

    @Test
    fun `resolveExternalHref preserves a custom deep-link scheme`() {
        assertEquals(
            "tel:+15555550123",
            AppScreensDecisions.resolveExternalHref(externalBase, "tel:+15555550123").toString()
        )
    }

    @Test
    fun `resolveExternalHref trims whitespace`() {
        // The WHATWG parser strips leading/trailing whitespace; java.net.URI does not,
        // so the decision function trims before parsing.
        assertEquals(
            "https://example.com",
            AppScreensDecisions.resolveExternalHref(externalBase, "  https://example.com  ")
                .toString()
        )
    }

    @Test
    fun `resolveExternalHref root-relative path resolves onto the document domain`() {
        // Browser <a href> semantics — this is what lets openURL reach other experiences by path.
        assertEquals(
            "https://testbench.rover.io/promo",
            AppScreensDecisions.resolveExternalHref(externalBase, "/promo").toString()
        )
    }

    @Test
    fun `resolveExternalHref protocol-relative inherits the document scheme`() {
        assertEquals(
            "https://example.com/path",
            AppScreensDecisions.resolveExternalHref(externalBase, "//example.com/path").toString()
        )
    }

    @Test
    fun `resolveExternalHref bare hostname resolves as a relative path`() {
        // Per the URL standard a scheme-less www.example.com is a relative path, not a
        // host — deliberately no address-bar-style host guessing.
        assertEquals(
            "https://testbench.rover.io/a/www.example.com",
            AppScreensDecisions.resolveExternalHref(externalBase, "www.example.com").toString()
        )
    }

    @Test
    fun `resolveExternalHref blank returns null`() {
        assertNull(AppScreensDecisions.resolveExternalHref(externalBase, "   "))
    }

    @Test
    fun `resolveExternalHref garbage returns null`() {
        assertNull(AppScreensDecisions.resolveExternalHref(externalBase, "ht tp://\\bad url"))
    }

    @Test
    fun `resolveExternalHref unencoded space is malformed and drops`() {
        // A WHATWG-only leniency: java.net.URI (RFC 3986) rejects the unencoded space, so the
        // href is treated as malformed and dropped rather than resolved.
        assertNull(AppScreensDecisions.resolveExternalHref(externalBase, "/a b"))
    }

    @Test
    fun `resolveExternalHref backslash path is malformed and drops`() {
        // Backslashes as path separators are a WHATWG-only leniency; java.net.URI rejects them,
        // so the href is dropped.
        assertNull(AppScreensDecisions.resolveExternalHref(externalBase, "\\evil.example\\path"))
    }

    // endregion

    // region coerceWebPresentationUrl

    @Test
    fun `coerceWebPresentationUrl passes http through unchanged`() {
        assertEquals(
            Uri.parse("http://example.com/x"),
            AppScreensDecisions.coerceWebPresentationUrl(Uri.parse("http://example.com/x"))
        )
    }

    @Test
    fun `coerceWebPresentationUrl passes https through unchanged`() {
        assertEquals(
            Uri.parse("https://example.com/x"),
            AppScreensDecisions.coerceWebPresentationUrl(Uri.parse("https://example.com/x"))
        )
    }

    @Test
    fun `coerceWebPresentationUrl upgrades a custom hierarchical scheme with a host to https`() {
        assertEquals(
            Uri.parse("https://example.com/x"),
            AppScreensDecisions.coerceWebPresentationUrl(Uri.parse("rv-app://example.com/x"))
        )
    }

    @Test
    fun `coerceWebPresentationUrl rejects an opaque uri`() {
        assertNull(AppScreensDecisions.coerceWebPresentationUrl(Uri.parse("mailto:x@y.com")))
    }

    @Test
    fun `coerceWebPresentationUrl rejects an opaque https uri`() {
        // An opaque, hostless https URI must not slip through the http(s) fast path into a tab.
        assertNull(AppScreensDecisions.coerceWebPresentationUrl(Uri.parse("https:foo")))
    }

    @Test
    fun `coerceWebPresentationUrl rejects an opaque http uri`() {
        assertNull(AppScreensDecisions.coerceWebPresentationUrl(Uri.parse("http:foo")))
    }

    @Test
    fun `coerceWebPresentationUrl rejects a hostless uri`() {
        assertNull(AppScreensDecisions.coerceWebPresentationUrl(Uri.parse("rv-app:/path")))
    }

    @Test
    fun `coerceWebPresentationUrl rejects a javascript uri`() {
        assertNull(AppScreensDecisions.coerceWebPresentationUrl(Uri.parse("javascript:alert(1)")))
    }

    @Test
    fun `coerceWebPresentationUrl rejects a file uri`() {
        // file:/// has an empty authority — coercion must not yield an https URL.
        assertNull(AppScreensDecisions.coerceWebPresentationUrl(Uri.parse("file:///etc/passwd")))
    }

    @Test
    fun `coerceWebPresentationUrl rejects a data uri`() {
        assertNull(AppScreensDecisions.coerceWebPresentationUrl(Uri.parse("data:text/html,hello")))
    }

    // endregion

    // region authorizeNavigationTarget

    private val domains = setOf("testbench.rover.io", "myapp.rover.io")

    @Test
    fun `authorizeNavigationTarget accepts an a path on an associated domain`() {
        assertEquals(
            Uri.parse("https://testbench.rover.io/a/standings"),
            AppScreensDecisions.authorizeNavigationTarget(
                Uri.parse("https://testbench.rover.io/a/standings"),
                domains
            )
        )
    }

    @Test
    fun `authorizeNavigationTarget upgrades http to https and accepts`() {
        assertEquals(
            Uri.parse("https://testbench.rover.io/a/home"),
            AppScreensDecisions.authorizeNavigationTarget(
                Uri.parse("http://testbench.rover.io/a/home"),
                domains
            )
        )
    }

    @Test
    fun `authorizeNavigationTarget rejects a foreign host`() {
        assertNull(
            AppScreensDecisions.authorizeNavigationTarget(
                Uri.parse("https://attacker.example/a/home"),
                domains
            )
        )
    }

    @Test
    fun `authorizeNavigationTarget rejects a custom scheme`() {
        assertNull(
            AppScreensDecisions.authorizeNavigationTarget(
                Uri.parse("rv-developer://testbench.rover.io/a/home"),
                domains
            )
        )
    }

    @Test
    fun `authorizeNavigationTarget rejects a non-a path on an associated domain`() {
        assertNull(
            AppScreensDecisions.authorizeNavigationTarget(
                Uri.parse("https://testbench.rover.io/about"),
                domains
            )
        )
    }

    @Test
    fun `authorizeNavigationTarget matches host case-insensitively`() {
        // A mixed-case host on an associated domain is accepted; the returned URL's origin
        // (scheme+host) lower-cases via templateKey/originOf downstream, so only acceptance matters.
        val authorized = AppScreensDecisions.authorizeNavigationTarget(
            Uri.parse("https://TestBench.Rover.IO/a/home"),
            domains
        )
        assertNotNull(authorized)
        assertEquals(
            "https://testbench.rover.io/a/home",
            AppScreensDecisions.templateKey(authorized!!)
        )
    }

    @Test
    fun `authorizeNavigationTarget does NOT match a subdomain of an associated domain`() {
        assertNull(
            AppScreensDecisions.authorizeNavigationTarget(
                Uri.parse("https://evil.testbench.rover.io/a/home"),
                domains
            )
        )
    }

    // endregion

    // region selectSession

    @Test
    fun `selectSession no warm session creates`() {
        assertEquals(
            AppScreensDecisions.SessionSelection.Create,
            AppScreensDecisions.selectSession(hasWarmSession = false, warmSessionOnStack = false)
        )
    }

    @Test
    fun `selectSession warm free reuses`() {
        assertEquals(
            AppScreensDecisions.SessionSelection.Reuse,
            AppScreensDecisions.selectSession(hasWarmSession = true, warmSessionOnStack = false)
        )
    }

    @Test
    fun `selectSession warm on stack is ephemeral`() {
        assertEquals(
            AppScreensDecisions.SessionSelection.Ephemeral,
            AppScreensDecisions.selectSession(hasWarmSession = true, warmSessionOnStack = true)
        )
    }

    // endregion

    // region recoveryAction (renderer-death policy)

    @Test
    fun `recoveryAction visible with no prior attempt recovers now`() {
        assertEquals(
            AppScreensDecisions.RecoveryAction.RecoverNow,
            AppScreensDecisions.recoveryAction(
                AppScreensDecisions.SessionLiveness.Visible,
                didAttemptRecovery = false
            )
        )
    }

    @Test
    fun `recoveryAction visible after burning its attempt shows error`() {
        assertEquals(
            AppScreensDecisions.RecoveryAction.ShowError,
            AppScreensDecisions.recoveryAction(
                AppScreensDecisions.SessionLiveness.Visible,
                didAttemptRecovery = true
            )
        )
    }

    @Test
    fun `recoveryAction off-screen stack member is marked dead`() {
        // The one-attempt flag is irrelevant off-screen: it always defers to lazy recovery.
        assertEquals(
            AppScreensDecisions.RecoveryAction.MarkDead,
            AppScreensDecisions.recoveryAction(
                AppScreensDecisions.SessionLiveness.OnStackHidden,
                didAttemptRecovery = false
            )
        )
        assertEquals(
            AppScreensDecisions.RecoveryAction.MarkDead,
            AppScreensDecisions.recoveryAction(
                AppScreensDecisions.SessionLiveness.OnStackHidden,
                didAttemptRecovery = true
            )
        )
    }

    @Test
    fun `recoveryAction warm idle is discarded`() {
        assertEquals(
            AppScreensDecisions.RecoveryAction.Discard,
            AppScreensDecisions.recoveryAction(
                AppScreensDecisions.SessionLiveness.WarmIdle,
                didAttemptRecovery = false
            )
        )
    }

    @Test
    fun `recoveryAction prewarming is discarded`() {
        assertEquals(
            AppScreensDecisions.RecoveryAction.Discard,
            AppScreensDecisions.recoveryAction(
                AppScreensDecisions.SessionLiveness.Prewarming,
                didAttemptRecovery = false
            )
        )
    }

    // endregion

    // region loadOrdering

    @Test
    fun `loadOrdering null scope is sequential`() {
        assertEquals(AppScreensDecisions.LoadOrdering.Sequential, AppScreensDecisions.loadOrdering(null))
    }

    @Test
    fun `loadOrdering known scope is concurrent`() {
        assertEquals(
            AppScreensDecisions.LoadOrdering.Concurrent,
            AppScreensDecisions.loadOrdering(AppScreenDataScope.PUBLIC)
        )
    }

    // endregion

    // region AppScreenDataScope.fromHeader

    @Test
    fun `fromHeader public`() {
        assertEquals(AppScreenDataScope.PUBLIC, AppScreenDataScope.fromHeader("public"))
    }

    @Test
    fun `fromHeader personalized case insensitive with whitespace`() {
        assertEquals(AppScreenDataScope.PERSONALIZED, AppScreenDataScope.fromHeader("  Personalized  "))
    }

    @Test
    fun `fromHeader unknown is null`() {
        assertNull(AppScreenDataScope.fromHeader("private"))
    }

    @Test
    fun `fromHeader null is null`() {
        assertNull(AppScreenDataScope.fromHeader(null))
    }

    @Test
    fun `fromHeader blank is null`() {
        assertNull(AppScreenDataScope.fromHeader("   "))
    }

    // endregion

    // region isReplayablePayload

    @Test
    fun `isReplayablePayload false for null payload`() {
        assertFalse(AppScreensDecisions.isReplayablePayload(null))
    }

    @Test
    fun `isReplayablePayload false for hydrate-only payload`() {
        // Recorded in the hydrate to morph window: responseJson is null, so the replay pipeline
        // would leave the page unhydrated. Must cold-load instead.
        val payload = ShowPayload(
            href = "https://testbench.rover.io/a/home",
            optimisticDataJson = null,
            responseJson = null,
            templateHash = null
        )
        assertFalse(AppScreensDecisions.isReplayablePayload(payload))
    }

    @Test
    fun `isReplayablePayload false for hydrate-only payload with optimistic data`() {
        val payload = ShowPayload(
            href = "https://testbench.rover.io/a/home",
            optimisticDataJson = "{\"optimistic\":true}",
            responseJson = null,
            templateHash = null
        )
        assertFalse(AppScreensDecisions.isReplayablePayload(payload))
    }

    @Test
    fun `isReplayablePayload true once morph resolved`() {
        val payload = ShowPayload(
            href = "https://testbench.rover.io/a/home",
            optimisticDataJson = null,
            responseJson = "{\"data\":{}}",
            templateHash = "abc123"
        )
        assertTrue(AppScreensDecisions.isReplayablePayload(payload))
    }

    // endregion
}
