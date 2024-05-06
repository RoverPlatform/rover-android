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

package io.rover.sdk.experiences.rich.compose.ui.layers

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.util.AttributeSet
import android.view.View.FOCUSABLE
import android.view.View.LAYER_TYPE_HARDWARE
import android.view.ViewGroup
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import io.rover.sdk.experiences.rich.compose.model.nodes.WebViewSource
import io.rover.sdk.experiences.rich.compose.model.nodes.interpolatedSource
import io.rover.sdk.experiences.rich.compose.ui.Environment
import io.rover.sdk.experiences.rich.compose.ui.data.Interpolator
import io.rover.sdk.experiences.rich.compose.ui.data.makeDataContext
import io.rover.sdk.experiences.rich.compose.ui.modifiers.LayerModifiers
import io.rover.sdk.experiences.rich.compose.ui.layout.ExpandMeasurePolicy

@Composable
internal fun WebViewLayer(node: io.rover.sdk.experiences.rich.compose.model.nodes.WebView, modifier: Modifier = Modifier) {
    WebViewLayer(
        source = node.source,
        isScrollEnabled = node.isScrollEnabled,
        layerModifiers = LayerModifiers(node),
        modifier = modifier
    )
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun WebViewLayer(
    source: WebViewSource,
    isScrollEnabled: Boolean = true,
    layerModifiers: LayerModifiers = LayerModifiers(),
    modifier: Modifier = Modifier
) {
    val dataContext = makeDataContext(
        userInfo = Environment.LocalUserInfo.current?.invoke() ?: emptyMap(),
        urlParameters = Environment.LocalUrlParameters.current,
        deviceContext = Environment.LocalDeviceContext.current,
        data = Environment.LocalData.current
    )
    val interpolator = Interpolator(
        dataContext
    )
    val interpolatedSource = source.interpolatedSource(interpolator)

    interpolatedSource?.let { webSource ->
        ApplyLayerModifiers(layerModifiers, modifier) { modifier ->
            Layout({
                AndroidView(
                    factory = { context ->
                        ExperiencesWebView(context).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )

                            webViewClient = WebViewClient()
                        }
                    }
                ) { view ->
                    view.settings.javaScriptEnabled = true

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        view.forceHasOverlappingRendering(false)
                    }

                    view.setBackgroundColor(Color.TRANSPARENT)
                    view.setLayerType(LAYER_TYPE_HARDWARE, null)

                    when (webSource) {
                        is WebViewSource.URL -> {
                            view.loadUrl(webSource.value)
                        }

                        is WebViewSource.HTML -> {
                            view.loadDataWithBaseURL(
                                null,
                                webSource.value,
                                "text/html; charset=utf-8",
                                "UTF-8",
                                null
                            )
                        }
                    }

                    view.focusable = FOCUSABLE
                    view.isFocusableInTouchMode = true

                    view.scrollEnabled = isScrollEnabled
                    view.isVerticalScrollBarEnabled = isScrollEnabled
                    view.isHorizontalScrollBarEnabled = isScrollEnabled
                }
            }, modifier = modifier, measurePolicy = ExpandMeasurePolicy(expandChildren = false))
        }
    }
}

internal class ExperiencesWebView constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : android.webkit.WebView(context, attrs, defStyleAttr) {
    var scrollEnabled: Boolean = true

    override fun overScrollBy(
        deltaX: Int,
        deltaY: Int,
        scrollX: Int,
        scrollY: Int,
        scrollRangeX: Int,
        scrollRangeY: Int,
        maxOverScrollX: Int,
        maxOverScrollY: Int,
        isTouchEvent: Boolean
    ): Boolean {
        return if (!scrollEnabled) false else super.overScrollBy(
            deltaX,
            deltaY,
            scrollX,
            scrollY,
            scrollRangeX,
            scrollRangeY,
            maxOverScrollX,
            maxOverScrollY,
            isTouchEvent
        )
    }
}

@Preview
@Composable
private fun WebViewPreview() {
    WebViewLayer(source = WebViewSource.HTML("<h1>Rover rules</h1>"))
}
