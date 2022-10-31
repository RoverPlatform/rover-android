package io.rover.campaigns.notifications.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.graphics.Canvas
import android.os.Handler
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.ItemTouchHelper
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import io.rover.campaigns.core.R
import io.rover.campaigns.core.RoverCampaigns
import io.rover.campaigns.core.logging.log
import io.rover.campaigns.core.platform.whenNotNull
import io.rover.campaigns.core.streams.androidLifecycleDispose
import io.rover.campaigns.core.streams.subscribe
import io.rover.campaigns.core.ui.concerns.BindableView
import io.rover.campaigns.core.ui.concerns.ViewModelBinding
import io.rover.campaigns.notifications.NotificationOpenInterface
import io.rover.campaigns.notifications.domain.Notification
import io.rover.campaigns.notifications.ui.concerns.NotificationCenterListViewModelInterface
import io.rover.campaigns.notifications.ui.concerns.NotificationItemViewModelInterface

/**
 * Embed this view to embed a list of previously received push notifications for the user to browse
 * through, as an "Inbox", "Notification Center", or similar.  You can even embed and configure this
 * view directly into your XML layouts.
 *
 * In order to display the list, there are several steps.
 *
 * 1. Add [NotificationCenterListView] to your layout, either in XML or progammatically.
 *
 * 2. Set [activity] with the host Activity that contains the List View.  This is needed for
 * navigation in response to tapping notifications to work correctly.
 *
 * 3. Resolve an instance of [NotificationCenterListViewModelInterface] from the Rover DI container
 * and set it as the [viewModel].
 *
 * See the [Notification Center
 * Documentation](https://www.rover.io/docs/android/notification-center/).
 */
open class NotificationCenterListView :
    CoordinatorLayout {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle)

    /**
     * You must provide an Activity here before binding the view model.
     */
    var activity: AppCompatActivity? = null
        set(activity) {
            field = activity
            viewModel = if (activity == null) {
                null
            } else RoverCampaigns.shared?.resolve(NotificationCenterListViewModelInterface::class.java, null, activity.lifecycle) ?: throw RuntimeException(
                "Ensure Rover Campaigns is initialized and NotificationsAssembler() added before using notification center."
            )
        }

    /**
     * This method will generate a row view.
     *
     * We bundle a basic row view, but if you would like to use your own row view, consider
     * overriding the factory for type [BindableView] named `notificationItemView` rather than
     * overriding this method.  See the [Notification Center
     * Documentation](https://www.rover.io/docs/android/notification-center/).
     */
    fun makeNotificationRowView(): BindableView<NotificationItemViewModelInterface> {
        @Suppress("IMPLICIT_CAST_TO_ANY", "UNCHECKED_CAST")
        return (RoverCampaigns.shared?.resolve(BindableView::class.java, "notificationItemView", context) ?: throw RuntimeException(
            "Please be sure that Rover Campaigns is initialized and NotificationsAssembler is added to RoverCampaigns.init before using NotificationCenterListView."
        )) as BindableView<NotificationItemViewModelInterface>
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
     * method.  See the [Notification Center
     * Documentation](https://www.rover.io/docs/android/notification-center/).
     */
    open fun makeSwipeToDeleteRevealBackgroundView(): View {
        return RoverCampaigns.shared?.resolve(
            View::class.java,
            "notificationItemSwipeToDeleteBackgroundView",
            context
        ) ?: throw RuntimeException(
            "Please be sure that Rover Campaigns is initialized and NotificationsAssembler is added before using NotificationCenterListView."
        )
    }

    /**
     * Resolves a view model instance needed for binding a row view against the given
     * [Notification].
     */
    open fun makeNotificationItemViewModel(notification: Notification): NotificationItemViewModelInterface {
        return RoverCampaigns.shared?.resolve(
            NotificationItemViewModelInterface::class.java,
            null,
            notification
        ) ?: throw RuntimeException(
            "Please be sure that Rover Campaigns is initialized and NotificationsAssembler is added before using NotificationCenterListView."
        )
    }

    /**
     * Override this method to customize what is displayed when the notification center list is
     * empty.
     *
     * An example implementation is provided here.  Copy and modify it for use in your own
     * implementation of this class.
     */
    open val emptyLayout = RoverCampaigns.shared?.resolve(
            View::class.java,
            "notificationListEmptyArea",
            context
        ) ?: throw RuntimeException("Please be sure that Rover Campaigns is initialized and NotificationsAssembler is added to RoverCampaigns.init before using NotificationCenterListView.")

    private var viewModel: NotificationCenterListViewModelInterface? by ViewModelBinding { viewModel, subscriptionCallback ->

        swipeRefreshLayout.isRefreshing = false

        if (viewModel == null) {
            setUpUnboundState()
        } else {
            viewModel.events()
                .androidLifecycleDispose(this)
                .subscribe({ event ->
                    when (event) {
                        is NotificationCenterListViewModelInterface.Event.ListUpdated -> {
                            // update the adapter
                            log.v("List replaced with ${event.notifications.size} notifications")
                            itemsView.visibility = if (event.notifications.isNotEmpty()) View.VISIBLE else View.GONE
                            emptyLayout.visibility = if (event.notifications.isEmpty()) View.VISIBLE else View.GONE
                            currentNotificationsList = event.notifications
                            currentStableIdsMap = event.stableIds
                            adapter.notifyDataSetChanged()
                        }
                        is NotificationCenterListViewModelInterface.Event.Refreshing -> {
                            swipeRefreshLayout.isRefreshing = event.refreshing
                        }
                        is NotificationCenterListViewModelInterface.Event.DisplayProblemMessage -> {
                            // TODO: make error resource overridable.
                            Snackbar.make(this, R.string.generic_problem, Snackbar.LENGTH_LONG).show()
                        }
                        is NotificationCenterListViewModelInterface.Event.Navigate -> {
                            val hostActivity = (activity
                                ?: throw RuntimeException("Please set the `activity` property on NotificationCenterListView.  Otherwise, navigation cannot work."))

                            // A view is not normally considered an appropriate place to do this
                            // (perhaps, for example, the activity should subscribe to an event from the
                            // view model).  However, doing it here allows for a clearer interface for
                            // consumers: all they need to do is implement and provide the Host object.
                            val intent = notificationOpen.intentForOpeningNotificationDirectly(event.notification)
                            if (intent != null) {
                                try {
                                    log.v("Invoking tap behaviour for notification: ${event.notification.tapBehavior}")
                                    hostActivity.startActivity(
                                        intent
                                    )
                                } catch (e: ActivityNotFoundException) {
                                    log.w("Target activity not available for launching Intent for notification.  Intent was: $intent")
                                }
                            } else log.w("Notification not suitable for launching from notification center.  Ignored.")
                        }
                    }
                }, { throw(it) }, { subscription -> subscriptionCallback(subscription) })

            swipeRefreshLayout.setOnRefreshListener {
                viewModel.requestRefresh()
            }
        }
    }

    private val swipeRefreshLayout =
        androidx.swiperefreshlayout.widget.SwipeRefreshLayout(
            context
        )

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
            return NotificationViewHolder(context, this@NotificationCenterListView, makeNotificationRowView(), makeSwipeToDeleteRevealBackgroundView())
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
        RoverCampaigns.shared?.resolve(NotificationOpenInterface::class.java)
        ?: throw java.lang.RuntimeException("Please be sure that Rover Campaigns is initialized and NotificationsAssembler is added to RoverCampaigns.init before using NotificationCenterListView.")
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
                    dX, dY,
                    actionState,
                    isCurrentlyActive
                )
            }
        }).attachToRecyclerView(itemsView)
    }

    private fun notificationClicked(notification: Notification) {
        viewModel?.notificationClicked(notification)
    }

    /**
     * This [RecyclerView.ViewHolder] wraps a [NotificationItemViewModelInterface].
     */
    private class NotificationViewHolder(
        private val context: Context,
        private val listView: NotificationCenterListView,
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
