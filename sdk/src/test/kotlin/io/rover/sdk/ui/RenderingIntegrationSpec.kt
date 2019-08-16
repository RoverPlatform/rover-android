package io.rover.sdk.ui

import com.nhaarman.mockitokotlin2.mock
import io.rover.helpers.shouldBeLessThan
import io.rover.helpers.shouldEqual
import io.rover.sdk.ViewModels
import io.rover.sdk.logging.GlobalStaticLogHolder
import io.rover.sdk.logging.JvmLogger
import io.rover.sdk.logging.log
import io.rover.sdk.data.domain.Experience
import io.rover.sdk.data.operations.data.decodeJson
import io.rover.sdk.ui.layout.Layout
import org.json.JSONObject
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import kotlin.system.measureNanoTime

class RenderingIntegrationSpec : Spek({
    describe("use of org.json within tests") {
        context("test suite run") {
            val parsedJson = JSONObject("""{"a": 42}""")
            it("can parse a value out of JSON") {
                parsedJson.getInt("a") shouldEqual 42
            }
        }
    }

    describe("integration test with a full experience in JSON") {
        GlobalStaticLogHolder.globalLogEmitter = JvmLogger()
        val experienceJson = this.javaClass.classLoader.getResourceAsStream("experience_without_polls.json").bufferedReader(Charsets.UTF_8).readText()
        val experience = Experience.decodeJson(JSONObject(experienceJson))

        log.v("There are ${experience.screens.count()} screens.")

        context("layout") {
            val viewModels = ViewModels(
                apiService = mock(),
                mainScheduler = mock(),
                eventEmitter = mock(),
                sessionTracker = mock(),
                imageOptimizationService = mock(),
                assetService = mock(),
                measurementService = mock(),
                pollVotingService = mock(),
                pollVotingStorage = mock()
            )

            val screenViewModels = experience.screens.map { screen ->
                // realObjectStack.resolve(ScreenViewModelInterface::class.java, null, it)!!

                val exampleExperience = Experience("", "", listOf(), mapOf(), listOf(), "")
                viewModels.screenViewModel(screen, exampleExperience, "")
            }

            val rendered: MutableList<Layout> = mutableListOf()
            val renderTimeNs = measureNanoTime {
                screenViewModels.forEach { screenViewModel ->
                    rendered.add(
                        screenViewModel.render(
                            100f
                        )
                    )
                }
            }
            val renderTimeMs = renderTimeNs / 1000000
            log.v("Took $renderTimeMs ms to render all ${experience.screens.count()} screens.")

            it("should have taken") {
                renderTimeMs shouldBeLessThan 200
            }
        }
    }
})