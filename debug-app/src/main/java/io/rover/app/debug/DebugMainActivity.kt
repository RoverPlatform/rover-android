package io.rover.app.debug

import android.os.Bundle
import android.support.design.widget.BottomNavigationView
import android.support.v7.app.AppCompatActivity
import android.view.View
import kotlinx.android.synthetic.main.activity_debug_main.navigation
import kotlinx.android.synthetic.main.activity_debug_main.notification_center
import kotlinx.android.synthetic.main.activity_debug_main.settings_fragment

class DebugMainActivity : AppCompatActivity() {

    private val mOnNavigationItemSelectedListener = BottomNavigationView.OnNavigationItemSelectedListener { item ->
        selectTab(item.itemId)
        return@OnNavigationItemSelectedListener true
    }

    private fun selectTab(itemId: Int) {
        if(itemId == R.id.navigation_settings) {
            // show
            supportFragmentManager.beginTransaction().show(this.settings_fragment).commit()
        } else {
            // hide
            supportFragmentManager.beginTransaction().hide(this.settings_fragment).commit()
        }
        this.notification_center.visibility = if(itemId == R.id.navigation_notifications) View.VISIBLE else View.GONE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_debug_main)

        navigation.setOnNavigationItemSelectedListener(mOnNavigationItemSelectedListener)

        selectTab(R.id.navigation_notifications)
    }
}
