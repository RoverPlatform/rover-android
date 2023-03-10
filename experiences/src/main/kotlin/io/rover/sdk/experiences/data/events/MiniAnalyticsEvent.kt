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

package io.rover.sdk.experiences.data.events

import io.rover.sdk.core.data.domain.Block
import io.rover.sdk.core.data.domain.ClassicExperienceModel
import io.rover.sdk.core.data.domain.Row
import io.rover.sdk.core.data.domain.Screen
import io.rover.sdk.core.data.graphql.operations.data.*
import io.rover.sdk.core.data.graphql.putProp
import io.rover.sdk.core.data.graphql.safeGetString
import io.rover.sdk.core.data.graphql.safeOptString
import org.json.JSONObject

sealed class MiniAnalyticsEvent {
    internal abstract fun encodeJson(): JSONObject

    data class BlockTapped(
        val experience: ClassicExperienceModel,
        val screen: Screen,
        val block: Block,
        val row: Row,
        val campaignId: String?
    ) : MiniAnalyticsEvent() {
        override fun encodeJson(): JSONObject {
            return JSONObject().apply {
                putOpt(ANALYTICS_EVENT_TYPE, BLOCK_TAPPED_CODE)
                putProp(this@BlockTapped, BlockTapped::experience) { experience.encodeJson() }
                putProp(this@BlockTapped, BlockTapped::screen) { screen.encodeJson() }
                putProp(this@BlockTapped, BlockTapped::block) { block.encodeJson() }
                putProp(this@BlockTapped, BlockTapped::row) { row.encodeJson() }
                putProp(this@BlockTapped, BlockTapped::campaignId) { campaignId }
            }
        }

        internal companion object {
            fun decodeJson(jsonObject: JSONObject): BlockTapped {
                return BlockTapped(
                    experience = ClassicExperienceModel.decodeJson(jsonObject.getJSONObject(BlockTapped::experience.name)),
                    screen = Screen.decodeJson(jsonObject.getJSONObject(BlockTapped::screen.name)),
                    block = Block.decodeJson(jsonObject.getJSONObject(BlockTapped::block.name)),
                    row = Row.decodeJSON(jsonObject.getJSONObject(BlockTapped::row.name)),
                    campaignId = jsonObject.safeOptString(BlockTapped::campaignId.name)
                )
            }
        }
    }

    class AppOpened() : MiniAnalyticsEvent() {
        override fun encodeJson(): JSONObject {
            return JSONObject().apply {
                putOpt(ANALYTICS_EVENT_TYPE, APP_OPENED_CODE)
            }
        }

        internal companion object {
            fun decodeJson(): AppOpened = AppOpened()
        }
    }

    data class PollAnswered(
        val experience: ClassicExperienceModel,
        val screen: Screen,
        val block: Block,
        val option: Option,
        val poll: Poll,
        val campaignId: String?
    ) : MiniAnalyticsEvent() {
        override fun encodeJson(): JSONObject {
            return JSONObject().apply {
                putOpt(ANALYTICS_EVENT_TYPE, POLL_ANSWERED_CODE)
                putProp(this@PollAnswered, PollAnswered::experience) { experience.encodeJson() }
                putProp(this@PollAnswered, PollAnswered::screen) { screen.encodeJson() }
                putProp(this@PollAnswered, PollAnswered::block) { block.encodeJson() }
                putProp(this@PollAnswered, PollAnswered::option) { option.encodeJson() }
                putProp(this@PollAnswered, PollAnswered::poll) { poll.encodeJson() }
                putProp(this@PollAnswered, PollAnswered::campaignId) { campaignId }
            }
        }
    }

    data class ExperienceDismissed(
        val experience: ClassicExperienceModel,
        val campaignId: String?
    ) : MiniAnalyticsEvent() {
        override fun encodeJson(): JSONObject {
            return JSONObject().apply {
                putOpt(ANALYTICS_EVENT_TYPE, EXPERIENCE_DISMISSED_CODE)
                putProp(
                    this@ExperienceDismissed,
                    ExperienceDismissed::experience
                ) { experience.encodeJson() }
                putProp(this@ExperienceDismissed, ExperienceDismissed::campaignId) { campaignId }
            }
        }

        internal companion object {
            fun decodeJson(jsonObject: JSONObject): ExperienceDismissed {
                return ExperienceDismissed(
                    experience = ClassicExperienceModel.decodeJson(jsonObject.getJSONObject(ExperienceDismissed::experience.name)),
                    campaignId = jsonObject.safeOptString(ExperienceDismissed::campaignId.name)
                )
            }
        }
    }

    data class ScreenDismissed(
        val experience: ClassicExperienceModel,
        val screen: Screen,
        val campaignId: String?
    ) : MiniAnalyticsEvent() {
        override fun encodeJson(): JSONObject {
            return JSONObject().apply {
                putOpt(ANALYTICS_EVENT_TYPE, SCREEN_DISMISSED_CODE)
                putProp(
                    this@ScreenDismissed,
                    ScreenDismissed::experience
                ) { experience.encodeJson() }
                putProp(this@ScreenDismissed, ScreenDismissed::screen) { screen.encodeJson() }
                putProp(this@ScreenDismissed, ScreenDismissed::campaignId) { campaignId }
            }
        }

        internal companion object {
            fun decodeJson(jsonObject: JSONObject): ScreenDismissed {
                return ScreenDismissed(
                    experience = ClassicExperienceModel.decodeJson(jsonObject.getJSONObject(ScreenDismissed::experience.name)),
                    screen = Screen.decodeJson(jsonObject.getJSONObject(ScreenDismissed::screen.name)),
                    campaignId = jsonObject.safeOptString(ScreenDismissed::campaignId.name)
                )
            }
        }
    }

    data class ExperiencePresented(
        val experience: ClassicExperienceModel,
        val campaignId: String?
    ) : MiniAnalyticsEvent() {
        override fun encodeJson(): JSONObject {
            return JSONObject().apply {
                putOpt(ANALYTICS_EVENT_TYPE, EXPERIENCE_PRESENTED_CODE)
                putProp(
                    this@ExperiencePresented,
                    ExperiencePresented::experience
                ) { experience.encodeJson() }
                putProp(this@ExperiencePresented, ExperiencePresented::campaignId) { campaignId }
            }
        }

        internal companion object {
            fun decodeJson(jsonObject: JSONObject): ExperiencePresented {
                return ExperiencePresented(
                    experience = ClassicExperienceModel.decodeJson(jsonObject.getJSONObject(ExperiencePresented::experience.name)),
                    campaignId = jsonObject.safeOptString(ExperiencePresented::campaignId.name)
                )
            }
        }
    }

    data class ExperienceViewed(
        val experience: ClassicExperienceModel,
        val campaignId: String?,
        val duration: Int = 0
    ) : MiniAnalyticsEvent() {
        override fun encodeJson(): JSONObject {
            return JSONObject().apply {
                putOpt(ANALYTICS_EVENT_TYPE, EXPERIENCE_VIEWED_CODE)
                putProp(
                    this@ExperienceViewed,
                    ExperienceViewed::experience
                ) { experience.encodeJson() }
                putProp(this@ExperienceViewed, ExperienceViewed::campaignId) { campaignId }
                putProp(this@ExperienceViewed, ExperienceViewed::duration) { duration }
            }
        }

        internal companion object {
            fun decodeJson(jsonObject: JSONObject, duration: Int = 0): ExperienceViewed {
                return ExperienceViewed(
                    experience = ClassicExperienceModel.decodeJson(jsonObject.getJSONObject(ScreenDismissed::experience.name)),
                    duration = duration,
                    campaignId = jsonObject.safeOptString(ScreenDismissed::campaignId.name)
                )
            }
        }
    }

    data class ScreenViewed(
        val experience: ClassicExperienceModel,
        val screen: Screen,
        val campaignId: String?,
        val duration: Int = 0
    ) : MiniAnalyticsEvent() {
        override fun encodeJson(): JSONObject {
            return JSONObject().apply {
                putOpt(ANALYTICS_EVENT_TYPE, SCREEN_VIEWED_CODE)
                putProp(this@ScreenViewed, ScreenViewed::experience) { experience.encodeJson() }
                putProp(this@ScreenViewed, ScreenViewed::screen) { screen.encodeJson() }
                putProp(this@ScreenViewed, ScreenViewed::campaignId) { campaignId }
                putProp(this@ScreenViewed, ScreenViewed::duration) { duration }
            }
        }

        internal companion object {
            fun decodeJson(jsonObject: JSONObject, duration: Int = 0): ScreenViewed {
                return ScreenViewed(
                    experience = ClassicExperienceModel.decodeJson(jsonObject.getJSONObject(BlockTapped::experience.name)),
                    screen = Screen.decodeJson(jsonObject.getJSONObject(BlockTapped::screen.name)),
                    duration = duration,
                    campaignId = jsonObject.safeOptString(BlockTapped::campaignId.name)
                )
            }
        }
    }

    data class ScreenPresented(
        val experience: ClassicExperienceModel,
        val screen: Screen,
        val campaignId: String?
    ) : MiniAnalyticsEvent() {
        override fun encodeJson(): JSONObject {
            return JSONObject().apply {
                putOpt(ANALYTICS_EVENT_TYPE, SCREEN_PRESENTED_CODE)
                putProp(
                    this@ScreenPresented,
                    ScreenPresented::experience
                ) { experience.encodeJson() }
                putProp(this@ScreenPresented, ScreenPresented::screen) { screen.encodeJson() }
                putProp(this@ScreenPresented, ScreenPresented::campaignId) { campaignId }
            }
        }

        internal companion object {
            fun decodeJson(jsonObject: JSONObject): ScreenPresented {
                return ScreenPresented(
                    experience = ClassicExperienceModel.decodeJson(jsonObject.getJSONObject(ScreenPresented::experience.name)),
                    screen = Screen.decodeJson(jsonObject.getJSONObject(ScreenPresented::screen.name)),
                    campaignId = jsonObject.safeOptString(ScreenPresented::campaignId.name)
                )
            }
        }
    }

    internal companion object {
        private const val BLOCK_TAPPED_CODE = "BLOCK TAPPED"
        private const val POLL_ANSWERED_CODE = "POLL ANSWERED"
        private const val EXPERIENCE_DISMISSED_CODE = "EXPERIENCE DISMISSED"

        private const val SCREEN_DISMISSED_CODE = "SCREEN DISMISSED"
        private const val EXPERIENCE_PRESENTED_CODE = "EXPERIENCE PRESENTED"
        private const val EXPERIENCE_VIEWED_CODE = "EXPERIENCE VIEWED"
        private const val SCREEN_VIEWED_CODE = "SCREEN VIEWED"
        private const val SCREEN_PRESENTED_CODE = "SCREEN PRESENTED"

        private const val APP_OPENED_CODE = "APP OPENED"
        private const val ANALYTICS_EVENT_TYPE = "ANALYTICS_EVENT_TYPE"

        fun decodeJson(jsonObject: JSONObject): MiniAnalyticsEvent {
            return when (jsonObject.safeGetString(ANALYTICS_EVENT_TYPE)) {
                BLOCK_TAPPED_CODE -> {
                    BlockTapped.decodeJson(jsonObject)
                }
                EXPERIENCE_DISMISSED_CODE -> {
                    ExperienceDismissed.decodeJson(jsonObject)
                }
                SCREEN_DISMISSED_CODE -> {
                    ScreenDismissed.decodeJson(jsonObject)
                }
                EXPERIENCE_PRESENTED_CODE -> {
                    ExperiencePresented.decodeJson(jsonObject)
                }
                EXPERIENCE_VIEWED_CODE -> {
                    ExperienceViewed.decodeJson(jsonObject)
                }
                SCREEN_VIEWED_CODE -> {
                    ScreenViewed.decodeJson(jsonObject)
                }
                APP_OPENED_CODE -> AppOpened.decodeJson()
                else -> {
                    ScreenPresented.decodeJson(jsonObject)
                }
            }
        }
    }
}

data class Option(val id: String, val text: String, val image: String? = null) {
    fun encodeJson(): JSONObject {
        return JSONObject().apply {
            putProp(this@Option, Option::id)
            putProp(this@Option, Option::text)
            putProp(this@Option, Option::image)
        }
    }
}

data class Poll(val id: String, val text: String) {
    fun encodeJson(): JSONObject {
        return JSONObject().apply {
            putProp(this@Poll, Poll::id)
            putProp(this@Poll, Poll::text)
        }
    }
}
