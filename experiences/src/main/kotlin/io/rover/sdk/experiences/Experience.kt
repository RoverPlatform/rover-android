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

package io.rover.sdk.experiences

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.rover.experiences.R
import io.rover.sdk.core.Rover
import io.rover.sdk.core.data.domain.ClassicExperienceModel
import io.rover.sdk.core.data.graphql.operations.data.decodeJson
import io.rover.sdk.core.events.UserInfoInterface
import io.rover.sdk.core.logging.log
import io.rover.sdk.experiences.ExperienceFetchViewModel.State
import io.rover.sdk.experiences.classic.ClassicExperience
import io.rover.sdk.experiences.data.http.RoverExperiencesWebService
import io.rover.sdk.experiences.rich.compose.data.JsonParser
import io.rover.sdk.experiences.rich.compose.model.values.ExperienceModel
import io.rover.sdk.experiences.rich.compose.ui.*
import io.rover.sdk.experiences.rich.compose.ui.fonts.FontLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Fetch & render a Rover Experience (either classic or new).
 *
 * @param onCanceled If this closure is supplied, in the event of a loading error this composable
 * will use a modal dialog prompt to ask the user if they want to cancel, and if they select yes,
 * this closure will be called.
 *
 * If [onCanceled] is not supplied, in the event of a loading error this composable will not offer
 * a cancel option, and instead just render an error message in-line in the place of the experience
 * content in lieu of an interactive prompt.
 *
 * Note: [onCanceled] exists in order to provide a way to handle the embedding vs non-embedding
 * use case.  Full-screen experiences, in the event user can not successfully retry, ought
 * to be resignable, whereas embedded experiences should likely not prevent
 * the surrounding UI from being usable.
 */
@Composable
fun Experience(
    url: Uri,
    onCanceled: (() -> Unit)? = null
) {
    Services.Inject { services ->
        // security check, verify that URL domain is what is configured in Rover (to prevent
        //  hostile deep link injection).

        if (!services.rover.associatedDomains.contains(url.host?.lowercase())) {
            Text("URL host is not associated with Rover.")
            return@Inject
        }

        val viewModel = viewModel<ExperienceFetchViewModel>()

        val context = LocalContext.current

        SideEffect {
            viewModel.start(
                context,
                services.webService,
                services.fontLoader
            )
        }

        LaunchedEffect(url) {
            viewModel.request(url)
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colors.background
        ) {
            when (val state = viewModel.state.collectAsState().value) {
                is State.Failed -> {
                    if (onCanceled == null) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(stringResource(id = R.string.rover_experiences_fetch_failure))
                            TextButton(onClick = {
                                viewModel.request(url)
                            }) {
                                Text(stringResource(id = R.string.rover_experiences_retry))
                            }
                        }
                    } else {
                        // if being used in full screen (such as in ExperienceActivity), instead
                        // use a full dialog box.
                        AlertDialog(
                            onDismissRequest = {
                                onCanceled()
                            },
                            text = {
                                Text(stringResource(R.string.rover_experiences_fetch_failure))
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    viewModel.request(url)
                                }) {
                                    Text(stringResource(R.string.rover_experiences_retry))
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = {
                                    onCanceled()
                                }) {
                                    Text(stringResource(id = R.string.rover_experiences_cancel))
                                }
                            }
                        )
                    }
                }
                is State.Success -> {
                    when (state.experience) {
                        is LoadedExperience.Standard -> {
                            NetworkExperience(
                                experience = state.experience.experience,
                                assetContext = state.experience.assetContext,
                                url = url,
                                typeFaceMapping = state.experience.typeFaceMapping,
                                experienceId = state.experience.experienceId,
                                experienceName = state.experience.experienceName,
                                urlParams = state.experience.urlParams
                            )
                        }
                        is LoadedExperience.Classic -> {
                            ClassicExperience(
                                classicExperience = state.experience.experience,
                                url = url,
                                urlParams = state.experience.urlParams
                            )
                        }
                    }
                }
                else -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

internal class ExperienceFetchViewModel() : ViewModel() {

    internal sealed class State {
        object Init : State()

        object Loading : State()

        data class Success(val experience: LoadedExperience) : State()

        data class Failed(val reason: Throwable) : State()
    }

    private val _state = MutableStateFlow<State>(State.Init)
    val state: StateFlow<State> = _state

    sealed class Command {
        object NoneYet : Command()
        class LoadExperience(val uri: Uri) : Command()
    }

    private val commands = MutableStateFlow<Command>(Command.NoneYet)

    fun start(
        context: Context,
        webService: RoverExperiencesWebService,
        fontLoader: FontLoader
    ) {
        // set up command processing pipeline. can handle cancellation, etc.
        viewModelScope.launch {
            commands
                .filterIsInstance<Command.LoadExperience>()
                // mapLatest allows us to cancel any in-flight request if new load command is issued
                .collectLatest { loadExperienceCommand ->
                    _state.emit(State.Loading)

                    // replace any non-HTTP scheme with HTTPS, to support deep linking setups.
                    val url = loadExperienceCommand.uri.buildUpon().scheme(
                        "https"
                    ).build()

                    val documentJsonUrl = url.buildUpon().apply {
                        appendPath("document.json")
                    }

                    // the URL params from the query string:
                    val urlParamsFromQueryString = url.queryParameterNames.mapNotNull { key ->
                        url.getQueryParameter(key)?.let { Pair(key, it) }
                    }.associate { it }

                    try {
                        val experienceResponse = webService.fetchData(documentJsonUrl.toString())

                        if (experienceResponse.isSuccessful) {
                            val data = withContext(Dispatchers.IO) {
                                val byteResult = kotlin.runCatching {
                                    experienceResponse.body()?.string()
                                }

                                if (byteResult.isSuccess) {
                                    return@withContext byteResult.getOrNull()
                                } else {
                                    log.w("Experience read error: ${byteResult.exceptionOrNull()}")
                                    _state.emit(State.Failed(byteResult.exceptionOrNull() ?: Exception("Unknown error")))
                                }
                            }

                            // now, check Rover-Experience-Version header:
                            val experienceVersion = experienceResponse.headers()["Rover-Experience-Version"]

                            // Get URL Param default values also provided as a header:
                            val defaultUrlParamValues = experienceResponse.headers()["Rover-Experience-Parameters"]

                            // now parse those as a query string into a map, misusing android's Uri class to do so:
                            val defaultUrlParamValuesMap = defaultUrlParamValues?.let { paramsString ->
                                val dummyUri = Uri.parse("https://DUMMY?$paramsString")

                                dummyUri.queryParameterNames.mapNotNull { key ->
                                    dummyUri.getQueryParameter(key)?.let { Pair(key, it) }
                                }.associate { it }
                            } ?: emptyMap()

                            log.v("URL parameters from URL query string: $urlParamsFromQueryString")
                            log.v("URL parameters provided from server: $defaultUrlParamValuesMap")

                            // merge the server defaults with those provided by the query string:

                            // now we finally have the URL params with defaults applied, to be
                            // passed to the two renderers.
                            val urlParams = defaultUrlParamValuesMap + urlParamsFromQueryString

                            log.i("Final URL parameters: $urlParams")

                            when (experienceVersion ?: "2") {
                                "1" -> {
                                    // classic experience
                                    val jsonObject = JSONObject(data.toString())

                                    val exp = ClassicExperienceModel.decodeJson(
                                        jsonObject
                                    )

                                    // decode data to classic experience model.
                                    _state.emit(
                                        State.Success(
                                            LoadedExperience.Classic(
                                                exp,
                                                urlParams
                                            )
                                        )
                                    )
                                }
                                "2" -> {
                                    // new experience.

                                    // decode data to experience model using JsonParser
                                    val experience = JsonParser.parseExperience(data.toString())
                                    if (experience == null) {
                                        log.w("Experience parsed to null")
                                        return@collectLatest
                                    }

                                    // get the ID and Name that were provided as headers
                                    val experienceId = experienceResponse.headers()["Rover-Experience-Id"]
                                    val experienceName = experienceResponse.headers()["Rover-Experience-Name"]

                                    // now, attempt to fetch the CDN configuration needed by
                                    // v2 (non-classic) experiences.
                                    val cdnConfigUrl = url.buildUpon().apply {
                                        path("configuration.json")
                                    }.build()

                                    val cdnConfig = webService.getConfiguration(cdnConfigUrl.toString())

                                    val assetContext = RemoteAssetContext(
                                        url,
                                        cdnConfig
                                    )

                                    val fontSources = experience.fonts.flatMap { font ->
                                        font.sources.apply {
                                            if (this == null) log.w("This file saved before Judo 1.11, custom fonts will not work.")
                                        } ?: emptyList()
                                    }

                                    val typefaceMapping = fontLoader.getTypefaceMappings(
                                        context,
                                        assetContext,
                                        fontSources
                                    )

                                    experience.buildTreeAndRelationships()

                                    _state.emit(
                                        State.Success(
                                            LoadedExperience.Standard(
                                                experience,
                                                assetContext,
                                                typefaceMapping,
                                                experienceId,
                                                experienceName,
                                                urlParams
                                            )
                                        )
                                    )
                                }
                                else -> {
                                    // unknown experience version
                                    log.w("Unknown experience version: $experienceVersion")
                                    _state.emit(State.Failed(Exception("Unknown experience version: $experienceVersion")))
                                }
                            }
                        } else {
                            log.w("Experience fetch failed: ${experienceResponse.code()}")
                            _state.emit(State.Failed(Exception("Experience fetch failed: ${experienceResponse.code()}")))
                        }
                    } catch (e: Exception) {
                        log.w("Experience fetch error: $e, at ${e.stackTraceToString()}")
                        _state.emit(State.Failed(e))
                    }
                }
        }
    }

    fun request(url: Uri) {
        commands.value = Command.LoadExperience(url)
    }
}

internal sealed class LoadedExperience() {
    data class Classic(
        val experience: ClassicExperienceModel,
        val urlParams: Map<String, String>
    ) : LoadedExperience()
    data class Standard(
        val experience: ExperienceModel,
        val assetContext: AssetContext,
        val typeFaceMapping: FontLoader.TypeFaceMapping,
        val experienceId: String?,
        val experienceName: String?,
        val urlParams: Map<String, String>
    ) : LoadedExperience()
}

/**
 * Render an already-downloaded experience.
 */
@Composable
private fun NetworkExperience(
    url: Uri,
    experience: ExperienceModel,
    assetContext: AssetContext,
    typeFaceMapping: FontLoader.TypeFaceMapping,
    /**
     * The ID of the experience, if provided by the server.
     */
    experienceId: String?,
    /**
     * The name of the experience, if provided by the server.
     */
    experienceName: String?,
    /**
     * URL parameters.
     */
    urlParams: Map<String, String>
) {
    Services.Inject { services ->
        // TODO: use remember on all these to avoid recomputation:

        fun readUserInfo(): Map<String, Any> = Rover.shared.resolve(
            UserInfoInterface::class.java
        )?.currentUserInfo ?: emptyMap()

        CompositionLocalProvider(
            Environment.LocalAssetContext provides assetContext,
            Environment.LocalExperienceUrl provides url,
            // default URL params provided by the experience model are used in lieu of values
            // coming from URI or server:
            Environment.LocalUrlParameters provides experience.urlParameters + urlParams,
            Environment.LocalUserInfo provides { readUserInfo() },
            Environment.LocalTypefaceMapping provides typeFaceMapping,
            Environment.LocalExperienceId provides experienceId,
            Environment.LocalExperienceName provides experienceName,
            Environment.LocalAuthorizerHandler provides {
                // use authorizers registered with the Rover singleton.
                services.authorizers.authorize(it)
            }
        ) {
            RenderExperience(
                experience = experience
            )
        }
    }
}
