/*
 * Copyright (c) 2026, Rover Labs, Inc. All rights reserved.
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

package io.rover.sdk.experiences.rich.compose.model

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.throws
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Assets unpacked from a .rover archive land in the same cache folders that [io.rover.sdk.experiences.rich.compose.ui.fonts.FontLoader]
 * now reads from and trusts by name, so the bytes written must be exactly the entry's bytes and
 * the final filename must never be observable half-written.
 */
class ZipEntryExtractionTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun zipWithEntry(name: String, bytes: ByteArray): ZipInputStream {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(name))
            zip.write(bytes)
            zip.closeEntry()
        }
        return ZipInputStream(ByteArrayInputStream(out.toByteArray()))
    }

    @Test
    fun `extracted file contains exactly the entry bytes`() {
        // 1500 is deliberately not a multiple of the copy buffer size.
        val bytes = ByteArray(1500) { (it % 251).toByte() }
        val zip = zipWithEntry("fonts/aaaa.ttf", bytes)
        zip.nextEntry
        val fonts = folder.newFolder("fonts")

        zip.extractCurrentEntryTo(fonts, "aaaa.ttf")

        assertThat(fonts.resolve("aaaa.ttf").readBytes().toList(), equalTo(bytes.toList()))
    }

    @Test
    fun `no temporary files are left beside the extracted file`() {
        val zip = zipWithEntry("fonts/aaaa.ttf", ByteArray(10))
        zip.nextEntry
        val fonts = folder.newFolder("fonts")

        zip.extractCurrentEntryTo(fonts, "aaaa.ttf")

        assertThat(fonts.list()!!.toList(), equalTo(listOf("aaaa.ttf")))
    }

    @Test
    fun `a failed extraction leaves an existing target untouched`() {
        val existing = "the good copy".toByteArray()
        val fonts = folder.newFolder("fonts")
        val target = fonts.resolve("aaaa.ttf").apply { writeBytes(existing) }

        // A zip truncated part way through its entry data fails while being read.
        val complete = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry("fonts/aaaa.ttf"))
                // Random data does not compress, so cutting the stream in half cuts entry data.
                zip.write(ByteArray(64 * 1024).also { java.util.Random(1).nextBytes(it) })
                zip.closeEntry()
            }
        }.toByteArray()
        val truncated = ZipInputStream(ByteArrayInputStream(complete.copyOf(complete.size / 2)))
        truncated.nextEntry

        assertThat({ truncated.extractCurrentEntryTo(fonts, "aaaa.ttf") }, throws<Exception>())

        assertThat(target.readBytes().toList(), equalTo(existing.toList()))
        assertThat(fonts.list()!!.toList(), equalTo(listOf("aaaa.ttf")))
    }

    @Test
    fun `a target outside its cache folder is refused`() {
        val fonts = folder.newFolder("fonts")
        val zip = zipWithEntry("fonts/../escaped.ttf", ByteArray(10))
        zip.nextEntry

        assertThat({ zip.extractCurrentEntryTo(fonts, "../escaped.ttf") }, throws<Exception>())

        assertThat(folder.root.list()!!.toList(), equalTo(listOf("fonts")))
        assertThat(fonts.list()!!.toList(), equalTo(emptyList()))
    }
}
