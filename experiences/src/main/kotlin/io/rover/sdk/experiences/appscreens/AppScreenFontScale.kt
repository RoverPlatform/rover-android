/*
 * Copyright (c) 2026, Rover Labs, Inc. All rights reserved.
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

import kotlin.math.roundToInt

/**
 * Maps the system font scale onto WebView's text zoom percentage.
 *
 * A WebView derives its initial effective text zoom from its construction
 * context's `Configuration.fontScale` once, and never observes later
 * configuration changes. The prewarmed App Screens WebView outlives every
 * lifecycle that would normally refresh it (it survives activity
 * recreation), so the host applies this value explicitly on every
 * attach/update instead of relying on the constructor-time default.
 */
internal fun textZoomFor(fontScale: Float): Int = (fontScale * 100).roundToInt()
