package io.rover.campaigns.core.permissions

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import io.rover.campaigns.core.logging.log
import io.rover.campaigns.core.streams.PublishSubject
import io.rover.campaigns.core.streams.Publishers
import org.reactivestreams.Publisher
import io.rover.campaigns.core.streams.doOnNext
import io.rover.campaigns.core.streams.filter
import io.rover.campaigns.core.streams.filterNulls
import io.rover.campaigns.core.streams.first
import io.rover.campaigns.core.streams.share

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

        if(alreadyGranted.isNotEmpty()) {
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
