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

package io.rover.sdk.adobeExperience

import io.rover.sdk.core.Rover
import io.rover.sdk.core.container.Assembler
import io.rover.sdk.core.container.Container
import io.rover.sdk.core.container.Resolver
import io.rover.sdk.core.container.Scope
import io.rover.sdk.core.events.UserInfoInterface
import io.rover.sdk.core.platform.LocalStorage
import io.rover.sdk.core.privacy.PrivacyService

class AdobeExperienceAssembler : Assembler {
    override fun assemble(container: Container) {
        container.register(
            Scope.Singleton,
            AdobeExperienceAuthorizer::class.java
        ) { resolver ->
            resolver.resolveSingletonOrFail(AdobeExperienceManager::class.java)
        }

        container.register(
            Scope.Singleton,
                AdobeExperienceManager::class.java
        ) { resolver ->
            AdobeExperienceManager(
                resolver.resolveSingletonOrFail(UserInfoInterface::class.java),
                resolver.resolveSingletonOrFail(LocalStorage::class.java),
                resolver.resolveSingletonOrFail(PrivacyService::class.java),
            )
        }
    }

    override fun afterAssembly(resolver: Resolver) {
        resolver.resolveSingletonOrFail(PrivacyService::class.java).registerTrackingEnabledChangedListener(
            resolver.resolveSingletonOrFail(AdobeExperienceManager::class.java)
        )
    }
}

val Rover.adobeExperienceAuthorizer: AdobeExperienceAuthorizer
    get() = Rover.shared.resolveSingletonOrFail(AdobeExperienceAuthorizer::class.java)
