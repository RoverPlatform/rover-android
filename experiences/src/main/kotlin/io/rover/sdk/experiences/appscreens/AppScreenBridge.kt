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

package io.rover.sdk.experiences.appscreens

import android.net.Uri
import android.webkit.WebView
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import io.rover.sdk.core.logging.log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration

/**
 * Thrown when the runtime rejects a down-call (a `callResult` with `ok:false`). Surfaces as a
 * liveness signal that M6 funnels into the same one-attempt recovery path as a renderer crash.
 */
internal class ShowRejectedException(message: String?) : Exception(message)

/**
 * Completes every pending down-call when the WebView renderer dies (M6). Raised into any caller
 * suspended in [AppScreenBridge.call] so it fails fast instead of waiting out the full timeout; the
 * navigator then rebuilds the WebView and bridge.
 */
internal class RenderProcessGoneException(
    val didCrash: Boolean = true
) : Exception("App Screen WebView render process gone (didCrash=$didCrash)")

/**
 * The bag of in-flight down-calls awaiting their `callResult`, keyed by call id.
 *
 * Extracted from [AppScreenBridge] so the fail-fast behaviour ([failAll]) is unit-testable without a
 * WebView or reply proxy. Backed by a [ConcurrentHashMap]; the bridge callback and the navigator
 * both touch it from the main thread, but a stray IO-side completion stays safe.
 */
internal class PendingCalls {
    private val pending = ConcurrentHashMap<Int, CompletableDeferred<JSONObject>>()

    fun register(id: Int): CompletableDeferred<JSONObject> =
        CompletableDeferred<JSONObject>().also { pending[id] = it }

    fun remove(id: Int) {
        pending.remove(id)
    }

    operator fun get(id: Int): CompletableDeferred<JSONObject>? = pending[id]

    /** Complete every outstanding call exceptionally with [cause] and clear the bag. */
    fun failAll(cause: Throwable) {
        val snapshot = pending.values.toList()
        pending.clear()
        snapshot.forEach { it.completeExceptionally(cause) }
    }

    /** Test/diagnostic: the number of calls currently awaiting a reply. */
    val size: Int get() = pending.size
}

/**
 * A single message received UP from the App Screen runtime over the `roverAppScreens` channel.
 *
 * The runtime stringifies every message, so each arrives as a JSON string that
 * [BridgeMessage.parse] turns into one of these typed variants. Unknown or malformed shapes parse
 * to null (logged at debug) so the bridge never crashes on unexpected traffic.
 */
internal sealed class BridgeMessage {
    /** The runtime has booted and installed itself; a waiting load can resume. */
    object Loaded : BridgeMessage()

    /**
     * A navigation request (M4). [optimisticData] is kept as raw JSON text so it can be spliced back
     * byte-perfect into a later `show` call; it is null when the runtime omitted it.
     */
    data class Navigate(
        val href: String,
        val optimisticData: String?,
        val transition: String?
    ) : BridgeMessage()

    /** A batch of prefetch hints (M5). */
    data class Links(val hrefs: List<String>) : BridgeMessage()

    /**
     * The reply to a down-call. [result] and [error] are raw JSON text (or null). [ok] false
     * indicates the runtime rejected the call.
     */
    data class CallResult(
        val id: Int,
        val ok: Boolean,
        val result: String?,
        val error: String?
    ) : BridgeMessage()

    companion object {
        /**
         * Parse a message object into a [BridgeMessage], or null when the `type` is missing,
         * unrecognized, or the payload is malformed for its type. Raw JSON values (`optimisticData`,
         * `result`) are preserved verbatim as their String serialization.
         */
        fun parse(json: JSONObject): BridgeMessage? {
            return when (json.optString("type")) {
                "loaded" -> Loaded
                "navigate" -> {
                    val href = json.optString("href", "").takeIf { it.isNotBlank() } ?: return null
                    Navigate(
                        href = href,
                        optimisticData = json.opt(AppScreenShowArgs.OPTIMISTIC_DATA_KEY)?.toString(),
                        transition = json.optString("transition", "").takeIf { it.isNotBlank() }
                    )
                }
                "links" -> {
                    val array = json.optJSONArray("hrefs") ?: return null
                    val hrefs = (0 until array.length()).mapNotNull { i ->
                        array.optString(i, "").takeIf { it.isNotBlank() }
                    }
                    Links(hrefs)
                }
                "callResult" -> {
                    if (!json.has("id")) return null
                    CallResult(
                        id = json.optInt("id", -1),
                        ok = json.optBoolean("ok", false),
                        result = json.opt("result")?.toString(),
                        error = json.opt("error")?.toString()
                    )
                }
                else -> null
            }
        }
    }
}

/**
 * The native side of the App Screen JS bridge.
 *
 * Installs a [WebViewCompat] web-message listener exposing `window.roverAppScreens` to page
 * scripts, tracks the [JavaScriptReplyProxy] captured from the first up-message (the runtime always
 * sends `loaded` before any down-call could complete), and offers [call] to invoke a runtime method
 * and await its `callResult`.
 *
 * All methods that touch the WebView / reply proxy must be invoked on the main thread; the callback
 * from the WebView also arrives on the main thread.
 */
internal class AppScreenBridge private constructor() {

    @Volatile
    private var loaded = CompletableDeferred<Unit>()
    private val nextId = AtomicInteger(1)
    private val pending = PendingCalls()

    @Volatile
    private var replyProxy: JavaScriptReplyProxy? = null

    /**
     * Invoked on the main thread for every `navigate` message from the runtime. Set by the
     * navigator when it creates the session. Null until then (messages are logged and dropped).
     */
    @Volatile
    var onNavigate: ((BridgeMessage.Navigate) -> Unit)? = null

    /**
     * Invoked on the main thread for every `links` prefetch-hint message from the runtime. Set by
     * the navigator when it creates the session; null until then (hints are logged and dropped).
     */
    @Volatile
    var onLinks: ((BridgeMessage.Links) -> Unit)? = null

    /** Suspends until the runtime reports it has booted (`{type:'loaded'}`). */
    suspend fun awaitLoaded() {
        loaded.await()
    }

    /**
     * Re-arm the [awaitLoaded] signal before reloading the same WebView (the message listener
     * survives page reloads, so the same bridge keeps receiving messages; only the boot signal
     * needs resetting). Call on the main thread, before `loadDataWithBaseURL`.
     */
    fun rearmLoaded() {
        loaded = CompletableDeferred()
    }

    /**
     * Invoke a runtime [method] with [argsJson] (already-valid JSON, spliced verbatim as a value),
     * awaiting its reply up to [timeout]. Returns the `result` object on success; throws
     * [ShowRejectedException] when the runtime replies `ok:false`, or times out.
     */
    suspend fun call(method: String, argsJson: String, timeout: Duration): JSONObject {
        val proxy = replyProxy
            ?: throw IllegalStateException("App Screen bridge down-call before runtime handshake")
        val id = nextId.getAndIncrement()
        val deferred = pending.register(id)

        // argsJson is already valid JSON built by the caller; splice it verbatim so raw JSON
        // (optimisticData/response) crosses byte-perfect. Do NOT re-parse/re-serialize it.
        val envelope = "{\"id\":$id,\"method\":${JSONObject.quote(method)},\"args\":$argsJson}"
        log.d("App Screen bridge down-call id=$id method=$method")
        proxy.postMessage(envelope)

        return try {
            withTimeout(timeout) { deferred.await() }
        } finally {
            pending.remove(id)
        }
    }

    /**
     * Fail every in-flight down-call with [cause] so no caller waits out its timeout after the
     * renderer dies. The navigator calls this on the dead session before rebuilding its WebView and
     * bridge. Also drops the (now-dead) reply proxy so a stray subsequent [call] fails fast rather
     * than posting into a dead page. Main thread.
     */
    fun fail(cause: Throwable) {
        replyProxy = null
        pending.failAll(cause)
    }

    private fun onMessage(proxy: JavaScriptReplyProxy, raw: String) {
        // Always track the latest reply proxy: a reload (handshake refetch) produces a fresh page
        // and reply proxy, and down-calls must target the current page.
        replyProxy = proxy

        val json = try {
            JSONObject(raw)
        } catch (e: Exception) {
            log.d("App Screen bridge received non-JSON message, ignoring: $e")
            return
        }

        when (val message = BridgeMessage.parse(json)) {
            is BridgeMessage.Loaded -> {
                log.d("App Screen bridge: runtime loaded")
                loaded.complete(Unit)
            }
            is BridgeMessage.Navigate -> {
                log.d(
                    "App Screen bridge: navigate href=${message.href} " +
                        "transition=${message.transition} optimisticData=${message.optimisticData != null}"
                )
                val handler = onNavigate
                if (handler == null) {
                    log.d("App Screen bridge: navigate with no handler installed, dropping")
                } else {
                    handler(message)
                }
            }
            is BridgeMessage.Links -> {
                log.d("App Screen bridge: links ${message.hrefs.size} href(s)")
                val handler = onLinks
                if (handler == null) {
                    log.d("App Screen bridge: links with no handler installed, dropping")
                } else {
                    handler(message)
                }
            }
            is BridgeMessage.CallResult -> {
                val deferred = pending[message.id]
                if (deferred == null) {
                    log.d("App Screen bridge: callResult for unknown id=${message.id}, ignoring")
                } else if (message.ok) {
                    val result = message.result?.let {
                        try {
                            JSONObject(it)
                        } catch (e: Exception) {
                            JSONObject()
                        }
                    } ?: JSONObject()
                    deferred.complete(result)
                } else {
                    deferred.completeExceptionally(ShowRejectedException(message.error))
                }
            }
            null -> log.d("App Screen bridge: unrecognized message ignored")
        }
    }

    companion object {
        const val JS_OBJECT_NAME = "roverAppScreens"

        /** Whether the platform WebView supports the web-message-listener bridge at all. */
        fun isSupported(): Boolean =
            WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)

        /**
         * Install the bridge on [webView], exposing `window.roverAppScreens` to scripts whose
         * origin is in [allowedOrigins]. MUST be called before `loadDataWithBaseURL` so the
         * injected object exists when page scripts run. Returns null when the feature is
         * unsupported (caller should show the load-failure state).
         */
        fun install(webView: WebView, allowedOrigins: Set<String>): AppScreenBridge? {
            if (!isSupported()) {
                log.w("App Screen bridge unavailable: WebView WEB_MESSAGE_LISTENER feature unsupported")
                return null
            }
            val bridge = AppScreenBridge()
            WebViewCompat.addWebMessageListener(
                webView,
                JS_OBJECT_NAME,
                allowedOrigins,
                object : WebViewCompat.WebMessageListener {
                    override fun onPostMessage(
                        view: WebView,
                        message: WebMessageCompat,
                        sourceOrigin: Uri,
                        isMainFrame: Boolean,
                        replyProxy: JavaScriptReplyProxy
                    ) {
                        message.data?.let { bridge.onMessage(replyProxy, it) }
                    }
                }
            )
            return bridge
        }
    }
}
