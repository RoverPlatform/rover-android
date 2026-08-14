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

package io.rover.sdk.notifications.communicationhub

import io.rover.sdk.core.platform.KeyValueStorage
import io.rover.sdk.core.platform.LocalStorage

/**
 * In-memory [LocalStorage] for tests, so storage-backed types can be exercised without
 * SharedPreferences (and therefore without Robolectric).
 *
 * Values survive for the lifetime of the instance, so a single instance can be handed to two
 * successive objects to test persistence across "process restarts".
 */
internal class InMemoryLocalStorage : LocalStorage {
    private val storages = mutableMapOf<String, KeyValueStorage>()

    override fun getKeyValueStorageFor(namedContext: String): KeyValueStorage =
        storages.getOrPut(namedContext) { InMemoryKeyValueStorage() }

    private class InMemoryKeyValueStorage : KeyValueStorage {
        private val values = linkedMapOf<String, String>()

        override fun get(key: String): String? = values[key]

        override fun set(key: String, value: String?) {
            if (value == null) {
                values.remove(key)
            } else {
                values[key] = value
            }
        }

        override fun unset(key: String) {
            values.remove(key)
        }

        override val keys: Set<String>
            get() = values.keys
    }
}
