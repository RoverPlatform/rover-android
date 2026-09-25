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

package io.rover.sdk.experiences.rich.compose.model

import android.content.Context
import android.util.Log
import io.rover.sdk.core.logging.log
import io.rover.sdk.experiences.rich.compose.data.JsonParser
import io.rover.sdk.experiences.rich.compose.data.LocalFolder
import io.rover.sdk.experiences.rich.compose.model.values.ExperienceModel
import okio.ByteString.Companion.readByteString
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

internal fun ExperienceModel.Companion.fromZipStream(context: Context, zipInputStream: ZipInputStream): ExperienceModel {
    var zipEntry: ZipEntry? = zipInputStream.nextEntry
    val imageCache = LocalFolder.getImagesCacheFolder(context)
    val mediaCache = LocalFolder.getMediaCacheFolder(context)
    val fontCache = LocalFolder.getFontsCacheFolder(context)

    var experience: ExperienceModel? = null

    while (zipEntry != null) {
        Log.println(
            Log.DEBUG,
            "ExperienceModel",
            "zipEntry.name: ${zipEntry.name}"
        )

        // temporary solution to test whether or not things are deserialized correctly
        if (zipEntry.name == "document.json") {
            val documentContents =
                zipInputStream.readByteString(zipEntry.size.toInt()).utf8()

            experience = io.rover.sdk.experiences.rich.compose.data.JsonParser.parseExperience(documentContents)
            experience!!.buildTreeAndRelationships()

            Log.println(
                Log.DEBUG,
                "ExperienceViewModel",
                "Loaded Experience nodes, initial screen ID is: ${experience.initialScreenID}"
            )
        }

        if (zipEntry.name.startsWith("images/") && imageCache != null) {
            try {
                zipInputStream.extractCurrentEntryTo(imageCache, zipEntry.name.removePrefix("images/"))
            } catch (e: Exception) {
                log.e("Failed to read embedded image ${zipEntry.name} and save it into cache: $e")
            }
        }

        if (zipEntry.name.startsWith("media/") && mediaCache != null) {
            try {
                zipInputStream.extractCurrentEntryTo(mediaCache, zipEntry.name.removePrefix("media/"))
            } catch (e: Exception) {
                log.e("Failed to read embedded media ${zipEntry.name} and save it into cache: $e")
            }
        }

        if (zipEntry.name.startsWith("fonts/") && fontCache != null) {
            try {
                zipInputStream.extractCurrentEntryTo(fontCache, zipEntry.name.removePrefix("fonts/"))
            } catch (e: Exception) {
                log.e("Failed to read embedded font ${zipEntry.name} and save it into cache: $e")
            }
        }

        zipEntry = zipInputStream.nextEntry
    }

    return experience!!
}

/**
 * Copies the current zip entry into [folder] under [name].
 *
 * The bytes go to a temporary sibling first and are renamed into place once complete, because
 * these cache folders are shared with the remote asset loaders, which trust any file they find
 * under a content-addressed name. A reader must never observe a half-written asset.
 *
 * Entry names come from the archive, so a name that resolves outside [folder] is refused.
 */
internal fun ZipInputStream.extractCurrentEntryTo(folder: File, name: String) {
    val target = File(folder, name)
    if (target.canonicalFile.parentFile != folder.canonicalFile) {
        throw java.io.IOException("Refusing to unpack asset outside its cache folder: $name")
    }
    val tempFile = File.createTempFile("rover-unpack-", ".tmp", folder)
    try {
        BufferedOutputStream(FileOutputStream(tempFile)).use { output ->
            val buffer = ByteArray(8 * 1024)
            var read = read(buffer)
            while (read != -1) {
                output.write(buffer, 0, read)
                read = read(buffer)
            }
        }
        if (!tempFile.renameTo(target)) {
            throw java.io.IOException("Could not move unpacked asset into place at $target")
        }
    } finally {
        if (tempFile.exists()) {
            tempFile.delete()
        }
    }
}
