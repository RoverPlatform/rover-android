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

package io.rover.sdk.notifications.ui

import android.content.Context
import android.graphics.Bitmap
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import io.rover.core.R
import io.rover.sdk.core.streams.androidLifecycleDispose
import io.rover.sdk.core.streams.subscribe
import io.rover.sdk.core.ui.concerns.BindableView
import io.rover.sdk.core.ui.concerns.ViewModelBinding
import io.rover.sdk.notifications.ui.concerns.NotificationItemViewModelInterface

/**
 * View for the inbox list items.  Is bound to the
 * [NotificationItemViewModelInterface] view model.
 */
open class NotificationItemView : FrameLayout, BindableView<NotificationItemViewModelInterface> {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    init {
        buildLayout()
    }

    protected open fun buildLayout() {
        LayoutInflater.from(context).inflate(R.layout.notification_center_default_item, this)
    }

    override var viewModel: NotificationItemViewModelInterface? by ViewModelBinding { viewModel, subscriptionCallback ->
        if (viewModel != null) {
            bind(viewModel)
            if (viewModel.showThumbnailArea) {
                viewModel.requestThumbnailImage()
                    .androidLifecycleDispose(this)
                    .subscribe({ bitmap ->
                        bindThumbnail(bitmap)
                    }, { throw(it) }, { subscriptionCallback(it) })
            }
        }
    }

    /**
     * Populates the view with the notification details.
     *
     * Override this when using a custom layout.
     */
    protected open fun bind(viewModel: NotificationItemViewModelInterface) {
        val bodyView = findViewById<TextView>(R.id.body_text)
        val titleView = findViewById<TextView>(R.id.title_text)
        val imageView = findViewById<AppCompatImageView>(R.id.image_view)

        imageView.visibility = if (viewModel.showThumbnailArea) View.VISIBLE else View.GONE
        bodyView.text = viewModel.notificationForDisplay.body
        titleView.text = viewModel.notificationForDisplay.title
    }

    protected open fun bindThumbnail(bitmap: Bitmap) {
        val imageView = findViewById<AppCompatImageView>(R.id.image_view)
        imageView.scaleType = ImageView.ScaleType.CENTER_CROP // Fill.
        imageView.setImageBitmap(bitmap)
    }
}
