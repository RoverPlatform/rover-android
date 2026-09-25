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

package io.rover.sdk.core.testing

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.annotation.RestrictTo
import io.rover.sdk.core.logging.log
import okhttp3.Interceptor

/**
 * The single seam through which a test harness may serve the SDK's HTTP traffic in-process (SDK-345).
 *
 * One member, on purpose. An earlier version also re-exported the `SharedPreferences` names and keys
 * behind `ConfigManager`'s and `HomeViewManager`'s caches, so that a harness priming those caches
 * could not name a different file than the SDK reads. Nothing primes them any more — the Bench
 * settings override layer pins the same facts before `Rover.initialize`, from the front door — so the
 * hook is back to the one thing it is for, and the managers' storage constants are private again.
 *
 * The Rover test bench installs an [Interceptor] here so its App Screens UI regression suite runs
 * fully offline against the *real production URLs*: every response the SDK sees comes from baked
 * fixtures served in-process, and no proxy or external harness is involved. (Offline for **these four
 * clients**, which is all this hook can speak for — the process at large is not silenced, and the
 * suite proves its own hermeticity separately, per-request, in
 * `AppScreensMockUITestCase.assertNothingEscapedToLiveNetwork`.) Every `OkHttpClient` the SDK builds
 * for Engage and App Screens traffic consults this object at construction time:
 *
 * - `io.rover.sdk.core.data.config.EngageHttpClient`
 * - `io.rover.sdk.notifications.communicationhub.data.network.EngageHttpClient`
 * - `io.rover.sdk.experiences.appscreens.network.AppScreensDocumentClient`
 * - `io.rover.sdk.experiences.appscreens.network.AppScreensDataClient`
 *
 * Each installs [interceptor] as its **last application interceptor** — `addInterceptor`, never
 * `addNetworkInterceptor`. Two properties follow from that and both are load-bearing:
 *
 * 1. An application interceptor runs *before* DNS resolution, connection establishment, TLS and the
 *    OkHttp cache, so a stub answer means nothing ever leaves the device. A network interceptor
 *    would only run once a real connection to (e.g.) `testbench.rover.io` had already been made.
 * 2. Being added last, it observes the fully-decorated request — `User-Agent`,
 *    `x-rover-account-token`, the fan `Authorization: Bearer` header, and the
 *    `deviceIdentifier`/`userID` query parameters — so a harness can assert on what the SDK would
 *    actually have sent, and distinguish personalized from anonymous requests.
 *
 * **This MUST remain `null` in production.** Nothing in the SDK ever assigns to it; the only writer
 * is a test harness, gated on an instrumentation argument. With [interceptor] null the clients are
 * built exactly as they were before this hook existed, so there is no behavioural difference for
 * shipping apps.
 *
 * This is NOT part of the supported public SDK API surface: it is annotated [RestrictTo] with
 * [LIBRARY_GROUP][RestrictTo.Scope.LIBRARY_GROUP], the same convention the other cross-module Rover
 * hooks use (`AppScreens`, `Rover.refreshRoverConfig`).
 *
 * ## The seam is inert in a release app, and that is enforced rather than asked for
 *
 * `@RestrictTo` is a lint annotation: it stops a build that opts into Rover's lint, and nothing else.
 * This object ships in the published AAR, so the type is nameable from any app that depends on the
 * SDK, and the thing it hands out is the fully-decorated request — `x-rover-account-token` and, on
 * personalized traffic, the fan `Authorization: Bearer` header. A `@RestrictTo` and a doc comment
 * saying "production never sets it" are not a control, so the *effect* is gated instead of the
 * assignment: the clients call [interceptorFor], which returns the installed interceptor **only when
 * the host application is debuggable** ([ApplicationInfo.FLAG_DEBUGGABLE]). In a release build the
 * assignment still succeeds and still does nothing, which is the property worth having — a library
 * co-resident in an integrator's shipping app cannot use this to read Rover traffic.
 *
 * The honest limits of that: it is not a defence against an attacker who already runs code in the
 * process (reflection reaches OkHttp's internals, the SDK's own storage and the account token in the
 * app's manifest regardless — this hook grants such an attacker convenience, not capability), and it
 * does not protect a debug build, which is the point of a debug build. What it does do is make the
 * shipping configuration inert by construction rather than by convention.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public object RoverNetworkTestHook {
    /**
     * The installed interceptor, or `null` (the production state).
     *
     * Assign this before `Rover.initialize` runs: the clients read it when they are lazily
     * constructed, so an interceptor installed after a client has already been built will not be
     * seen by that client. `@Volatile` because it is written from the instrumentation thread and
     * read from whichever thread first builds each client.
     *
     * Note that a non-null value here is *not* on its own enough to serve traffic — see
     * [interceptorFor], which is what the clients actually consult.
     */
    @Volatile
    public var interceptor: Interceptor? = null

    /**
     * The interceptor the SDK's OkHttp clients should install, or `null` for unmodified behaviour.
     *
     * Answers `null` unless a harness has installed one **and** [context]'s application is
     * debuggable, so an interceptor assigned inside a release app is ignored (loudly — the refusal
     * logs, because a silently inert hook would be a mystery to whoever assigned it).
     *
     * @param context any context from the host app; only its [Context.getApplicationInfo] flags are read.
     */
    public fun interceptorFor(context: Context): Interceptor? {
        val installed = interceptor ?: return null
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) {
            log.w(
                "RoverNetworkTestHook.interceptor is set, but this application is not debuggable: " +
                    "ignoring it. This seam exists for the Rover test bench's own instrumented suite " +
                    "and is inert in release builds by design.",
            )
            return null
        }
        return installed
    }
}
