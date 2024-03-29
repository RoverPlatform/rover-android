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

package io.rover.sdk.experiences.classic.assets

import android.graphics.Shader
import io.rover.sdk.core.data.domain.Background
import io.rover.sdk.core.data.domain.BackgroundContentMode
import io.rover.sdk.core.data.domain.BackgroundScale
import io.rover.sdk.core.data.domain.Image
import io.rover.sdk.core.logging.log
import io.rover.sdk.experiences.classic.BackgroundImageConfiguration
import io.rover.sdk.experiences.classic.PixelSize
import io.rover.sdk.experiences.classic.Rect
import io.rover.sdk.experiences.classic.dpAsPx
import java.net.URI
import kotlin.math.roundToInt

internal class ImageOptimizationService {

    private val urlOptimizationEnabled = true

    /**
     * Take a given background and return a URI and image configuration that may be used to display
     * it efficiently.  It may perform transforms on the URI and background image configuration to
     * cut down retrieving and decoding an unnecessary larger image than needed for the context.
     *
     * Note that this does not actually perform any sort optimization operation locally.
     *
     * @return The optimized image configuration, which includes the URI with optimization
     * parameters.  May be null if the background in question has no image.
     */
    fun optimizeImageBackground(background: Background, targetViewPixelSize: PixelSize, density: Float): OptimizedImage? {
        return if (urlOptimizationEnabled) {
            imageConfigurationOptimizedByImgix(background, targetViewPixelSize, density)
        } else {
            localScaleOnlyImageConfiguration(background, targetViewPixelSize, density)
        }
    }

    private fun localScaleOnlyImageConfiguration(
        background: Background,
        targetViewPixelSize: PixelSize,
        density: Float
    ): OptimizedImage? {
        val imageDensity = imageDensity(background)
        val backgroundImage = background.image ?: return null
        val uri = background.image!!.url
        val appleScalingFactor = densityAsDpi(density) / imageDensity.toFloat()
        val imageWidthPx = (appleScalingFactor * backgroundImage.width).toInt()
        val imageHeightPx = (appleScalingFactor * backgroundImage.height).toInt()

        val insets = when (background.contentMode) {
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
            BackgroundContentMode.Tile, BackgroundContentMode.Stretch -> Rect(0, 0, 0, 0)
            BackgroundContentMode.Fit -> {
                val fitScaleFactor = minOf(
                    (targetViewPixelSize.width) / imageWidthPx.toFloat(),
                    (targetViewPixelSize.height) / imageHeightPx.toFloat()
                )

                val fitScaledImageWidthPx = imageWidthPx * fitScaleFactor
                val fitScaledImageHeightPx = imageHeightPx * fitScaleFactor

                val widthInset = ((targetViewPixelSize.width / 2) - (fitScaledImageWidthPx / 2)).toInt()
                val heightInset = ((targetViewPixelSize.height / 2) - (fitScaledImageHeightPx / 2)).toInt()

                log.v("fitScaleFactor: $fitScaleFactor fitScaledImageWidthPx: $fitScaledImageHeightPx fitScaledImageHeightPx: $fitScaledImageHeightPx")

                Rect(
                    widthInset,
                    heightInset,
                    widthInset,
                    heightInset
                )
            }
            BackgroundContentMode.Fill -> {
                val fitScaleFactor = maxOf(
                    targetViewPixelSize.width / imageWidthPx.toFloat(),
                    targetViewPixelSize.height / imageHeightPx.toFloat()
                )

                val fitScaledImageWidthPx = imageWidthPx * fitScaleFactor
                val fitScaledImageHeightPx = imageHeightPx * fitScaleFactor

                val widthInset = ((targetViewPixelSize.width / 2) - (fitScaledImageWidthPx / 2)).toInt()
                val heightInset = ((targetViewPixelSize.height / 2) - (fitScaledImageHeightPx / 2)).toInt()

                log.v("fitScaleFactor: $fitScaleFactor fitScaledImageWidthPx: $fitScaledImageHeightPx fitScaledImageHeightPx: $fitScaledImageHeightPx")

                Rect(
                    widthInset,
                    heightInset,
                    widthInset,
                    heightInset
                )
            }
        }

        val tileMode = when (background.contentMode) {
            BackgroundContentMode.Tile -> Shader.TileMode.REPEAT
            else -> null
        }

        return OptimizedImage(
            uri,
            BackgroundImageConfiguration(
                insets,
                tileMode,
                if (background.contentMode == BackgroundContentMode.Tile) imageDensity else densityAsDpi(density)
            )
        )
    }

    private fun imageConfigurationOptimizedByImgix(
        background: Background,
        targetViewPixelSize: PixelSize,
        density: Float
    ): OptimizedImage? {
        val backgroundImage = background.image ?: return null
        val uri = background.image!!.url
        val imageDensity = imageDensity(background)
        val imageDensityScalingFactor = densityAsDpi(density) / imageDensity.toFloat()

        val imageDimsPx = PixelSize(
            backgroundImage.width,
            backgroundImage.height
        ) * imageDensityScalingFactor

        val (imgixParameters: Map<String, String>, optimizedImageConfiguration) = when (background.contentMode) {
            BackgroundContentMode.Original -> {
                // Imgix has a mode where I can crop & scale at the same time: apply `rect` and
                // then `w` and `h` after the fact.
                val viewPortSizeAsImagePixels = targetViewPixelSize / imageDensityScalingFactor
                val insetOffsetScreenPixels = (targetViewPixelSize - imageDimsPx) / 2f
                val horizontalInsetImagePixels = maxOf(0, (backgroundImage.width - viewPortSizeAsImagePixels.width) / 2)
                val verticalInsetImagePixels = maxOf(0, (backgroundImage.height - viewPortSizeAsImagePixels.height) / 2)

                val imageCropRect = Rect(
                    horizontalInsetImagePixels,
                    verticalInsetImagePixels,
                    backgroundImage.width - horizontalInsetImagePixels,
                    backgroundImage.height - verticalInsetImagePixels
                )

                val scalingFactor = if (imageDensityScalingFactor > 1) {
                    // image will be scaled up locally using the local insets, so do not apply
                    // any scaling on imgix.
                    1f
                } else {
                    // image will be scaled up using imgix.
                    imageDensityScalingFactor
                }

                Pair(
                    hashMapOf(
                        Pair("rect", "${imageCropRect.left},${imageCropRect.top},${imageCropRect.width()},${imageCropRect.height()}"),
                        Pair("w", minOf(backgroundImage.width, (imageCropRect.width() * scalingFactor).toInt()).toString()),
                        Pair("h", minOf(backgroundImage.height, (imageCropRect.height() * scalingFactor).toInt()).toString())
                    ),

                    BackgroundImageConfiguration(
                        Rect(
                            maxOf(insetOffsetScreenPixels.width, 0),
                            maxOf(insetOffsetScreenPixels.height, 0),
                            maxOf(insetOffsetScreenPixels.width, 0),
                            maxOf(insetOffsetScreenPixels.height, 0)
                        ),
                        null,
                        // by using the insets above to scale/crop the image we're implementing the
                        // density scale factor, so for display the density will be ignored (so just
                        // set it to be the display density).
                        densityAsDpi(density)
                    )
                )
            }
            BackgroundContentMode.Fill -> {
                Pair(
                    hashMapOf(
                        Pair("w", (targetViewPixelSize.width).toString()),
                        Pair("h", (targetViewPixelSize.height).toString()),
                        Pair("fit", "min")
                    ),
                    BackgroundImageConfiguration(
                        Rect(
                            0,
                            0,
                            0,
                            0
                        ),
                        null,
                        // by using the insets above to scale/crop the image we're implementing the
                        // density scale factor, so for display the density will be ignored (so just
                        // set it to be the display density).
                        densityAsDpi(density)
                    )
                )
            }
            BackgroundContentMode.Fit -> {
                val targetViewPixelSizeAccountingForBorder = PixelSize(
                    targetViewPixelSize.width,
                    targetViewPixelSize.height
                )

                val fitScaleFactor = minOf(
                    targetViewPixelSizeAccountingForBorder.width / imageDimsPx.width.toFloat(),
                    targetViewPixelSizeAccountingForBorder.height / imageDimsPx.height.toFloat()
                )

                val fitScaledImageDims = imageDimsPx * fitScaleFactor
                val insets = (targetViewPixelSizeAccountingForBorder / 2) - (fitScaledImageDims / 2)

                Pair(
                    hashMapOf(
                        // here we'll ask Imgix to to the equivalent operation as we just did above,
                        // but we'll still do it locally too to produce the appropriate insets,
                        // since Imgix will appropriately not return whitespace and instead just
                        // return a scaled down if necessary aspect correct version of the original
                        // image.
                        Pair("w", (targetViewPixelSizeAccountingForBorder.width).toString()),
                        Pair("h", (targetViewPixelSizeAccountingForBorder.height).toString()),
                        Pair("fit", "max")
                    ),
                    BackgroundImageConfiguration(
                        Rect(
                            insets.width,
                            insets.height,
                            insets.width,
                            insets.height
                        ),
                        null,
                        // by using the insets above to scale/crop the image we're implementing the
                        // density scale factor, so for display the density will be ignored (so just
                        // set it to be the display density).
                        densityAsDpi(density)
                    )
                )
            }
            BackgroundContentMode.Stretch -> {
                // Unlike the fit=min/max modes used above, for doing an aspect-incorrect scale
                // imgix does not distinguish between aspect matching and scaling up.  Determine
                // ourselves if it is worth it for either of the dimensions(ie., that we're not
                // asking Imgix to scale the image up, the exact opposite of our goal)
                val scaleDownTo = PixelSize(
                    minOf(backgroundImage.width, targetViewPixelSize.width),
                    minOf(backgroundImage.height, targetViewPixelSize.height)
                )

                Pair(
                    hashMapOf(
                        Pair("w", scaleDownTo.width.toString()),
                        Pair("h", scaleDownTo.height.toString()),
                        Pair("fit", "scale")
                    ),
                    BackgroundImageConfiguration(
                        Rect(
                            0,
                            0,
                            0,
                            0
                        ),
                        null,
                        // by using the insets above to scale/crop the image we're implementing the
                        // density scale factor, so for display the density will be ignored (so just
                        // set it to be the display density).
                        densityAsDpi(density)
                    )
                )
            }
            BackgroundContentMode.Tile -> {
                val imageDensityToDeviceDensityRatio = imageDensity.toFloat() / densityAsDpi(density)

                Pair(
                    // so, if we have a HiDpi device (xxhdpi or denser) we will only have scaling-up
                    // going on (taken care of because we set the density on the bitmap). However,
                    // if we have a low-dpi device then we have an opportunity to ask imgix to scale
                    // down the image for us and cut down on the amount of pixels downloaded.

                    if (imageDensityToDeviceDensityRatio < 1) {
                        // image density is lower than the screen, so will need to be scaled up
                        // to match the display (which should be done locally)
                        hashMapOf()
                    } else {
                        // image density is higher than the screen, so we can scale it down
                        // cloud-side.
                        hashMapOf(
                            Pair("w", (backgroundImage.width / imageDensityToDeviceDensityRatio).toInt().toString()),
                            Pair("h", (backgroundImage.height / imageDensityToDeviceDensityRatio).toInt().toString())
                        )
                    },
                    BackgroundImageConfiguration(
                        Rect(
                            0,
                            0,
                            0,
                            0
                        ),
                        Shader.TileMode.REPEAT,
                        if (imageDensityToDeviceDensityRatio < 1) {
                            // the density differential needs to scale up the image, so we'll have
                            // Android's UI framework do it for us locally:
                            imageDensity
                        } else {
                            densityAsDpi(density)
                        }
                    )
                )
            }
        }

        // Parse with java.net.URI and then use URLEncoder to safely encode the params

        val uriWithParameters = setQueryParameters(uri, imgixParameters)

        return OptimizedImage(
            URI(uriWithParameters.toString()),
            optimizedImageConfiguration
        )
    }

    /**
     * Take a given image block and return the URI with optimization parameters needed to display
     * it.
     *
     * We use a service called Imgix to do cloud-side transforms of our images. Here we're using it
     * for a scale down transform, if needed. On first usage the cloud service will execute the
     * transform and then cache the result, meaning that all other users on other devices viewing
     * the same image asset will get the previously processed bits.
     *
     * @return optimized URI.
     */
    fun optimizeImageBlock(
        image: Image,
        blockBorderWidth: Int,
        targetViewPixelSize: PixelSize,
        density: Float
    ): URI {
        val imageSizePixels = PixelSize(
            image.width,
            image.height
        )

        // Now take border width into account.
        val borderWidth = blockBorderWidth.dpAsPx(density)
        val targetViewSizeWithoutBorderWidth = PixelSize(
            targetViewPixelSize.width - borderWidth,
            targetViewPixelSize.height - borderWidth
        )

        val smallestSize = PixelSize(
            minOf(imageSizePixels.width, targetViewSizeWithoutBorderWidth.width),
            minOf(imageSizePixels.height, targetViewSizeWithoutBorderWidth.height)
        )

        return setQueryParameters(
            image.url,
            mapOf(
                Pair("w", smallestSize.width.toString()),
                Pair("h", smallestSize.height.toString()),
                Pair("fit", "scale")
            )
        )
    }

    // This is only used for polls until this class is refactored to not be tightly coupled with backgrounds
    fun optimizeImageForFill(
        image: Image,
        targetViewPixelSize: PixelSize
    ): URI {
        return setQueryParameters(
            image.url,
            mapOf(
                Pair("w", targetViewPixelSize.width.toString()),
                Pair("h", targetViewPixelSize.height.toString()),
                Pair("fit", "min")
            )
        )
    }

    /**
     * Map the 1X, 2X, and 3X background scale values (which are an iOS convention) to DPI values.
     */
    private fun imageDensity(background: Background): Int {
        return when (background.scale) {
            BackgroundScale.X1 -> 160
            BackgroundScale.X2 -> 320
            BackgroundScale.X3 -> 480
        }
    }

    private fun densityAsDpi(density: Float): Int {
        return (160 * density).roundToInt()
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
