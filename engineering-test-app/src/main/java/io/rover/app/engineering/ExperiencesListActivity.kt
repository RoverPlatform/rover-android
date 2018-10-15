package io.rover.app.engineering

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.design.widget.TabLayout
import android.support.v4.view.PagerAdapter
import android.support.v7.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import com.uber.autodispose.AutoDispose
import com.uber.autodispose.android.lifecycle.AndroidLifecycleScopeProvider
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.subjects.PublishSubject
import io.rover.account.AuthService
import io.rover.account.ui.LoginActivity
import io.rover.app.engineering.services.ExperienceRepository
import io.rover.app.engineering.viewmodels.ExperienceListFilter
import io.rover.app.engineering.viewmodels.ExperiencesListViewModel
import io.rover.app.engineering.views.ExperiencesListView
import io.rover.experiences.ui.containers.ExperienceActivity
import kotlinx.android.synthetic.main.activity_experiences_list.*

class ExperiencesListActivity : AppCompatActivity() {

    /**
     * The [android.support.v4.view.PagerAdapter] that will provide
     * fragments for each of the sections. We use a
     * [PagerAdapter] derivative to switch through our custom views.
     */
    private var mSectionsPagerAdapter: SectionsPagerAdapter? = null

    private val authService: AuthService by lazy {
        (this.application as ExperiencesApplication).authService
    }

    private val experienceRepository: ExperienceRepository
        get() = (application as ExperiencesApplication).experienceRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_experiences_list)

        setSupportActionBar(toolbar)

        val pagerAdapter =  SectionsPagerAdapter(this, experienceRepository)
        // Create the adapter that will return a fragment for each of the three
        // primary sections of the activity.
        mSectionsPagerAdapter = pagerAdapter

        pagerAdapter
            .events
            .to(AutoDispose.with(AndroidLifecycleScopeProvider.from(this)).forObservable())
            .subscribe({ event ->
                when(event) {
                    is SectionsPagerAdapter.Event.ForceLogout -> {
                        signOut()
                    }
                    is SectionsPagerAdapter.Event.ExperienceSelected -> {
                        startActivity(
                            ExperienceActivity.makeIntent(
                                this.applicationContext,
                                event.id,
                                null
                            )
                        )
                    }
                }
            }, { error ->
                throw(error)
            })

        // Set up the ViewPager with the sections adapter.
        container.adapter = mSectionsPagerAdapter

        container.addOnPageChangeListener(TabLayout.TabLayoutOnPageChangeListener(tabs))
        tabs.addOnTabSelectedListener(TabLayout.ViewPagerOnTabSelectedListener(container))

        // if not logged in:
        if (!authService.isLoggedIn) {
            startActivity(
                Intent(this, LoginActivity::class.java)
            )
            finish()
        }
    }


    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_inbox, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        val id = item.itemId

        if (id == R.id.action_sign_out) {
            signOut()
        }

        return super.onOptionsItemSelected(item)
    }

    private fun signOut() {
        authService.logOut()
        startActivity(
            Intent(this, LoginActivity::class.java)
        )
        finish()
    }

    /**
     * A [PagerAdapter] that returns a view corresponding to one of the experiences list filtered
     * tabs.
     *
     * Acting somewhat like a view model.
     */
    class SectionsPagerAdapter(
        private val context: Context,
        private val experienceRepository: ExperienceRepository
    ): PagerAdapter() {
        private val activeViews: HashMap<ExperienceListFilter, ExperiencesListView> = hashMapOf()

        override fun isViewFromObject(view: View, `object`: Any): Boolean =
            view == activeViews[ExperienceListFilter.values()[`object` as Int]]

        override fun instantiateItem(container: ViewGroup, position: Int): Any {
            val view = ExperiencesListView(context)
            container.addView(view, 0)
            val filter = ExperienceListFilter.values()[position]
            activeViews[filter] = view

            val viewModel = ExperiencesListViewModel(
                filter,
                experienceRepository
            )

            // subscribe to navigate to experience event
            viewModel.events
                .observeOn(AndroidSchedulers.mainThread())
                .flatMap { event ->
                    when(event) {
                        is ExperiencesListViewModel.Event.NavigateToExperience ->
                            Observable.just(Event.ExperienceSelected(event.id, event.name))
                        is ExperiencesListViewModel.Event.ForceLogOut -> {
                            Observable.just(Event.ForceLogout())
                        }
                        else -> Observable.empty()
                    }
                }
                .subscribe(subject)

            viewModel.events


            // TODO: inject viewmodel from surrounding context properly!
            view.viewModel = viewModel

            return position
        }

        override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
            val positionKey = ExperienceListFilter.values()[`object` as Int]

            val victim = activeViews[positionKey]
            container.removeView(victim)
            activeViews.remove(positionKey)
        }

        override fun getCount(): Int = ExperienceListFilter.values().count()

        private val subject = PublishSubject.create<Event>()

        val events: Observable<Event> = subject

        sealed class Event {
            class ForceLogout(): Event()
            data class ExperienceSelected(val id: String, val name: String): Event()
        }

    }
}
