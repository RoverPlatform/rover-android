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

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.rover.sdk.experiences.data.URLRequest
import io.rover.sdk.experiences.rich.compose.model.fromZipStream
import io.rover.sdk.experiences.rich.compose.model.values.ExperienceModel
import io.rover.sdk.experiences.rich.compose.ui.fonts.FontLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/**
 * Retrieve an experience from a ZIP archive given at URL. Does not work with classic experiences.
 *
 * @param userInfo A closure that provides a hashmap of data to be available under `user.` in the
 *                 experience. If null, defaults to the values provided within the Experience.
 */
@Composable
fun FileExperience(
    fileUrl: Uri,
    modifier: Modifier = Modifier,
    userInfo: (() -> Map<String, Any>)? = null
) {
    val viewModel = viewModel<LoadExperienceViewModel>()
    val context = LocalContext.current

    Services.Inject { services ->
        if (viewModel.experience == null) {
            LaunchedEffect(true) {
                viewModel.loadExperience(context, services.fontLoader, fileUrl)
            }
        }

        CompositionLocalProvider(
            Environment.LocalAssetContext provides UnpackedTempfilesZipContext
        ) {
            viewModel.experience?.let { experienceModel ->

                val authorize = { urlRequest: URLRequest ->
                    experienceModel.authorizers.forEach { authorizer ->
                        authorizer.authorize(urlRequest)
                    }
                }

                CompositionLocalProvider(
                    Environment.LocalTypefaceMapping provides viewModel.typefaceMapping,
                    Environment.LocalUserInfo provides userInfo,

                    // Experience files have design-time/default values for url params,
                    // authorizers, and user info:

                    Environment.LocalUrlParameters provides experienceModel.urlParameters,
                    Environment.LocalAuthorizerHandler provides authorize,
                    Environment.LocalUserInfo provides { experienceModel.userInfo }
                ) {
                    RenderExperience(experienceModel, modifier = modifier)
                }
            }
        }
    }
}

/**
 * Load an experience from a Experiences file ZIP container in memory.
 *
 * @param userInfo A closure that provides a hashmap of data to be available under `user.` in the
 *                 experience. If null, defaults to the values provided within the Experience.
 */
@Composable
fun FileExperience(
    data: ByteArray,
    modifier: Modifier = Modifier,
    userInfo: (() -> Map<String, Any>)? = null
) {
    // Display an Experience from the given ZIP archive that is already loaded into memory
    // in the given Data byte buffer.
    val viewModel = viewModel<LoadExperienceViewModel>()
    val context = LocalContext.current

    Services.Inject { services ->
        if (viewModel.experience == null) {
            LaunchedEffect(true) {
                viewModel.loadExperience(context, services.fontLoader, data)
            }
        }

        CompositionLocalProvider(Environment.LocalAssetContext provides UnpackedTempfilesZipContext) {
            viewModel.experience?.let { experienceModel ->
                CompositionLocalProvider(
                    Environment.LocalTypefaceMapping provides viewModel.typefaceMapping,
                    Environment.LocalUserInfo provides userInfo,
                    // provide the in-file URL parameters because query parameters can't be specified when loading from a bytearray
                    Environment.LocalUrlParameters provides experienceModel.urlParameters
                ) {
                    RenderExperience(experienceModel, modifier = modifier)
                }
            }
        }
    }
}

/**
 * This view model is responsible for loading an Experience document ZIP container from a local
 * source.
 */
internal class LoadExperienceViewModel : ViewModel() {
    val tag = "LoadExperienceViewModel"
    var experience: ExperienceModel? by mutableStateOf(null)
    var typefaceMapping: FontLoader.TypeFaceMapping? by mutableStateOf(null)

    fun loadExperience(
        context: Context,
        fontLoader: FontLoader,
        zipFileUri: Uri
    ) {
        viewModelScope.launch(context = Dispatchers.IO) {
            ZipInputStream(context.contentResolver.openInputStream(zipFileUri)).use { zipInputStream ->
                try {
                    experience = ExperienceModel.fromZipStream(context, zipInputStream)
                    val fontSources = experience!!.fonts.flatMap { font ->
                        font.sources.apply {
                            if (this == null) Log.w(tag, "This file saved before Judo 1.11, custom fonts will not work.")
                        } ?: emptyList()
                    }

                    typefaceMapping = fontLoader.getTypefaceMappings(
                        context,
                        UnpackedTempfilesZipContext,
                        fontSources
                    )
                } catch (exception: Exception) {
                    Log.e(tag, "Unable to load experience: $exception")
                }
            }
        }
    }

    fun loadExperience(context: Context, fontLoader: FontLoader, zipFileData: ByteArray) {
        viewModelScope.launch(context = Dispatchers.IO) {
            ZipInputStream(ByteArrayInputStream(zipFileData)).use { zipInputStream ->
                try {
                    experience = ExperienceModel.fromZipStream(context, zipInputStream)

                    val fontSources = experience!!.fonts.flatMap { font ->
                        font.sources.apply {
                            if (this == null) Log.w(tag, "This file saved before Judo 1.11, custom fonts will not work.")
                        } ?: emptyList()
                    }

                    typefaceMapping = fontLoader.getTypefaceMappings(
                        context,
                        UnpackedTempfilesZipContext,
                        fontSources
                    )
                } catch (exception: Exception) {
                    Log.e(tag, "Unable to load experience: $exception")
                }
            }
        }
    }
}
