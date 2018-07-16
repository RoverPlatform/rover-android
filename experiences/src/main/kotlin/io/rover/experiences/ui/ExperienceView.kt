package io.rover.experiences.ui

import android.animation.Animator
import android.animation.ObjectAnimator
import android.animation.TypeEvaluator
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.support.design.widget.AppBarLayout
import android.support.design.widget.CoordinatorLayout
import android.support.design.widget.Snackbar
import android.support.v4.widget.CircularProgressDrawable
import android.support.v7.app.ActionBar
import android.support.v7.widget.AppCompatImageView
import android.support.v7.widget.Toolbar
import android.util.AttributeSet
import android.view.Gravity
import android.view.Menu
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import io.rover.experiences.R
import io.rover.experiences.ui.containers.StandaloneExperienceHostActivity
import io.rover.experiences.ui.navigation.ExperienceNavigationView
import io.rover.experiences.ui.toolbar.ViewExperienceToolbar
import io.rover.core.logging.log
import io.rover.core.streams.androidLifecycleDispose
import io.rover.core.streams.subscribe
import io.rover.core.ui.concerns.BindableView
import io.rover.core.ui.concerns.ViewModelBinding
import io.rover.core.ui.dpAsPx
import io.rover.core.platform.whenNotNull
import org.reactivestreams.Publisher

/**
 * Embed this view to include a Rover Experience in a layout.
 *
 * Most applications will likely want to use [StandaloneExperienceHostActivity] and
 * [ExperienceFragment] to display an Experience, but for more custom setups (say, tablet-enabled
 * single-activity apps that avoid fragments), you can embed [ExperienceView] directly.
 *
 * In order to display an Experience, use the implementation of
 * [ViewModelFactoryInterface.viewModelForExperience] to create an instance of the needed Experience
 * view model, and then bind it to the view model with setViewModel.
 *
 * Note about Android state restoration: Rover SDK views handle state saving & restoration through
 * their view models, so you will need store a Parcelable on behalf of ExperienceView and
 * [ExperienceViewModel] (grabbing the state Parcelable from the view model at save time and
 * restoring it by passing it to the view model factory at restart time).
 *
 * See [StandaloneExperienceHostActivity] for an example of how to integrate.
 */
class ExperienceView : CoordinatorLayout, BindableView<ExperienceViewModelInterface> {
    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    override var viewModel: BindableView.Binding<ExperienceViewModelInterface>? by ViewModelBinding(false) { binding, subscriptionCallback ->
        // sadly have to set rebindingAllowed to be false because of complexity dealing with the
        // toolbar; the toolbar may not be configured twice.
        val viewModel = binding?.viewModel

        val toolbarHost = toolbarHost
            ?: throw RuntimeException("You must set the ToolbarHost up on ExperienceView before binding the view to a view model.")

        experienceNavigationView.viewModel = null

        if (viewModel != null) {
            viewModel.events.androidLifecycleDispose(this).subscribe({ event ->
                when (event) {
                    is ExperienceViewModelInterface.Event.DisplayError -> {
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

            val startingBrightness = Settings.System.getInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS
            )
            log.v("Starting brightness is: $startingBrightness")
            val startingBrightnessFraction = startingBrightness / 255f
            val window = toolbarHost.provideWindow()
            // set a starting value that matches what the current device brightness is so the
            // animator below has a sane starting position.
            window.attributes = (window.attributes ?: WindowManager.LayoutParams()).apply {
                screenBrightness = startingBrightnessFraction
            }
            var runningAnimator : Animator? = null
            viewModel.extraBrightBacklight.androidLifecycleDispose(this).subscribe({ extraBright ->
                runningAnimator?.cancel()
                val animator = ObjectAnimator.ofObject(
                    window,
                    "attributes",
                    TypeEvaluator<WindowManager.LayoutParams> { fraction, startValue, endValue ->
                        // and and take the starting value and copy it, setting the new interpolated screen brightness.
                        val newParams = WindowManager.LayoutParams().apply { copyFrom(startValue) }
                        val newBrightness = startValue.screenBrightness + (fraction * (endValue.screenBrightness - startValue.screenBrightness))
                        newParams.screenBrightness = newBrightness
                        newParams
                    },
                    WindowManager.LayoutParams().apply {
                        screenBrightness = when (extraBright) {
                            true -> WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
                            false -> startingBrightnessFraction
                        }
                    }
                )
                // it's unclear why, the animator has a habit of running up against the endvalue and
                // running there a large portion of the animation duration.  For now, the workaround
                // is to set the runtime to something pretty long.
                animator.duration = 3000
                animator.interpolator = LinearInterpolator()
                animator.start()
                runningAnimator = animator
            }, { throw(it) }, { subscriptionCallback(it) })

            viewModel.experienceNavigation.androidLifecycleDispose(this).subscribe( { experienceNavigationViewModel ->
                experienceNavigationView.viewModel = BindableView.Binding(
                    experienceNavigationViewModel
                )
                turnOffProgressIndicator ()
            }, { throw(it) }, { subscriptionCallback(it) })

            viewModel.loadingState.androidLifecycleDispose(this).subscribe( { loadingState ->
                if(loadingState) {
                    turnOnProgressIndicator()
                } else {
                    turnOffProgressIndicator()
                }
            }, { throw(it) }, { subscriptionCallback(it) })

            viewModel.fetchOrRefresh()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        toolbarHost = null
    }

    protected var progressIndicatorView: View? = null

    protected fun setUpProgressIndicator() {
        val drawable = CircularProgressDrawable(context)
        drawable.start()
        val imageView = AppCompatImageView(
            context
        )
        progressIndicatorView = imageView
        imageView.setImageDrawable(drawable)
        addView(imageView)

        (imageView.layoutParams as CoordinatorLayout.LayoutParams).apply {
            width = 40.dpAsPx(context.resources.displayMetrics)
            height = 40.dpAsPx(context.resources.displayMetrics)
            gravity = Gravity.CENTER_HORIZONTAL or Gravity.CENTER_VERTICAL
        }

    }

    protected fun turnOnProgressIndicator() {
        progressIndicatorView?.visibility = View.VISIBLE
    }

    protected fun turnOffProgressIndicator() {
        progressIndicatorView?.visibility = View.GONE
    }

    private var toolbar: Toolbar? = null

    private val experienceNavigationView: ExperienceNavigationView = ExperienceNavigationView(context)

    private val appBarLayout = AppBarLayout(context)

    init {
        addView(
            experienceNavigationView
        )

        (experienceNavigationView.layoutParams as CoordinatorLayout.LayoutParams).apply {
            behavior = AppBarLayout.ScrollingViewBehavior()
            width = LayoutParams.MATCH_PARENT
            height = LayoutParams.MATCH_PARENT
        }

        val appBarLayout = appBarLayout
        addView(appBarLayout)
        (appBarLayout.layoutParams as CoordinatorLayout.LayoutParams).apply {
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
        val toolbarHost = toolbarHost ?: throw RuntimeException("You must set the ToolbarHost up on ExperienceView before binding the view to a view model.")
        ViewExperienceToolbar(
            this,
            toolbarHost.provideWindow(),
            this.context,
            toolbarHost
        )
    }
}

