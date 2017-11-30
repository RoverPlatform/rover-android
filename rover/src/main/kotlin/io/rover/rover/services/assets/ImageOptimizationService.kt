package io.rover.rover.services.assets

import android.graphics.Shader
import android.util.DisplayMetrics
import io.rover.rover.core.domain.Background
import io.rover.rover.core.domain.BackgroundContentMode
import io.rover.rover.core.domain.BackgroundScale
import io.rover.rover.core.domain.ImageBlock
import io.rover.rover.core.logging.log
import io.rover.rover.ui.types.PixelSize
import io.rover.rover.ui.types.Rect
import io.rover.rover.ui.types.dpAsPx
import io.rover.rover.ui.viewmodels.BackgroundImageConfiguration
import java.net.URI

class ImageOptimizationService: ImageOptimizationServiceInterface {

    private val urlOptimizationEnabled = true

    override fun optimizeImageBackground(background: Background, targetViewPixelSize: PixelSize, displayMetrics: DisplayMetrics): OptimizedImage? {
        return if(urlOptimizationEnabled) {
            imageConfigurationOptimizedByImgix(background, targetViewPixelSize, displayMetrics)
        } else {
            localScaleOnlyImageConfiguration(background, targetViewPixelSize, displayMetrics)
        }
    }

    private fun localScaleOnlyImageConfiguration(
        background: Background,
        targetViewPixelSize: PixelSize,
        displayMetrics: DisplayMetrics
    ): OptimizedImage? {
        val imageDensity = imageDensity(background)
        val backgroundImage = background.backgroundImage ?: return null
        val uri = background.backgroundImage?.url ?: return null
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

        val tileMode = when(background.backgroundContentMode) {
            BackgroundContentMode.Tile -> Shader.TileMode.REPEAT
            else -> null
        }

        return OptimizedImage(
            uri, BackgroundImageConfiguration(
                insets,
                tileMode,
                if(background.backgroundContentMode == BackgroundContentMode.Tile) imageDensity else displayMetrics.densityDpi
            )
        )
    }

    private fun imageConfigurationOptimizedByImgix(
        background: Background,
        targetViewPixelSize: PixelSize,
        displayMetrics: DisplayMetrics
    ): OptimizedImage? {
        val backgroundImage = background.backgroundImage ?: return null
        val uri = background.backgroundImage?.url ?: return null
        val imageDensity = imageDensity(background)
        val imageDensityScalingFactor = displayMetrics.densityDpi.toFloat() / imageDensity.toFloat()

        val imageDimsPx = PixelSize(
            backgroundImage.width,
            backgroundImage.height
        ) * imageDensityScalingFactor

        val (imgixParameters: Map<String, String>, optimizedImageConfiguration) = when(background.backgroundContentMode) {
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

                val scalingFactor = if(imageDensityScalingFactor > 1) {
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
                        displayMetrics.densityDpi
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
                        displayMetrics.densityDpi
                    )
                )
            }
            BackgroundContentMode.Fit -> {
                val targetViewPixelSizeAccountingForBorder = PixelSize(
                    targetViewPixelSize.width,
                    targetViewPixelSize.height
                )

                val fitScaleFactor = minOf(
                    targetViewPixelSizeAccountingForBorder.width  / imageDimsPx.width.toFloat(),
                    targetViewPixelSizeAccountingForBorder.height / imageDimsPx.height.toFloat()
                )

                val fitScaledImageDims = imageDimsPx * fitScaleFactor
                val  insets = (targetViewPixelSizeAccountingForBorder / 2) - (fitScaledImageDims / 2)

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
                        displayMetrics.densityDpi
                    )
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
                        targetViewPixelSize.width, targetViewPixelSize.height
                    )
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
                        displayMetrics.densityDpi
                    )
                )
            }
            BackgroundContentMode.Tile -> {
                val imageDensityToDeviceDensityRatio = imageDensity.toFloat() / displayMetrics.densityDpi

                Pair(
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
                            0,
                            0,
                            0,
                            0
                        ),
                        Shader.TileMode.REPEAT,
                        if(imageDensityToDeviceDensityRatio < 1) {
                            // the density differential needs to scale up the image, so we'll have
                            // Android's UI framework do it for us locally:
                            imageDensity
                        } else {
                            displayMetrics.densityDpi
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
     * We use a service called Imgix to do cloud-side transforms of our images. Here we're using it
     * for a scale down transform, if needed. On first usage the cloud service will execute the
     * transform and then cache the result, meaning that all other users on other devices viewing
     * the same image asset will get the previously processed bits.
     */
    override fun optimizeImageBlock(
        block: ImageBlock,
        targetViewPixelSize: PixelSize,
        displayMetrics: DisplayMetrics
    ): URI? {
        return if(block.image != null) {
            // TODO: should any of this Imgix scaling logic be pulled out into a separate concern?

            val imageSizePixels = PixelSize(
                block.image.width,
                block.image.height
            )

            // Now take border width into account.
            val borderWidth = block.borderWidth.dpAsPx(displayMetrics.density)
            val targetViewSizeWithoutBorderWidth = PixelSize(
                targetViewPixelSize.width - borderWidth,
                targetViewPixelSize.height - borderWidth
            )

            // if the ultimate image to be rendered on the screen is going to smaller (in terms of
            // pixel count) than the source, in terms of pixel count (relevant to data plan usage
            // for users), then we'll ask Imgix to execute the scale operation for us instead.
            // However, we will ask for an aspect-correct scale from Imgix because we'll end up
            // scaling up the larger dimension on our end, saving even more bytes.  Note that we
            // won't need to have View change the scaling mode from the FIT_XY mode we're already
            // using because no crop operation is going on here.
            val smallestSize = minOf(imageSizePixels, targetViewSizeWithoutBorderWidth)

            return setQueryParameters(
                block.image.url,
                mapOf(
                    Pair("w", smallestSize.width.toString()),
                    Pair("h", smallestSize.height.toString())
                )
            )
        } else {
            null
        }
    }

    /**
     * Map the 1X, 2X, and 3X background scale values (which are an iOS convention) to DPI values.
     */
    private fun imageDensity(background: Background): Int {
        return when(background.backgroundScale) {
            BackgroundScale.X1 -> 160
            BackgroundScale.X2 -> 320
            BackgroundScale.X3 -> 480
        }
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
