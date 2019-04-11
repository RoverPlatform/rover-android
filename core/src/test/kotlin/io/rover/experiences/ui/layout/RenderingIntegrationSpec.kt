package io.rover.experiences.ui.layout

import io.rover.core.ViewModels
import io.rover.core.logging.GlobalStaticLogHolder
import io.rover.core.logging.JvmLogger
import io.rover.core.logging.log
import io.rover.core.data.domain.Experience
import io.rover.core.data.operations.data.decodeJson
import io.rover.core.ui.layout.Layout
import org.amshove.kluent.mock
import org.amshove.kluent.shouldBeLessThan
import org.amshove.kluent.shouldEqual
import org.json.JSONObject
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import kotlin.system.measureNanoTime

class RenderingIntegrationSpec : Spek({
    describe("use of org.json within tests") {
        context("test suite run") {
            val parsedJson = JSONObject("""{"a": 42}""")
            it("can parse a value out of JSON") {
                parsedJson.getInt("a").shouldEqual(42)
            }
        }
    }

    describe("integration test with a full experience in JSON") {
        GlobalStaticLogHolder.globalLogEmitter = JvmLogger()
        val experienceJson = this.javaClass.classLoader.getResourceAsStream("experience.json").bufferedReader(Charsets.UTF_8).readText()
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
                measurementService = mock()
            )

            val screenViewModels = experience.screens.map { screen ->
                // realObjectStack.resolve(ScreenViewModelInterface::class.java, null, it)!!
                viewModels.screenViewModel(screen)
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
                renderTimeMs.shouldBeLessThan(200)
            }
        }
    }
})