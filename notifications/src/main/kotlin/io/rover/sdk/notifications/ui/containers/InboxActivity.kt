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

package io.rover.sdk.notifications.ui.containers

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import io.rover.notifications.R
import io.rover.sdk.notifications.ui.InboxListView

class InboxActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val listView = InboxListView(
            this
        )

        title = getString(R.string.inbox_title)

        setContentView(
            listView
        )

        // On Android 15+ (SDK 35+) edge-to-edge drawing is enforced. AppCompat positions the
        // ActionBar below the status bar but no longer physically offsets the content region;
        // it instead delivers the combined status-bar + ActionBar inset to the content view.
        // The content view here is a plain CoordinatorLayout that does not consume insets on its
        // own, so without this the list would draw underneath the status bar and ActionBar.
        // A little breathing room so the first row doesn't butt right up against the app bar.
        val listTopSpacing = (8 * resources.displayMetrics.density).toInt()

        ViewCompat.setOnApplyWindowInsetsListener(listView) { view, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.updatePadding(
                left = insets.left,
                top = insets.top + listTopSpacing,
                right = insets.right,
                bottom = insets.bottom
            )
            WindowInsetsCompat.CONSUMED
        }
    }

    companion object {
        /**
         * Returns an intent for opening the stock version of the NotificationCenterActivity.
         */
        @JvmStatic
        fun makeIntent(packageContext: Context): Intent {
            return Intent(packageContext, InboxActivity::class.java)
        }
    }
}
