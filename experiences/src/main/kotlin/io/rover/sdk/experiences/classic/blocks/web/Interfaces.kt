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

package io.rover.sdk.experiences.classic.blocks.web

import io.rover.sdk.experiences.classic.blocks.concerns.background.BackgroundViewModelInterface
import io.rover.sdk.experiences.classic.blocks.concerns.border.BorderViewModelInterface
import io.rover.sdk.experiences.classic.blocks.concerns.layout.BlockViewModelInterface
import io.rover.sdk.experiences.classic.blocks.concerns.layout.CompositeBlockViewModelInterface
import io.rover.sdk.experiences.classic.blocks.concerns.layout.LayoutableViewModel
import io.rover.sdk.experiences.classic.concerns.BindableViewModel
import io.rover.sdk.experiences.classic.concerns.MeasuredBindableView
import java.net.URL

internal interface ViewWebInterface : MeasuredBindableView<WebViewModelInterface>

internal interface WebViewModelInterface : BindableViewModel {
    val url: URL
    val scrollingEnabled: Boolean
}

internal interface WebViewBlockViewModelInterface :
    CompositeBlockViewModelInterface,
    LayoutableViewModel,
    BlockViewModelInterface,
    BackgroundViewModelInterface,
    BorderViewModelInterface,
    WebViewModelInterface
