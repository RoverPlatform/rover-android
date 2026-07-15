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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises the pure navigation core over trivial fake sessions (each a fresh [Any] identity, so
 * the model's identity-based warm/on-stack reasoning is what is under test). No WebView, coroutine,
 * or Android dependency is involved.
 */
class NavigatorModelTest {

    private class FakeSession(val templatePath: String)

    private fun model(created: MutableList<FakeSession> = mutableListOf()) =
        NavigatorModel<FakeSession> { tp -> FakeSession(tp).also { created.add(it) } }

    private val home = "/a/home"
    private val detail = "/a/player-detail"
    private val standings = "/a/standings"

    @Test
    fun `start creates the master and registers it warm`() {
        val created = mutableListOf<FakeSession>()
        val m = model(created)

        val outcome = m.start(home, "https://x/a/home", null)

        assertEquals(AppScreensDecisions.SessionSelection.Create, outcome.selection)
        assertEquals(1, m.rootStack.size)
        assertFalse(outcome.entry.isEphemeral)
        assertEquals(1, created.size)
        assertTrue(m.hasWarmSession(home))
    }

    @Test
    fun `navigate to a fresh template creates and registers warm`() {
        val m = model()
        m.start(home, "https://x/a/home", null)

        val outcome = m.navigate(detail, "https://x/a/player-detail?id=1", "{\"id\":1}", null, fromSheet = false)

        assertEquals(AppScreensDecisions.SessionSelection.Create, outcome.selection)
        assertFalse(outcome.entry.isEphemeral)
        assertEquals(2, m.rootStack.size)
        assertEquals("{\"id\":1}", outcome.entry.optimisticDataJson)
    }

    @Test
    fun `popping a template session leaves it warm for reuse`() {
        val m = model()
        m.start(home, "https://x/a/home", null)
        val push = m.navigate(detail, "https://x/a/player-detail?id=1", null, null, fromSheet = false)
        val detailSession = push.entry.session

        // Pop the detail entry (a committed NavHost pop back to home).
        val removed = m.onPopped(listOf(push.entry.entryId))
        assertEquals(listOf(detailSession), removed.map { it.session })
        assertFalse(removed.first().isEphemeral)
        assertEquals(1, m.rootStack.size)

        // Navigating to the same template now reuses the SAME session (warm, off-stack).
        val reuse = m.navigate(detail, "https://x/a/player-detail?id=2", null, null, fromSheet = false)
        assertEquals(AppScreensDecisions.SessionSelection.Reuse, reuse.selection)
        assertSame(detailSession, reuse.entry.session)
    }

    @Test
    fun `detail to detail while on stack is ephemeral with a distinct session`() {
        val m = model()
        m.start(home, "https://x/a/home", null)
        val first = m.navigate(detail, "https://x/a/player-detail?id=1", null, null, fromSheet = false)

        // The detail template is still on the stack, so a second navigate is a one-off.
        val second = m.navigate(detail, "https://x/a/player-detail?id=9", null, null, fromSheet = false)
        assertEquals(AppScreensDecisions.SessionSelection.Ephemeral, second.selection)
        assertTrue(second.entry.isEphemeral)
        assertTrue(first.entry.session !== second.entry.session)
        assertEquals(3, m.rootStack.size)

        // Popping the ephemeral returns it as ephemeral (navigator destroys it).
        val removed = m.onPopped(listOf(second.entry.entryId))
        assertEquals(1, removed.size)
        assertTrue(removed.first().isEphemeral)
    }

    @Test
    fun `sheet transition pushes onto the sheet stack and presents a new sheet`() {
        val m = model()
        m.start(home, "https://x/a/home", null)

        val outcome = m.navigate(standings, "https://x/a/standings", null, "sheet", fromSheet = false)
        assertTrue(outcome.toSheet)
        assertTrue(outcome.presentedNewSheet)
        assertEquals(1, m.sheetStack.size)
        assertEquals(1, m.rootStack.size)
    }

    @Test
    fun `sheet transition from inside a sheet is a plain in-sheet push`() {
        val m = model()
        m.start(home, "https://x/a/home", null)
        m.navigate(standings, "https://x/a/standings", null, "sheet", fromSheet = false)

        val inner = m.navigate(detail, "https://x/a/player-detail?id=1", null, "sheet", fromSheet = true)
        assertTrue(inner.toSheet)
        assertFalse(inner.presentedNewSheet)
        assertEquals(2, m.sheetStack.size)
    }

    @Test
    fun `session on the sheet stack forces ephemeral on the root stack`() {
        val m = model()
        m.start(home, "https://x/a/home", null)
        // Present detail in a sheet: detail becomes warm AND on the sheet stack.
        val sheetPush = m.navigate(detail, "https://x/a/player-detail?id=1", null, "sheet", fromSheet = false)

        // Now navigate to the same template on the root stack: the on-stack check spans both stacks.
        val rootPush = m.navigate(detail, "https://x/a/player-detail?id=2", null, null, fromSheet = false)
        assertEquals(AppScreensDecisions.SessionSelection.Ephemeral, rootPush.selection)
        assertTrue(sheetPush.entry.session !== rootPush.entry.session)
    }

    @Test
    fun `sheet dismissal releases the whole sheet stack`() {
        val m = model()
        m.start(home, "https://x/a/home", null)
        m.navigate(standings, "https://x/a/standings", null, "sheet", fromSheet = false)
        m.navigate(detail, "https://x/a/player-detail?id=1", null, null, fromSheet = true)

        val removed = m.onSheetDismissed()
        assertEquals(2, removed.size)
        assertEquals(0, m.sheetStack.size)
    }

    @Test
    fun `teardown clears stacks but preserves the warm pool for reuse`() {
        val m = model()
        val start = m.start(home, "https://x/a/home", null)
        m.navigate(detail, "https://x/a/player-detail?id=1", null, null, fromSheet = false)

        val removed = m.teardown()
        assertEquals(2, removed.size)
        assertEquals(0, m.rootStack.size)
        assertTrue(m.hasWarmSession(home))

        // A fresh start reuses the warm master session.
        val restart = m.start(home, "https://x/a/home", null)
        assertEquals(AppScreensDecisions.SessionSelection.Reuse, restart.selection)
        assertSame(start.entry.session, restart.entry.session)
    }

    @Test
    fun `forgetWarm removes a destroyed session from the pool`() {
        val m = model()
        m.start(home, "https://x/a/home", null)
        val push = m.navigate(detail, "https://x/a/player-detail?id=1", null, null, fromSheet = false)
        m.onPopped(listOf(push.entry.entryId))

        m.forgetWarm(push.entry.session)
        assertFalse(m.hasWarmSession(detail))

        // With no warm session, navigating creates anew.
        val again = m.navigate(detail, "https://x/a/player-detail?id=3", null, null, fromSheet = false)
        assertEquals(AppScreensDecisions.SessionSelection.Create, again.selection)
        assertTrue(again.entry.session !== push.entry.session)
    }

    @Test
    fun `warmIdleSessionFor returns a pooled session only while it is off every stack`() {
        val m = model()
        val start = m.start(home, "https://x/a/home", null)

        // The master is on the root stack, so it is NOT idle (a navigate to it would be ephemeral,
        // never reuse) — the surface-crossing claim guard must not see it here.
        assertNull(m.warmIdleSessionFor(home))

        // Tear the stacks down (a surface reset): the master stays pooled but is now idle.
        m.teardown()
        assertSame(start.entry.session, m.warmIdleSessionFor(home))

        // An unknown template has no pooled session.
        assertNull(m.warmIdleSessionFor(standings))
    }

    @Test
    fun `prewarmed session registers warm and is then reused on navigate`() {
        val created = mutableListOf<FakeSession>()
        val m = model(created)
        m.start(home, "https://x/a/home", null)

        // Simulate a prewarm: a session created out-of-band that joins the warm pool.
        val prewarmed = FakeSession(detail)
        assertTrue(m.registerWarm(detail, prewarmed))
        assertTrue(m.hasWarmSession(detail))

        // Navigating to the prewarmed template reuses the SAME session with no new creation.
        val createdBefore = created.size
        val outcome = m.navigate(detail, "https://x/a/player-detail?id=1", null, null, fromSheet = false)
        assertEquals(AppScreensDecisions.SessionSelection.Reuse, outcome.selection)
        assertSame(prewarmed, outcome.entry.session)
        assertEquals(createdBefore, created.size)
    }

    @Test
    fun `registerWarm refuses to clobber an existing warm session`() {
        val m = model()
        m.start(home, "https://x/a/home", null)
        val first = FakeSession(detail)
        assertTrue(m.registerWarm(detail, first))

        val second = FakeSession(detail)
        assertFalse(m.registerWarm(detail, second))
        // The original remains the warm session.
        val outcome = m.navigate(detail, "https://x/a/player-detail?id=1", null, null, fromSheet = false)
        assertSame(first, outcome.entry.session)
    }

    @Test
    fun `isTemplateKnown spans warm pool and both stacks`() {
        val m = model()
        m.start(home, "https://x/a/home", null)
        assertTrue(m.isTemplateKnown(home))
        assertFalse(m.isTemplateKnown(detail))

        m.navigate(standings, "https://x/a/standings", null, "sheet", fromSheet = false)
        assertTrue(m.isTemplateKnown(standings))

        val prewarmed = FakeSession(detail)
        m.registerWarm(detail, prewarmed)
        assertTrue(m.isTemplateKnown(detail))
    }

    // region livenessOf (renderer-death classification)

    @Test
    fun `livenessOf top of root stack is visible`() {
        val m = model()
        val start = m.start(home, "https://x/a/home", null)
        assertEquals(AppScreensDecisions.SessionLiveness.Visible, m.livenessOf(start.entry.session))
    }

    @Test
    fun `livenessOf below-top root entry is on-stack hidden`() {
        val m = model()
        val start = m.start(home, "https://x/a/home", null)
        val detailPush = m.navigate(detail, "https://x/a/player-detail?id=1", null, null, fromSheet = false)

        // Detail is on top (visible); home sits below it.
        assertEquals(AppScreensDecisions.SessionLiveness.Visible, m.livenessOf(detailPush.entry.session))
        assertEquals(AppScreensDecisions.SessionLiveness.OnStackHidden, m.livenessOf(start.entry.session))
    }

    @Test
    fun `livenessOf root top under a presented sheet counts as not-visible`() {
        val m = model()
        val start = m.start(home, "https://x/a/home", null)
        val sheet = m.navigate(standings, "https://x/a/standings", null, "sheet", fromSheet = false)

        // The sheet top is the visible surface; the root top (home) is covered → hidden.
        assertEquals(AppScreensDecisions.SessionLiveness.Visible, m.livenessOf(sheet.entry.session))
        assertEquals(AppScreensDecisions.SessionLiveness.OnStackHidden, m.livenessOf(start.entry.session))
    }

    @Test
    fun `livenessOf a session on no stack is warm idle`() {
        val m = model()
        m.start(home, "https://x/a/home", null)
        val prewarmed = FakeSession(detail)
        m.registerWarm(detail, prewarmed)
        assertEquals(AppScreensDecisions.SessionLiveness.WarmIdle, m.livenessOf(prewarmed))
    }

    @Test
    fun `livenessOf a popped-warm session becomes warm idle`() {
        val m = model()
        m.start(home, "https://x/a/home", null)
        val push = m.navigate(detail, "https://x/a/player-detail?id=1", null, null, fromSheet = false)
        assertEquals(AppScreensDecisions.SessionLiveness.Visible, m.livenessOf(push.entry.session))

        m.onPopped(listOf(push.entry.entryId))
        // Off the stack but still warm → idle.
        assertEquals(AppScreensDecisions.SessionLiveness.WarmIdle, m.livenessOf(push.entry.session))
    }

    // endregion

    // region deferred ephemeral disposal

    @Test
    fun `deferDisposal then takeDisposed returns the entry exactly once`() {
        val m = model()
        m.start(home, "https://x/a/home", null)
        val first = m.navigate(detail, "https://x/a/player-detail?id=1", null, null, fromSheet = false)
        val second = m.navigate(detail, "https://x/a/player-detail?id=9", null, null, fromSheet = false)
        assertTrue(second.entry.isEphemeral)

        m.deferDisposal(second.entry)
        // First take reclaims the parked entry...
        assertSame(second.entry, m.takeDisposed(second.entry.entryId))
        // ...a second take for the same id returns null (single-shot).
        assertNull(m.takeDisposed(second.entry.entryId))
    }

    @Test
    fun `takeDisposed for an unknown id returns null`() {
        val m = model()
        m.start(home, "https://x/a/home", null)
        assertNull(m.takeDisposed("never-deferred"))
    }

    @Test
    fun `drainPendingDisposal returns all pending and empties the map`() {
        val m = model()
        m.start(home, "https://x/a/home", null)
        val a = m.navigate(detail, "https://x/a/player-detail?id=1", null, null, fromSheet = false)
        val b = m.navigate(detail, "https://x/a/player-detail?id=2", null, null, fromSheet = false)

        m.deferDisposal(a.entry)
        m.deferDisposal(b.entry)

        val drained = m.drainPendingDisposal()
        assertEquals(setOf(a.entry, b.entry), drained.toSet())
        // The map is now empty: a second drain is empty and the ids can no longer be taken.
        assertTrue(m.drainPendingDisposal().isEmpty())
        assertNull(m.takeDisposed(a.entry.entryId))
        assertNull(m.takeDisposed(b.entry.entryId))
    }

    // endregion

    @Test
    fun `sessionFor resolves across both stacks and returns null for unknown`() {
        val m = model()
        val start = m.start(home, "https://x/a/home", null)
        val sheet = m.navigate(standings, "https://x/a/standings", null, "sheet", fromSheet = false)

        assertSame(start.entry.session, m.sessionFor(start.entry.entryId))
        assertSame(sheet.entry.session, m.sessionFor(sheet.entry.entryId))
        assertNull(m.sessionFor("nonexistent"))
    }

    @Test
    fun `a second surface presenting the same template reuses and re-masters the warm session`() {
        // Models the multi-surface steal: a first surface presents /a/home (creating the master),
        // then a second surface presents the same template over it. The navigator teardown()s the
        // covered surface's stack (leaving the session warm) and re-establishes; because the warm
        // session is now off-stack, the second start Reuses the very same session as its new master —
        // the model-level decision behind handing (stealing) the shared WebView to the new host.
        val m = model()
        val first = m.start(home, "https://x/a/home", null)
        val firstSession = first.entry.session

        // Second surface presents: the first surface's stack is torn down (session left warm)...
        val torndown = m.teardown()
        assertEquals(listOf(firstSession), torndown.map { it.session })
        assertTrue(m.hasWarmSession(home))

        // ...then the second surface establishes and reuses the same warm session as its master.
        val second = m.start(home, "https://x/a/home", null)
        assertEquals(AppScreensDecisions.SessionSelection.Reuse, second.selection)
        assertSame(firstSession, second.entry.session)
        assertEquals(1, m.rootStack.size)
    }
}
