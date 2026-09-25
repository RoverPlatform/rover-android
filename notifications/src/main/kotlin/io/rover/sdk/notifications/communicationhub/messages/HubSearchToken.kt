/*
 * Copyright (c) 2026, Rover Labs, Inc. All rights reserved.
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

package io.rover.sdk.notifications.communicationhub.messages

/**
 * A search token applied in the Hub messages search, narrowing results to a
 * single subscription's posts or a single sender's conversations. Tokens are
 * only ever inserted by tapping a search suggestion, and at most one is
 * active at a time.
 *
 * Filtering always uses the token's id, never its display name — two
 * subscriptions (or participants) can share a name.
 */
internal sealed class HubSearchToken {
    abstract val name: String

    /** Stable identity for deduplication and Compose keys, distinct across cases. */
    abstract val key: String

    /**
     * The subscription logo or participant avatar to show on the chip and in
     * suggestions, when one exists; the UI falls back to a type glyph.
     */
    abstract val imageUrl: String?

    data class Subscription(
        val id: String,
        override val name: String,
        override val imageUrl: String? = null,
    ) : HubSearchToken() {
        override val key: String get() = "sub:$id"
    }

    data class Sender(
        val participantID: String,
        override val name: String,
        override val imageUrl: String? = null,
    ) : HubSearchToken() {
        override val key: String get() = "sender:$participantID"
    }
}
