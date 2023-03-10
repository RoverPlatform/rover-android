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

package io.rover.sdk.experiences

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import io.rover.sdk.core.logging.log

/**
 * Embed this fragment to display a Rover Experience embedded within your own UI.
 *
 * To specify which Experience should be loaded, provide an arguments bundle to the fragment
 * with a `url` property.
 *
 * Note that arguments must be provided before the fragment is asked to yield its UI.
 */
public class ExperienceFragment() : Fragment() {
    constructor(url: String) : this() {
        arguments = Bundle().apply {
            putString("url", url)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val urlString = arguments?.getString("url")
        val uri = urlString?.let { Uri.parse(it) }

        if (uri == null) {
            log.w("ExperienceFragment was created without a URL to load. Displaying blank.")
        }

        return ComposeView(requireContext()).apply {
            setContent {
                if (uri != null) {
                    Experience(url = uri)
                }
            }
        }
    }
}
