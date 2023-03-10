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

package io.rover.sdk.core.permissions

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import io.rover.sdk.core.logging.log
import io.rover.sdk.core.streams.PublishSubject
import io.rover.sdk.core.streams.Publishers
import io.rover.sdk.core.streams.doOnNext
import io.rover.sdk.core.streams.filter
import io.rover.sdk.core.streams.filterNulls
import io.rover.sdk.core.streams.first
import io.rover.sdk.core.streams.share
import org.reactivestreams.Publisher

class PermissionsNotifier(
    private val applicationContext: Context
) : PermissionsNotifierInterface {
    override fun permissionGranted(permissionId: String) {
        grantedPermissions.onNext(permissionId)
    }

    override fun notifyForAnyOfPermission(permissions: Set<String>): Publisher<String> {
        val alreadyGranted = permissions.filter { permissionId ->
            ContextCompat.checkSelfPermission(
                applicationContext,
                permissionId
            ) == PackageManager.PERMISSION_GRANTED
        }

        if (alreadyGranted.isNotEmpty()) {
            log.v("Permissions ${alreadyGranted.joinToString(", ")} already granted.")
        }

        return Publishers.concat(
            Publishers.just(
                if (alreadyGranted.isNotEmpty()) {
                    alreadyGranted.first()
                } else null
            ),
            updates.filter { permissions.contains(it) }
        ).filterNulls().first()
    }

    override fun notifyForPermission(permissionId: String): Publisher<String> {
        return notifyForAnyOfPermission(setOf(permissionId))
    }

    private val grantedPermissions = PublishSubject<String>()

    private val updates = grantedPermissions.doOnNext { permission ->
        log.v("Permission granted: $permission")
    }.share()
}
