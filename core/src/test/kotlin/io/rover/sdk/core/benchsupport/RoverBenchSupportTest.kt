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

package io.rover.sdk.core.benchsupport

import com.natpryce.hamkrest.absent
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.squareup.moshi.Moshi
import io.rover.sdk.core.data.config.CommHubColorScheme
import io.rover.sdk.core.data.config.ConfigManager
import io.rover.sdk.core.data.config.EngageApiService
import io.rover.sdk.core.data.config.HomeViewManager
import io.rover.sdk.core.data.config.HomeViewResponse
import io.rover.sdk.core.data.config.HubConfig
import io.rover.sdk.core.data.config.RoverConfig
import io.rover.sdk.core.platform.KeyValueStorage
import io.rover.sdk.core.platform.LocalStorage
import kotlin.concurrent.thread
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@OptIn(RoverBenchSupportApi::class)
class RoverBenchSupportTest {

    private lateinit var moshi: Moshi
    private lateinit var localStorage: LocalStorage
    private lateinit var configStorage: KeyValueStorage
    private lateinit var homeViewStorage: KeyValueStorage

    private val backendConfig = RoverConfig(
        hub = HubConfig(
            isHomeEnabled = true,
            isInboxEnabled = true,
            isSettingsViewEnabled = true,
            deepLink = "rover://hub"
        ),
        colorScheme = CommHubColorScheme.DARK,
        accentColor = "#FF0000"
    )

    @Before
    fun setUp() {
        moshi = Moshi.Builder().build()

        configStorage = mock<KeyValueStorage>()
        homeViewStorage = mock<KeyValueStorage>()
        localStorage = mock<LocalStorage>()
        whenever(localStorage.getKeyValueStorageFor("config")).thenReturn(configStorage)
        whenever(localStorage.getKeyValueStorageFor("home-view")).thenReturn(homeViewStorage)

        whenever(configStorage[CONFIG_STORAGE_KEY])
            .thenReturn(moshi.adapter(RoverConfig::class.java).toJson(backendConfig))
        whenever(homeViewStorage[HomeViewManager.STORAGE_KEY])
            .thenReturn(
                moshi.adapter(HomeViewResponse::class.java)
                    .toJson(HomeViewResponse(experienceURL = BACKEND_HOME_URL))
            )

        RoverBenchSupport.clearOverrides()
    }

    @After
    fun tearDown() {
        // The store is process-global, so leaving overrides set would leak into other tests,
        // and the managers each case constructs would stay registered for the rest of the run.
        RoverBenchSupport.clearOverrides()
        BenchSupportOverrideStore.removeAllChangeListeners()
    }

    // MARK: - Override resolution

    @Test
    fun `no override passes the backend value through`() {
        assertThat(RoverConfigOverrides().applyTo(backendConfig), equalTo(backendConfig))
    }

    @Test
    fun `value forces the overridden field and leaves the rest alone`() {
        val overridden = RoverConfigOverrides(
            isHomeEnabled = Override.Value(false),
            accentColor = Override.Value("#00FF00")
        ).applyTo(backendConfig)

        assertThat(overridden.hub.isHomeEnabled, equalTo(false))
        assertThat(overridden.accentColor, equalTo("#00FF00"))
        assertThat(overridden.hub.isInboxEnabled, equalTo(true))
        assertThat(overridden.hub.deepLink, equalTo("rover://hub"))
        assertThat(overridden.colorScheme, equalTo(CommHubColorScheme.DARK))
    }

    @Test
    fun `unset removes a nullable field`() {
        val overridden = RoverConfigOverrides(
            hubDeepLink = Override.Unset,
            colorScheme = Override.Unset,
            accentColor = Override.Unset
        ).applyTo(backendConfig)

        assertThat(overridden.hub.deepLink, absent())
        assertThat(overridden.colorScheme, absent())
        assertThat(overridden.accentColor, absent())
    }

    @Test
    fun `unset resolves a non-nullable hub flag to its declared default`() {
        val overridden = RoverConfigOverrides(
            isHomeEnabled = Override.Unset,
            isInboxEnabled = Override.Unset
        ).applyTo(backendConfig)

        assertThat(overridden.hub.isHomeEnabled, equalTo(HubConfig().isHomeEnabled))
        assertThat(overridden.hub.isInboxEnabled, equalTo(HubConfig().isInboxEnabled))
    }

    @Test
    fun `overrides never touch fields they do not cover`() {
        val overridden = RoverConfigOverrides(isHomeEnabled = Override.Value(false))
            .applyTo(backendConfig)

        assertThat(overridden.hub.isSettingsViewEnabled, equalTo(true))
    }

    // MARK: - ConfigManager layering

    @Test
    fun `config manager applies overrides set before it is constructed`() {
        RoverBenchSupport.configOverrides = RoverConfigOverrides(
            isInboxEnabled = Override.Value(false)
        )

        val manager = ConfigManager(localStorage)

        assertThat(manager.config.value.hub.isInboxEnabled, equalTo(false))
    }

    @Test
    fun `setting an override republishes immediately`() {
        val manager = ConfigManager(localStorage)
        assertThat(manager.config.value.colorScheme, equalTo(CommHubColorScheme.DARK))

        RoverBenchSupport.configOverrides = RoverConfigOverrides(
            colorScheme = Override.Value(CommHubColorScheme.LIGHT)
        )

        assertThat(manager.config.value.colorScheme, equalTo(CommHubColorScheme.LIGHT))
    }

    @Test
    fun `overrides survive a backend refresh`() {
        val manager = ConfigManager(localStorage)
        RoverBenchSupport.configOverrides = RoverConfigOverrides(
            hubDeepLink = Override.Value("testbench-deep-link://tab/hub")
        )

        manager.updateFromBackend(
            backendConfig.copy(hub = backendConfig.hub.copy(deepLink = "rover://elsewhere"))
        )

        assertThat(manager.config.value.hub.deepLink, equalTo("testbench-deep-link://tab/hub"))
    }

    @Test
    fun `clearing overrides restores the backend value`() {
        val manager = ConfigManager(localStorage)
        RoverBenchSupport.configOverrides = RoverConfigOverrides(
            accentColor = Override.Value("#00FF00")
        )
        assertThat(manager.config.value.accentColor, equalTo("#00FF00"))

        RoverBenchSupport.clearOverrides()

        assertThat(manager.config.value.accentColor, equalTo("#FF0000"))
    }

    @Test
    fun `overrides are not persisted`() {
        val manager = ConfigManager(localStorage)
        RoverBenchSupport.configOverrides = RoverConfigOverrides(
            accentColor = Override.Value("#00FF00")
        )

        manager.updateFromBackend(backendConfig)

        val json = argumentCaptor<String>()
        verify(configStorage).set(eq(CONFIG_STORAGE_KEY), json.capture())
        assertThat(moshi.adapter(RoverConfig::class.java).fromJson(json.firstValue), equalTo(backendConfig))
    }

    /**
     * Guards the contract that ConfigManager's publication lock exists for: a backend sync and
     * an override change, racing from different threads, must settle on the latest of each
     * rather than on whichever thread happened to compute its snapshot first.
     *
     * This asserts the contract; it is not a reproduction. The unserialized window is a few
     * instructions wide, and hammering it did not surface the reversal on developer hardware,
     * so treat a pass as "the invariant still holds", not as proof the lock is unnecessary.
     */
    @Test
    fun `concurrent backend updates and override changes settle on the latest of each`() {
        repeat(ITERATIONS) {
            RoverBenchSupport.clearOverrides()
            val manager = ConfigManager(localStorage)

            val backendConfigs = (0 until OPERATIONS).map {
                backendConfig.copy(accentColor = "#00000$it")
            }
            val overrides = (0 until OPERATIONS).map {
                RoverConfigOverrides(
                    colorScheme = Override.Value(
                        if (it % 2 == 0) CommHubColorScheme.LIGHT else CommHubColorScheme.DARK
                    )
                )
            }

            val backendWriter = thread { backendConfigs.forEach(manager::updateFromBackend) }
            val overrideWriter = thread {
                overrides.forEach { RoverBenchSupport.configOverrides = it }
            }
            backendWriter.join()
            overrideWriter.join()

            // Whichever side published last, it must have seen both parties' final writes: a
            // stale snapshot landing last would revert one of them.
            assertThat(
                manager.config.value,
                equalTo(overrides.last().applyTo(backendConfigs.last()))
            )
        }
    }

    // MARK: - HomeViewManager layering

    @Test
    fun `home view manager applies the URL override over the cached URL`() {
        val manager = HomeViewManager(mock<EngageApiService>(), localStorage)
        assertThat(manager.experienceURL.value, equalTo(BACKEND_HOME_URL))

        RoverBenchSupport.homeExperienceURLOverride = Override.Value(OVERRIDDEN_HOME_URL)

        assertThat(manager.experienceURL.value, equalTo(OVERRIDDEN_HOME_URL))
    }

    @Test
    fun `unsetting the home view URL presents the hub as having no home view`() {
        val manager = HomeViewManager(mock<EngageApiService>(), localStorage)

        RoverBenchSupport.homeExperienceURLOverride = Override.Unset

        assertThat(manager.experienceURL.value, absent())
    }

    @Test
    fun `clearing overrides restores the fetched home view URL`() {
        RoverBenchSupport.homeExperienceURLOverride = Override.Value(OVERRIDDEN_HOME_URL)
        val manager = HomeViewManager(mock<EngageApiService>(), localStorage)
        assertThat(manager.experienceURL.value, equalTo(OVERRIDDEN_HOME_URL))

        RoverBenchSupport.clearOverrides()

        assertThat(manager.experienceURL.value, equalTo(BACKEND_HOME_URL))
    }

    private companion object {
        // ConfigManager's storage key, which is private to it.
        const val CONFIG_STORAGE_KEY = "io.rover.communicationHub.config"
        const val BACKEND_HOME_URL = "https://cached.rover.io/experience"
        const val OVERRIDDEN_HOME_URL = "https://bench.rover.io/a/overall-test"

        const val ITERATIONS = 20
        const val OPERATIONS = 20
    }
}
