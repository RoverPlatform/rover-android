package io.rover.sdk.core.routing

import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AppCompatActivity
import io.rover.sdk.core.Rover
import io.rover.sdk.core.logging.log
import java.net.URI

/**
 * The intent filter you add for your universal links and `rv-$appname://` deep links should by default
 * point to this activity.
 */
open class TransientLinkLaunchActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rover = Rover.shared
        if (rover == null) {
            log.e("A deep or universal link mapped to Rover was opened, but Rover is not initialized.  Ignoring.")
            return
        }
        val linkOpen = rover.resolve(LinkOpenInterface::class.java)
        if (linkOpen == null) {
            log.e("A deep or universal link mapped to Rover was opened, but LinkOpenInterface is not registered in the Rover container. Ensure ExperiencesAssembler() is in Rover.initialize(). Ignoring.")
            return
        }

        val uri = URI(intent.data.toString())

        log.v("Transient link launch activity running for received URI: '${intent.data}'")

        val intentStack = linkOpen.localIntentForReceived(uri)

        log.v("Launching stack ${intentStack.size} deep: ${intentStack.joinToString("\n") { it.toString() }}")

        if (intentStack.isNotEmpty()) ContextCompat.startActivities(this, intentStack.toTypedArray())
        finish()
    }
}