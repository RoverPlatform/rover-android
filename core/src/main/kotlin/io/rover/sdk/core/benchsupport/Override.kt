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

@file:OptIn(RoverBenchSupportApi::class)

package io.rover.sdk.core.benchsupport

/**
 * A tri-state override for a single backend-provided value.
 *
 * The three states are distinct on purpose: "leave the backend value alone" and "force this
 * value to be absent" are different intents, and a plain nullable value cannot express both.
 *
 * Note this type shadows `java.lang.Override` (a default import on Kotlin/JVM) at use sites
 * that import it explicitly; that is intentional, and mirrors the `Override<T>` type in the
 * iOS SDK's Bench support API.
 *
 * @see RoverBenchSupport
 */
@RoverBenchSupportApi
public sealed class Override<out T : Any> {
    /**
     * Pass the backend value through untouched. This is the default for every field.
     */
    public object NoOverride : Override<Nothing>()

    /**
     * Force the value to be absent, regardless of what the backend provides.
     *
     * For a nullable field this means `null`. For a non-nullable field it means the field's
     * declared default, since "absent" and "default" are the same thing for a value that
     * cannot be null.
     */
    public object Unset : Override<Nothing>()

    /**
     * Force [value], regardless of what the backend provides.
     */
    public data class Value<out T : Any>(public val value: T) : Override<T>()
}

/**
 * Applies this override to a nullable [backendValue].
 */
internal fun <T : Any> Override<T>.resolve(backendValue: T?): T? = when (this) {
    Override.NoOverride -> backendValue
    Override.Unset -> null
    is Override.Value -> value
}

/**
 * Applies this override to a non-nullable [backendValue], where [Override.Unset] resolves to
 * [unsetValue] — the field's declared default.
 */
internal fun <T : Any> Override<T>.resolve(backendValue: T, unsetValue: T): T = when (this) {
    Override.NoOverride -> backendValue
    Override.Unset -> unsetValue
    is Override.Value -> value
}
