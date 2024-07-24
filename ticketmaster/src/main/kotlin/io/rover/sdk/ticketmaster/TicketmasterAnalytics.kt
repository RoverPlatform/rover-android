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

package io.rover.sdk.ticketmaster

import android.os.Bundle
import com.ticketmaster.discoveryapi.models.DiscoveryEvent
import com.ticketmaster.purchase.action.TMCheckoutEndReason
import com.ticketmaster.purchase.action.TMTicketSelectionEndReason

interface TicketmasterAnalytics {
    /**
     * Post an event from the Ticketmaster Ignite [Tickets SDK](https://ignite.ticketmaster.com/v1/docs/information#user-analytics)
     * for use with Rover.  This method should be called from the `onTicketSelectionStarted` method on a class that implements the
     * `TMPurchaseUserAnalyticsListener` interface.
     *
     * @param event The event as a `DiscoveryEvent` object.
     *
     * Example:
     *
     * ```kotlin
     * class MyActivity : AppCompatActivity(), TMPurchaseUserAnalyticsListener {
     * //..
     * override fun onTicketSelectionStarted(event: DiscoveryEvent) {
     *   Rover.shared.ticketmasterAnalytics.onTicketSelectionStarted(event)
     * }
     * ````
     */
    fun onTicketSelectionStarted(event: DiscoveryEvent)

    /**
     * Post an event from the Ticketmaster Ignite [Tickets SDK](https://ignite.ticketmaster.com/v1/docs/information#user-analytics)
     * for use with Rover.  This method should be called from the `onTicketSelectionFinished` method on a class that implements the
     * `TMPurchaseUserAnalyticsListener` interface.
     *
     * @param event The event as a `DiscoveryEvent` object.
     * @param reason The reason the ticket selection ended as a `TMTicketSelectionEndReason` object.
     *
     * Example:
     *
     * ```kotlin
     * class MyActivity : AppCompatActivity(), TMPurchaseUserAnalyticsListener {
     * //..
     * override fun onTicketSelectionFinished(event: DiscoveryEvent, reason: TMTicketSelectionEndReason) {
     *   Rover.shared.ticketmasterAnalytics.onTicketSelectionFinished(event, reason)
     * }
     * ````
     */
    fun onTicketSelectionFinished(event: DiscoveryEvent, reason: TMTicketSelectionEndReason)

    /**
     * Post an event from the Ticketmaster Ignite [Tickets SDK](https://ignite.ticketmaster.com/v1/docs/information#user-analytics)
     * for use with Rover.  This method should be called from the `onCheckoutStarted` method on a class that implements the
     * `TMPurchaseUserAnalyticsListener` interface.
     *
     * @param event The event as a `DiscoveryEvent` object.
     *
     * Example:
     *
     * ```kotlin
     * class MyActivity : AppCompatActivity(), TMPurchaseUserAnalyticsListener {
     * //..
     * override fun onCheckoutStarted(event: DiscoveryEvent) {
     *   Rover.shared.ticketmasterAnalytics.onCheckoutStarted(event)
     * }
     * ````
     */
    fun onCheckoutStarted(event: DiscoveryEvent)

    /**
     * Post an event from the Ticketmaster Ignite [Tickets SDK](https://ignite.ticketmaster.com/v1/docs/information#user-analytics)
     * for use with Rover.  This method should be called from the `onCheckoutFinished` method on a class that implements the
     * `TMPurchaseUserAnalyticsListener` interface.
     *
     * @param event The event as a `DiscoveryEvent` object.
     * @param reason The reason the checkout finished as a `TMCheckoutEndReason` object.
     *
     * Example:
     *
     * ```kotlin
     * class MyActivity : AppCompatActivity(), TMPurchaseUserAnalyticsListener {
     * //..
     * override fun onCheckoutFinished(event: DiscoveryEvent, reason: TMCheckoutEndReason) {
     *   Rover.shared.ticketmasterAnalytics.onCheckoutFinished(event, reason)
     * }
     * ````
     */
    fun onCheckoutFinished(event: DiscoveryEvent, reason: TMCheckoutEndReason)

    /**
     * Post an event from the Ticketmaster Ignite [Tickets SDK](https://ignite.ticketmaster.com/v1/docs/information#user-analytics)
     * for use with Rover.  This method should be called from the `Observer<UserAnalyticsDelegate.AnalyticsData?>` block.
     *
     * @param action The action name as a string, usually provided by the Ticketmaster
     * `UserAnalyticsDelegate.AnalyticsData` object.
     * @param bundle The bundle containing information about the event, also provided by the
     * Ticketmaster `UserAnalyticsDelegate.AnalyticsData` object.
     *
     * Example:
     *
     * ```kotlin
     * override fun onCreate(savedInstanceState: Bundle?) {
     *     super.onCreate(savedInstanceState)
     *     ...
     *     UserAnalyticsDelegate.handler.getLiveData().observeForever(userAnalyticsObserver)
     * }
     *
     * private val userAnalyticsObserver = Observer<UserAnalyticsDelegate.AnalyticsData?> {
     *     it?.let {
     *         //Post the event to the Rover SDK
     *         Rover.ticketmasterAnalytics.postTicketmasterEvent(it.actionName, it.data)
     *     }
     * }
     * ````
     */
    fun postTicketmasterEvent(action: String, bundle: Bundle?)
}