package io.rover.sdk.data.events

import io.rover.sdk.data.domain.Block
import io.rover.sdk.data.domain.Experience
import io.rover.sdk.data.domain.Row
import io.rover.sdk.data.domain.Screen
import io.rover.sdk.data.graphql.putProp
import io.rover.sdk.data.graphql.safeGetString
import io.rover.sdk.data.graphql.safeOptString
import io.rover.sdk.data.operations.data.decodeJSON
import io.rover.sdk.data.operations.data.decodeJson
import io.rover.sdk.data.operations.data.encodeJson
import org.json.JSONObject

sealed class RoverEvent {
    internal abstract fun encodeJson(): JSONObject

    data class BlockTapped(
        val experience: Experience,
        val screen: Screen,
        val block: Block,
        val row: Row,
        val campaignId: String?
    ) : RoverEvent() {
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
                    experience = Experience.decodeJson(jsonObject.getJSONObject(BlockTapped::experience.name)),
                    screen = Screen.decodeJson(jsonObject.getJSONObject(BlockTapped::screen.name)),
                    block = Block.decodeJson(jsonObject.getJSONObject(BlockTapped::block.name)),
                    row = Row.decodeJSON(jsonObject.getJSONObject(BlockTapped::row.name)),
                    campaignId = jsonObject.safeOptString(BlockTapped::campaignId.name)
                )
            }
        }
    }

    data class PollAnswered(
        val experience: Experience,
        val screen: Screen,
        val block: Block,
        val option: Option,
        val poll: Poll,
        val campaignId: String?
    ) : RoverEvent() {
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
        val experience: Experience,
        val campaignId: String?
    ) : RoverEvent() {
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
                    experience = Experience.decodeJson(jsonObject.getJSONObject(ExperienceDismissed::experience.name)),
                    campaignId = jsonObject.safeOptString(ExperienceDismissed::campaignId.name)
                )
            }
        }
    }

    data class ScreenDismissed(
        val experience: Experience,
        val screen: Screen,
        val campaignId: String?
    ) : RoverEvent() {
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
                    experience = Experience.decodeJson(jsonObject.getJSONObject(ScreenDismissed::experience.name)),
                    screen = Screen.decodeJson(jsonObject.getJSONObject(ScreenDismissed::screen.name)),
                    campaignId = jsonObject.safeOptString(ScreenDismissed::campaignId.name)
                )
            }
        }
    }

    data class ExperiencePresented(
        val experience: Experience,
        val campaignId: String?
    ) : RoverEvent() {
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
                    experience = Experience.decodeJson(jsonObject.getJSONObject(ExperiencePresented::experience.name)),
                    campaignId = jsonObject.safeOptString(ExperiencePresented::campaignId.name)
                )
            }
        }
    }

    data class ExperienceViewed(
        val experience: Experience,
        val campaignId: String?,
        val duration: Int = 0
    ) : RoverEvent() {
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
                    experience = Experience.decodeJson(jsonObject.getJSONObject(ScreenDismissed::experience.name)),
                    duration = duration,
                    campaignId = jsonObject.safeOptString(ScreenDismissed::campaignId.name)
                )
            }
        }
    }

    data class ScreenViewed(
        val experience: Experience,
        val screen: Screen,
        val campaignId: String?,
        val duration: Int = 0
    ) : RoverEvent() {
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
                    experience = Experience.decodeJson(jsonObject.getJSONObject(BlockTapped::experience.name)),
                    screen = Screen.decodeJson(jsonObject.getJSONObject(BlockTapped::screen.name)),
                    duration = duration,
                    campaignId = jsonObject.safeOptString(BlockTapped::campaignId.name)
                )
            }
        }
    }

    data class ScreenPresented(
        val experience: Experience,
        val screen: Screen,
        val campaignId: String?
    ) : RoverEvent() {
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
                    experience = Experience.decodeJson(jsonObject.getJSONObject(ScreenPresented::experience.name)),
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
        private const val ANALYTICS_EVENT_TYPE = "ANALYTICS_EVENT_TYPE"

        fun decodeJson(jsonObject: JSONObject): RoverEvent {
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
