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

package io.rover.sdk.experiences.classic.blocks.image

import android.view.View
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatImageView
import androidx.lifecycle.LifecycleOwner
import io.rover.sdk.core.logging.log
import io.rover.sdk.core.streams.androidLifecycleDispose
import io.rover.sdk.core.streams.subscribe
import io.rover.sdk.experiences.classic.concerns.MeasuredBindableView
import io.rover.sdk.experiences.classic.concerns.ViewModelBinding

/**
 * Mixin that binds an image block view model to the relevant parts of an [ImageView].
 */
internal class ViewImage(
    private val imageView: AppCompatImageView
) : ViewImageInterface {
    private val shortAnimationDuration = imageView.resources.getInteger(
        android.R.integer.config_shortAnimTime
    )

    init {
        imageView.scaleType = ImageView.ScaleType.FIT_XY
    }

    override var viewModelBinding: MeasuredBindableView.Binding<ImageBlockViewModelInterface>? by ViewModelBinding { binding, subscriptionCallback ->
        if (binding != null) {
            val measuredSize = binding.measuredSize ?: throw RuntimeException("ViewImage may only be used with a view model binding including a measured size (ie. used within a Rover screen layout).")

            with(imageView) {
                // cancel any in-flight fade animation from a prior bind of this recycled view:
                animate().cancel()
                // ensure a recycled view can never show a stale image from a prior bind:
                setImageDrawable(null)
                // Fail open: remain visible at the block's opacity rather than transparent, so
                // that a missed image update can never leave the view invisible.  The fade-in
                // effect below is purely cosmetic.
                alpha = binding.viewModel.opacity
                val isAccessible = binding.viewModel.isClickable || !binding.viewModel.isDecorative
                // We do not want to set the image as focusable, because this causes undesirable
                // behaviour for users not using TalkBack/a11y technology: they'd have to click
                // the image twice if it has a click action on it.  importantForAccessibility
                // is all we need to have the image be announced by TalkBack.
                importantForAccessibility = if (isAccessible) {
                    contentDescription = binding.viewModel.accessibilityLabel
                    View.IMPORTANT_FOR_ACCESSIBILITY_YES
                } else {
                    View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }
            }

            // we need to know the laid out dimensions of the view in order to ask the view
            // model for an optimized version of the image suited to the view's size.

            // Note: this subscription must NOT be disposed on view detach
            // (androidLifecycleDispose(view)): RecyclerView detaches this view when it is
            // recycled into its cache, and can later return it to the screen without rebinding
            // it.  The subscription has to survive that detach so an image arriving while the
            // view is cached still reaches it.  [ViewModelBinding] cancels this subscription
            // (via subscriptionCallback) whenever the view is rebound, and as a backstop it is
            // disposed when the host lifecycle is destroyed so it cannot outlive the activity.
            val lifecycleOwner = imageView.context as? LifecycleOwner
            (
                if (lifecycleOwner != null) {
                    binding.viewModel.imageUpdates.androidLifecycleDispose(lifecycleOwner)
                } else {
                    binding.viewModel.imageUpdates
                }
                ).subscribe({ imageUpdate ->
                imageView.setImageBitmap(imageUpdate.bitmap)
                if (imageUpdate.fadeIn && imageView.isAttachedToWindow) {
                    imageView.alpha = 0f
                    imageView.animate()
                        // TODO: we should not have to peek the surrounding ImageBlockViewModel interface to discover opacity.
                        .alpha(binding.viewModel.opacity)
                        .setDuration(shortAnimationDuration.toLong())
                        .start()
                } else {
                    imageView.alpha = binding.viewModel.opacity
                }
            }, { error -> log.w("Problem in image update stream: $error, ignoring.") }, { subscriptionCallback(it) })

            binding.viewModel.informDimensions(measuredSize)
        }
    }
}
