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

package io.rover.sdk.experiences.rich.compose.model.values

import androidx.compose.ui.text.intl.Locale

typealias StringKey = String
typealias LocaleIdentifier = String

typealias StringTable = Map<LocaleIdentifier, Map<StringKey, String>>

internal fun StringTable.resolve(key: StringKey): String {
    // A simple (and not complete) attempt at RFC 4647 basic filtering.
    val matchedLocale = this[Locale.current.toLanguageTag()]

    matchedLocale?.get(key)?.let { translation ->
        return translation
    }

    val languageCode = Locale.current.language

    val matchedLanguage = this.keys.find { languageEntry ->
        languageEntry == languageCode ||
            languageEntry.startsWith(languageCode + "-") ||
            languageEntry.startsWith(languageCode + "_")
    }

    return this[matchedLanguage]?.get(key) ?: key
}
