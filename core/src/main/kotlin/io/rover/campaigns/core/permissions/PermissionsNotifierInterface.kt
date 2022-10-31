package io.rover.campaigns.core.permissions

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
