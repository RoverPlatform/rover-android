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

@file:JvmName("LengthExtensions")

package io.rover.sdk.experiences.classic.layout

// /**
// * Returns the amount the Length's absolute value, taking into
// * account the given [against] denominator if the Length is a proportional one (percentage).
// *
// * The main goal of this function is to abstract away [Length]'s absolute
// * and proportional modalities.
// */
// fun Length.measuredAgainst(against: Double): Double = when (unit) {
//    UnitOfMeasure.Percentage -> value * against
//    UnitOfMeasure.Points -> value
// }
//
// /**
// * Returns the amount the Length's absolute value, taking into
// * account the given [against] denominator if the Length is a proportional one (percentage).
// *
// * The goal of this function is to abstract away [Length]'s absolute
// * and proportional modalities.
// */
// fun Length.measuredAgainst(against: Float): Float = when (unit) {
//    UnitOfMeasure.Percentage -> value.toFloat() * against
//    UnitOfMeasure.Points -> value.toFloat()
// }
