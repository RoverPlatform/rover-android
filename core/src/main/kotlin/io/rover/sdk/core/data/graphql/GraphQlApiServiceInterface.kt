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

package io.rover.sdk.core.data.graphql

import io.rover.sdk.core.data.GraphQlRequest
import io.rover.sdk.core.data.NetworkResult
import io.rover.sdk.core.data.domain.ClassicExperienceModel
import io.rover.sdk.core.data.domain.EventSnapshot
import io.rover.sdk.core.data.graphql.operations.FetchExperienceRequest
import org.reactivestreams.Publisher

interface GraphQlApiServiceInterface {
    /**
     * Performs the given [GraphQlRequest] when subscribed and yields the result to the subscriber.
     */
    fun <TEntity> operation(
        request: GraphQlRequest<TEntity>
    ): Publisher<NetworkResult<TEntity>>

    /**
     * Submit analytics events when subscribed, yielding the results to the subscriber.
     */
    fun submitEvents(
        events: List<EventSnapshot>
    ): Publisher<NetworkResult<String>>

    fun fetchExperience(
        query: FetchExperienceRequest.ExperienceQueryIdentifier
    ): Publisher<NetworkResult<ClassicExperienceModel>>
}
