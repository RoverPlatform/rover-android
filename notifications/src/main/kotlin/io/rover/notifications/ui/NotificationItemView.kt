package io.rover.notifications.ui

import android.content.Context
import android.graphics.Bitmap
import android.support.v7.widget.AppCompatImageView
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import io.rover.core.R
import io.rover.core.streams.androidLifecycleDispose
import io.rover.core.streams.subscribe
import io.rover.core.ui.concerns.ViewModelBinding
import io.rover.notifications.ui.concerns.NotificationItemViewModelInterface

/**
 * View for the notification center list items.  Is bound to the
 * [NotificationItemViewModelInterface] view model.
 */
open class NotificationItemView: FrameLayout {
    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    init {
        LayoutInflater.from(context).inflate(R.layout.notification_center_default_item, this)
    }

    var viewModel: NotificationItemViewModelInterface? by ViewModelBinding { viewModel, subscriptionCallback ->
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
    protected fun bind(viewModel: NotificationItemViewModelInterface) {

        val bodyView = findViewById<TextView>(R.id.body_text)
        val titleView = findViewById<TextView>(R.id.title_text)
        val imageView = findViewById<AppCompatImageView>(R.id.image_view)

        imageView.visibility = if(viewModel.showThumbnailArea) View.VISIBLE else View.GONE
        bodyView.text = viewModel.notificationForDisplay.body
        titleView.text = viewModel.notificationForDisplay.title
    }

    protected fun bindThumbnail(bitmap: Bitmap) {
        val imageView = findViewById<AppCompatImageView>(R.id.image_view)
        imageView.scaleType = ImageView.ScaleType.CENTER_CROP // Fill.
        imageView.setImageBitmap(bitmap)
    }
}

// so I'm inflating.  How do I handle that in my custom view?

// how will developers override?  They will need to provide an alternative version.
// Perhaps:

// - mark this class as open
// - have the view model factory return a view object of this type
// - devs can then do what they like.

// - and they can also modify the view model.

// NOTE! because they cannot change the subscription to the view model (MicroRx methods are
// internal), we must ensure there's a separate method that is given the image as the template pattern.

// however.
