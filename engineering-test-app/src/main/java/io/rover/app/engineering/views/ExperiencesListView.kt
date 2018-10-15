package io.rover.app.engineering.views

import android.content.Context
import android.support.design.widget.Snackbar
import android.support.v4.widget.SwipeRefreshLayout
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.uber.autodispose.AutoDispose
import com.uber.autodispose.android.ViewScopeProvider
import io.reactivex.android.schedulers.AndroidSchedulers
import io.rover.app.engineering.R
import io.rover.app.engineering.viewmodels.ExperiencesListViewModel

class ExperiencesListView(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
): SwipeRefreshLayout(context, attrs) {
    constructor(context: Context) : this(context, null, 0)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

    var viewModel: ExperiencesListViewModel? = null
        set(viewModel) {
            field = viewModel

            if(viewModel == null) return

            setOnRefreshListener {
                viewModel.requestRefresh()
            }

            viewModel
                .events
                .observeOn(AndroidSchedulers.mainThread())
                .to(AutoDispose.with(ViewScopeProvider.from(this)).forObservable())
                .subscribe({ event ->
                    when (event) {
                        is ExperiencesListViewModel.Event.ListStartedRefreshing -> {
                            isRefreshing = true
                        }
                        is ExperiencesListViewModel.Event.ListRefreshed -> {
                            val experiencesList = event.events
                            isRefreshing = false
                            // set an adapter!
                            recyclerView.adapter = object : RecyclerView.Adapter<ExperienceItemHolder>() {
                                override fun onBindViewHolder(holder: ExperienceItemHolder, position: Int) {
                                    val experience = experiencesList[position]
                                    holder.bind(experience.name) {
                                        viewModel.selectExperience(experience)
                                    }
                                }

                                override fun getItemCount(): Int = experiencesList.count()

                                override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExperienceItemHolder {
                                    val view = LayoutInflater.from(context).inflate(io.rover.app.engineering.R.layout.view_experience_list_item, parent, false)

                                    return ExperienceItemHolder(
                                        view
                                    )
                                }
                            }
                        }
                        is ExperiencesListViewModel.Event.DisplayProblem -> {
                            Snackbar.make(this, "Error: ${event.message}", Snackbar.LENGTH_LONG).show()
                            isRefreshing = false
                            bindEmptyAdapter()
                        }
                        is ExperiencesListViewModel.Event.ForceLogOut -> {
                            // TODO: this should be handled in the surrounding activity and the user
                            // navigated to the log in screen directly.

                            Snackbar.make(this, "Please sign out and try again.", Snackbar.LENGTH_LONG).show()
                            isRefreshing = false

                            bindEmptyAdapter()
                        }
                    }
                }, { error ->
                    if (Thread.currentThread().id != 1L) throw RuntimeException("error on wrong thread?")
                    isRefreshing = false
                    Snackbar.make(this, "Error: ${error.message}", Snackbar.LENGTH_LONG).show()
                    bindEmptyAdapter()
                })

            viewModel.requestRefresh()
        }

    private fun bindEmptyAdapter() {
        recyclerView.adapter = object : RecyclerView.Adapter<ExperienceItemHolder>() {
            override fun onBindViewHolder(holder: ExperienceItemHolder, position: Int) {
                throw RuntimeException("This is an empty item view holder.")
            }

            override fun getItemCount(): Int {
                return 0
            }

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExperienceItemHolder {
                throw RuntimeException("This is an empty item view holder.")
            }
        }
    }

    class ExperienceItemHolder(
        val view: View
    ): RecyclerView.ViewHolder(view) {
        private val textView = view.findViewById<TextView>(R.id.experience_name_view)

        init {
            view.isClickable = true
        }

        fun bind(name: String, clicked: () -> Unit) {
            textView.text = name
            view.setOnClickListener {
                clicked()
            }
        }
    }

    private val recyclerView = RecyclerView(context, attrs, defStyleAttr)

    init {
        recyclerView.layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
        addView(recyclerView)


    }
}
