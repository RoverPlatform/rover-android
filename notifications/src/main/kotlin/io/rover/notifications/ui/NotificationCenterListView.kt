package io.rover.notifications.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Handler
import android.support.design.widget.CoordinatorLayout
import android.support.design.widget.Snackbar
import android.support.v4.widget.SwipeRefreshLayout
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.helper.ItemTouchHelper
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import io.rover.core.R
import io.rover.core.Rover
import io.rover.core.logging.log
import io.rover.core.streams.androidLifecycleDispose
import io.rover.core.streams.subscribe
import io.rover.core.ui.concerns.ViewModelBinding
import io.rover.core.platform.whenNotNull
import io.rover.notifications.domain.Notification
import io.rover.notifications.NotificationOpenInterface
import io.rover.notifications.ui.concerns.NotificationCenterListViewModelInterface
import io.rover.notifications.ui.concerns.NotificationItemViewModelInterface

/**
 * Embed this view to embed a list of previously received push notifications for the user to browse
 * through, as an "Inbox", "Notification Center", or similar.  You can even embed and configure this
 * view directly into your XML layouts.
 *
 * In order to display the list, there are several steps.
 *
 * 1. Add [NotificationCenterListView] to your layout, either in XML or progammatically.
 *
 * 2. Set [notificationCenterHost] with your own implementation of [NotificationCenterHost].  This
 * is needed for navigation in response to tapping notifications to work correctly.
 *
 * 3. Then use the implementation of [ViewModelFactoryInterface.viewModelForNotificationCenterList]
 * (either the provided one or your own custom version) to create an instance of the needed
 * [NotificationCenterListViewModel] view model, and then bind it with [setViewModel].
 *
 * You may specify the row item view by either setting xml property TODO or with TODO
 *
 * Also you must override [PushPlugin.notificationCenterIntent] to produce an Intent that will get
 * your app to the state where your usage of NotificationCenterView is being displayed.
 *
 * Note about Android state restoration: Rover SDK views handle state saving & restoration through
 * their view models, so you will need store a Parcelable on behalf of ExperienceView and
 * [NotificationCentreViewModel] (grabbing the state parcelable from the view model at save time and
 * restoring it by passing it to the view model factory at restart time).
 *
 * See [StandaloneNotificationCenterActivity] for an example of how to integrate.
 */
open class NotificationCenterListView : CoordinatorLayout {
    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle)

    /**
     * You must provide an Activity here before binding the view model.
     */
    var activity: AppCompatActivity? = null

    /**
     * This method will generate a row view.
     *
     * We bundle a basic row view, but if you would like to use your own row view, then you may
     * override [NotificationItemView] with your own implementation and then override this method.
     */
    open fun makeNotificationRowView(): NotificationItemView {
        return NotificationItemView(context)
    }

    /**
     * This method will generate a row's swipe to delete reveal row.  This contains the shaded red
     * area and the delete icon.
     *
     * Even if you are using a custom row view, you may leave this method untouched and keep
     * the default version of the reveal layout.
     */
    open fun makeSwipeToDeleteRevealBackgroundView(): View {
        val inflater = LayoutInflater.from(context)
        return inflater.inflate(R.layout.notification_center_default_item_delete_swipe_reveal, null)
    }

    open fun makeNotificationItemViewModel(notification: Notification): NotificationItemViewModelInterface {
        return NotificationItemViewModel(notification, Rover.sharedInstance.assetService)
    }

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
                                ?: throw RuntimeException("Please set notificationCenterHost on NotificationCenterListView.  Otherwise, navigation cannot work."))

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

            if(isAttachedToWindow) {
                viewModel.becameVisible()
            }
        }
    }

    private val swipeRefreshLayout = SwipeRefreshLayout(
        context
    )

    private val emptySwitcherLayout = FrameLayout(
        context
    ).apply { swipeRefreshLayout.addView(this) }

    private val emptyLayout = TextView(context).apply {
        text = "None yet" // TODO
    }.apply {
        gravity = Gravity.CENTER_VERTICAL or Gravity.CENTER_HORIZONTAL
        emptySwitcherLayout.addView(this)
    }

    private val itemsView = RecyclerView(
        context
    ).apply { emptySwitcherLayout.addView(this) }

    // State:
    private var currentNotificationsList: List<Notification>? = null
    private var currentStableIdsMap: Map<String, Int>? = null

    private val adapter = object : RecyclerView.Adapter<NotificationViewHolder>() {
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
        Rover.sharedInstance.resolveSingletonOrFail(NotificationOpenInterface::class.java)
    }

    init {
        setUpUnboundState()

        this.addView(swipeRefreshLayout)

        itemsView.layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
        itemsView.adapter = adapter

        // TODO: in design mode, put a description!

        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            // no drag and drop desired.
            override fun onMove(recyclerView: RecyclerView?, viewHolder: RecyclerView.ViewHolder?, target: RecyclerView.ViewHolder?): Boolean = false

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                if(viewHolder != null) {
                    ItemTouchHelper.Callback.getDefaultUIUtil().onSelected((viewHolder as NotificationViewHolder).rowItemView)
                }
            }

            override fun onChildDrawOver(c: Canvas?, recyclerView: RecyclerView?, viewHolder: RecyclerView.ViewHolder?, dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean) {
                if(viewHolder != null) {
                    ItemTouchHelper.Callback.getDefaultUIUtil().onDrawOver(c, recyclerView, (viewHolder as NotificationViewHolder).rowItemView, dX, dY, actionState, isCurrentlyActive)
                }
            }

            override fun clearView(recyclerView: RecyclerView?, viewHolder: RecyclerView.ViewHolder?) {
                if(viewHolder != null) {
                    ItemTouchHelper.Callback.getDefaultUIUtil().clearView((viewHolder as NotificationViewHolder).rowItemView)
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
                    (viewHolder as NotificationViewHolder).rowItemView,
                    dX, dY,
                    actionState,
                    isCurrentlyActive
                )
            }


        }).attachToRecyclerView(itemsView)

        viewModel = Rover.sharedInstance.resolve(NotificationCenterListViewModelInterface::class.java, null) ?: throw RuntimeException(
            "NotificationCenterListViewModelInterface not registered in DI container.\n" +
            "Ensure NotificationsAssembler() provided to Rover.initialize() before using notification center."
        )

        viewTreeObserver.addOnGlobalLayoutListener {
            val rect = Rect()
            val visible = getGlobalVisibleRect(rect)
            if(visible && isShown) {
                viewModel?.becameVisible()
            } else {
                viewModel?.becameInvisible()
            }
        }
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if(!hasWindowFocus) {
            viewModel?.becameInvisible()
        }
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
        val rowItemView: NotificationItemView,
        swipeToDeleteRevealView: View,
        containerView: ViewGroup = FrameLayout(context)
    ): RecyclerView.ViewHolder(containerView) {
        init {
            containerView.addView(swipeToDeleteRevealView)
            containerView.addView(rowItemView)
        }

        var notificationItemViewModel: NotificationItemViewModelInterface? by ViewModelBinding { viewModel, _ ->
            rowItemView.viewModel = viewModel
        }

        init {
            rowItemView.isClickable = true
            rowItemView.setOnClickListener {
                notificationItemViewModel.whenNotNull { listView.notificationClicked(it.notificationForDisplay) }
            }
        }
    }
}
