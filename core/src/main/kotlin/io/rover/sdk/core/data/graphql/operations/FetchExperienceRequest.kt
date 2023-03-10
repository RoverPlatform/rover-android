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

package io.rover.sdk.core.data.graphql.operations

import io.rover.sdk.core.data.GraphQlRequest
import io.rover.sdk.core.data.domain.ClassicExperienceModel
import io.rover.sdk.core.data.graphql.operations.data.decodeJson
import org.json.JSONObject

class FetchExperienceRequest(
    private val queryIdentifier: ExperienceQueryIdentifier
) : GraphQlRequest<ClassicExperienceModel> {
    override val operationName: String = "FetchExperience"

    override val mutation: Boolean
        get() = false

    override val fragments: List<String>
        get() = listOf("experienceFields")

    override val query: String
        get() {
            return when (queryIdentifier) {
                is ExperienceQueryIdentifier.ById -> {
                    if (queryIdentifier.useDraft) {
                        """
                    query FetchExperienceById(${"\$"}id: ID, ${"\$"}versionID: String) {
                        experience(id: ${"\$"}id, versionID: ${"\$"}versionID) {
                            ...experienceFields
                        }
                    }
                        """.trimIndent()
                    } else {
                        """
                    query FetchExperienceById(${"\$"}id: ID) {
                        experience(id: ${"\$"}id) {
                            ...experienceFields
                        }
                    }
                        """.trimIndent()
                    }
                }
                is ExperienceQueryIdentifier.ByUniversalLink -> {
                    """
                query FetchExperienceByCampaignURL(${"\$"}campaignURL: String) {
                    experience(campaignURL: ${"\$"}campaignURL) {
                        ...experienceFields
                    }
                }
                    """.trimIndent()
                }
            }
        }
    override val variables: JSONObject = JSONObject().apply {
        when (queryIdentifier) {
            is ExperienceQueryIdentifier.ById -> {
                put("id", queryIdentifier.id)
                if (queryIdentifier.useDraft) {
                    put("versionID", "current")
                }
            }
            is ExperienceQueryIdentifier.ByUniversalLink -> {
                put("campaignURL", queryIdentifier.uri)
            }
        }
    }

    override fun decodePayload(responseObject: JSONObject): ClassicExperienceModel {
        return ClassicExperienceModel.decodeJson(
            responseObject.getJSONObject("data").getJSONObject("experience")
        )
    }

    sealed class ExperienceQueryIdentifier {
        /**
         * Experiences may be started by just their ID.
         *
         * (This method is typically used when experiences are started from a deep link or
         * progammatically.)
         */
        data class ById(val id: String, val useDraft: Boolean) : ExperienceQueryIdentifier()

        /**
         * Experiences may be started from a universal link.  The link itself may ultimately, but
         * opaquely, address the experience and a possibly associated campaign, but it is up to the
         * cloud API to resolve it.
         *
         * (This method is typically used when experiences are started from external sources,
         * particularly email, social, external apps, and other services integrated into the app).
         */
        data class ByUniversalLink(val uri: String) : ExperienceQueryIdentifier()
    }
}
