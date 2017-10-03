package io.rover.rover.services.network

import io.rover.rover.core.domain.Background
import io.rover.rover.core.domain.BackgroundContentMode
import io.rover.rover.core.domain.BackgroundScale
import io.rover.rover.core.domain.ButtonBlock
import io.rover.rover.core.domain.Color
import io.rover.rover.core.domain.HorizontalAlignment
import io.rover.rover.core.domain.ID
import io.rover.rover.core.domain.Image
import io.rover.rover.core.domain.ImageBlock
import io.rover.rover.core.domain.Insets
import io.rover.rover.core.domain.Length
import io.rover.rover.core.domain.Offsets
import io.rover.rover.core.domain.Position
import io.rover.rover.core.domain.Screen
import io.rover.rover.core.domain.StatusBarStyle
import io.rover.rover.core.domain.TextAlignment
import io.rover.rover.core.domain.TitleBarButtons
import io.rover.rover.core.domain.UnitOfMeasure
import io.rover.rover.core.domain.VerticalAlignment
import io.rover.rover.junit4ReportingWorkaround
import io.rover.rover.platform.DateFormattingInterface
import org.amshove.kluent.When
import org.amshove.kluent.any
import org.amshove.kluent.calling
import org.amshove.kluent.mock
import org.amshove.kluent.shouldEqual
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import org.json.JSONObject
import org.junit.Assert.fail
import org.junit.platform.runner.JUnitPlatform
import org.junit.runner.RunWith
import java.lang.AssertionError
import java.net.URI

@RunWith(JUnitPlatform::class)
class WireEncoderSpec: Spek({
    given("a wire encoder") {

        val dateFormatting = mock<DateFormattingInterface>()
        When.calling(dateFormatting.dateAsIso8601(any())).thenReturn("Jan 01 1970")
        val wireEncoder = WireEncoder(dateFormatting)

        on("decoding an experience") {
            val json = this.javaClass.classLoader.getResourceAsStream("comprehensive_experience.json").bufferedReader(Charsets.UTF_8).readText()
            val decoded = wireEncoder.decodeExperience(JSONObject(json).getJSONObject("data").getJSONObject("experience"))

            it("should be amazing") {
                junit4ReportingWorkaround {
                    decoded.id.shouldEqual(ID("5873dee6d5bf3e002de4d70e"))

                    // TODO this will change to be a deep object instead
                    decoded.homeScreenId.shouldEqual(ID("SJOcEZhRe"))

                    decoded.screens.count().shouldEqual(4)

                    // order is important!

                    decoded.screens.first().apply {
                        id shouldEqual ID("BkKeyw-Ux")
                        autoColorStatusBar shouldEqual true
                        backgroundColor shouldEqual Color(211, 238, 247, 1.0)
                        backgroundContentMode shouldEqual BackgroundContentMode.Original
                        backgroundImage shouldEqual null
                        backgroundScale shouldEqual BackgroundScale.X1
                        id shouldEqual ID("BkKeyw-Ux")
                        isStretchyHeaderEnabled shouldEqual true
                        statusBarColor shouldEqual Color(99, 56, 115, 1.0)
                        statusBarStyle shouldEqual StatusBarStyle.Light
                        titleBarBackgroundColor shouldEqual Color(128, 73, 149, 1.0)
                        titleBarButtonColor shouldEqual Color(255, 255, 255, 1.0)
                        titleBarButtons shouldEqual TitleBarButtons.Back
                        titleBarText shouldEqual "Save for Father's Day"
                        titleBarTextColor shouldEqual Color(255, 255, 255, 1.0)
                        useDefaultTitleBarStyle shouldEqual false

                        rows.first().apply {
                            autoHeight shouldEqual true
                            backgroundColor shouldEqual Color(129, 129, 129, 0.0)
                            backgroundContentMode shouldEqual BackgroundContentMode.Original
                            backgroundImage shouldEqual null
                            backgroundScale shouldEqual BackgroundScale.X1
                            id shouldEqual ID("SyAO1vbUe")

                            System.out.println(blocks.map { it.javaClass.simpleName })
                            blocks.first().apply {
                                javaClass shouldEqual ImageBlock::class.java
                                this as ImageBlock

                                action shouldEqual null
                                autoHeight shouldEqual true
                                backgroundColor shouldEqual Color(238, 238, 238, 0.0)
                                backgroundContentMode shouldEqual BackgroundContentMode.Original
                                backgroundImage shouldEqual null
                                backgroundScale shouldEqual BackgroundScale.X1
                                borderColor shouldEqual Color(129, 129, 129, 1.0)

                                borderRadius shouldEqual 0
                                borderWidth shouldEqual 0
                                height shouldEqual Length(UnitOfMeasure.Points, 65.9067796610169)
                                horizontalAlignment shouldEqual HorizontalAlignment.Center
                                id shouldEqual ID("SygAdkv-Ll")
                                image shouldEqual Image(
                                    height = 154,
                                    isURLOptimizationEnabled = true,
                                    name = "loweslogo.png",
                                    size = 9096,
                                    url = URI.create("https://images-rover-io.imgix.net/uploads/c81e728d9d4c2f636f067f89cc14862c/4dd2526b-1dc1-4c39-9541-8a091c85c621-loweslogo.png"),
                                    width = 236
                                )
                                insets shouldEqual Insets(0, 0, 0, 0)
                                offsets shouldEqual Offsets(
                                    bottom = Length(UnitOfMeasure.Points, 10.0),
                                    center = Length(UnitOfMeasure.Points, 0.0),
                                    left = Length(UnitOfMeasure.Points, 109.5),
                                    middle = Length(UnitOfMeasure.Points, 22.3516949152542),
                                    right = Length(UnitOfMeasure.Points, 109.5),
                                    top = Length(UnitOfMeasure.Points, 20.0)
                                )
                                opacity shouldEqual 1.0
                                position shouldEqual Position.Stacked
                                verticalAlignment shouldEqual VerticalAlignment.Top
                                width shouldEqual Length(UnitOfMeasure.Points, 101.0)
                            }
                        }
                    }
                }
            }
        }
    }
})
