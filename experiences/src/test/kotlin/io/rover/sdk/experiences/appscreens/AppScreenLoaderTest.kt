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

package io.rover.sdk.experiences.appscreens

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import io.rover.sdk.core.data.AuthenticationContextInterface
import io.rover.sdk.core.data.domain.Attributes
import io.rover.sdk.core.events.UserInfoInterface
import io.rover.sdk.core.platform.DeviceIdentificationInterface
import io.rover.sdk.experiences.appscreens.network.AppScreenDataEnvelope
import io.rover.sdk.experiences.appscreens.network.AppScreenDocument
import io.rover.sdk.experiences.appscreens.network.AppScreensDataClient
import io.rover.sdk.experiences.appscreens.network.AppScreensDocumentClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppScreenLoaderTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val url = Uri.parse("https://testbench.rover.io/a/home")

    private fun doc(etag: String?, scope: AppScreenDataScope?) =
        AppScreenDocument(html = "<html></html>", etag = etag, dataScope = scope)

    @Test
    fun `loadDocument records the effective scope in the registry`() = runBlocking {
        val registry = AppScreenScopeRegistry()
        val loader = AppScreenLoader(
            FakeDocClient(context, doc("\"v1\"", AppScreenDataScope.PUBLIC)),
            FakeDataClient(context),
            registry
        )

        loader.loadDocument(url)

        assertEquals(AppScreenDataScope.PUBLIC, registry.scopeFor("https://testbench.rover.io/a/home"))
        assertEquals(AppScreenDataScope.PUBLIC, loader.recordedScope(url))
    }

    @Test
    fun `recordedScope keys by origin so the same path on two hosts is independent`() = runBlocking {
        val registry = AppScreenScopeRegistry()
        val one = Uri.parse("https://one.example/a/home")
        val two = Uri.parse("https://two.example/a/home")
        val loader = AppScreenLoader(
            FakeDocClient(context, doc("\"v1\"", AppScreenDataScope.PUBLIC)),
            FakeDataClient(context),
            registry
        )

        loader.loadDocument(one)

        assertEquals(AppScreenDataScope.PUBLIC, loader.recordedScope(one))
        assertEquals(null, loader.recordedScope(two))
    }

    @Test
    fun `loadDocument records personalized when scope header absent (fail safe)`() = runBlocking {
        val registry = AppScreenScopeRegistry()
        val loader = AppScreenLoader(
            FakeDocClient(context, doc("\"v1\"", null)),
            FakeDataClient(context),
            registry
        )

        loader.loadDocument(url)

        assertEquals(AppScreenDataScope.PERSONALIZED, registry.scopeFor("https://testbench.rover.io/a/home"))
    }

    @Test
    fun `reconcile renders immediately when etag matches template hash`() = runBlocking {
        val loader = AppScreenLoader(
            FakeDocClient(context),
            FakeDataClient(context),
            AppScreenScopeRegistry()
        )
        val envelope = envelope(templateHash = "fx-home-v4")

        var refetchCount = 0
        val reconciled = loader.reconcileScreenData(envelope, documentETag = "\"fx-home-v4\"") {
            refetchCount++
            doc("\"fx-home-v4\"", AppScreenDataScope.PUBLIC)
        }

        assertEquals(0, refetchCount)
        assertEquals("\"fx-home-v4\"", reconciled.documentETag)
    }

    @Test
    fun `reconcile refetches exactly once on mismatch then renders`() = runBlocking {
        val loader = AppScreenLoader(
            FakeDocClient(context),
            FakeDataClient(context),
            AppScreenScopeRegistry()
        )
        val envelope = envelope(templateHash = "fx-home-v5")

        var refetchCount = 0
        val reconciled = loader.reconcileScreenData(envelope, documentETag = "\"fx-home-v4\"") {
            refetchCount++
            // Refetched document now agrees with the template.
            doc("\"fx-home-v5\"", AppScreenDataScope.PUBLIC)
        }

        assertEquals(1, refetchCount)
        assertEquals("\"fx-home-v5\"", reconciled.documentETag)
    }

    @Test
    fun `reconcile fails open after a single refetch still mismatches`() = runBlocking {
        val loader = AppScreenLoader(
            FakeDocClient(context),
            FakeDataClient(context),
            AppScreenScopeRegistry()
        )
        val envelope = envelope(templateHash = "fx-home-v5")

        var refetchCount = 0
        val reconciled = loader.reconcileScreenData(envelope, documentETag = "\"fx-home-v4\"") {
            refetchCount++
            // Still stale — must not loop.
            doc("\"fx-home-v4\"", AppScreenDataScope.PUBLIC)
        }

        assertEquals(1, refetchCount)
        assertEquals("\"fx-home-v4\"", reconciled.documentETag)
    }

    @Test
    fun `loadScreenData delegates to the data client`() = runBlocking {
        val expected = envelope(templateHash = "h1")
        val loader = AppScreenLoader(
            FakeDocClient(context),
            FakeDataClient(context, expected),
            AppScreenScopeRegistry()
        )

        val result = loader.loadScreenData(url, AppScreenDataScope.PUBLIC)

        assertEquals(expected, result)
    }

    private fun envelope(templateHash: String?) = AppScreenDataEnvelope(
        rawJson = """{"templateHash":${templateHash?.let { "\"$it\"" } ?: "null"}}""",
        templateHash = templateHash,
        responseScope = AppScreenDataScope.PUBLIC
    )

    // region fakes

    private class FakeDocClient(
        context: Context,
        private vararg val documents: AppScreenDocument
    ) : AppScreensDocumentClient(context) {
        private var index = 0
        override suspend fun fetchDocument(url: Uri, policy: DocumentCachePolicy): AppScreenDocument {
            return documents[index.coerceAtMost(documents.size - 1)].also {
                if (index < documents.size - 1) index++
            }
        }
    }

    private class FakeDataClient(
        context: Context,
        private val envelope: AppScreenDataEnvelope? = null
    ) : AppScreensDataClient(context, FakeAuth, FakeUser, FakeDevice) {
        override suspend fun fetchScreenData(
            screenUrl: Uri,
            scope: AppScreenDataScope
        ): AppScreenDataEnvelope = envelope
            ?: AppScreenDataEnvelope("{}", null, AppScreenDataScope.PUBLIC)
    }

    private object FakeAuth : AuthenticationContextInterface {
        override val sdkToken: String? = null
        override val sdkAuthenticationEnabledDomains: Set<String> = emptySet()
        override fun setSdkAuthenticationIdToken(token: String?) {}
        override fun clearSdkAuthenticationIdToken() {}
        override var sdkAuthenticationIdTokenRefreshCallback: () -> Unit = {}
        override suspend fun obtainSdkAuthenticationIdToken(checkValidity: Boolean): String? = null
        override fun enableSdkAuthIdTokenRefreshForDomain(pattern: String) {}
    }

    private object FakeUser : UserInfoInterface {
        override fun update(builder: (attributes: HashMap<String, Any>) -> Unit) {}
        override fun clear() {}
        override val currentUserInfo: Attributes = emptyMap()
    }

    private object FakeDevice : DeviceIdentificationInterface {
        override val installationIdentifier: String = "device"
        override var deviceName: String? = null
    }

    // endregion
}
