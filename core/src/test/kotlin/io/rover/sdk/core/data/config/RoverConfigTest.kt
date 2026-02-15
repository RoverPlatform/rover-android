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

import com.squareup.moshi.Moshi
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RoverConfigTest {

    private lateinit var moshi: Moshi
    private lateinit var adapter: com.squareup.moshi.JsonAdapter<RoverConfig>

    @Before
    fun setUp() {
        moshi = Moshi.Builder().build()
        adapter = moshi.adapter(RoverConfig::class.java)
    }

    // MARK: - Deserialization Tests

    @Test
    fun `deserialize new backend structure with all properties`() {
        val json = """
        {
          "hub": {
            "isHomeEnabled": true,
            "isInboxEnabled": false,
            "isSettingsViewEnabled": true,
            "deepLink": "rover://hub"
          },
          "colorScheme": "dark",
          "accentColor": "#FF0000"
        }
        """.trimIndent()

        val config = adapter.fromJson(json)

        assertNotNull(config)
        assertTrue(config!!.hub.isHomeEnabled)
        assertFalse(config.hub.isInboxEnabled)
        assertTrue(config.hub.isSettingsViewEnabled)
        assertEquals("rover://hub", config.hub.deepLink)
        assertEquals(CommHubColorScheme.DARK, config.colorScheme)
        assertEquals("#FF0000", config.accentColor)
    }

    @Test
    fun `deserialize new backend structure with default values`() {
        val json = """
        {
          "hub": {},
          "colorScheme": "light",
          "accentColor": null
        }
        """.trimIndent()

        val config = adapter.fromJson(json)

        assertNotNull(config)
        assertFalse(config!!.hub.isHomeEnabled)
        assertTrue(config.hub.isInboxEnabled)
        assertFalse(config.hub.isSettingsViewEnabled)
        assertNull(config.hub.deepLink)
        assertEquals(CommHubColorScheme.LIGHT, config.colorScheme)
        assertNull(config.accentColor)
    }

    @Test
    fun `deserialize with missing hub object uses defaults`() {
        val json = """
        {
          "colorScheme": "auto",
          "accentColor": "#00FF00"
        }
        """.trimIndent()

        val config = adapter.fromJson(json)

        assertNotNull(config)
        assertFalse(config!!.hub.isHomeEnabled)
        assertTrue(config.hub.isInboxEnabled)
        assertFalse(config.hub.isSettingsViewEnabled)
        assertNull(config.hub.deepLink)
        assertEquals(CommHubColorScheme.AUTO, config.colorScheme)
        assertEquals("#00FF00", config.accentColor)
    }

    @Test
    fun `deserialize with all null values`() {
        val json = """
        {
          "hub": {
            "deepLink": null
          },
          "colorScheme": null,
          "accentColor": null
        }
        """.trimIndent()

        val config = adapter.fromJson(json)

        assertNotNull(config)
        assertNull(config!!.hub.deepLink)
        assertNull(config.colorScheme)
        assertNull(config.accentColor)
    }

    @Test
    fun `deserialize empty JSON uses all defaults`() {
        val json = "{}"

        val config = adapter.fromJson(json)

        assertNotNull(config)
        assertFalse(config!!.hub.isHomeEnabled)
        assertTrue(config.hub.isInboxEnabled)
        assertFalse(config.hub.isSettingsViewEnabled)
        assertNull(config.hub.deepLink)
        assertNull(config.colorScheme)
        assertNull(config.accentColor)
    }

    @Test
    fun `deserialize with auto color scheme`() {
        val json = """
        {
          "hub": {
            "isHomeEnabled": true,
            "isInboxEnabled": true,
            "isSettingsViewEnabled": false,
            "deepLink": null
          },
          "colorScheme": "auto",
          "accentColor": "#0000FF"
        }
        """.trimIndent()

        val config = adapter.fromJson(json)

        assertNotNull(config)
        assertEquals(CommHubColorScheme.AUTO, config!!.colorScheme)
    }

    // MARK: - Serialization Tests

    @Test
    fun `serialize RoverConfig with all properties`() {
        val hubConfig = HubConfig(
            isHomeEnabled = true,
            isInboxEnabled = false,
            isSettingsViewEnabled = true,
            deepLink = "rover://hub"
        )
        val config = RoverConfig(
            hub = hubConfig,
            colorScheme = CommHubColorScheme.DARK,
            accentColor = "#FF0000"
        )

        val json = adapter.toJson(config)

        assertNotNull(json)
        assertTrue(json.contains("\"hub\""))
        assertTrue(json.contains("\"isHomeEnabled\":true"))
        assertTrue(json.contains("\"isInboxEnabled\":false"))
        assertTrue(json.contains("\"isSettingsViewEnabled\":true"))
        assertTrue(json.contains("\"deepLink\":\"rover://hub\""))
        assertTrue(json.contains("\"colorScheme\":\"dark\""))
        assertTrue(json.contains("\"accentColor\":\"#FF0000\""))
    }

    @Test
    fun `serialize RoverConfig with defaults`() {
        val config = RoverConfig()

        val json = adapter.toJson(config)

        assertNotNull(json)
        assertTrue(json.contains("\"hub\""))
        assertTrue(json.contains("\"isHomeEnabled\":false"))
        assertTrue(json.contains("\"isInboxEnabled\":true"))
        assertTrue(json.contains("\"isSettingsViewEnabled\":false"))
    }

    // MARK: - Roundtrip Tests

    @Test
    fun `serialization roundtrip preserves all data`() {
        val originalHub = HubConfig(
            isHomeEnabled = true,
            isInboxEnabled = false,
            isSettingsViewEnabled = true,
            deepLink = "rover://hub"
        )
        val original = RoverConfig(
            hub = originalHub,
            colorScheme = CommHubColorScheme.DARK,
            accentColor = "#FF0000"
        )

        val json = adapter.toJson(original)
        val deserialized = adapter.fromJson(json)

        assertNotNull(deserialized)
        assertEquals(original.hub.isHomeEnabled, deserialized!!.hub.isHomeEnabled)
        assertEquals(original.hub.isInboxEnabled, deserialized.hub.isInboxEnabled)
        assertEquals(original.hub.isSettingsViewEnabled, deserialized.hub.isSettingsViewEnabled)
        assertEquals(original.hub.deepLink, deserialized.hub.deepLink)
        assertEquals(original.colorScheme, deserialized.colorScheme)
        assertEquals(original.accentColor, deserialized.accentColor)
    }

    @Test
    fun `serialization roundtrip with defaults`() {
        val original = RoverConfig()

        val json = adapter.toJson(original)
        val deserialized = adapter.fromJson(json)

        assertNotNull(deserialized)
        assertEquals(original.hub.isHomeEnabled, deserialized!!.hub.isHomeEnabled)
        assertEquals(original.hub.isInboxEnabled, deserialized.hub.isInboxEnabled)
        assertEquals(original.hub.isSettingsViewEnabled, deserialized.hub.isSettingsViewEnabled)
        assertEquals(original.hub.deepLink, deserialized.hub.deepLink)
        assertEquals(original.colorScheme, deserialized.colorScheme)
        assertEquals(original.accentColor, deserialized.accentColor)
    }

    // MARK: - HubConfig Tests

    @Test
    fun `HubConfig has correct default values`() {
        val hubConfig = HubConfig()

        assertFalse(hubConfig.isHomeEnabled)
        assertTrue(hubConfig.isInboxEnabled)
        assertFalse(hubConfig.isSettingsViewEnabled)
        assertNull(hubConfig.deepLink)
    }

    @Test
    fun `HubConfig can be created with custom values`() {
        val hubConfig = HubConfig(
            isHomeEnabled = true,
            isInboxEnabled = false,
            isSettingsViewEnabled = true,
            deepLink = "custom://link"
        )

        assertTrue(hubConfig.isHomeEnabled)
        assertFalse(hubConfig.isInboxEnabled)
        assertTrue(hubConfig.isSettingsViewEnabled)
        assertEquals("custom://link", hubConfig.deepLink)
    }
}
