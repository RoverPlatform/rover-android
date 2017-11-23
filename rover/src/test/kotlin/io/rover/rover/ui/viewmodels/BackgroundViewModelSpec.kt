package io.rover.rover.ui.viewmodels

import android.util.DisplayMetrics
import io.rover.rover.core.domain.Background
import io.rover.rover.core.domain.BackgroundContentMode
import io.rover.rover.core.domain.BackgroundScale
import io.rover.rover.core.domain.Color
import io.rover.rover.core.domain.Image
import io.rover.rover.services.assets.AssetService
import io.rover.rover.ui.types.PixelSize
import org.amshove.kluent.mock
import org.amshove.kluent.shouldEqual
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import org.jetbrains.spek.api.dsl.xon
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder


class BackgroundViewModelSpec: Spek({
    val assetService : AssetService = mock()

    fun createDisplayMetrics(dpi: Int): DisplayMetrics {
        val mdpi = 160
        return DisplayMetrics().apply {
            densityDpi = dpi
            density = dpi / mdpi.toFloat()
        }
    }

    fun decodeUriParams(uri: URI): Map<String, String> {
        return uri.query.split("&").map { it.split("=").map { URLDecoder.decode(it, "UTF-8") } }.associate { Pair(it[0], it[1]) }
    }

    given("an original size image block background at 3X (480 dpi) that must be cropped") {
        val image = Image(
            120,
            100,
            "interesting.jpg",
            6000,
            URI("https://rover.io/image.jpg")
        )

        val background = object : Background {
            override val backgroundColor: Color = Color(0x7f, 0x7f, 0x7f, 0.0)

            override val backgroundContentMode: BackgroundContentMode = BackgroundContentMode.Original

            override val backgroundImage: Image? = image

            override val backgroundScale: BackgroundScale = BackgroundScale.X3
        }

        val backgroundViewModel = BackgroundViewModel(
            background,
            assetService,
            null
        )

        // TODO: test with a border later

        on("optimized to display in exactly the same size block on same density display") {
            val displayMetrics = createDisplayMetrics(480)

            val (uri, optimizedConfiguration, targetDensity) = backgroundViewModel.imageConfigurationOptimizedByImgix(
                image.url,
                image,
                PixelSize(120, 100),
                displayMetrics
            )

            val decodedParams = decodeUriParams(uri)

            it("asks imgix for a no-op crop") {
                decodedParams["rect"].shouldEqual("0,0,120,100")
            }

            it("asks imgix for a no-op scale") {
                decodedParams["w"].shouldEqual("120")
                decodedParams["h"].shouldEqual("100")
            }

            it("sets exactly fitting insets") {
                optimizedConfiguration.insets.left.shouldEqual(0)
                optimizedConfiguration.insets.top.shouldEqual(0)
                optimizedConfiguration.insets.right.shouldEqual(0)
                optimizedConfiguration.insets.bottom.shouldEqual(0)
            }
        }

        on("optimized to display in exactly the same size block on a 560 dpi display") {
            val displayMetrics = createDisplayMetrics(560)

            val (uri, optimizedConfiguration, targetDensity) = backgroundViewModel.imageConfigurationOptimizedByImgix(
                image.url,
                image,
                PixelSize(140, 117), // * 1.16666~ (480 dp -> 560 dp factor)
                displayMetrics
            )

            val decodedParams = decodeUriParams(uri)

            it("asks imgix for a no-op crop") {
                // we wouldn't want imgix to scale up for us, that would be a waste.
                decodedParams["rect"].shouldEqual("0,0,120,100")
            }

            it("asks imgix for a no-op scale") {
                decodedParams["w"].shouldEqual("120")
                decodedParams["h"].shouldEqual("100")
            }

            it("sets exactly fitting insets") {
                optimizedConfiguration.insets.left.shouldEqual(0)
                optimizedConfiguration.insets.top.shouldEqual(0)
                optimizedConfiguration.insets.right.shouldEqual(0)
                optimizedConfiguration.insets.bottom.shouldEqual(0)
            }
        }

        on("optimized to display in a slightly wider block on same density display") {
            val displayMetrics = createDisplayMetrics(480)

            val (uri, optimizedConfiguration, targetDensity) = backgroundViewModel.imageConfigurationOptimizedByImgix(
                image.url,
                image,
                PixelSize(140, 100), // wider by 20 image pixels (which is the same as display pixels here)
                displayMetrics
            )

            val decodedParams = decodeUriParams(uri)

            it("asks imgix for a no-op crop") {
                decodedParams["rect"].shouldEqual("0,0,120,100")
            }

            it("asks imgix for a no-op scale") {
                decodedParams["w"].shouldEqual("120")
                decodedParams["h"].shouldEqual("100")
            }

            it("sets horizontal insets") {
                optimizedConfiguration.insets.left.shouldEqual(10)
                optimizedConfiguration.insets.right.shouldEqual(10)
                optimizedConfiguration.insets.top.shouldEqual(0)
                optimizedConfiguration.insets.bottom.shouldEqual(0)
            }
        }

        on("optimized to display in a slightly wider block on a 560 dpi display") {
            val displayMetrics = createDisplayMetrics(560)

            val (uri, optimizedConfiguration, targetDensity) = backgroundViewModel.imageConfigurationOptimizedByImgix(
                image.url,
                image,
                PixelSize(163, 117), // wider by 20 image pixels and then * 1.16666~ (480 dp -> 560 dp factor)
                displayMetrics
            )

            val decodedParams = decodeUriParams(uri)

            it("asks imgix for a no-op crop") {
                // we wouldn't want imgix to scale up for us, that would be a waste.
                decodedParams["rect"].shouldEqual("0,0,120,100")
            }

            it("asks imgix for a no-op scale") {
                decodedParams["w"].shouldEqual("120")
                decodedParams["h"].shouldEqual("100")
            }

            it("sets horizontal insets") {
                optimizedConfiguration.insets.left.shouldEqual(11) // TODO: maybe should be 12? rounding accumulation
                optimizedConfiguration.insets.right.shouldEqual(11)
                optimizedConfiguration.insets.top.shouldEqual(0)
                optimizedConfiguration.insets.bottom.shouldEqual(0)
            }
        }

        on("optimized to display in a narrower block on same density display") {
            val displayMetrics = createDisplayMetrics(480)

            val (uri, optimizedConfiguration, targetDensity) = backgroundViewModel.imageConfigurationOptimizedByImgix(
                image.url,
                image,
                PixelSize(100, 100), // 20 image pixels narrower.
                displayMetrics
            )

            val decodedParams = decodeUriParams(uri)

            it("asks imgix to crop the sides") {
                decodedParams["rect"].shouldEqual("10,0,100,100")
            }

            it("asks imgix for a no-op scale") {
                decodedParams["w"].shouldEqual("100")
                decodedParams["h"].shouldEqual("100")
            }

            it("sets zero insets for the width dimension because the crop was done for us by imgix") {
                optimizedConfiguration.insets.left.shouldEqual(0)
                optimizedConfiguration.insets.right.shouldEqual(0)
                optimizedConfiguration.insets.top.shouldEqual(0)
                optimizedConfiguration.insets.bottom.shouldEqual(0)
            }
        }

        on("optimized to display in a narrower block on a 560 dpi display") {
            val displayMetrics = createDisplayMetrics(560)

            val (uri, optimizedConfiguration, targetDensity) = backgroundViewModel.imageConfigurationOptimizedByImgix(
                image.url,
                image,
                PixelSize(117, 117), // 20 image pixels narrower and then * 1.16666~ (480 dp -> 560 dp factor)
                displayMetrics
            )

            val decodedParams = decodeUriParams(uri)

            it("asks imgix for a crop") {
                // we wouldn't want imgix to scale up for us, that would be a waste.
                decodedParams["rect"].shouldEqual("10,0,100,100")
            }

            it("asks imgix for a no-op scale") {
                decodedParams["w"].shouldEqual("100")
                decodedParams["h"].shouldEqual("100")
            }

            it("sets zero insets for the width dimension because the crop was done for us by imgix") {
                optimizedConfiguration.insets.left.shouldEqual(0)
                optimizedConfiguration.insets.right.shouldEqual(0)
                optimizedConfiguration.insets.top.shouldEqual(0)
                optimizedConfiguration.insets.bottom.shouldEqual(0)
            }
        }

        on("optimized to display in exactly the same size block on a 160 dpi display") {
            val displayMetrics = createDisplayMetrics(160)

            val (uri, optimizedConfiguration, targetDensity) = backgroundViewModel.imageConfigurationOptimizedByImgix(
                image.url,
                image,
                PixelSize(40, 33),  // * 0.3 (480 dp -> 160 dp factor)
                displayMetrics
            )

            val decodedParams = decodeUriParams(uri)

            it("asks imgix for a no-op crop") {
                decodedParams["rect"].shouldEqual("0,0,120,100")

            }

            it("asks imgix to scale down by a factor of three ") {
                decodedParams["w"].shouldEqual("40")
                decodedParams["h"].shouldEqual("33")
            }

            it("sets exactly fitting insets") {
                optimizedConfiguration.insets.left.shouldEqual(0)
                optimizedConfiguration.insets.top.shouldEqual(0)
                optimizedConfiguration.insets.right.shouldEqual(0)
                optimizedConfiguration.insets.bottom.shouldEqual(0)
            }
        }

        on("optimized to display in a narrower block on a 160 dpi display") {
            val displayMetrics = createDisplayMetrics(160)

            val (uri, optimizedConfiguration, targetDensity) = backgroundViewModel.imageConfigurationOptimizedByImgix(
                image.url,
                image,
                PixelSize(33, 33),  // 20 image pixels narrower and then * 0.3 (480 dp -> 160 dp factor)
                displayMetrics
            )

            val decodedParams = decodeUriParams(uri)

            it("asks imgix for a crop") {
                decodedParams["rect"].shouldEqual("10,0,100,100")
            }

            it("asks imgix to scale down") {
                decodedParams["w"].shouldEqual("33")
                decodedParams["h"].shouldEqual("33")
            }

            it("sets exactly fitting insets") {
                optimizedConfiguration.insets.left.shouldEqual(0)
                optimizedConfiguration.insets.top.shouldEqual(0)
                optimizedConfiguration.insets.right.shouldEqual(0)
                optimizedConfiguration.insets.bottom.shouldEqual(0)
            }
        }

        on("optimized to display in a slightly wider block on a 160 dpi display") {
            val displayMetrics = createDisplayMetrics(160)

            val (uri, optimizedConfiguration, targetDensity) = backgroundViewModel.imageConfigurationOptimizedByImgix(
                image.url,
                image,
                PixelSize(47, 33), // wider by 20 image pixels and then * 0.3 (480 dp -> 160 dp factor)
                displayMetrics
            )

            val decodedParams = decodeUriParams(uri)

            it("asks imgix for a no-op crop") {
                decodedParams["rect"].shouldEqual("0,0,120,100")
            }

            it("asks imgix to scale down") {
                decodedParams["w"].shouldEqual("40")
                decodedParams["h"].shouldEqual("33")
            }

            it("sets horizontal insets") {
                optimizedConfiguration.insets.left.shouldEqual(3)
                optimizedConfiguration.insets.right.shouldEqual(3)
                optimizedConfiguration.insets.top.shouldEqual(0)
                optimizedConfiguration.insets.bottom.shouldEqual(0)
            }
        }
    }
})
