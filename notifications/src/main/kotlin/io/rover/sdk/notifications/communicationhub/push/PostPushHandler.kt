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

package io.rover.sdk.notifications.communicationhub.push

import com.squareup.moshi.Moshi
import com.squareup.moshi.adapters.Rfc3339DateJsonAdapter
import io.rover.sdk.core.logging.log
import io.rover.sdk.notifications.communicationhub.data.dto.PostItem
import io.rover.sdk.notifications.communicationhub.data.repository.RoverEngageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONException
import org.json.JSONObject
import java.net.MalformedURLException
import java.util.Date

/**
 * Handles Rover Post push notifications by parsing post data and saving to repository.
 * Also creates standard Android notifications for display.
 */
internal class PostPushHandler(
    private val repository: RoverEngageRepository,
    private val coroutineScope: CoroutineScope
) {
    
    private val moshi = Moshi.Builder()
        .add(Date::class.java, Rfc3339DateJsonAdapter())
        .build()
        
    private val postItemAdapter = moshi.adapter(PostItem::class.java)
    
    /**
     * Process a Post push notification.
     */
    fun handleCommunicationHubPush(roverJson: String) {
        try {
            val jsonObject = JSONObject(roverJson)
            val postData = jsonObject.getJSONObject("post")

            // Parse post data
            val postItem = postItemAdapter.fromJson(postData.toString())
                ?: throw IllegalArgumentException("Failed to parse post data")

            // Save to repository asynchronously
            coroutineScope.launch {
                try {
                    repository.savePostFromPush(postItem)
                    log.v("Post saved from push: ${postItem.id}")
                } catch (e: Exception) {
                    log.e("Failed to save post from push: ${e.message}")
                }
            }

        } catch (e: JSONException) {
            log.w("Invalid Post push notification received: '${e.message}'")
            log.w("... contents were: $roverJson")
        } catch (e: MalformedURLException) {
            log.w("Invalid Post push notification URL: '${e.message}'")
            log.w("... contents were: $roverJson")
        } catch (e: Exception) {
            log.e("Error processing Post push notification: ${e.message}")
        }
    }
}