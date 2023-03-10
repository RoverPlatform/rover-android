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

// package io.rover.sdk.experiences.classic.toolbar
//
// import androidx.appcompat.widget.Toolbar
// import org.reactivestreams.Publisher
//
// internal interface ViewExperienceToolbarInterface {
//    /**
//     * Set the toolbar view model.  However, uncharacteristically of the other bindable view mixins,
//     * this one is a method that returns a new [Toolbar] view.  This must be done because
//     * Android's Toolbar has limitations that prevent all of its styling being configurable after
//     * creation. Thus, uncharacteristically of the View mixins, this one is responsible for actually
//     * creating the view.
//     */
//    fun setViewModelAndReturnToolbar(
//        toolbarViewModel: ExperienceToolbarViewModelInterface
//    ): Toolbar
// }
//
// internal interface ExperienceToolbarViewModelInterface {
//    val toolbarEvents: Publisher<Event>
//
//    val configuration: ToolbarConfiguration
//
//    fun pressedBack()
//
//    fun pressedClose()
//
//    sealed class Event {
//        class PressedBack : Event()
//        class PressedClose : Event()
//    }
// }
