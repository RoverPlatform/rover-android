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

package io.rover.sdk.experiences.rich.compose.ui.modifiers

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import io.rover.sdk.experiences.rich.compose.model.nodes.Screen
import io.rover.sdk.experiences.rich.compose.model.values.Alignment
import io.rover.sdk.experiences.rich.compose.model.values.Background
import io.rover.sdk.experiences.rich.compose.model.values.ColorReference
import io.rover.sdk.experiences.rich.compose.model.values.Overlay
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BackgroundOverlayModifiersTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyBackgroundNodeDoesNotCrash() {
        composeRule.setContent {
            BackgroundModifier(
                background = emptyScreenBackground(),
                modifier = Modifier,
                content = ::simpleContent,
            )
        }

        composeRule.waitForIdle()
    }

    @Test
    fun emptyOverlayNodeDoesNotCrash() {
        composeRule.setContent {
            OverlayModifier(
                overlay = emptyScreenOverlay(),
                modifier = Modifier,
                content = ::simpleContent,
            )
        }

        composeRule.waitForIdle()
    }

    @Composable
    private fun simpleContent(modifier: Modifier) {
        Box(modifier = modifier.size(1.dp))
    }

    private fun emptyScreenBackground(): Background {
        return Background(
            node = Screen(
                id = "empty-background",
                backgroundColor = ColorReference.SystemColor("blue"),
            ),
            alignment = Alignment.CENTER,
        )
    }

    private fun emptyScreenOverlay(): Overlay {
        return Overlay(
            node = Screen(
                id = "empty-overlay",
                backgroundColor = ColorReference.SystemColor("blue"),
            ),
            alignment = Alignment.CENTER,
        )
    }
}
