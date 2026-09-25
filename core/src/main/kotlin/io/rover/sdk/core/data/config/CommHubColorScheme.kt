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

package io.rover.sdk.core.data.config

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Color scheme preference for Rover.
 *
 * Maps to backend API values for UI usage.
 *
 * ### Why [JsonClass] is here
 *
 * No adapter is registered for this type, so Moshi deserializes it through its reflective enum
 * adapter, which looks each constant up by name. R8 renames those constant fields while leaving the
 * name strings in the static initializer alone, so the lookup fails and Moshi rethrows it as
 * `AssertionError: Missing field in ...`. Moshi ships a keep rule that prevents this, conditional on
 * this annotation, so the annotation is load-bearing despite asking for no codegen.
 *
 * Nothing here fails without it. A consumer's minified build crashes on startup instead, and
 * nothing on our side reports it. That is SDK-451. [CommHubColorSchemeKeepRuleTest] is the guard.
 */
@JsonClass(generateAdapter = false)
public enum class CommHubColorScheme {
    @Json(name = "auto")
    AUTO,
    
    @Json(name = "light")
    LIGHT,
    
    @Json(name = "dark")
    DARK
}
