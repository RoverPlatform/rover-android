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

package io.rover.sdk.experiences.rich.compose.ui

import android.net.Uri
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.navigation.NavHostController
import io.rover.sdk.core.data.AuthenticationContextInterface
import io.rover.sdk.experiences.data.URLRequest
import io.rover.sdk.experiences.rich.compose.model.nodes.Node
import io.rover.sdk.experiences.rich.compose.model.nodes.Screen
import io.rover.sdk.experiences.rich.compose.model.values.Axis
import io.rover.sdk.experiences.rich.compose.model.values.DocumentFont
import io.rover.sdk.experiences.rich.compose.model.values.ExperienceModel
import io.rover.sdk.experiences.rich.compose.ui.fonts.FontLoader

/**
 * This object contains all the Composition Local properties used within the Compose hierarchy.
 *
 * There are analogous to the SwiftUI environment.
 */
internal object Environment {
    /**
     * Various singletons and services.
     */
    val LocalServices = compositionLocalOf<Services?> { null }

    /**
     * This composition local contains the data loaded by a parent data source, if any.
     */
    val LocalData = compositionLocalOf<Any?> { null }

    /**
     * The URL parameters (if applicable) provided with the URL used to load the experience.
     */
    val LocalUrlParameters = compositionLocalOf<Map<String, String>> { emptyMap() }

    /**
     * A method for retrieving User Info, if one is set.
     */
    val LocalUserInfo = compositionLocalOf<(() -> Map<String, Any>)?> { null }

    /**
     * The device context for the current device.
     */
    val LocalDeviceContext = compositionLocalOf<Map<String, Any>> { emptyMap() }

    /**
     * The remote URL (if applicable) that this experience was loaded from.
     */
    val LocalExperienceUrl = compositionLocalOf<Uri?> { null }

    /**
     * Remote id for the current Experience. Updated when loading a remote Experience if applicable.
     */
    val LocalExperienceId = compositionLocalOf<String?> { null }

    /**
     * Remote name for the current Experience. Updated when loading a remote Experience if applicable.
     */
    val LocalExperienceName = compositionLocalOf<String?> { null }

    /**
     * The remote URL (if applicable) that this experience was loaded from including url parameters.
     */
    val LocalExperienceSourceUrl = compositionLocalOf<Uri?> { null }

    /**
     * The node object from the experience model that this layer is being rendered from.
     *
     * It serves to punch through a few layers to aid in certain cases where heuristics
     * are necessary (such as getting details about the associated node within the Shadow modifier).
     */
    val LocalNode = compositionLocalOf<Node?> { null }

    /**
     * The experience model object that this layer is being rendered from.
     */
    val LocalExperienceModel = compositionLocalOf<ExperienceModel?> { null }

    /**
     * The screen model object that this layer is being rendered from.
     */
    val LocalScreen = compositionLocalOf<Screen?> { null }

    /**
     * Indicates the dark mode appearance setting of the parent screen.
     */
    val LocalIsDarkTheme = compositionLocalOf<Boolean> { false }

    /**
     * Indicates the axis of the nearest parent stack.
     */
    val LocalStackAxis = compositionLocalOf<Axis?> { null }

    /**
     * In cases where a collection can contain multiple carousels, this index provides
     * disambiguation to distinguish the states of the carousels.
     */
    val LocalCollectionIndex = compositionLocalOf { 0 }
    val LocalCarouselStates = mutableStateMapOf<ViewID, CarouselState>()

    /**
     * The state of the nearest parent carousel.
     */
    val LocalCarouselState = compositionLocalOf<CarouselState?> { null }

    /**
     * This local is true when view is within a carousel page that is currently hidden.
     */
    val LocalCarouselInHiddenPage = compositionLocalOf<Boolean> { false }

    /**
     * The index of the page number in the carousel, if applicable.
     */
    val LocalCarouselPageNumber = compositionLocalOf<Int?> { null }

    /**
     * The anonymous function to be called when a [io.rover.sdk.experiences.rich.compose.model.values.Action.PerformSegue] action is activated.
     * Set by [RenderExperience], this lets any child easily access the overarching Experience navigation graph (through [NavHostController]).
     */
    val LocalNavigateToScreen = compositionLocalOf<((String, Any?) -> Unit)?> { null }
    val LocalNavigateUp = compositionLocalOf<(() -> Unit)?> { null }
    val LocalDismissExperience = compositionLocalOf<(() -> Unit)?> { null }

    val LocalAssetContext = compositionLocalOf<AssetContext> { UnpackedTempfilesZipContext }

    val LocalAuthenticationContext = compositionLocalOf<AuthenticationContextInterface?> { null }

    /**
     * Set by [RenderExperience], this allows the registered callback for an authorizer to be used
     * by any Data Sources within the Experience.
     */
    val LocalAuthorizerHandler = compositionLocalOf<AuthorizerHandler?> { null }

    /**
     * Typefaces loaded from font sources.
     */
    val LocalTypefaceMapping = compositionLocalOf<FontLoader.TypeFaceMapping?> { null }

    /**
     * Pre-defined font styles specified in the document.
     */
    val LocalDocumentFonts = compositionLocalOf<List<DocumentFont>> { emptyList() }
}

internal data class ViewID(
    val nodeID: String,
    val collectionIndex: Int
) {
    override fun toString(): String {
        return "$nodeID-$collectionIndex"
    }
}



/**
 * The shape of the custom action handler callback.
 */
internal typealias AuthorizerHandler = suspend (URLRequest) -> Unit
