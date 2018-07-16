package io.rover.experiences.ui

import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import io.rover.core.Rover
import io.rover.core.logging.log
import java.net.URI

/**
 * The intent filter you add for your universal links and `rv-$appname://` deep links should by default
 * point to this activity.
 */
open class TransientLinkLaunchActivity : AppCompatActivity() {
    private val linkOpen by lazy {
        Rover.sharedInstance.linkOpen
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uri = URI(intent.data.toString())

        log.v("Transient link launch activity running for received URI: '${intent.data}'")

        val intentStack = linkOpen.localIntentForReceived(
            uri
        )

        log.v("Launching stack ${intentStack.size} deep: ${intentStack.joinToString("\n") { it.toString() }}")

        ContextCompat.startActivities(
            this,
            intentStack.toTypedArray()
        )
        finish()
    }
}
