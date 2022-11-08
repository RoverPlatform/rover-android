package io.rover.sdk.core.data.domain

import io.rover.sdk.core.events.domain.Event

/**
 * In certain places the Rover API returns unstructured data and similarly the Rover SDK is
 * expected to submit unstructured data in yet other places, particularly for [Event]s.
 */
typealias Attributes = Map<String, Any>
