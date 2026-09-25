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

package io.rover.sdk.experiences

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import io.rover.sdk.experiences.data.http.RoverExperiencesWebService
import io.rover.sdk.experiences.rich.compose.data.ExperiencesHttpClient
import io.rover.sdk.experiences.rich.compose.model.values.CDNConfiguration
import io.rover.sdk.experiences.rich.compose.ui.fonts.FontLoader
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.Response
import java.util.concurrent.atomic.AtomicInteger

/**
 * The Experience composable holds its [ExperienceFetchViewModel] with `viewModel(key = url)`, so the
 * view model lives in the host's ViewModelStore and outlives the composable. An embedded experience
 * leaves and re-enters composition freely (scrolled out of and back into a feed, navigated away from
 * and back to, a fragment view recreated, the activity recreated on rotation), and every re-entry
 * re-runs the LaunchedEffect that calls [ExperienceFetchViewModel.start] on that same retained
 * instance.
 *
 * One load pipeline must survive all of that. Everything else about when a load happens is left as
 * it was: a request still refetches, so a feed still refreshes when the user comes back to it.
 */
@RunWith(RobolectricTestRunner::class)
class ExperienceFetchViewModelTest {

    // Dispatchers.Unconfined rather than a TestDispatcher: the load hops threads (the web service
    // and the font loader both leave the main dispatcher), and a TestDispatcher queues those
    // resumptions on its scheduler, which a plain runBlocking never advances.
    @Before
    fun setUp() = Dispatchers.setMain(Dispatchers.Unconfined)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private class CountingWebService : RoverExperiencesWebService {
        val documentFetches = AtomicInteger()
        val configurationFetches = AtomicInteger()

        override suspend fun fetchData(url: String): Response<ResponseBody> {
            documentFetches.incrementAndGet()
            delay(SIMULATED_ROUND_TRIP_MS)
            return Response.success(
                EMPTY_V2_DOCUMENT.toResponseBody("application/json".toMediaType()),
                Headers.headersOf("Rover-Experience-Version", "2")
            )
        }

        override suspend fun getConfiguration(url: String): CDNConfiguration {
            configurationFetches.incrementAndGet()
            delay(SIMULATED_ROUND_TRIP_MS)
            return CDNConfiguration(ASSET_LOCATION, ASSET_LOCATION, ASSET_LOCATION)
        }
    }

    /**
     * Polls for an observable condition rather than sleeping for a fixed settle. Every load in this
     * suite ends in a terminal state, so the wait is as long as the machine needs and no longer.
     */
    private suspend fun awaitUntil(what: String, condition: () -> Boolean) {
        try {
            withTimeout(AWAIT_TIMEOUT_MS) {
                while (!condition()) {
                    delay(POLL_INTERVAL_MS)
                }
            }
        } catch (timeout: TimeoutCancellationException) {
            fail("timed out after ${AWAIT_TIMEOUT_MS}ms waiting for $what")
        }
    }

    /** Awaits a loaded experience, reporting a failed load immediately rather than timing out on it. */
    private suspend fun ExperienceFetchViewModel.awaitLoaded() =
        awaitUntil("the experience to load") {
            when (val current = state.value) {
                is ExperienceFetchViewModel.State.Success -> true
                is ExperienceFetchViewModel.State.Failed -> {
                    fail("the load failed: ${current.reason}")
                    false
                }
                else -> false
            }
        }

    /**
     * Requires a count to stay put across a quiet window.
     *
     * The fan-out assertions are "exactly one", which a poll that stops at the first fetch could
     * satisfy by sampling before the duplicates land. Under this suite's Dispatchers.Unconfined
     * every duplicate increments synchronously inside request(), so that cannot happen today, but
     * the assertion should not be resting on the dispatcher: waiting out a window after the load
     * has already finished catches a straggler that a different dispatcher might introduce.
     */
    private suspend fun assertNoFurther(what: String, expected: Int, count: () -> Int) {
        assertThat(what, count(), equalTo(expected))
        delay(QUIET_WINDOW_MS)
        assertThat("$what (a straggler landed after the load settled)", count(), equalTo(expected))
    }

    private val application = ApplicationProvider.getApplicationContext<Application>()

    private fun fontLoader() = FontLoader(application, ExperiencesHttpClient(application))

    /** Stands in for one entry of the Experience composable into composition. */
    private fun ExperienceFetchViewModel.enterComposition(webService: RoverExperiencesWebService) =
        start(application, webService, fontLoader())

    @Test
    fun `re-entering composition does not add another fetch pipeline`() = runBlocking {
        val webService = CountingWebService()
        val viewModel = ExperienceFetchViewModel()

        repeat(84) { viewModel.enterComposition(webService) }
        viewModel.request(URL)
        viewModel.awaitLoaded()

        assertNoFurther("document.json fetches", 1) { webService.documentFetches.get() }
        assertNoFurther("configuration.json fetches", 1) { webService.configurationFetches.get() }
    }

    /**
     * The deterministic half of the fan-out claim, and the authoritative one: no wall clock at all.
     *
     * Every collector is driven on a [StandardTestDispatcher], and the web service suspends on a
     * gate that is never opened, at the point of entry and before anything that would leave the
     * dispatcher. Draining the scheduler therefore parks every collector that exists inside
     * `fetchData`, and the count of arrivals is the count of pipelines, with no scheduling
     * assumption left to make. (The suite's other tests cannot use a TestDispatcher: a load that
     * runs to completion hops threads through the web service and the font loader, and those
     * resumptions queue on a scheduler that a plain runBlocking never advances.)
     */
    @Test
    fun `only one collector exists however many times the composable re-enters`() {
        val scheduler = TestCoroutineScheduler()
        Dispatchers.setMain(StandardTestDispatcher(scheduler))

        val arrivals = AtomicInteger()
        val neverOpened = CompletableDeferred<Unit>()
        val gatedWebService = object : RoverExperiencesWebService {
            override suspend fun fetchData(url: String): Response<ResponseBody> {
                arrivals.incrementAndGet()
                neverOpened.await()
                error("the gate is never opened")
            }

            override suspend fun getConfiguration(url: String): CDNConfiguration =
                error("the load never gets past document.json")
        }

        val viewModel = ExperienceFetchViewModel()
        repeat(84) { viewModel.enterComposition(gatedWebService) }
        viewModel.request(URL)
        scheduler.advanceUntilIdle()

        assertThat("one pipeline should have reached the web service", arrivals.get(), equalTo(1))
    }

    @Test
    fun `requesting the same url again still refetches it`() = runBlocking {
        val webService = CountingWebService()
        val viewModel = ExperienceFetchViewModel()

        viewModel.enterComposition(webService)
        viewModel.request(URL)
        viewModel.awaitLoaded()
        viewModel.request(URL)
        awaitUntil("the second load to start") { webService.documentFetches.get() >= 2 }
        viewModel.awaitLoaded()

        assertThat(webService.documentFetches.get(), equalTo(2))
    }

    @Test
    fun `a later entry into composition supplies the dependencies that get used`() = runBlocking {
        val firstEntry = CountingWebService()
        val secondEntry = CountingWebService()
        val viewModel = ExperienceFetchViewModel()

        viewModel.enterComposition(firstEntry)
        viewModel.request(URL)
        viewModel.awaitLoaded()
        assertThat(firstEntry.documentFetches.get(), equalTo(1))

        viewModel.enterComposition(secondEntry)
        viewModel.request(OTHER_URL)
        awaitUntil("the second entry's service to be used") { secondEntry.documentFetches.get() >= 1 }
        viewModel.awaitLoaded()

        assertThat(secondEntry.documentFetches.get(), equalTo(1))
        assertThat(firstEntry.documentFetches.get(), equalTo(1))
    }

    @Test
    fun `a different url still loads`() = runBlocking {
        val webService = CountingWebService()
        val viewModel = ExperienceFetchViewModel()

        viewModel.enterComposition(webService)
        viewModel.request(URL)
        viewModel.awaitLoaded()
        viewModel.request(OTHER_URL)
        awaitUntil("the second url to load") { webService.documentFetches.get() >= 2 }
        viewModel.awaitLoaded()

        assertThat(webService.documentFetches.get(), equalTo(2))
    }

    private companion object {
        val URL: Uri = Uri.parse("https://example.com/some-experience")
        val OTHER_URL: Uri = Uri.parse("https://example.com/another-experience")
        const val ASSET_LOCATION = "https://assets.example.com/"
        const val SIMULATED_ROUND_TRIP_MS = 50L
        const val POLL_INTERVAL_MS = 5L
        const val QUIET_WINDOW_MS = 250L
        const val AWAIT_TIMEOUT_MS = 20_000L
        const val EMPTY_V2_DOCUMENT = """
            {"nodes":[],"screenIDs":[],"initialScreenID":"screen","appearance":"auto",
             "colors":[],"gradients":[],"fonts":[],"segues":[]}
        """
    }
}
