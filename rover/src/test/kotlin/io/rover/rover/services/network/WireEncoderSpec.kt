package io.rover.rover.services.network

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
import org.junit.platform.runner.JUnitPlatform
import org.junit.runner.RunWith

@RunWith(JUnitPlatform::class)
class WireEncoderSpec: Spek({
    given("a wire encoder") {
        val dateFormatting = mock<DateFormattingInterface>()
        When.calling(dateFormatting.dateAsIso8601(any())).thenReturn("Jan 01 1970")
        val wireEncoder = WireEncoder(dateFormatting)

        on("decoding a context") {
            val json = this.javaClass.classLoader.getResourceAsStream("comprehensive_experience.json").bufferedReader(Charsets.UTF_8).readText()
            val decoded = wireEncoder.decodeExperience(JSONObject(json).getJSONObject("data").getJSONObject("experience"))

            it("should be amazing") {
                decoded.id.shouldEqual(6)
            }
        }
    }
})
