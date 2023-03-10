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

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import io.rover.sdk.experiences.rich.compose.ui.Services

/**
 * Launch this activity with an Experience URL (either universal/app link or deep link)
 * to display an Experience.
 */
open class ExperienceActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val url = intent.data

        setContent {
            ClassicExperienceSettingsTheme {
                if (url != null) {
                    Experience(
                        url = url,
                        // providing this param makes Experience() composable
                        // present a modal dialog offering cancel to user if they enounter
                        // an error fetching experience, which calls back here
                        // and allows us to resign the activity.
                        onCanceled = {
                            this.finish()
                        }
                    )
                } else {
                    Text("No URL provided")
                }
            }
        }
    }

    companion object {
        fun makeIntent(context: Context, experienceLink: Uri): Intent {
            return Intent(Intent.ACTION_VIEW, experienceLink, context, ExperienceActivity::class.java)
        }
    }
}

/**
 * Synthesize a Material design theme using the [AppThemeDescription] provided to
 * [ExperiencesAssembler]. This is to enable the [ToolbarConfiguration.useExistingStyle] setting
 * to work when the experience composable is embedded in [ExperienceActivity]
 * or [ExperienceFragment].
 */
@Composable
private fun ClassicExperienceSettingsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable() () -> Unit
) {
    Services.Inject { services ->
        val colors = if (darkTheme) {
            lightColors(
                primary = Color(services.experiencesClassic.appThemeDescription.darkColors.primary),
                onPrimary = Color(services.experiencesClassic.appThemeDescription.darkColors.onPrimary)
            )
        } else {
            lightColors(
                primary = Color(services.experiencesClassic.appThemeDescription.lightColors.primary),
                onPrimary = Color(services.experiencesClassic.appThemeDescription.lightColors.onPrimary)
            )
        }

        MaterialTheme(
            colors = colors,
            content = content
        )
    }
}
