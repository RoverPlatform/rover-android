/*
 * Copyright 2009 ZXing authors
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

package io.rover.shaded.zxing.com.google.zxing.multi;

import io.rover.shaded.zxing.com.google.zxing.BinaryBitmap;
import io.rover.shaded.zxing.com.google.zxing.DecodeHintType;
import io.rover.shaded.zxing.com.google.zxing.NotFoundException;
import io.rover.shaded.zxing.com.google.zxing.Result;

import java.util.Map;

/**
 * Implementation of this interface attempt to read several barcodes from one image.
 *
 * @see io.rover.shaded.zxing.com.google.zxing.Reader
 * @author Sean Owen
 */
public interface MultipleBarcodeReader {

  Result[] decodeMultiple(BinaryBitmap image) throws NotFoundException;

  Result[] decodeMultiple(BinaryBitmap image,
                          Map<DecodeHintType,?> hints) throws NotFoundException;

}
