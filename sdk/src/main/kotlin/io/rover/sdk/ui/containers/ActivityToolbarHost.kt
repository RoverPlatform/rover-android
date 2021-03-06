package io.rover.sdk.ui.containers

import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import android.view.Menu
import android.view.Window
import io.rover.sdk.streams.PublishSubject
import io.rover.sdk.streams.share
import io.rover.sdk.ui.RoverView
import org.reactivestreams.Publisher

/**
 * Activities that wish to host [RoverView] must instantiate [ActivityToolbarHost], and
 * then also implement [AppCompatActivity.onCreateOptionsMenu] wherein they must then set the menu
 * on the toolbar host with [ActivityToolbarHost.menu].
 *
 * This arrangement is required on account of Android lacking
 *
 * It assumes that you are using [AppCompatActivity], which is strongly recommended in standard
 * Android apps.
 *
 * TODO: include direction about either setting the ActionBar feature to off in code or by style.
 */
internal class ActivityToolbarHost(private val activity: AppCompatActivity) : RoverView.ToolbarHost {
    var menu: Menu? = null
        set(newMenu) {
            field = newMenu

            emitIfPrerequisitesAvailable()
        }

    private val emitterSubject = PublishSubject<Pair<ActionBar, Menu>>()
    private val emitter = emitterSubject.share()

    private var actionBar: ActionBar? = null

    private fun emitIfPrerequisitesAvailable() {
        val actionBar = actionBar
        val menu = menu

        if (actionBar != null && menu != null) {
            emitterSubject.onNext(Pair(actionBar, menu))
        }
    }

    override fun setToolbarAsActionBar(toolbar: Toolbar): Publisher<Pair<ActionBar, Menu>> {
        try {
            activity.setSupportActionBar(toolbar)
        } catch (e: IllegalStateException) {
            throw RuntimeException("You've used RoverView inside an Activity that already has an action bar set.\n" +
                "Do not request Window.FEATURE_SUPPORT_ACTION_BAR and set windowActionBar to false in your theme to use a Toolbar instead.\n" +
                "Consider changing the Activity's theme to use a .NoActionBar variant of your theme.", e)
        }
        // and then retrieved the wrapped actionbar delegate
        actionBar = activity.supportActionBar
        return emitter
    }

    override fun provideWindow(): Window {
        return activity.window
    }
}