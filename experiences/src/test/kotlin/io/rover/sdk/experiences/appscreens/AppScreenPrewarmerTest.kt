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
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

/**
 * Exercises the pure prewarm enqueue policy ([PrewarmPlanner]) and the prewarmer's declared
 * defaults/constants. The async orchestration (WebView creation, stagger timing, retries) is driven
 * on-device; here only the deterministic, WebView-free parts are unit-tested.
 */
class AppScreenPrewarmerTest {

    private val home = "/a/home"
    private val detail = "/a/player-detail"
    private val standings = "/a/standings"

    @Test
    fun `plan keeps unknown, un-skippable paths in DOM order`() {
        val result = PrewarmPlanner.plan(
            candidateTemplatePaths = listOf(detail, standings),
            isKnown = { false },
            isSkippable = { false }
        )
        assertEquals(listOf(detail, standings), result)
    }

    @Test
    fun `plan drops known (warm or on-stack) templates`() {
        val result = PrewarmPlanner.plan(
            candidateTemplatePaths = listOf(home, detail, standings),
            isKnown = { it == home || it == detail },
            isSkippable = { false }
        )
        assertEquals(listOf(standings), result)
    }

    @Test
    fun `plan drops skippable (queued, in-flight, or exhausted) templates`() {
        val result = PrewarmPlanner.plan(
            candidateTemplatePaths = listOf(detail, standings),
            isKnown = { false },
            isSkippable = { it == standings }
        )
        assertEquals(listOf(detail), result)
    }

    @Test
    fun `plan de-duplicates within a hint preserving first occurrence order`() {
        val result = PrewarmPlanner.plan(
            candidateTemplatePaths = listOf(detail, standings, detail, standings),
            isKnown = { false },
            isSkippable = { false }
        )
        assertEquals(listOf(detail, standings), result)
    }

    @Test
    fun `plan yields empty when everything is known or skippable`() {
        val result = PrewarmPlanner.plan(
            candidateTemplatePaths = listOf(detail, standings),
            isKnown = { it == detail },
            isSkippable = { it == standings }
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `default attach strategy is Detached`() {
        assertEquals(PrewarmAttachStrategy.Detached, AppScreenPrewarmer.DEFAULT_STRATEGY)
    }

    @Test
    fun `stagger is 300ms and one retry is allowed`() {
        assertEquals(300.milliseconds, AppScreenPrewarmer.STAGGER)
        // MAX_ATTEMPTS = initial + one retry.
        assertEquals(2, AppScreenPrewarmer.MAX_ATTEMPTS)
    }
}
