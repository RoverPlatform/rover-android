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

package io.rover.sdk.experiences.classic.navigation

import io.rover.sdk.core.data.domain.Block
import java.net.URI

/**
 * Should navigate to the given URL or Screen.
 */
internal sealed class NavigateToFromBlock(
    val block: Block
) {
    /**
     * Navigate to something external to the experience through the Rover URI [Router].
     */
    class External(
        val uri: URI,
        block: Block
    ) : NavigateToFromBlock(block)

    class GoToScreenAction(
        val screenId: String,
        block: Block
    ) : NavigateToFromBlock(block)

    class PresentWebsiteAction(
        val url: URI,
        block: Block
    ) : NavigateToFromBlock(block)

    class None(
        block: Block
    ) : NavigateToFromBlock(block)

    class Custom(
        block: Block
    ) : NavigateToFromBlock(block)
}
