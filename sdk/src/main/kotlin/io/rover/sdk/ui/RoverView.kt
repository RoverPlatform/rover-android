package io.rover.sdk.ui

import android.animation.Animator
import android.animation.ObjectAnimator
import android.animation.TypeEvaluator
import android.content.Context
import android.os.Build
import android.provider.Settings
import com.google.android.material.appbar.AppBarLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.snackbar.Snackbar
import androidx.swiperefreshlayout.widget.CircularProgressDrawable
import androidx.appcompat.app.ActionBar
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.Toolbar
import android.util.AttributeSet
import android.view.Gravity
import android.view.Menu
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import io.rover.sdk.R
import io.rover.sdk.ui.containers.RoverActivity
import io.rover.sdk.ui.navigation.NavigationView
import io.rover.sdk.ui.toolbar.ViewExperienceToolbar
import io.rover.sdk.logging.log
import io.rover.sdk.streams.androidLifecycleDispose
import io.rover.sdk.streams.subscribe
import io.rover.sdk.ui.concerns.MeasuredBindableView
import io.rover.sdk.ui.concerns.ViewModelBinding
import io.rover.sdk.platform.whenNotNull
import org.reactivestreams.Publisher

/**
 * Embed this view to include a Rover Experience in a layout.
 *
 * Most applications will likely want to use [RoverActivity] to display an Experience, but for
 * more custom setups (say, tablet-enabled single-activity apps that avoid fragments), you can embed
 * [RoverView] directly, although you will need to do a few more things manually.
 *
 * In order to display an Experience, instantiate RoverViewModel and set it to
 * [viewModelBinding].
 *
 * Note about Android state restoration: Rover SDK views handle state saving & restoration through
 * their view models, so you will need store a Parcelable on behalf of RoverView and
 * [RoverViewModel] (grabbing the state Parcelable from the view model at save time and
 * restoring it by passing it to the view model factory at restart time).
 *
 * See [RoverActivity] for an example of how to integrate.
 */
internal class RoverView : CoordinatorLayout, MeasuredBindableView<RoverViewModelInterface> {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    override var viewModelBinding: MeasuredBindableView.Binding<RoverViewModelInterface>? by ViewModelBinding(rebindingAllowed = false) { binding, subscriptionCallback ->
        // sadly have to set rebindingAllowed to be false because of complexity dealing with the
        // toolbar; the toolbar may not be configured twice.
        val viewModel = binding?.viewModel

        val toolbarHost = toolbarHost
            ?: throw RuntimeException("You must set the ToolbarHost up on RoverView before binding the view to a view model.")

        navigationView.viewModelBinding = null

        if (viewModel != null) {
            viewModel.events.androidLifecycleDispose(this).subscribe({ event ->
                when (event) {
                    is RoverViewModelInterface.Event.DisplayError -> {
                        Snackbar.make(this, R.string.rover_experiences_fetch_failure, Snackbar.LENGTH_LONG).show()
                        log.w("Unable to retrieve experience: ${event.engineeringReason}")
                    }
                }
            }, { error ->
                throw error
            }, { subscription -> subscriptionCallback(subscription) })

            viewModel.actionBar.androidLifecycleDispose(this).subscribe({ toolbarViewModel ->
                // regenerate and replace the toolbar
                val newToolbar = viewExperienceToolbar.setViewModelAndReturnToolbar(
                    toolbarViewModel
                )
                connectToolbar(newToolbar)
            }, { throw(it) }, { subscriptionCallback(it) })

            val window = toolbarHost.provideWindow()

            viewModel.extraBrightBacklight.androidLifecycleDispose(this).subscribe({ extraBright ->
                if (extraBright) {
                    window.attributes = (window.attributes ?: WindowManager.LayoutParams()).apply {
                        screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
                    }
            }}, { throw(it) }, { subscriptionCallback(it) })

            viewModel.navigationViewModel.androidLifecycleDispose(this).subscribe({ experienceNavigationViewModel ->
                navigationView.viewModelBinding = MeasuredBindableView.Binding(
                    experienceNavigationViewModel
                )
                turnOffProgressIndicator()
            }, { throw(it) }, { subscriptionCallback(it) })

            viewModel.loadingState.androidLifecycleDispose(this).subscribe({ loadingState ->
                if (loadingState) {
                    turnOnProgressIndicator()
                } else {
                    turnOffProgressIndicator()
                }
            }, { throw(it) }, { subscriptionCallback(it) })

            viewModel.fetchOrRefresh()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        toolbarHost = null
    }

    private var progressIndicatorView: View? = null

    private fun setUpProgressIndicator() {
        val drawable = CircularProgressDrawable(context)
        drawable.start()
        val imageView = AppCompatImageView(
            context
        )
        progressIndicatorView = imageView
        imageView.setImageDrawable(drawable)
        addView(imageView)

        (imageView.layoutParams as LayoutParams).apply {
            width = 40.dpAsPx(context.resources.displayMetrics)
            height = 40.dpAsPx(context.resources.displayMetrics)
            gravity = Gravity.CENTER_HORIZONTAL or Gravity.CENTER_VERTICAL
        }
    }

    private fun turnOnProgressIndicator() {
        progressIndicatorView?.visibility = View.VISIBLE
    }

    private fun turnOffProgressIndicator() {
        progressIndicatorView?.visibility = View.GONE
    }

    private var toolbar: Toolbar? = null

    private val navigationView: NavigationView = NavigationView(context)

    private val appBarLayout =
        AppBarLayout(context)

    init {
        addView(
            navigationView
        )

        (navigationView.layoutParams as LayoutParams).apply {
            behavior = AppBarLayout.ScrollingViewBehavior()
            width = LayoutParams.MATCH_PARENT
            height = LayoutParams.MATCH_PARENT
        }

        val appBarLayout = appBarLayout
        addView(appBarLayout)
        (appBarLayout.layoutParams as LayoutParams).apply {
            width = LayoutParams.MATCH_PARENT
            height = LayoutParams.WRAP_CONTENT
        }

        setUpProgressIndicator()
    }

    interface ToolbarHost {
        /**
         * The ExperiencesView will generate the toolbar and lay it out within it's own view.
         *
         * However, for it to work completely, it needs to be set as the Activity's (Fragment?)
         * toolbar.
         *
         * In response to this, the Activity will provide an ActionBar (and then, after a small
         * delay, a Menu).
         */
        fun setToolbarAsActionBar(toolbar: Toolbar): Publisher<Pair<ActionBar, Menu>>

        fun provideWindow(): Window
    }

    var toolbarHost: ToolbarHost? = null
        set(host) {
            field = host
            originalStatusBarColor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                host?.provideWindow()?.statusBarColor ?: 0
            } else 0
        }

    private var originalStatusBarColor: Int = 0 // this is set as a side-effect of the attached window

    private fun connectToolbar(newToolbar: Toolbar) {
        toolbar.whenNotNull { appBarLayout.removeView(it) }

        appBarLayout.addView(newToolbar)
        (newToolbar.layoutParams as AppBarLayout.LayoutParams).apply {
            scrollFlags = AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS
        }

        toolbar = newToolbar
    }

    private val viewExperienceToolbar by lazy {
        val toolbarHost = toolbarHost ?: throw RuntimeException("You must set the ToolbarHost up on RoverView before binding the view to a view model.")
        ViewExperienceToolbar(
            this,
            toolbarHost.provideWindow(),
            this.context,
            toolbarHost
        )
    }
}
