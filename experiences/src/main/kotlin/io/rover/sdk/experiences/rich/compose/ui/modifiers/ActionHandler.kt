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

package io.rover.sdk.experiences.rich.compose.ui.modifiers

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import io.rover.sdk.core.Rover
import io.rover.sdk.experiences.rich.compose.model.values.Action
import io.rover.sdk.experiences.rich.compose.ui.Environment
import io.rover.sdk.experiences.rich.compose.ui.Services
import io.rover.sdk.experiences.rich.compose.ui.data.Interpolator
import io.rover.sdk.experiences.rich.compose.ui.data.makeDataContext
import io.rover.sdk.experiences.services.ButtonTapped
import io.rover.sdk.experiences.services.CustomActionActivated
import io.rover.sdk.experiences.services.InterpolatedConversionsTrackerService

/**
 * Creates a click handler for a Rover action.
 * 
 * This shared handler extracts the common logic for executing actions and tracking events,
 * used by both ActionModifier and Material3 icon buttons.
 * 
 * @param action The action to execute, or null for no action
 * @return A click handler lambda, or null if action is null
 */
@Composable
internal fun createActionHandler(action: Action?): (() -> Unit)? {
    if (action == null) return null
    
    val tag = "RoverExperiences.ActionHandler"
    
    // Build data context and interpolator
    val dataContext = makeDataContext(
        userInfo = Environment.LocalUserInfo.current?.invoke() ?: emptyMap(),
        urlParameters = Environment.LocalUrlParameters.current,
        deviceContext = Environment.LocalDeviceContext.current,
        data = Environment.LocalData.current
    )
    val interpolator = Interpolator(dataContext)
    
    // Capture environment locals
    val node = Environment.LocalNode.current
    val screen = Environment.LocalScreen.current
    val urlParameters = Environment.LocalUrlParameters.current
    val experienceId = Environment.LocalExperienceId.current
    val experienceName = Environment.LocalExperienceName.current
    val experienceUrl = Environment.LocalExperienceUrl.current
    val localData = Environment.LocalData.current
    val localActivity = LocalContext.current as? Activity
    val experience = Environment.LocalExperienceModel.current
    val campaignId = urlParameters["campaignID"]
    
    // Capture action-specific navigation/dismiss functions
    val navigateFunction = Environment.LocalNavigateToScreen.current
    val dismissExperienceFunction = Environment.LocalDismissExperience.current
    val containingContext = LocalContext.current
    
    // Get services - this will be captured in the lambda
    val services = Environment.LocalServices.current

    if (services == null) {
        Log.e("createActionHandler", "Missing composition locals")
        return null
    }
    
    // Event tracking function
    fun fireEvent() {
        if (experience == null || screen == null || node == null) {
            Log.w(tag, "Button tapped with environment locals missing. Skipping event send.")
            return
        }
        
        val conversionTrackerService = Rover.shared.resolve(InterpolatedConversionsTrackerService::class.java)
        conversionTrackerService?.trackConversions(action.conversionTags, dataContext)
        
        services.eventEmitter.emit(
            ButtonTapped(
                experienceName = experienceName,
                experienceId = experienceId,
                experienceUrl = experienceUrl,
                screenName = screen.name,
                screenId = screen.id,
                screenTags = screen.metadata?.tags?.toList() ?: emptyList(),
                screenProperties = screen.metadata?.propertiesMap ?: emptyMap(),
                data = localData,
                urlParameters = urlParameters,
                campaignId = campaignId,
                nodeName = node.name,
                nodeId = node.id,
                nodeTags = node.metadata?.tags?.toList() ?: emptyList(),
                nodeProperties = node.metadata?.propertiesMap ?: emptyMap(),
            )
        )
    }
    
    // Return the appropriate click handler based on action type
    return when (action) {
        is Action.PerformSegue -> {
            {
                fireEvent()
                when (val screenId = action.screenID) {
                    is String -> navigateFunction?.invoke(screenId, localData)
                    null -> Log.w(tag, "Tried to do $action without a screen ID. Ignoring!")
                }
            }
        }
        
        is Action.OpenURL -> {
            {
                fireEvent()
                interpolator.interpolate(action.url)?.let { url ->
                    val intent = services.rover.intentForLink(Uri.parse(url)) ?: Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(url)
                    )
                    
                    try {
                        containingContext.startActivity(intent)
                    } catch (e: ActivityNotFoundException) {
                        Log.w(tag, "Activity not found to handle URI: $url")
                    }
                    
                    if (action.dismissExperience) {
                        dismissExperienceFunction?.invoke()
                    }
                }
            }
        }
        
        is Action.PresentWebsite -> {
            {
                fireEvent()
                interpolator.interpolate(action.url)?.let { url ->
                    val customTabIntent = CustomTabsIntent.Builder().build()
                    try {
                        customTabIntent.launchUrl(containingContext, Uri.parse(url))
                    } catch (e: ActivityNotFoundException) {
                        Log.w(tag, "Unable to present web URL (either bad scheme or missing browser): $url")
                    }
                }
            }
        }
        
        is Action.Close -> {
            {
                fireEvent()
                dismissExperienceFunction?.invoke()
            }
        }
        
        is Action.Custom -> {
            {
                fireEvent()
                
                if (action.dismissExperience) {
                    dismissExperienceFunction?.invoke()
                }
                
                if (screen != null && node != null) {
                    services.eventEmitter.emit(
                        CustomActionActivated(
                            experienceName = experienceName,
                            experienceId = experienceId,
                            experienceUrl = experienceUrl,
                            screenName = screen.name,
                            screenId = screen.id,
                            screenTags = screen.metadata?.tags?.toList() ?: emptyList(),
                            screenProperties = screen.metadata?.propertiesMap ?: emptyMap(),
                            data = localData,
                            urlParameters = urlParameters,
                            campaignId = campaignId,
                            nodeName = node.name,
                            nodeId = node.id,
                            nodeTags = node.metadata?.tags?.toList() ?: emptyList(),
                            nodeProperties = node.metadata?.propertiesMap ?: emptyMap(),
                            activity = localActivity
                        )
                    )
                } else {
                    Log.w(tag, "Custom action activated with LocalScreen or LocalNode environment local missing. Skipping event send.")
                }
            }
        }
    }
}
