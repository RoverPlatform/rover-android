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

package io.rover.sdk.experiences.rich.compose.ui.layers

import android.view.ViewGroup
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import io.rover.sdk.core.logging.log
import io.rover.sdk.experiences.rich.compose.model.values.AssetSource
import io.rover.sdk.experiences.rich.compose.model.values.ResizingMode
import io.rover.sdk.experiences.rich.compose.model.values.interpolatedSource
import io.rover.sdk.experiences.rich.compose.ui.AssetContext
import io.rover.sdk.experiences.rich.compose.ui.Environment
import io.rover.sdk.experiences.rich.compose.ui.ViewID
import io.rover.sdk.experiences.rich.compose.ui.data.Interpolator
import io.rover.sdk.experiences.rich.compose.ui.data.makeDataContext
import io.rover.sdk.experiences.rich.compose.ui.modifiers.LayerModifiers
import kotlinx.coroutines.launch

/**
 * @param measurePolicy Specify the sizing behaviour for this media player. Note that this measure
 * policy needs to support Rover's packed intrinsics.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
internal fun MediaPlayer(
        source: AssetSource,
        looping: Boolean,
        autoPlay: Boolean,
        showControls: Boolean,
        viewID: ViewID,
        resizingMode: ResizingMode? = null,
        removeAudio: Boolean = false,
        posterImageURL: String? = null,
        timeoutControls: Boolean = true,
        modifier: Modifier = Modifier,
        layerModifiers: LayerModifiers = LayerModifiers(),
        measurePolicy: MeasurePolicy,
) {
    val context = LocalContext.current

    val dataContext = makeDataContext(
        userInfo = Environment.LocalUserInfo.current?.invoke() ?: emptyMap(),
        urlParameters = Environment.LocalUrlParameters.current,
        deviceContext = Environment.LocalDeviceContext.current,
        data = Environment.LocalData.current,
    )
    val interpolator = Interpolator(
        dataContext,
    )

    val interpolatedSource = source.interpolatedSource(interpolator)

    interpolatedSource?.let { assetSource ->
        val mediaUri: String = when (assetSource) {
            is AssetSource.FromFile -> {
                Environment.LocalAssetContext.current.uriForFileSource(LocalContext.current, AssetContext.AssetType.MEDIA, assetSource).toString()
            }
            is AssetSource.FromURL -> {
                assetSource.url
            }
        }

        val exoPlayer = remember {
            ExoPlayer.Builder(context).build().apply {
                repeatMode = when (looping) {
                    true -> Player.REPEAT_MODE_ALL
                    false -> Player.REPEAT_MODE_OFF
                }

                if (removeAudio) {
                    volume = 0f
                }

                addMediaItem(MediaItem.fromUri(mediaUri))

                prepare()
            }
        }

        val carouselState = Environment.LocalCarouselState.current

        var playing by remember { mutableStateOf(false) }
        DisposableEffect(Unit) {
            val listener = object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    playing = isPlaying
                }
            }
            exoPlayer.addListener(listener)
            onDispose {
                exoPlayer.removeListener(listener)
            }
        }

        val inHiddenPage = Environment.LocalCarouselInHiddenPage.current

        val carouselPage = Environment.LocalCarouselPageNumber.current

        LaunchedEffect(playing, carouselState?.pagerState?.currentPage) {
            val page = carouselState?.pagerState?.currentPage
            if (playing) {
                while (carouselState?.pagerState?.currentPage == page) {
                    val position = exoPlayer.currentPosition / 1000f
                    val duration = exoPlayer.duration / 1000f

                    val progress = position / duration

                    carouselPage?.let { carouselPage ->
                        carouselState?.updateProgress(carouselPage, progress)
                    }

                    // update every 100ms
                    kotlinx.coroutines.delay(100)
                }
            }
        }

        DisposableEffect(
            // exoplayer will never change, but we need to release it when composition is
            // disposed.
            exoPlayer
        ) {
            onDispose {
                exoPlayer.release()
            }
        }

        ApplyLayerModifiers(layerModifiers, modifier) { modifier ->
            Layout(
                {
                    val coroutineScope = rememberCoroutineScope()

                    AndroidView(factory = {
                        PlayerView(context).apply {
                            player = exoPlayer

                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )

                            resizeMode = if (resizingMode == ResizingMode.SCALE_TO_FIT) {
                                AspectRatioFrameLayout.RESIZE_MODE_FIT
                            } else {
                                AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            }

                            if (!timeoutControls) {
                                controllerShowTimeoutMs = -1
                            }

                            useController = showControls

                            useArtwork = false

                            this.keepScreenOn = true

                            exoPlayer.addListener(object : Player.Listener {
                                override fun onPlaybackStateChanged(playbackState: Int) {
                                    if (playbackState == Player.STATE_ENDED) {
                                        coroutineScope.launch {
                                            carouselState?.animateAutoAdvance()
                                        }
                                    }
                                }
                            })
                        }
                    })

                    // inform any parent carousel of a media player being active
                    // in the current page
                    DisposableEffect(inHiddenPage) {
                        // becoming invisible
                        if (inHiddenPage) {
                            carouselState?.mediaLayerDisappeared(viewID)
                        } else {
                            carouselState?.mediaLayerAppeared(viewID)
                        }

                        // when an autoplay video first appears (and is (no)longer in a hidden carousel
                        // page, if applicable) it should start at the beginning.
                        if (!inHiddenPage && autoPlay) {
                            carouselPage?.let { carouselPage ->
                                carouselState?.updateProgress(carouselPage, 0f)
                            }
                            exoPlayer.seekTo(0)
                        }
                        
                        onDispose {
                            // in case we haven't yet been evaluated with inHiddenPage == true
                            // before being disposed, handle that boundary case
                           carouselState?.mediaLayerDisappeared(viewID)
                        }
                    }

                    LifecycleResumeEffect(inHiddenPage) {
                        if (inHiddenPage) {
                            exoPlayer.pause()
                        }

                        if (!inHiddenPage && autoPlay) {
                            exoPlayer.play()
                        }

                        onPauseOrDispose {
                            exoPlayer.pause()
                        }
                    }
                },
                measurePolicy = measurePolicy,
                modifier = modifier,
            )
        }
    }
}

