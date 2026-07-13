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

    // region templatePath

    @Test
    fun `templatePath strips query`() {
        assertEquals(
            "/a/player-detail",
            AppScreensDecisions.templatePath(Uri.parse("https://testbench.rover.io/a/player-detail?id=12"))
        )
    }

    @Test
    fun `templatePath without query`() {
        assertEquals(
            "/a/standings",
            AppScreensDecisions.templatePath(Uri.parse("https://testbench.rover.io/a/standings"))
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

    // region recoveryAction (M6 renderer-death policy)

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
}
