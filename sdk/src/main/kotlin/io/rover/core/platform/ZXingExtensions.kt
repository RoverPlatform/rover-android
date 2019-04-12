@file:JvmName("ZXingExtensions")

/*
 * Copyright (C) 2008 ZXing authors
 * Copyright (C) 2017 Rover Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.rover.core.platform

import android.graphics.Bitmap
import android.graphics.Color.BLACK
import android.graphics.Color.WHITE
import io.rover.shaded.zxing.com.google.zxing.common.BitMatrix

/**
 * Convert a [BitMatrix] bitmap from ZXing to an Android [Bitmap].
 */
fun BitMatrix.toAndroidBitmap(): Bitmap {
    // More or less obtained from zxing/client/android/encode/QRCodeEncoder.java
    val pixels = IntArray(width * height)
    for (y in 0 until height) {
        val offset = y * width
        for (x in 0 until width) {
            pixels[offset + x] = if (get(x, y)) BLACK else WHITE
        }
    }

    // TODO: do I really need a friggen 32-bit ARGB bitmap for this?  surely a 1-bit bitmap would be
    // better and smaller (and I already use them for masks elsewhere in the app)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    return bitmap
}