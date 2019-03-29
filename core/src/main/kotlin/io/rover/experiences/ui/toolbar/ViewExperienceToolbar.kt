package io.rover.experiences.ui.toolbar

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.support.v7.app.ActionBar
import android.support.v7.appcompat.R.attr.borderlessButtonStyle
import android.support.v7.widget.AppCompatButton
import android.support.v7.widget.Toolbar
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.Menu
import android.view.View
import android.view.Window
import io.rover.core.R
import io.rover.core.logging.log
import io.rover.core.streams.androidLifecycleDispose
import io.rover.core.streams.doOnUnsubscribe
import io.rover.core.streams.subscribe
import io.rover.experiences.ui.ExperienceView
import org.reactivestreams.Subscription

class ViewExperienceToolbar(
    private val hostView: View,
    hostWindowForStatusBar: Window,
    private val context: Context,
    private val toolbarHost: ExperienceView.ToolbarHost
) : ViewExperienceToolbarInterface {
    private val menuItemId = View.generateViewId()

    private val defaultStatusBarColor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        hostWindowForStatusBar.statusBarColor
    } else 0

    /**
     * We'll cancel the subscription to the observable chain created in setViewModelAndReturnToolbar()
     */
    private var activeMenuSubscription: Subscription? = null

    private var retrievedMenu: Menu? = null

    private val toolbar = Toolbar(context)

    private val closeButton = AppCompatButton(context, null, borderlessButtonStyle)

    init {
        closeButton.text = context.getString(R.string.close)
        // TODO: default close button style to whatever is set in toolbar style
        toolbar.addView(closeButton)
        (closeButton.layoutParams as ActionBar.LayoutParams).gravity = Gravity.END
    }

    private var cachedActionIcon: Drawable? = null

    override fun setViewModelAndReturnToolbar(
        toolbarViewModel: ExperienceToolbarViewModelInterface
    ): Toolbar {
        val configuration = toolbarViewModel.configuration

        closeButton.setOnClickListener { }

        // I need to keep state for the toolbar subscription so I can unsubscribe it when bind.
        activeMenuSubscription?.cancel()

        toolbarHost.setToolbarAsActionBar(toolbar)
            .androidLifecycleDispose(hostView)
            .doOnUnsubscribe {
                log.v("Removing exist item with id $menuItemId from $retrievedMenu")
                retrievedMenu?.removeItem(menuItemId)
            }
            .subscribe({ (actionBar, menu) ->
                actionBar.setDisplayHomeAsUpEnabled(true)
                
                // we must keep a hold of this so we can remove it on unsubscribe
                retrievedMenu = menu

                toolbar.setNavigationOnClickListener {
                    toolbarViewModel.pressedBack()
                }

                closeButton.setOnClickListener {
                    toolbarViewModel.pressedClose()
                }

                // TODO: this one must be changed to style method
                toolbar.title = if (configuration.useExistingStyle) {
                    configuration.appBarText
                } else {
                    SpannableStringBuilder(configuration.appBarText).apply {
                        setSpan(ForegroundColorSpan(configuration.textColor), 0, configuration.appBarText.length, 0)
                    }
                }

                if (!configuration.useExistingStyle) {
                    // TODO may do with the style above instead
                    toolbar.background = ColorDrawable(configuration.color)
                    closeButton.setTextColor(configuration.buttonColor)

                    // best effort: if on API 21 or better, set the back nav icon color. Otherwise,
                    // it just fails back to the default.
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        toolbar.navigationIcon?.setTint(configuration.buttonColor)
                    }
                }

                // status bar color only supported on Lollipop and greater.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    toolbarHost.provideWindow().statusBarColor = if (configuration.useExistingStyle) defaultStatusBarColor else {
                        configuration.statusBarColor
                    }
                }

                showOrHideAction(configuration.upButton)

                closeButton.visibility = if (toolbarViewModel.configuration.closeButton) View.VISIBLE else View.GONE
            }, { error -> throw(error) }, { subscription ->
                activeMenuSubscription = subscription
            })

        return toolbar
    }

    private fun showOrHideAction(visibility: Boolean) {
        if (visibility) {
            toolbar.navigationIcon = toolbar.navigationIcon ?: cachedActionIcon
        } else {
            cachedActionIcon = toolbar.navigationIcon ?: cachedActionIcon
            toolbar.navigationIcon = null
        }
    }
}
