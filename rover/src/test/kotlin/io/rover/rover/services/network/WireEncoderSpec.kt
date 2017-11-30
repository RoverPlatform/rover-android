package io.rover.rover.services.network

import io.rover.rover.core.domain.AttributeValue
import io.rover.rover.core.domain.BackgroundContentMode
import io.rover.rover.core.domain.BackgroundScale
import io.rover.rover.core.domain.Color
import io.rover.rover.core.domain.Event
import io.rover.rover.core.domain.HorizontalAlignment
import io.rover.rover.core.domain.ID
import io.rover.rover.core.domain.Image
import io.rover.rover.core.domain.ImageBlock
import io.rover.rover.core.domain.Insets
import io.rover.rover.core.domain.Length
import io.rover.rover.core.domain.Offsets
import io.rover.rover.core.domain.Position
import io.rover.rover.core.domain.StatusBarStyle
import io.rover.rover.core.domain.TitleBarButtons
import io.rover.rover.core.domain.UnitOfMeasure
import io.rover.rover.core.domain.VerticalAlignment
import io.rover.rover.junit4ReportingWorkaround
import io.rover.rover.platform.DateFormattingInterface
import io.rover.rover.platform.decodeDeviceStateFromJsonStringForTests
import io.rover.rover.platform.decodeExperienceFromStringForTests
import io.rover.rover.platform.encodeEventsToStringJsonForTests
import io.rover.rover.platform.encodeJsonToStringForTests
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
import org.skyscreamer.jsonassert.JSONAssert
import java.net.URI
import java.text.SimpleDateFormat
import java.util.*

class WireEncoderSpec: Spek({
    given("a wire encoder") {

        val dateFormatting = mock<DateFormattingInterface>()
        When.calling(dateFormatting.dateAsIso8601(any())).thenReturn("2017-10-04T16:56Z")
        val wireEncoder = WireEncoder(dateFormatting)

        on("encoding some events") {
            val events = listOf(
                Event(
                    hashMapOf(
                        Pair("a key", AttributeValue.String("a value"))
                    ), "I am event", SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'", Locale.US).parse("2017-10-04T16:56Z"), UUID.fromString("55c5ae35-a8e2-4049-a883-fedc55d22ba9")
                )
            )

            it("should match some pre-rendered JSON") {
                val expectedJson = this.javaClass.classLoader.getResourceAsStream("outbound_events.json").bufferedReader(Charsets.UTF_8).readText()
                val json = wireEncoder.encodeEventsToStringJsonForTests(events)
                junit4ReportingWorkaround {
                    JSONAssert.assertEquals(expectedJson, json, true)
                }
            }
        }

        on("decoding a device") {
            val expectedJson = this.javaClass.classLoader.getResourceAsStream("comprehensive_device.json").bufferedReader(Charsets.UTF_8).readText()
            val decoded = wireEncoder.decodeDeviceStateFromJsonStringForTests(JSONObject(expectedJson).getJSONObject("data").getJSONObject("device").toString(4))

            it("produces valid JSON that can be encoded back into equivalent JSON") {
                // if we can roundtrip the comprehensive JSON Experience structure to the Rover
                // SDK model representation and back accurately, then we have a pretty strong
                // guarantee that the deserialization logic is accurate and complete.

                val reWrapped = JSONObject().apply {
                    put("data", JSONObject().apply {
                        put("device", JSONObject(decoded.encodeJsonToStringForTests()))
                    })
                }

                val rejsonned = reWrapped.toString(4)
                junit4ReportingWorkaround {
                    JSONAssert.assertEquals(expectedJson, rejsonned, true)
                }
            }
        }

        on("decoding an experience") {
            val expectedJson = this.javaClass.classLoader.getResourceAsStream("comprehensive_experience.json").bufferedReader(Charsets.UTF_8).readText()
            val decoded = wireEncoder.decodeExperienceFromStringForTests(JSONObject(expectedJson).getJSONObject("data").getJSONObject("experience").toString(4))

            it("reads correct values from the payload") {
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

            it("produces valid JSON that can be encoded back into equivalent JSON") {
                // if we can roundtrip the comprehensive JSON Experience structure to the Rover
                // SDK model representation and back accurately, then we have a pretty strong
                // guarantee that the deserialization logic is accurate and complete.

                val reWrapped = JSONObject().apply {
                    put("data", JSONObject().apply {
                        put("experience", JSONObject(decoded.encodeJsonToStringForTests()))
                    })
                }

                val rejsonned = reWrapped.toString(4)
                junit4ReportingWorkaround {
                    JSONAssert.assertEquals(expectedJson, rejsonned, true)
                }
            }
        }
    }
})
