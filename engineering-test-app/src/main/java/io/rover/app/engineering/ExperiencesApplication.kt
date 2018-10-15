package io.rover.app.engineering

import android.app.Application
import android.content.Intent
import com.microsoft.appcenter.AppCenter
import com.microsoft.appcenter.crashes.AbstractCrashesListener
import com.microsoft.appcenter.crashes.Crashes
import com.microsoft.appcenter.crashes.ingestion.models.ErrorAttachmentLog
import com.microsoft.appcenter.crashes.model.ErrorReport
import com.microsoft.appcenter.distribute.Distribute
import io.reactivex.schedulers.Schedulers
import io.rover.account.AccountAssembler
import io.rover.account.AuthService
import io.rover.app.engineering.services.ExperienceRepository
import io.rover.app.engineering.services.V1ApiNetworkClient
import io.rover.core.CoreAssembler
import io.rover.core.Rover
import io.rover.core.data.AuthenticationContext
import io.rover.core.logging.GlobalStaticLogHolder
import io.rover.core.logging.LogBuffer
import io.rover.experiences.ExperiencesAssembler
import timber.log.Timber

/**
 * Android entry point for the Rover Android Experiences app.
 */
class ExperiencesApplication: Application() {

    private val roverBaseUrl by lazy { resources.getString(R.string.rover_endpoint) }

    val authService by lazy {
        Rover.sharedInstance.resolveSingletonOrFail(
            AuthenticationContext::class.java
        ) as AuthService
    }

    val experienceRepository by lazy {
        ExperienceRepository(
            V1ApiNetworkClient(
                authService,
                roverBaseUrl
            ),
            Schedulers.io()
        )
    }

    override fun onCreate() {
        super.onCreate()

        if (!BuildConfig.DEBUG) {
            AppCenter.start(
                this,
                getString(R.string.microsoft_app_center_secret),
                // TODO might replace App Center Crashes with Crashlytics.
                Crashes::class.java,
                Distribute::class.java
            )

            Crashes.setListener(
                object : AbstractCrashesListener() {
                    override fun getErrorAttachments(report: ErrorReport?): MutableIterable<ErrorAttachmentLog> {
                        val logger = GlobalStaticLogHolder.globalLogEmitter as LogBuffer?
                        return mutableListOf(ErrorAttachmentLog.attachmentWithText(logger?.getLogsAsText() ?: "No log buffer available", "log.txt"))
                    }
                }
            )
        }

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            // TODO: Timber tree that reports to logs into Rover's own logger so the logs can be gathered by App Center crashes as seen above.
        }

        Rover.initialize(
            CoreAssembler(
                "",
                this,
                listOf("rv-experiences"),
                endpoint = "$roverBaseUrl/graphql"
            ),
            AccountAssembler(
                this,
                Intent(
                    this,
                    ExperiencesListActivity::class.java
                ),
                roverBaseUrl
            ),
            ExperiencesAssembler()
        )

        Rover.installSaneGlobalHttpCache(this)

        // So, a typical app using Rover would do the usual static-context Rover.initialize()
        // routine here.  However, we will not do so since we are using a late-bound custom
        // authentication context.  See AuthService. Now we have to at least warm up authservice, so
        // it can do that side-effect of initializing the Rover SDK in the event that authentication
        // is already persisted.
        authService
    }
}
