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

package io.rover.sdk.experiences.rich.api.models

import com.squareup.moshi.JsonClass

/**
 * Carries [JsonClass] for the same reason as `io.rover.sdk.core.data.config.CommHubColorScheme`:
 * nothing registers an adapter for it, so Moshi resolves it reflectively by constant name, and
 * Moshi's keep rule against R8 renaming those fields is conditional on this annotation. See SDK-451.
 *
 * Unlike the enums in `rich.compose.model.values`, which an explicit @FromJson/@ToJson adapter
 * displaces from that path, this one is reached only through `rich.core.data.JsonParser`, which has
 * no caller inside the SDK. It is public API rather than unreachable, so the hole is real.
 *
 * [SegueStyleKeepRuleTest] is the guard.
 */
@JsonClass(generateAdapter = false)
enum class SegueStyle(val code: String) {
    PUSH("push"),
    MODAL("modal")
}
