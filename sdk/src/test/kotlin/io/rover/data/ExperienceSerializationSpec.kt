package io.rover.data

import io.rover.sdk.data.domain.Experience
import io.rover.sdk.data.operations.data.decodeJson
import io.rover.sdk.data.operations.data.encodeJson
import org.json.JSONObject
import org.skyscreamer.jsonassert.JSONAssert
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object ExperienceSerializationSpec : Spek({
    describe("experience serialization") {
        context("round trip (deserialization -> serialization)") {

            val experienceSourceJsonString = this.javaClass.classLoader.getResourceAsStream("experience.json").bufferedReader(Charsets.UTF_8).readText()
            val experienceSourceJson = JSONObject(experienceSourceJsonString)
            val experience = Experience.decodeJson(experienceSourceJson)

            it("should reserialize to equivalent JSON") {
                val experienceNewJsonString = experience.encodeJson().toString(4)

                JSONAssert.assertEquals(experienceSourceJsonString, experienceNewJsonString, false)
            }
        }
    }
})