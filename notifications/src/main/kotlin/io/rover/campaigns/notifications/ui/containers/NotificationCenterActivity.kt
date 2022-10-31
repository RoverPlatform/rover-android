package io.rover.campaigns.notifications.ui.containers

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import io.rover.campaigns.notifications.R
import io.rover.campaigns.notifications.ui.NotificationCenterListView

class NotificationCenterActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val listView = NotificationCenterListView(
            this
        )

        listView.activity = this

        title = getString(R.string.notification_center)

        setContentView(
            listView
        )
    }

    companion object {
        /**
         * Returns an intent for opening the stock version of the NotificationCenterActivity.
         */
        @JvmStatic
        fun makeIntent(packageContext: Context): Intent {
            return Intent(packageContext, NotificationCenterActivity::class.java)
        }
    }
}
