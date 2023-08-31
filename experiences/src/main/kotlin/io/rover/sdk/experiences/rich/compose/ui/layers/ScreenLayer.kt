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
import android.util.Size
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.LayoutModifier
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import io.rover.experiences.R
import io.rover.sdk.core.Rover
import io.rover.sdk.core.tracking.ConversionsTrackerService
import io.rover.sdk.experiences.rich.compose.model.nodes.*
import io.rover.sdk.experiences.rich.compose.model.values.*
import io.rover.sdk.experiences.rich.compose.ui.Environment
import io.rover.sdk.experiences.rich.compose.ui.Services
import io.rover.sdk.experiences.rich.compose.ui.layers.stacks.ZStackLayer
import io.rover.sdk.experiences.rich.compose.ui.layout.experiencesHorizontalFlex
import io.rover.sdk.experiences.rich.compose.ui.layout.experiencesVerticalFlex
import io.rover.sdk.experiences.rich.compose.ui.layout.fallbackMeasure
import io.rover.sdk.experiences.rich.compose.ui.layout.mapMaxIntrinsicWidthAsMeasure
import io.rover.sdk.experiences.rich.compose.ui.layout.mapMinIntrinsicAsFlex
import io.rover.sdk.experiences.rich.compose.ui.modifiers.ActionModifier
import io.rover.sdk.experiences.rich.compose.ui.modifiers.experiencesFrame
import io.rover.sdk.experiences.rich.compose.ui.utils.rememberSystemBarController
import io.rover.sdk.experiences.rich.compose.ui.values.getComposeColor
import io.rover.sdk.experiences.services.ExperienceScreenViewed

/**
 * Starting layer for a [Screen]. This is the starting point of the experience and any further screens accessible through navigation.
 *
 * It offers up a [Surface] that fills the max size and starts creating the [Screen]'s children.
 * This is necessary as we can only place children inside its parents layout size. So while we want everything to be measured correctly,
 * we also need to have the option to place things outside the parent layout size, such as when a layer is offset.
 * The surface filling the whole screen achieves exactly that for its children (the whole Experience, basically).
 */
@SuppressLint("UnusedMaterialScaffoldPaddingParameter")
@Composable
internal fun ScreenLayer(node: Screen, appearance: Appearance) {
    val systemBarController = rememberSystemBarController()

    val isDarkTheme = Environment.LocalIsDarkTheme.current
    val localData = Environment.LocalData.current
    val urlParameters = Environment.LocalUrlParameters.current
    val experienceId = Environment.LocalExperienceId.current
    val experienceName = Environment.LocalExperienceName.current

    val backgroundColor = remember { node.androidStatusBarBackgroundColor.getComposeColor(isDarkTheme) }

    LaunchedEffect(true) {
        systemBarController.setStatusBarColor(backgroundColor)
        systemBarController.setStatusBarIconTint(node.androidStatusBarStyle, appearance)
    }

    Services.Inject { services ->
        LaunchedEffect(true) {
            val conversionTrackerService = Rover.shared.resolve(ConversionsTrackerService::class.java)
            conversionTrackerService?.trackConversions(node.conversionTags)

            services.eventEmitter.emit(
                ExperienceScreenViewed(
                    experienceName,
                    experienceId,
                    node.name,
                    node.id,
                    node.metadata?.tags?.toList() ?: emptyList(),
                    node.metadata?.propertiesMap ?: emptyMap(),
                    localData,
                    urlParameters,
                    urlParameters["campaignID"]
                )
            )
        }

        CompositionLocalProvider(
            Environment.LocalScreen provides node
        ) {
            Scaffold(
                topBar = {
                    (node.children.firstOrNull { it is AppBar } as? AppBar)?.let {
                        ExperiencesAppBar(
                            appBar = it,
                            buttonColor = remember { it.buttonColor.getComposeColor(isDarkTheme) },
                            backgroundColor = remember { it.backgroundColor.getComposeColor(isDarkTheme) },
                            screenBackground = backgroundColor
                        )
                    }
                }
            ) {
                Surface(color = node.backgroundColor.getComposeColor()) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        ZStackLayer {
                            // So, for historical reasons the Mac editor has the following
                            // frame modifier on each of the screen layer's children:
                            //   .frame(width: geometry.size.width, height: geometry.size.height, alignment: .center)
                            //   (where geometry.size is just the fixed size of the screen).

                            // This has the effect of inhibiting ZStack's "adopt size of largest
                            // child" behaviour, which we need to emulate here, which is done
                            // by FixedToProposedSize().
                            Children(children = node.children, modifier = FixedToProposedSize())
                        }
                    }
                }
            }
        }
    }
}


/**
 * This layout modifier adopts the size proposed to it by the parent.
 *
 * It will place its child centered in that space. It will not grow to handle an oversized child.
 *
 * This purpose of this modifier is to defeat ZStack's behaviour of adopting the size of
 * any oversized children and proposing that size to any siblings.
 *
 * This is needed to emulate the behaviour of how Screens are set up in the iOS SDK/Mac editor,
 * where (roughly) the following setup is used:
 *
 * ```swift
 * GeometryReader { geometry in
 *   ZStack {
 *     allChildren
 *        // this frame modifier is applied to each of the children. FixedToProposedSize is meant
 *        // to emulate this.
 *       .frame(width: geometry.size.width, height: geometry.size.height, alignment: .center)
 *   }
 * }
 * ```
 *
 */
private class FixedToProposedSize(): LayoutModifier {
    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints
    ): MeasureResult {
        val placeable = measurable.measure(constraints)

        // centering neutralization - if a measurable comes up with a measured size greater
        // than the constraints it was measured with, then Compose "helpfully" attempts to center
        // it. We don't want that behaviour, so these terms neutralize it.
        val centeringCompX = (placeable.width - placeable.measuredWidth) / 2
        val centeringCompY = (placeable.height - placeable.measuredHeight) / 2

        // Note: I know it seems weird that we neutralize Compose's centering and go ahead
        // and center it ourselves, but Compose's behaviour only centers if oversized. We want it
        // *always* centered.

        return layout(constraints.maxWidth, constraints.maxHeight) {
            placeable.place(
                x = ((constraints.maxWidth - placeable.measuredWidth) / 2) - centeringCompX,
                y = ((constraints.maxHeight - placeable.measuredHeight) / 2) - centeringCompY
            )
        }
    }

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(
        measurable: IntrinsicMeasurable,
        height: Int
    ): Int {
        return mapMaxIntrinsicWidthAsMeasure(height) { proposedSize ->
            // child size, clamped to proposed size
            val childSize = measurable.fallbackMeasure(proposedSize)
            Size(
                childSize.width.coerceAtMost(proposedSize.width),
                childSize.height.coerceAtMost(proposedSize.height)
            )
        }
    }

    // and pass through flex:

    override fun IntrinsicMeasureScope.minIntrinsicWidth(
        measurable: IntrinsicMeasurable,
        height: Int
    ): Int {
        return mapMinIntrinsicAsFlex {
            measurable.experiencesHorizontalFlex()
        }
    }

    override fun IntrinsicMeasureScope.minIntrinsicHeight(
        measurable: IntrinsicMeasurable,
        width: Int
    ): Int {
        return mapMinIntrinsicAsFlex {
            measurable.experiencesVerticalFlex()
        }
    }

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(
        measurable: IntrinsicMeasurable,
        width: Int
    ): Int {
        throw IllegalStateException("maxIntrinsicHeight not supported on Experiences measurables")
    }
}

@Composable
private fun ExperiencesAppBar(
    appBar: AppBar,
    buttonColor: Color,
    backgroundColor: Color,
    screenBackground: Color
) {
    val stringTable = Environment.LocalExperienceModel.current?.localizations

    TopAppBar(
        title = {
            TextLayer(
                text = stringTable?.resolve(appBar.title) ?: appBar.title,
                textColor = appBar.titleColor,
                font = appBar.titleFont,
                lineLimit = 1
            )
        },
        elevation = 0.dp,
        navigationIcon = {
            if (!appBar.hideUpIcon) {
                // Only show back button if the current screen has another under it in the stack.
                val navigateUpAction = Environment.LocalNavigateUp.current

                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = stringResource(R.string.rover_screen_layer_navigation_back_button),
                    tint = buttonColor,
                    modifier = Modifier.clickable { navigateUpAction?.invoke() }
                )
            }
        },
        backgroundColor = backgroundColor,
        actions = {
            val itemsOverflow = appBar.children.size > 3
            val itemsFilteredByPriority = appBar.children.sortedBy { node ->
                when ((node as MenuItem).showAsAction) {
                    MenuItemVisibility.ALWAYS -> 0
                    MenuItemVisibility.IF_ROOM -> 1
                    MenuItemVisibility.NEVER -> 2
                }
            }

            if (itemsOverflow) {
                ActionItem(itemsFilteredByPriority[0] as MenuItem, appBar.buttonColor)
                ActionItem(itemsFilteredByPriority[1] as MenuItem, appBar.buttonColor)
                DropdownActionItem(itemsFilteredByPriority, appBar.buttonColor)
            } else {
                itemsFilteredByPriority.forEach { item ->
                    ActionItem(item as MenuItem, appBar.buttonColor)
                }
            }
        },
        // The color "beneath" the app bar should be the same as the screen.
        modifier = Modifier.background(color = screenBackground)
    )
}

@Composable
private fun DropdownActionItem(children: List<Node>, buttonColor: ColorReference) {
    var showingOverflowDropdown by remember { mutableStateOf(false) }

    ActionItem(
        item = MenuItem(
            id = "",
            title = stringResource(id = R.string.rover_screen_layer_appbar_dropdown_title),
            showAsAction = MenuItemVisibility.ALWAYS,
            iconMaterialName = "more_vert"
        ),
        buttonColor = buttonColor,
        onClick = { showingOverflowDropdown = !showingOverflowDropdown }
    )

    DropdownMenu(
        expanded = showingOverflowDropdown,
        onDismissRequest = { showingOverflowDropdown = false }
    ) {
        children.subList(fromIndex = 2, toIndex = children.size).forEach { dropDownItem ->
            val menuItem = dropDownItem as MenuItem

            ActionModifier(action = menuItem.action, modifier = Modifier) {
                DropdownMenuItem(onClick = { }) {
                    Text(menuItem.title)
                }
            }
        }
    }
}

@Composable
private fun ActionItem(item: MenuItem, buttonColor: ColorReference, onClick: (() -> Unit)? = null) {
    IconButton(onClick = { onClick?.invoke() }) {
        val iconNode = Icon(
            id = "",
            icon = NamedIcon(item.iconMaterialName),
            color = buttonColor,
            pointSize = 24,
            action = item.action
        )

        IconLayer(iconNode)
    }
}

@Preview
@Composable
fun AppBarSimple() {
    val appBar = AppBar(
        id = "test_appBar",
        hideUpIcon = false,
        buttonColor = ColorReference.SystemColor(colorName = "black"),
        title = "Rover Appbar!",
        titleColor = ColorReference.SystemColor(colorName = "black"),
        titleFont = Font.Document("", ""),
        backgroundColor = ColorReference.SystemColor(colorName = "white")
    )
    ExperiencesAppBar(appBar = appBar, screenBackground = Color.Transparent, buttonColor = Color.Black, backgroundColor = Color.Black)
}
