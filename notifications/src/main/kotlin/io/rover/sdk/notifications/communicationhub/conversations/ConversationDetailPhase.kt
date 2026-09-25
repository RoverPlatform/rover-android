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

package io.rover.sdk.notifications.communicationhub.conversations

/**
 * How far the conversation detail screen has got in obtaining its conversation.
 *
 * One value rather than a flag per outcome, so the combinations that mean nothing cannot be
 * written down: missing while already showing, showing while still looking.
 */
internal enum class ConversationLoadState {
    /** Nothing has reported yet, or the on-demand backfill is running. */
    Loading,

    /** The backfill finished and the conversation is nowhere to be found. */
    NotFound,

    /**
     * The attempt to obtain the conversation threw. Not terminal, and not [NotFound]: nothing
     * here established that the conversation is absent, and the store's flow is still live, so an
     * arrival can still start the screen.
     */
    Failed,

    /** The conversation is in the store: its replies are bootstrapped and its poll runs. */
    Showing,

    /**
     * The store's flow to this screen has failed. Terminal: the collector is completed, so nothing
     * can fill the thread again and no later answer may move the screen back to [Showing].
     */
    Disconnected,
}

/**
 * What the conversation detail screen renders, derived from the store and the load state rather
 * than from which coroutine finished last.
 */
internal enum class ConversationDetailPhase {
    /** Waiting on the conversation. No thread and no composer. */
    Loading,

    /** The conversation is in the store. The only phase that renders the reply composer. */
    Thread,

    /** The conversation has been looked for and is not there. */
    NotFound,

    /**
     * The conversation could not be loaded. Distinct from [NotFound] because a conversation that
     * genuinely exists reaches here: the store's flow to the screen can fail while the lookup is
     * still able to confirm the row.
     */
    LoadFailed;

    companion object {
        /**
         * A conversation that is not in the store never resolves to [Thread], whatever the load
         * state says: the thread carries the reply composer, and a reply must not be typed into a
         * conversation that does not exist.
         *
         * A conversation that has arrived wins over a load state left behind by an earlier
         * attempt. The store is the answer; the screen reacts to the arrival elsewhere.
         */
        fun from(hasConversation: Boolean, loadState: ConversationLoadState): ConversationDetailPhase {
            if (hasConversation) return Thread
            return when (loadState) {
                // Still obtaining it: an empty store under [ConversationLoadState.Showing] is the
                // moment between the lookup answering and the store's flow emitting.
                ConversationLoadState.Loading, ConversationLoadState.Showing -> Loading
                ConversationLoadState.NotFound -> NotFound
                ConversationLoadState.Failed, ConversationLoadState.Disconnected -> LoadFailed
            }
        }
    }
}
