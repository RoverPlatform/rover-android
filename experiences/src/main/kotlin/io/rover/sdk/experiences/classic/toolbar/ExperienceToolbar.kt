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

package io.rover.sdk.experiences.classic.toolbar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Yields a material [TopAppBar] from [ToolbarConfiguration].
 *
 * Defaults to Jetpack Compose Material's theme settings if [ToolbarConfiguration.useExistingStyle]
 * is set.
 */
@Composable
internal fun ExperienceToolbar(
    toolbar: ToolbarConfiguration,
    onBackButtonPressed: () -> Unit,
    onCloseButtonPressed: () -> Unit
) {
    val backgroundColor = if (toolbar.useExistingStyle) MaterialTheme.colors.primarySurface else Color(toolbar.color)
    TopAppBar(
        title = {
            Text(
                toolbar.appBarText,
                color = if (toolbar.useExistingStyle) Color.Unspecified else Color(toolbar.textColor)
            )
        },
        backgroundColor = backgroundColor,
        contentColor = if (toolbar.useExistingStyle) contentColorFor(backgroundColor = backgroundColor) else Color(toolbar.buttonColor),
        navigationIcon = {
            if (toolbar.upButton) {
                IconButton(onClick = {
                    onBackButtonPressed()
                }) {
                    Icon(
                        Icons.Filled.ArrowBack,
                        contentDescription = null
                    )
                }
            }
        },
        actions = {
            if (toolbar.closeButton) {
                IconButton(onClick = {
                    onCloseButtonPressed()
                }) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = null
                    )
                }
            }
        },
        modifier = Modifier
            .background(color = backgroundColor)
            .windowInsetsPadding(WindowInsets.statusBars)
    )
}
