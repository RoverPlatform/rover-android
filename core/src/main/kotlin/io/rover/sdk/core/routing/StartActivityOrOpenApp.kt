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

package io.rover.sdk.core.routing

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.annotation.RestrictTo
import androidx.core.content.ContextCompat
import io.rover.sdk.core.logging.log

/**
 * Starts [intent] from this activity, and when no installed activity can handle it, opens the app
 * with [openApp] instead so the launch still lands somewhere.
 *
 * Rover's trampolines (inbound links in core, notification taps in notifications) are the only
 * activity in their task, so finishing one without launching anything takes the task with it and
 * the user sees the app open and immediately close. The intent they dispatch can be an ACTION_VIEW
 * for a remotely configured deep link, so nothing guarantees a handler exists.
 *
 * [failureMessage] is logged at warning level when [intent] cannot be started, with the intent
 * appended, since the fallback below recovers it. The three terminal branches, where nothing can
 * be launched, log at error level. When [openApp] is null, or is the very same instance as [intent] because the caller
 * had already fallen back to it, there is nothing further to try, and that is logged rather than
 * relaunching what just threw.
 *
 * Cross-module helper exposed to sibling Rover modules only via [RestrictTo]
 * ([LIBRARY_GROUP][RestrictTo.Scope.LIBRARY_GROUP]); NOT part of the supported public SDK API
 * surface.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
fun Activity.startActivityOrOpenApp(intent: Intent, openApp: Intent?, failureMessage: String) {
    try {
        ContextCompat.startActivity(this, intent, null)
    } catch (_: ActivityNotFoundException) {
        log.w("$failureMessage\n\nIntent was: $intent")
        when {
            openApp == null -> {
                log.e("No app open intent is registered, so this launch cannot be recovered.")
            }
            openApp === intent -> {
                log.e("The intent was already the app open intent, so there is nothing left to fall back to.")
            }
            else -> {
                try {
                    ContextCompat.startActivity(this, openApp, null)
                } catch (_: ActivityNotFoundException) {
                    log.e("The app open intent could not be launched either.  Giving up on this launch.")
                }
            }
        }
    }
}
