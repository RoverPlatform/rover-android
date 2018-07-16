package io.rover.experiences.ui.navigation

import android.content.Context
import android.os.Build
import android.support.annotation.RequiresApi
import android.support.transition.Slide
import android.support.transition.TransitionManager
import android.support.transition.TransitionSet
import android.util.AttributeSet
import android.util.LruCache
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import io.rover.core.streams.androidLifecycleDispose
import io.rover.core.streams.subscribe
import io.rover.core.platform.whenNotNull
import io.rover.core.ui.concerns.ViewModelBinding
import io.rover.core.ui.concerns.BindableView
import io.rover.experiences.ui.layout.screen.ScreenView
import io.rover.experiences.ui.layout.screen.ScreenViewModelInterface

/**
 * Navigation behaviour between screens of an Experience.
 */
class ExperienceNavigationView : FrameLayout, BindableView<ExperienceNavigationViewModelInterface> {
    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    // TODO: implement an onCreate and if inEditMode() display a "Rover Experience" text view.

    /**
     * The current view, stored so we can yield an android state parcelable and thus enable
     * state restore.
     */
    private var activeView: ScreenView? = null

    private val viewCache: LruCache<ScreenViewModelInterface, ScreenView> = object : LruCache<ScreenViewModelInterface, ScreenView>(3) {
        override fun entryRemoved(evicted: Boolean, key: ScreenViewModelInterface?, oldValue: ScreenView?, newValue: ScreenView?) {
            removeView(oldValue)
        }
    }

    private fun getViewForScreenViewModel(screenViewModel: ScreenViewModelInterface): ScreenView {
        return viewCache[screenViewModel] ?: ScreenView(
            context
        ).apply {
            this@ExperienceNavigationView.addView(this)
            this.visibility = View.GONE
            viewCache.put(screenViewModel, this)
            this.viewModel = BindableView.Binding(screenViewModel)
        }
    }

    override var viewModel: BindableView.Binding<ExperienceNavigationViewModelInterface>? by ViewModelBinding { binding, subscriptionCallback ->
        val viewModel = binding?.viewModel
        viewModel?.screen?.androidLifecycleDispose(this)?.subscribe({ screenUpdate ->
            if (!screenUpdate.animate) {
                val newView = getViewForScreenViewModel(screenUpdate.screenViewModel)
                activeView?.visibility = View.GONE
                newView.visibility = View.VISIBLE
                activeView = newView
            } else {
                if (screenUpdate.backwards) {
                    val newView = getViewForScreenViewModel(screenUpdate.screenViewModel)
                    newView.bringToFront()
                    newView.visibility = View.GONE

                    val set = TransitionSet().apply {
                        addTransition(
                            Slide(
                                Gravity.START
                            ).addTarget(newView)
                        )
                        activeView.whenNotNull { activeView ->
                            addTransition(
                                Slide(
                                    Gravity.END
                                ).addTarget(activeView)
                            )
                        }
                    }

                    TransitionManager.beginDelayedTransition(this, set)
                    activeView.whenNotNull {
                        it.visibility = View.GONE
                    }
                    newView.visibility = View.VISIBLE

                    activeView = newView
                } else {
                    // forwards
                    val newView = getViewForScreenViewModel(screenUpdate.screenViewModel)
                    newView.bringToFront()
                    newView.visibility = View.GONE

                    val set = TransitionSet().apply {
                        activeView.whenNotNull { activeView ->
                            addTransition(
                                Slide(
                                    Gravity.START
                                ).addTarget(activeView)
                            )
                        }
                        addTransition(
                            Slide(
                                Gravity.END
                            ).addTarget(newView)
                        )
                    }

                    TransitionManager.beginDelayedTransition(this, set)
                    newView.visibility = View.VISIBLE
                    activeView.whenNotNull { it.visibility = View.GONE }

                    activeView = newView
                }
            }
        }, { error -> throw error }, { subscription -> subscriptionCallback(subscription) })

        viewModel?.start()
    }
}
