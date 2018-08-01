package io.rover.notifications.ui.containers

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import io.rover.notifications.R
import io.rover.notifications.ui.NotificationCenterListView

class NotificationCenterActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val listView = NotificationCenterListView(
            applicationContext
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
