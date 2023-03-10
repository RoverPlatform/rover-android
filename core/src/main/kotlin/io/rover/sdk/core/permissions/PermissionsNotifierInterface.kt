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

import org.reactivestreams.Publisher

interface PermissionsNotifierInterface {
    /**
     * Indicate that a permission has just been granted by the user.  The UI code that handled the
     * permission result from the UI framework should call this.
     *
     * Any configured Rover SDK behaviour that is predicated on this permission becoming available
     * will activate. If this method is not called, then that behaviour will not work until the next
     * time the app is started.
     */
    fun permissionGranted(permissionId: String)

    fun notifyForPermission(permissionId: String): Publisher<String>

    /**
     * Subscribe to be notified of the given permission(s) being available.  Yields when the
     * permission is available, not before.
     *
     * If the permission is already available, will yield immediately.
     */
    fun notifyForAnyOfPermission(permissions: Set<String>): Publisher<String>
}
