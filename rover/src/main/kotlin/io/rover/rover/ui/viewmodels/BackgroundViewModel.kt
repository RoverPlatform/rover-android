package io.rover.rover.ui.viewmodels

import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.Shader
import android.util.DisplayMetrics
import io.rover.rover.core.domain.Background
import io.rover.rover.core.domain.BackgroundContentMode
import io.rover.rover.core.domain.BackgroundScale
import io.rover.rover.core.domain.Image
import io.rover.rover.core.logging.log
import io.rover.rover.services.assets.AssetService
import io.rover.rover.services.network.NetworkResult
import io.rover.rover.services.network.NetworkTask
import io.rover.rover.ui.types.PixelSize
import io.rover.rover.ui.types.dpAsPx
import io.rover.rover.ui.views.asAndroidColor
import java.net.URI

class BackgroundViewModel(
    private val background: Background,
    private val assetService: AssetService,
    private val borderViewModel: BorderViewModelInterface? = null
) : BackgroundViewModelInterface {
    override val backgroundColor: Int
        get() = background.backgroundColor.asAndroidColor()

    private val urlOptimizationEnabled = true

    override fun requestBackgroundImage(
        targetViewPixelSize: PixelSize,
        displayMetrics: DisplayMetrics,
        callback: (Bitmap, BackgroundImageConfiguration) -> Unit
    ): NetworkTask? {
        val uri = background.backgroundImage?.url
        return if (uri != null) {
            val backgroundImage = background.backgroundImage!!

            val (urlToFetch, imageConfiguration, imageDensity) = if(!urlOptimizationEnabled) {
                localScaleOnlyImageConfiguration(
                    uri,
                    backgroundImage,
                    targetViewPixelSize,
                    displayMetrics
                )
            } else {
                imageConfigurationOptimizedByImgix(
                    uri,
                    backgroundImage,
                    targetViewPixelSize,
                    displayMetrics
                )
            }

            assetService.getImageByUrl(urlToFetch.toURL()) { result ->
                val y = when (result) {
                    is NetworkResult.Success -> {
                        callback(
                            result.response.apply {
                                // this will only have an effect in tiled mode (which is exactly
                                // where we need it), since we always scale to the insets otherwise.
                                density = imageDensity
                            },
                            imageConfiguration
                        )
                    }
                    is NetworkResult.Error -> {
                        // TODO perhaps attempt a retry? or should a lower layer attempt retry?
                        // concern should remain here if the experience UI should react or indicate
                        // an error somehow.
                        log.e("Problem retrieving image: ${result.throwable}")
                    }
                }
            }
        } else {
            // log.v("Null URI.  No image set.")
            null
        }
    }

    private val borderWidth: Int
        get() = borderViewModel?.borderWidth ?: 0

    /**
     * Map the 1X, 2X, and 3X background scale values (which are an iOS convention) to DPI values.
     */
    private val imageDensity = when(background.backgroundScale) {
        BackgroundScale.X1 -> 160
        BackgroundScale.X2 -> 320
        BackgroundScale.X3 -> 480
    }

    @Deprecated("Use Imgix version instead")
    private fun localScaleOnlyImageConfiguration(
        uri: URI,
        backgroundImage: Image,
        targetViewPixelSize: PixelSize,
        displayMetrics: DisplayMetrics
    ): Triple<URI, BackgroundImageConfiguration, Int> {
        val borderWidthPx = borderWidth.dpAsPx(displayMetrics)

        val appleScalingFactor = displayMetrics.densityDpi.toFloat() / imageDensity.toFloat()
        val imageWidthPx = (appleScalingFactor * backgroundImage.width).toInt()
        val imageHeightPx = (appleScalingFactor * backgroundImage.height).toInt()

        val insets = when(background.backgroundContentMode) {
            BackgroundContentMode.Original -> {
                // Note: these may go negative for when the image is bigger than the view (ie.,
                // cropping will be required)
                val heightInset = (targetViewPixelSize.height / 2) - (imageHeightPx / 2)
                val widthInset = targetViewPixelSize.width / 2 - (imageWidthPx / 2)

                Rect(
                    widthInset,
                    heightInset,
                    widthInset,
                    heightInset
                )
            }
            BackgroundContentMode.Tile, BackgroundContentMode.Stretch -> Rect(borderWidthPx, borderWidthPx, borderWidthPx, borderWidthPx)
            BackgroundContentMode.Fit -> {
                val fitScaleFactor = minOf(
                    targetViewPixelSize.width / imageWidthPx.toFloat(),
                    targetViewPixelSize.height / imageHeightPx.toFloat()
                )

                val fitScaledImageWidthPx = imageWidthPx * fitScaleFactor
                val fitScaledImageHeightPx = imageHeightPx * fitScaleFactor

                val heightInset = ((targetViewPixelSize.height / 2) - (fitScaledImageHeightPx / 2)).toInt()
                val widthInset = ((targetViewPixelSize.width / 2) - (fitScaledImageWidthPx / 2)).toInt()

                log.v("fitScaleFactor: $fitScaleFactor fitScaledImageWidthPx: $fitScaledImageHeightPx fitScaledImageHeightPx: $fitScaledImageHeightPx")

                Rect(
                    widthInset + borderWidthPx,
                    heightInset + borderWidthPx,
                    widthInset + borderWidthPx,
                    heightInset + borderWidthPx
                )
            }
            BackgroundContentMode.Fill -> {
                val fitScaleFactor = maxOf(
                    targetViewPixelSize.width / imageWidthPx.toFloat(),
                    targetViewPixelSize.height / imageHeightPx.toFloat()
                )

                val fitScaledImageWidthPx = imageWidthPx * fitScaleFactor
                val fitScaledImageHeightPx = imageHeightPx * fitScaleFactor

                val heightInset = ((targetViewPixelSize.height / 2) - (fitScaledImageHeightPx / 2)).toInt()
                val widthInset = ((targetViewPixelSize.width / 2) - (fitScaledImageWidthPx / 2)).toInt()

                log.v("fitScaleFactor: $fitScaleFactor fitScaledImageWidthPx: $fitScaledImageHeightPx fitScaledImageHeightPx: $fitScaledImageHeightPx")

                Rect(
                    widthInset + borderWidthPx,
                    heightInset + borderWidthPx,
                    widthInset + borderWidthPx,
                    heightInset + borderWidthPx
                )
            }
        }

        val tileMode = when(background.backgroundContentMode) {
            BackgroundContentMode.Tile -> Shader.TileMode.REPEAT
            else -> null
        }

        return Triple(
            uri, BackgroundImageConfiguration(
                insets,
                tileMode
            ),
            if(background.backgroundContentMode == BackgroundContentMode.Tile) imageDensity else displayMetrics.densityDpi
        )
    }

    public fun imageConfigurationOptimizedByImgix(
        uri: URI,
        backgroundImage: Image,
        targetViewPixelSize: PixelSize,
        displayMetrics: DisplayMetrics
    ): Triple<URI, BackgroundImageConfiguration, Int> {
        val borderWidthPx = borderWidth.dpAsPx(displayMetrics)

        val imageDensityScalingFactor = displayMetrics.densityDpi.toFloat() / imageDensity.toFloat()

        val imageWidthPx = (imageDensityScalingFactor * backgroundImage.width).toInt()
        val imageHeightPx = (imageDensityScalingFactor * backgroundImage.height).toInt()

        val (imgixParameters: Map<String, String>, optimizedImageConfiguration, imageDensity) = when(background.backgroundContentMode) {
            BackgroundContentMode.Original -> {
                // Imgix has a mode where I can crop & scale at the same time: apply `rect` and
                // then `w` and `h` after the fact.

                // all we need to do is figure out what area rect (centered) of the image we want to
                // keep.
                val viewPortSizeAsImagePixels = Pair(
                    // TODO: include border width
                    (targetViewPixelSize.width / imageDensityScalingFactor).toInt(),
                    (targetViewPixelSize.height / imageDensityScalingFactor).toInt()
                )


                // TODO: include border width

                val horizontalInsetOffsetScreenPixels = ((targetViewPixelSize.width - imageWidthPx) / 2f).toInt()
                val verticalInsetOffsetScreenPixels = ((targetViewPixelSize.height - imageHeightPx) / 2f).toInt()

                val horizontalInsetImagePixels = maxOf(0, (backgroundImage.width - viewPortSizeAsImagePixels.first) / 2)
                val verticalInsetImagePixels = maxOf(0, (backgroundImage.height - viewPortSizeAsImagePixels.second) / 2)


                val imageCropRect = Rect(
                    horizontalInsetImagePixels,
                    verticalInsetImagePixels,
                    backgroundImage.width - horizontalInsetImagePixels,
                    backgroundImage.height - verticalInsetImagePixels
                )

                val scalingFactor = if(imageDensityScalingFactor > 1) {
                    // image will be scaled up locally using the local insets, so do not apply
                    // any scaling on imgix.
                    1f
                } else {
                    // image will be scaled up using imgix.
                    imageDensityScalingFactor
                }

                Triple(
                    hashMapOf(
                        Pair("rect", "${imageCropRect.left},${imageCropRect.top},${imageCropRect.width()},${imageCropRect.height()}"),
                        Pair("w", minOf(backgroundImage.width, (imageCropRect.width() * scalingFactor).toInt()).toString()),
                        Pair("h", minOf(backgroundImage.height, (imageCropRect.height() * scalingFactor).toInt()).toString())
                    ),

                    BackgroundImageConfiguration(
                        Rect(
                            maxOf(horizontalInsetOffsetScreenPixels, 0),
                            maxOf(verticalInsetOffsetScreenPixels, 0),
                            maxOf(horizontalInsetOffsetScreenPixels, 0),
                            maxOf(verticalInsetOffsetScreenPixels, 0)
                        ),
                        null
                    ),
                    // by using the insets above to scale/crop the image we're implementing the
                    // density scale factor, so for display the density will be ignored (so just
                    // set it to be the display density).
                    displayMetrics.densityDpi
                )
            }
            BackgroundContentMode.Fill -> {
                Triple(
                    hashMapOf(
                        Pair("w", (targetViewPixelSize.width - borderWidthPx * 2).toString()),
                        Pair("h", (targetViewPixelSize.height - borderWidthPx * 2).toString()),
                        Pair("fit", "min")
                    ),
                    BackgroundImageConfiguration(
                        Rect(
                            borderWidthPx,
                            borderWidthPx,
                            borderWidthPx,
                            borderWidthPx
                        ),
                        null
                    ),
                    // by using the insets above to scale/crop the image we're implementing the
                    // density scale factor, so for display the density will be ignored (so just
                    // set it to be the display density).
                    displayMetrics.densityDpi
                )
            }
            BackgroundContentMode.Fit -> {
                val fitScaleFactor = minOf(
                    targetViewPixelSize.width / imageWidthPx.toFloat(),
                    targetViewPixelSize.height / imageHeightPx.toFloat()
                )

                val fitScaledImageWidthPx = imageWidthPx * fitScaleFactor
                val fitScaledImageHeightPx = imageHeightPx * fitScaleFactor

                val heightInset = ((targetViewPixelSize.height / 2) - (fitScaledImageHeightPx / 2)).toInt()
                val widthInset = ((targetViewPixelSize.width / 2) - (fitScaledImageWidthPx / 2)).toInt()

               // log.v("fitScaleFactor: $fitScaleFactor fitScaledImageWidthPx: $fitScaledImageHeightPx fitScaledImageHeightPx: $fitScaledImageHeightPx")

                Triple(
                    hashMapOf(
                        // here we'll ask Imgix to to the equivalent operation as we just did above,
                        // but we'll still do it locally too to produce the appropriate insets,
                        // since Imgix will appropriately not return whitespace and instead just
                        // return a scaled down if necessary aspect correct version of the original
                        // image.
                        Pair("w", (targetViewPixelSize.width - borderWidthPx * 2).toString()),
                        Pair("h", (targetViewPixelSize.height - borderWidthPx * 2).toString()),
                        Pair("fit", "max")
                    ),
                    BackgroundImageConfiguration(
                        Rect(
                            widthInset + borderWidthPx,
                            heightInset + borderWidthPx,
                            widthInset + borderWidthPx,
                            heightInset + borderWidthPx
                        ),
                        null
                    ),
                    // by using the insets above to scale/crop the image we're implementing the
                    // density scale factor, so for display the density will be ignored (so just
                    // set it to be the display density).
                    displayMetrics.densityDpi
                )
            }
            BackgroundContentMode.Stretch -> {
                // Unlike the fit=min/max modes used above, for doing an aspect-incorrect scale
                // imgix does not distinguish between aspect matching and scaling up.  Determine
                // ourselves if it is worth it (ie., that we're not asking Imgix to scale the image
                // up, the exact opposite of our goal)
                val scaleDownTo = minOf(
                    PixelSize(backgroundImage.width, backgroundImage.height),
                    PixelSize(
                        targetViewPixelSize.width - borderWidthPx * 2, targetViewPixelSize.height - borderWidthPx * 2
                    )
                )

                Triple(
                    hashMapOf(
                        Pair("w", scaleDownTo.width.toString()),
                        Pair("h", scaleDownTo.height.toString()),
                        Pair("fit", "scale")
                    ),
                    BackgroundImageConfiguration(
                        Rect(
                            borderWidthPx,
                            borderWidthPx,
                            borderWidthPx,
                            borderWidthPx
                        ),
                        null
                    ),
                    // by using the insets above to scale/crop the image we're implementing the
                    // density scale factor, so for display the density will be ignored (so just
                    // set it to be the display density).
                    displayMetrics.densityDpi
                )
            }
            BackgroundContentMode.Tile -> {
                val imageDensityToDeviceDensityRatio = imageDensity.toFloat() / displayMetrics.densityDpi

                Triple(
                    // so, if we have a hidpi device (xxhdpi or denser) we will only have scaling-up
                    // going on (taken care of because we set the density on the bitmap). However,
                    // if we have a low-dpi device then we have an opportunity to ask imgix to scale
                    // down the image for us and cut down on the amount of pixels downloaded.

                    if(imageDensityToDeviceDensityRatio < 1) {
                        // image density is lower than the screen, so will need to be scaled up
                        // to match the display (which should be done locally)
                        hashMapOf()
                    } else {
                        // image density is higher than the screen, so we can scale it down
                        // cloud-side.
                        hashMapOf(
                            Pair("w", (backgroundImage.width / imageDensityToDeviceDensityRatio).toInt().toString() ),
                            Pair("h", (backgroundImage.height / imageDensityToDeviceDensityRatio).toInt().toString() )
                        )
                    },
                    BackgroundImageConfiguration(
                        Rect(
                            borderWidthPx,
                            borderWidthPx,
                            borderWidthPx,
                            borderWidthPx
                        ),
                        Shader.TileMode.REPEAT
                    ),
                    if(imageDensityToDeviceDensityRatio < 1) {
                        // the density differential needs to scale up the image, so we'll have
                        // Android's UI framework do it for us locally:
                        imageDensity
                    } else {
                        displayMetrics.densityDpi
                    }
                )
            }
        }

        // Parse with java.net.URI and then use URLEncoder to safely encode the params


        val uriWithParameters = setQueryParameters(uri, imgixParameters)

        return Triple(
            URI(uriWithParameters.toString()),
            optimizedImageConfiguration,
            imageDensity
        )
    }

    private fun setQueryParameters(uri: URI, parameters: Map<String, String>): URI {
        return URI(
            uri.scheme,
            uri.authority,
            uri.path,
            parameters.map { (key, value) -> "$key=$value" }.joinToString("&"),
            uri.fragment
        )
    }
}
