package io.rover.core.data.domain

import io.rover.core.events.domain.Event

/**
 * In certain places the Rover API returns unstructured data and similarly the Rover SDK is
 * expected to submit unstructured data in yet other places, particularly for [Event]s.
 */
typealias Attributes = Map<String, Any>
