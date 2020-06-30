package io.rover.app.example

import android.app.Application
import android.util.Log
import io.rover.sdk.Rover
import io.rover.sdk.data.events.RoverEvent
import io.rover.sdk.services.RoverEventListener

class ExampleApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // initialize Rover SDK:
        Rover.installSaneGlobalHttpCache(this)
        Rover.initialize(this, getString(R.string.rover_api_token))

        Rover.shared?.eventEmitter?.addEventListener(object : RoverEventListener {
            override fun onPollAnswered(event: RoverEvent.PollAnswered) {
                super.onPollAnswered(event)

                val attributes = mutableMapOf(
                    "experienceID" to event.experience.id,
                    "experienceName" to event.experience.name,
                    "screenID" to event.screen.id,
                    "screenName" to event.screen.name,
                    "blockID" to event.block.id,
                    "blockName" to event.block.name,
                    "pollID" to event.poll.id,
                    "pollText" to event.poll.text,
                    "optionID" to event.option.id,
                    "optionText" to event.option.text
                )

                event.option.image?.let {
                    attributes.put("optionImage", it)
                }

                event.campaignId?.let {
                    attributes.put("campaignID", it)
                }

                Log.i("RoverEvents:", "Event: $attributes")
            }
        }
        )
    }
}
