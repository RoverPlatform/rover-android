package io.rover.core.permissions

import android.content.Context
import android.content.pm.PackageManager
import android.support.v4.content.ContextCompat
import io.rover.core.logging.log
import io.rover.core.streams.PublishSubject
import io.rover.core.streams.Publishers
import org.reactivestreams.Publisher
import io.rover.core.streams.doOnNext
import io.rover.core.streams.filter
import io.rover.core.streams.filterNulls
import io.rover.core.streams.first
import io.rover.core.streams.share

class PermissionsNotifier(
    private val applicationContext: Context
) : PermissionsNotifierInterface {
    override fun permissionGranted(permissionId: String) {
        grantedPermissions.onNext(permissionId)
    }

    override fun notifyForPermission(permissionId: String): Publisher<String> {
        return Publishers.concat(
            Publishers.just(
                if (ContextCompat.checkSelfPermission(
                    applicationContext,
                    permissionId
                ) == PackageManager.PERMISSION_GRANTED) {
                    log.v("Permission $permissionId already granted.")
                    permissionId
                } else null
            ),
            updates.filter { it == permissionId }
        ).filterNulls().first()
    }

    private val grantedPermissions = PublishSubject<String>()

    private val updates = grantedPermissions.doOnNext { permission ->
        log.v("Permission granted: $permission")
    }.share()
}
