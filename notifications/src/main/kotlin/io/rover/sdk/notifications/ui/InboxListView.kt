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

import android.content.ActivityNotFoundException
import android.content.Context
import android.graphics.Canvas
import android.os.Handler
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewTreeLifecycleOwner
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import io.rover.core.R
import io.rover.sdk.core.Rover
import io.rover.sdk.core.logging.log
import io.rover.sdk.core.platform.whenNotNull
import io.rover.sdk.core.streams.androidLifecycleDispose
import io.rover.sdk.core.streams.subscribe
import io.rover.sdk.core.ui.concerns.BindableView
import io.rover.sdk.core.ui.concerns.ViewModelBinding
import io.rover.sdk.notifications.NotificationOpenInterface
import io.rover.sdk.notifications.domain.Notification
import io.rover.sdk.notifications.ui.concerns.InboxListViewModelInterface
import io.rover.sdk.notifications.ui.concerns.NotificationItemViewModelInterface

/**
 * Embed this view to embed a list of previously received push notifications for the user to browse
 * through, as an "Inbox", "Notification Center", or similar.  You can even embed and configure this
 * view directly into your XML layouts.
 *
 * Add [InboxListView] to your layout, either in XML or progammatically.
 *
 * Note: InboxListView requires a AppCompat (not AndroidX/Jetpack) theme.  To embed this view
 * in a modern activity (or Jetpack Compose) that does not use AppCompat, use
 * you can use `ContextThemeWrapper(context, R.style.Theme_AppCompat_Light)` to wrap your context.
 */
open class InboxListView :
    CoordinatorLayout, LifecycleOwner {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle)

    /**
     * This property is deprecated.
     */
    @Deprecated("This property no longer needs to be set.")
    var activity: AppCompatActivity? = null

    private val registry by lazy {
        LifecycleRegistry(this)
    }

    override fun getLifecycle(): Lifecycle = registry

    /**
     * This method will generate a row view.
     *
     * We bundle a basic row view, but if you would like to use your own row view, consider
     * overriding the factory for type [BindableView] named `notificationItemView` rather than
     * overriding this method.
     */
    fun makeNotificationRowView(): BindableView<NotificationItemViewModelInterface> {
        @Suppress("IMPLICIT_CAST_TO_ANY", "UNCHECKED_CAST")
        return (
            Rover.shared.resolve(BindableView::class.java, "notificationItemView", context) ?: throw RuntimeException(
                "Please be sure that Rover is initialized and NotificationsAssembler is added to Rover.init before using NotificationCenterListView."
            )
            ) as BindableView<NotificationItemViewModelInterface>
    }

    /**
     * This method will generate a row's swipe to delete reveal row.  This contains the shaded red
     * area and the delete icon.
     *
     * Even if you are using a custom row view, you may leave this method untouched and keep
     * the default version of the reveal layout.
     *
     * However, if you would like to use your own reveal view, consider overriding the factory for
     * type [View] named `notificationItemSwipeToDeleteBackgroundView` rather than overriding this
     * method.
     */
    open fun makeSwipeToDeleteRevealBackgroundView(): View {
        return Rover.shared.resolve(
            View::class.java,
            "notificationItemSwipeToDeleteBackgroundView",
            context
        ) ?: throw RuntimeException(
            "Please be sure that Rover is initialized and NotificationsAssembler is added before using NotificationCenterListView."
        )
    }

    /**
     * Resolves a view model instance needed for binding a row view against the given
     * [Notification].
     */
    open fun makeNotificationItemViewModel(notification: Notification): NotificationItemViewModelInterface {
        return Rover.shared.resolve(
            NotificationItemViewModelInterface::class.java,
            null,
            notification
        ) ?: throw RuntimeException(
            "Please be sure that Rover is initialized and NotificationsAssembler is added before using NotificationCenterListView."
        )
    }

    /**
     * Override this method to customize what is displayed when the inbox list is
     * empty.
     *
     * An example implementation is provided here.  Copy and modify it for use in your own
     * implementation of this class.
     */
    open val emptyLayout = Rover.shared.resolve(
        View::class.java,
        "notificationListEmptyArea",
        context
    ) ?: throw RuntimeException("Please be sure that Rover is initialized and NotificationsAssembler is added to Rover.init before using InboxListView.")

    private var viewModel: InboxListViewModelInterface? by ViewModelBinding { viewModel, subscriptionCallback ->

        swipeRefreshLayout.isRefreshing = false

        if (viewModel == null) {
            setUpUnboundState()
        } else {
            viewModel.events()
                .androidLifecycleDispose(this as View)
                .subscribe({ event ->
                    when (event) {
                        is InboxListViewModelInterface.Event.ListUpdated -> {
                            // update the adapter
                            log.v("List replaced with ${event.notifications.size} notifications")
                            itemsView.visibility =
                                if (event.notifications.isNotEmpty()) VISIBLE else GONE
                            emptyLayout.visibility =
                                if (event.notifications.isEmpty()) VISIBLE else GONE
                            currentNotificationsList = event.notifications
                            currentStableIdsMap = event.stableIds
                            adapter.notifyDataSetChanged()
                        }

                        is InboxListViewModelInterface.Event.Refreshing -> {
                            swipeRefreshLayout.isRefreshing = event.refreshing
                        }

                        is InboxListViewModelInterface.Event.DisplayProblemMessage -> {
                            // TODO: make error resource overridable.
                            Snackbar.make(this, R.string.generic_problem, Snackbar.LENGTH_LONG)
                                .show()
                        }

                        is InboxListViewModelInterface.Event.Navigate -> {

                            // A view is not normally considered an appropriate place to do this
                            // (perhaps, for example, the activity should subscribe to an event from the
                            // view model).  However, doing it here allows for a clearer interface for
                            // consumers: all they need to do is implement and provide the Host object.
                            val intent =
                                notificationOpen.intentForOpeningNotificationDirectly(event.notification)
                            if (intent != null) {
                                try {
                                    log.v("Invoking tap behaviour for notification: ${event.notification.tapBehavior}")
                                    context.startActivity(
                                        intent
                                    )
                                } catch (e: ActivityNotFoundException) {
                                    log.w("Target activity not available for launching Intent for notification.  Intent was: $intent")
                                }
                            } else log.w("Notification not suitable for launching from inbox (tap behaviour was ${event.notification.tapBehavior}.  Ignored.")
                        }
                    }
                }, { throw (it) }, { subscription -> subscriptionCallback(subscription) })

            swipeRefreshLayout.setOnRefreshListener {
                viewModel.requestRefresh()
            }
        }
    }

    private val swipeRefreshLayout by lazy {
        androidx.swiperefreshlayout.widget.SwipeRefreshLayout(
            context
        )
    }

    private val emptySwitcherLayout = FrameLayout(
        context
    ).apply {
        swipeRefreshLayout.addView(this)
        this.addView(emptyLayout)
    }

    private val itemsView = androidx.recyclerview.widget.RecyclerView(
        context
    ).apply { emptySwitcherLayout.addView(this) }

    // State:
    private var currentNotificationsList: List<Notification>? = null
    private var currentStableIdsMap: Map<String, Int>? = null

    private val adapter = object : androidx.recyclerview.widget.RecyclerView.Adapter<NotificationViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
            return NotificationViewHolder(context, this@InboxListView, makeNotificationRowView(), makeSwipeToDeleteRevealBackgroundView())
        }

        override fun getItemCount(): Int {
            return currentNotificationsList?.size ?: 0
        }

        override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
            val notification = currentNotificationsList?.get(position)

            notification.whenNotNull { holder.notificationItemViewModel = makeNotificationItemViewModel(it) }
        }

        override fun getItemId(position: Int): Long {
            val notification = currentNotificationsList?.get(position) ?: return -1
            return currentStableIdsMap?.get(notification.id)?.toLong() ?: return -1
        }
    }.apply {
        setHasStableIds(true)
    }

    private fun setUpUnboundState() {
        emptyLayout.visibility = View.GONE
        itemsView.visibility = View.GONE

        swipeRefreshLayout.isRefreshing = false

        swipeRefreshLayout.setOnRefreshListener {
            log.e("Swipe refresh gesture happened before view model bound.")
            swipeRefreshLayout.isRefreshing = false
        }
    }

    private val notificationOpen: NotificationOpenInterface by lazy {
        Rover.shared.resolve(NotificationOpenInterface::class.java)
            ?: throw java.lang.RuntimeException("Please be sure that Rover is initialized and NotificationsAssembler is added to Rover.init before using NotificationCenterListView.")
    }

    init {
        setUpUnboundState()

        this.addView(swipeRefreshLayout)

        itemsView.layoutManager =
            androidx.recyclerview.widget.LinearLayoutManager(
                context,
                androidx.recyclerview.widget.LinearLayoutManager.VERTICAL,
                false
            )
        itemsView.adapter = adapter

        // TODO: in design mode, put a description!

        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            // no drag and drop desired.
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean = false

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                if (viewHolder != null) {
                    ItemTouchHelper.Callback.getDefaultUIUtil().onSelected((viewHolder as NotificationViewHolder).rowItemView.view)
                }
            }

            override fun onChildDrawOver(c: Canvas, recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean) {
                if (viewHolder != null) {
                    ItemTouchHelper.Callback.getDefaultUIUtil().onDrawOver(c, recyclerView, (viewHolder as NotificationViewHolder).rowItemView.view, dX, dY, actionState, isCurrentlyActive)
                }
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                if (viewHolder != null) {
                    ItemTouchHelper.Callback.getDefaultUIUtil().clearView((viewHolder as NotificationViewHolder).rowItemView.view)
                }
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                log.d("Deleting notification at location")
                val notification = currentNotificationsList?.get(viewHolder.adapterPosition)

                Handler(context.mainLooper).post {
                    // the view model may end up emitting the refreshed list synchronously here,
                    // so we'll defer dispatching the action until the item touch handler itself
                    // has completed executing onSwiped() (and anything else it may be doing in
                    // a batch of work on the main looper).  This seems to ameliorate some visual
                    // artifacts in which the revealed red area can be left displaying on top of
                    // an incompletely deleted row item.
                    notification.whenNotNull { viewModel?.deleteNotification(it) }
                }
            }

            override fun onChildDraw(
                canvas: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                ItemTouchHelper.Callback.getDefaultUIUtil().onDraw(
                    canvas,
                    recyclerView,
                    (viewHolder as NotificationViewHolder).rowItemView.view,
                    dX,
                    dY,
                    actionState,
                    isCurrentlyActive
                )
            }
        }).attachToRecyclerView(itemsView)

        viewModel = Rover.shared.resolve(InboxListViewModelInterface::class.java, null, lifecycle) ?: throw RuntimeException(
            "Ensure Rover is initialized and NotificationsAssembler() added before using the Inbox."
        )
    }

    private fun notificationClicked(notification: Notification) {
        viewModel?.notificationClicked(notification)
    }

    /**
     * This [RecyclerView.ViewHolder] wraps a [NotificationItemViewModelInterface].
     */
    private class NotificationViewHolder(
        private val context: Context,
        private val listView: InboxListView,
        val rowItemView: BindableView<NotificationItemViewModelInterface>,
        swipeToDeleteRevealView: View,
        containerView: ViewGroup = FrameLayout(context)
    ) : RecyclerView.ViewHolder(containerView) {
        init {
            containerView.addView(swipeToDeleteRevealView)
            containerView.addView(rowItemView.view)
        }

        var notificationItemViewModel: NotificationItemViewModelInterface? by ViewModelBinding { viewModel, _ ->
            rowItemView.viewModel = viewModel
        }

        init {
            rowItemView.view.isClickable = true
            rowItemView.view.setOnClickListener {
                notificationItemViewModel.whenNotNull { listView.notificationClicked(it.notificationForDisplay) }
            }
        }
    }
}
