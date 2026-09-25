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

package io.rover.sdk.core.data.config

import com.squareup.moshi.JsonClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Guards the [JsonClass] annotation on [CommHubColorScheme], which exists so that Moshi's keep rule
 * matches the enum and R8 leaves its constant field names alone. [CommHubColorScheme] has the
 * detail.
 *
 * Worth a test of its own because nothing else notices the annotation going missing: every other
 * test here passes without it and an unminified build is unaffected. It surfaces only as a startup
 * crash in a consumer's minified release build, which is how SDK-451 reached us.
 */
class CommHubColorSchemeKeepRuleTest {

    @Test
    fun `CommHubColorScheme is annotated so Moshi's enum keep rule matches it`() {
        val annotation = CommHubColorScheme::class.java.getAnnotation(JsonClass::class.java)

        assertNotNull(
            "CommHubColorScheme must keep @JsonClass. Without it R8 renames AUTO/LIGHT/DARK and " +
                "Rover.initialize throws AssertionError in a consumer's minified build. See SDK-451.",
            annotation
        )
        assertEquals(
            "generateAdapter must stay false: the annotation is here for the keep rule, and Moshi " +
                "generates no adapter for an enum.",
            false,
            annotation!!.generateAdapter
        )
    }
}
