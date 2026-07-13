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
import io.rover.sdk.core.logging.log
import io.rover.sdk.experiences.appscreens.network.AppScreenDataEnvelope
import io.rover.sdk.experiences.appscreens.network.AppScreenDocument
import io.rover.sdk.experiences.appscreens.network.AppScreensDataClient
import io.rover.sdk.experiences.appscreens.network.AppScreensDocumentClient

/**
 * Orchestrates the network half of the App Screen load pipeline: the document fetch (with scope
 * registry recording), the scope-dependent `.json` fetch, and the hash-handshake reconciliation.
 *
 * ## Design
 * The loader is deliberately WebView-free so it can be unit-tested against faked clients. The one
 * WebView-touching step in the handshake — refetching the document and reloading the page — is
 * injected as a suspend callback ([reconcileScreenData]'s `refetchDocument`), keeping all decision
 * sequencing here while the coordinator owns the view side. Two coarse suspend entry points
 * ([loadDocument], [loadScreenData]) plus the handshake reconciliation ([reconcileScreenData])
 * were chosen over a single event Flow because the cold pipeline has exactly two awaited network
 * legs with a well-defined join point, and plain suspend functions compose more legibly with the
 * coordinator's `withTimeout` bounds than a Flow would.
 */
internal class AppScreenLoader(
    private val documentClient: AppScreensDocumentClient,
    private val dataClient: AppScreensDataClient,
    private val scopeRegistry: AppScreenScopeRegistry
) {
    /** The final reconciled pairing to render: a data envelope and the document ETag it matches. */
    internal data class ReconciledScreenData(
        val envelope: AppScreenDataEnvelope,
        val documentETag: String?
    )

    /**
     * Fetch the HTML document for [url] and record its effective scope in the registry (so a later
     * load of the same template may proceed concurrently). Returns the document verbatim.
     */
    suspend fun loadDocument(
        url: Uri,
        policy: AppScreensDocumentClient.DocumentCachePolicy =
            AppScreensDocumentClient.DocumentCachePolicy.Default
    ): AppScreenDocument {
        val document = documentClient.fetchDocument(url, policy)
        val scope = AppScreensDecisions.effectiveScope(document.dataScope)
        scopeRegistry.record(AppScreensDecisions.templatePath(url), scope)
        return document
    }

    /** The last-recorded scope for [url]'s template, or null if never observed. */
    fun recordedScope(url: Uri): AppScreenDataScope? =
        scopeRegistry.scopeFor(AppScreensDecisions.templatePath(url))

    /**
     * Fetch the `.json` data document for [url] under [effectiveScope]. The one-shot
     * public→personalized retry is handled inside the data client.
     */
    suspend fun loadScreenData(url: Uri, effectiveScope: AppScreenDataScope): AppScreenDataEnvelope =
        dataClient.fetchScreenData(url, effectiveScope)

    /**
     * Reconcile a freshly-fetched [envelope] against the document [documentETag] via the hash
     * handshake. On a mismatch it invokes [refetchDocument] exactly once (the coordinator's
     * callback that refetches the document with a conditional request and reloads/awaits the
     * WebView), then re-checks. A second mismatch fails open (renders anyway) so a screen always
     * appears; it never loops.
     */
    suspend fun reconcileScreenData(
        envelope: AppScreenDataEnvelope,
        documentETag: String?,
        refetchDocument: suspend () -> AppScreenDocument
    ): ReconciledScreenData {
        var currentETag = documentETag
        var alreadyRetried = false
        while (true) {
            when (AppScreensDecisions.handshake(currentETag, envelope.templateHash, alreadyRetried)) {
                is AppScreensDecisions.HandshakeDecision.Render -> {
                    if (alreadyRetried &&
                        AppScreensDecisions.normalizeETag(currentETag) != envelope.templateHash
                    ) {
                        log.w(
                            "App Screen hash handshake still mismatched after one refetch " +
                                "(etag=$currentETag hash=${envelope.templateHash}); rendering anyway"
                        )
                    }
                    return ReconciledScreenData(envelope, currentETag)
                }
                is AppScreensDecisions.HandshakeDecision.RefetchDocumentOnce -> {
                    log.w(
                        "App Screen hash handshake mismatch (etag=$currentETag " +
                            "hash=${envelope.templateHash}); refetching document once"
                    )
                    val refetched = refetchDocument()
                    currentETag = refetched.etag
                    alreadyRetried = true
                }
            }
        }
    }
}
