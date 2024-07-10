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

package io.rover.sdk.adobeExperience

/**
 * An API to set and clear Adobe Experience Platform credentials on Rover.
 */
interface AdobeExperienceAuthorizer {
    /**
     * Set the user's Adobe Experience Platform's Experience Cloud ID (ECID).

     * @param ECID The value of the `ecid`.
     *
     * @see See https://developer.adobe.com/client-sdks/home/base/mobile-core/identity/ for details on the Experience Cloud ID (ECID)
     */
    fun setECID(ecid: String)

    /**
     * Clear the user's Adobe Experience credentials after a successful sign-out.
     */
    fun clearCredentials()
}
