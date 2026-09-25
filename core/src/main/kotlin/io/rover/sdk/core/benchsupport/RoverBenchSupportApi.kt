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

package io.rover.sdk.core.benchsupport

/**
 * Marks the Bench support API: hooks that exist for Rover's own test bench apps and
 * automated test harnesses, not for SDK integrators.
 *
 * Everything behind this marker is unsupported, may change or disappear in any release,
 * and has no bearing on production behaviour. Using it requires an explicit
 * `@OptIn(RoverBenchSupportApi::class)`, which is deliberately noisy — an integrator who
 * reaches for one of these hooks by accident gets a compile error rather than a
 * quietly-overridden configuration.
 *
 * @see RoverBenchSupport for the entry point.
 */
@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "This is a Rover Bench support API: unsupported, unstable, and intended only " +
        "for Rover's test bench apps. Opt in with @OptIn(RoverBenchSupportApi::class) if that " +
        "is really what you want."
)
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER
)
public annotation class RoverBenchSupportApi
