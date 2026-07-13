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

package io.rover.sdk.core

import io.rover.sdk.core.container.InjectionContainer
import io.rover.sdk.core.data.AuthenticationContextInterface
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CoreAssemblerTest {

    @Test
    fun `enables sdk auth token attachment for the engage host and every associated domain`() {
        // Only exercise assemble() (factory registration). afterAssembly() performs heavy
        // eager Android runtime wiring (WorkManager, session emitters) that is out of scope
        // for verifying the auth-domain construction site.
        val container = InjectionContainer(emptyList())
        CoreAssembler(
            accountToken = "sdk-token",
            application = RuntimeEnvironment.getApplication(),
            urlSchemes = listOf("rv-test"),
            associatedDomains = listOf("rover.judo.app", "testbench.rover.io"),
            engageEndpoint = "https://engage.rover.io/"
        ).assemble(container)

        val authenticationContext =
            container.resolveSingletonOrFail(AuthenticationContextInterface::class.java)

        // The engage endpoint host continues to be enabled.
        assertTrue(authenticationContext.sdkAuthenticationEnabledDomains.contains("engage.rover.io"))
        // Every associatedDomains entry is now also enabled for SDK auth token attachment.
        assertTrue(authenticationContext.sdkAuthenticationEnabledDomains.contains("rover.judo.app"))
        assertTrue(authenticationContext.sdkAuthenticationEnabledDomains.contains("testbench.rover.io"))
    }
}
