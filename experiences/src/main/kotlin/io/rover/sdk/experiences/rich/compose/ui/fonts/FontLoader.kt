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

package io.rover.sdk.experiences.rich.compose.ui.fonts

import android.content.Context
import android.graphics.Typeface
import io.rover.sdk.experiences.rich.compose.data.ExperiencesHttpClient
import io.rover.sdk.experiences.rich.compose.data.LocalFolder
import io.rover.sdk.experiences.rich.compose.model.values.DocumentFont
import io.rover.sdk.experiences.rich.compose.ui.AssetContext
import io.rover.sdk.core.logging.log
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Url
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

internal class FontLoader(
    private val context: Context,
    private val httpClient: ExperiencesHttpClient
) {
    interface ResourceAPI {
        @GET
        suspend fun getFile(
            @Url aURL: String
        ): Response<ResponseBody>
    }

    private val retrofit by lazy {
        Retrofit
            .Builder()
            // This base URL is usually not used, but is required by Retrofit.
            .baseUrl("https://api.rover.io/")
            .addConverterFactory(MoshiConverterFactory.create(io.rover.sdk.experiences.rich.compose.data.JsonParser.moshi))
            .client(httpClient.client)
            .build()
    }

    private val api: ResourceAPI =
        retrofit.create(ResourceAPI::class.java)

    data class TypeFaceMapping(
        val mapping: Map<String, Typeface> = hashMapOf()
    )

    suspend fun getTypefaceMappings(
        context: Context,
        assetContext: AssetContext,
        sources: List<DocumentFont.Source>
    ): TypeFaceMapping = withContext(Dispatchers.IO) {
        // Use ConcurrentHashMap to safely handle concurrent writes from parallel downloads
        val result = ConcurrentHashMap<String, Typeface>()

        // Documents frequently repeat the same asset across several font entries (every
        // entry listing its whole family), so group by asset and fetch each file only once.
        sources.groupBy { it.assetName }.values.map { sourcesForAsset ->
            async {
                val source = sourcesForAsset.first()
                runCatching {
                    val sourceUrl = assetContext.uriForFontSource(context, source)
                    if (sourceUrl == null) {
                        log.w("Asset context did not yield a source URL for font source $source")
                        return@async
                    }

                    val sourceScheme = sourceUrl.scheme?.lowercase() ?: return@async

                    val typeface = when (sourceScheme) {
                        "file", "content" -> loadLocalFont(sourceUrl)
                        else -> loadRemoteFont(sourceUrl, source.assetName, context)
                    } ?: return@async

                    // TODO we do not support TTC for now, so only one font name is expected.
                    // TODO: variable fonts (other than default face) also not supported.
                    sourcesForAsset.forEach { s ->
                        s.fontNames.firstOrNull()?.let { fontName -> result[fontName] = typeface }
                    }
                }.onFailure { exception ->
                    if (exception is CancellationException) {
                        throw exception
                    }
                    log.e("Failed to load font: $source: $exception")
                }
            }
        }.awaitAll()

        log.d("Fonts loaded: $result")
        TypeFaceMapping(result)
    }

    private fun loadLocalFont(
        sourceUrl: android.net.Uri
    ): Typeface? {
        val sourcePath = sourceUrl.path ?: return null
        return Typeface.createFromFile(File(sourcePath))
    }

    /**
     * Loads a remotely hosted font, keeping the downloaded file in the fonts cache folder so
     * later loads of the same asset are served from disk.
     *
     * Asset names are content hashes assigned by the authoring tool and the CDN serves them as
     * immutable, so a file that exists under that name is safe to reuse indefinitely.
     */
    private suspend fun loadRemoteFont(
        sourceUrl: android.net.Uri,
        assetName: String,
        context: Context
    ): Typeface? {
        val cacheFolder = LocalFolder.getFontsCacheFolder(context) ?: return null
        val cachedFile = File(cacheFolder, assetName)

        // Asset names come from the document, so refuse anything that resolves outside the folder.
        if (cachedFile.canonicalFile.parentFile != cacheFolder.canonicalFile) {
            log.w("Refusing font asset name that escapes the fonts cache folder: $assetName")
            return null
        }

        if (cachedFile.length() > 0) {
            runCatching { Typeface.createFromFile(cachedFile) }
                .onSuccess { return it }
                .onFailure { log.w("Cached font $assetName unreadable, re-downloading: $it") }
            cachedFile.delete()
        }

        val response = api.getFile(sourceUrl.toString())
        val body = response.body()?.bytes() ?: return null

        // Write to a temp file and rename so a concurrent loader never sees a partial font.
        val tempFile = File.createTempFile("rover-font-", ".tmp", cacheFolder)
        try {
            tempFile.writeBytes(body)
            if (!tempFile.renameTo(cachedFile) && cachedFile.length() == 0L) {
                // Rename failed and nobody else won the race; fall back to a one-off load.
                return Typeface.createFromFile(tempFile)
            }
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
        return Typeface.createFromFile(cachedFile)
    }
}
