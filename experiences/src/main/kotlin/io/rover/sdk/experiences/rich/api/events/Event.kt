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

package io.rover.sdk.experiences.rich.api.events

import android.app.Activity
import io.rover.sdk.experiences.rich.api.models.Action
import io.rover.sdk.experiences.rich.api.models.Experience
import io.rover.sdk.experiences.rich.api.models.Metadata
import io.rover.sdk.experiences.rich.api.models.Node
import io.rover.sdk.experiences.rich.api.models.Screen

// TODO: determine what is to become of this, we're pretty much gonna
//  enqueue a rover event directly so below may just be for reference.

/**
 * These are the types of the Events emitted on the Event Bus.
 */
sealed class Event {
    /**
     * This Event is fired when the user views an Experience screen.
     *
     * @param experience The data model of the Experience.
     * @param screen The specific screen being Viewed.
     * @param dataContext A hashMap of all the data made available to the Screen being viewed.
     * This has three fields: `data` (the Data Source data being shown on the Screen), `url` (query
     * parameters on the URL used to open the Experience), and `user` (the user info supplied to
     * the Experience by your app code).
     */
    data class ScreenViewed(
        val experience: Experience,
        val screen: Screen,

        @Deprecated("Use data, urlParameters, and userInfo directly.")
        val dataContext: Map<String, Any?>,

        val data: Any?,
        val urlParameters: Map<String, String>,
        val userInfo: Map<String, Any>
    ) : Event()

    /**
     * This Event is fired when the user taps (or otherwise activates) an Action, such as a button.
     *
     * @param experience The data model of the Experience.
     * @param screen The specific screen being Viewed.
     * @param node The node on which the Action was received (ie. the button that was tapped)
     * @param action The action data type that describes what behaviour will be executed for this action.
     * @param dataContext A hashMap of all the data made available to node that was tapped.
     * This has three fields: `data` (the Data Source data being shown on the Screen), `url` (query
     * parameters on the URL used to open the Experience), and `user` (the user info supplied to
     * the Experience by your app code).
     */
    internal data class ActionReceived(
        val experience: Experience,
        val screen: Screen,
        val node: Node,
        val action: Action,
        val dataContext: Map<String, Any?>
    ) : Event()

    /**
     * This internal type largely duplicates [CustomActionActivationEvent], because a reference to the
     * containing Activity needs to be aggregated from the [ExperienceFragment].
     */
    internal data class CustomActionActivationInternal(
        val node: Node,
        val screen: Screen,
        val experience: Experience,
        val metadata: Metadata?,

        val data: Any?,
        val urlParameters: Map<String, String>,
        val userInfo: Map<String, Any>
    )

    /**
     * Describes a user's activation (ie., a tap) of a custom action on a layer in a Judo experience.
     * A value of this type is given to any registered custom action callbacks registered with
     * [Judo.addCustomActionCallback]. Use this to implement the behavior for custom buttons
     * and the like.
     *
     * This type provides the context for the user activation custom action, giving the node
     * (layer), screen, and experience data model objects in addition to the data context (URL
     * parameters, user info, and data from a Web API data source).
     *
     * It also provides a reference to the Activity that is presenting the experience,
     * allowing you to do implement your own effects, including dismissing the experience or
     * starting a new Intent on top of the task.
     */
    data class CustomActionActivationEvent(
        val node: Node,
        val screen: Screen,
        val experience: Experience,
        val metadata: Metadata?,
        val data: Any?,
        val urlParameters: Map<String, String>,
        val userInfo: Map<String, Any>,

        // This field is populated when this event is re-emitted from the ExperienceFragment.
        val activity: Activity?
    )

    /**
     * This event is fired when ProfileService sees an update to user profile (ids, traits, etc.)
     * information.
     */
    internal object Identified : Event()

    internal object PushTokenUpdated : Event()
}

fun interface ScreenViewedCallback {
    fun screenViewed(event: Event.ScreenViewed)
}

fun interface CustomActionCallback {
    fun customActionActivated(event: Event.CustomActionActivationEvent)
}
