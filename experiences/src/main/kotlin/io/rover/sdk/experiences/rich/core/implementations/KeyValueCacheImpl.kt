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

package io.rover.sdk.experiences.rich.core.implementations

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import io.rover.sdk.experiences.rich.core.cache.KeyValueCache

internal class KeyValueCacheImpl(
    private val context: Context
) : KeyValueCache {

    private val sharedPreferences: SharedPreferences by lazy {
        context.getSharedPreferences(
            PREFERENCES_NAME,
            MODE_PRIVATE
        )
    }

    override fun putString(keyValuePair: Pair<String, String>): Boolean {
        return sharedPreferences.edit().apply {
            putString(keyValuePair.first, keyValuePair.second)
        }.commit()
    }

    override fun retrieveString(key: String): String? {
        return sharedPreferences.getString(key, null)
    }

    override fun remove(key: String): Boolean {
        return sharedPreferences.edit().remove(key).commit()
    }

    companion object {
        private const val PREFERENCES_NAME: String = "rover-experiences-preferences"
    }
}
