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

package io.rover.sdk.experiences.appscreens

import androidx.annotation.RestrictTo
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * A single native, host-supplied action rendered by App Screens as a floating affordance in the
 * safe-area top band of the ROOT screen only (see [LocalAppScreenRootAffordance]).
 *
 * This is the Android counterpart of the iOS Hub inbox affordance: the embedding host (the
 * Communication Hub) hands App Screens a small value (icon, optional badge, tap action) and App
 * Screens renders it — it is deliberately NOT a web/document element. Cross-module type, exposed to
 * sibling Rover modules only via [RestrictTo][androidx.annotation.RestrictTo]
 * ([LIBRARY_GROUP][RestrictTo.Scope.LIBRARY_GROUP]); not part of the supported public SDK surface.
 *
 * @param icon The icon to render inside the affordance's circular container.
 * @param badgeText Optional badge text (e.g. an unread count); when non-null a Material3 badge is
 * drawn over the icon. Null hides the badge.
 * @param contentDescription Accessibility description for the affordance's tap target.
 * @param onTap Invoked when the affordance is tapped. The host drives its own navigation here (the
 * Hub navigates to its inbox route); App Screens does not interpret the tap.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public data class AppScreenRootAffordance(
    val icon: ImageVector,
    val badgeText: String?,
    val contentDescription: String,
    val onTap: () -> Unit
)

/**
 * Supplies the current [AppScreenRootAffordance] to the App Screens UI, or null (the default) when
 * no host affordance should be shown.
 *
 * When non-null, App Screens overlays the affordance on the root entry only: it is hidden while any
 * detail is pushed on top (root stack size > 1) and restored on pop, and is naturally covered by a
 * presented sheet. Cross-module hook exposed to sibling Rover modules only via
 * [RestrictTo][androidx.annotation.RestrictTo] ([LIBRARY_GROUP][RestrictTo.Scope.LIBRARY_GROUP]); it
 * is NOT part of the supported public SDK API surface.
 */
@get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public val LocalAppScreenRootAffordance = compositionLocalOf<AppScreenRootAffordance?> { null }
