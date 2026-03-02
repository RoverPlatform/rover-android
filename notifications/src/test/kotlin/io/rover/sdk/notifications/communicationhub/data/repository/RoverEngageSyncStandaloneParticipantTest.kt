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

package io.rover.sdk.notifications.communicationhub.data.repository

import io.rover.sdk.notifications.communicationhub.data.RoverEngageTestBase
import io.rover.sdk.core.data.sync.SyncStandaloneParticipant
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.*

/**
 * Tests for RCHSync integration with SyncStandaloneParticipant
 * 
 * These tests verify that CommHubRepository properly implements the SyncStandaloneParticipant
 * interface and integrates correctly with the broader Rover SDK sync system.
 */
class RoverEngageSyncStandaloneParticipantTest : RoverEngageTestBase() {


    // SyncStandaloneParticipant interface reference
    private lateinit var syncParticipant: SyncStandaloneParticipant

    override fun setupTest() {
        // Cast repository to SyncStandaloneParticipant interface
        syncParticipant = repository as SyncStandaloneParticipant
    }

    /**
     * Test basic SyncStandaloneParticipant interface implementation
     */
    @Test
    fun testSyncStandaloneParticipantInterface() = runTest {
        // Verify that CommHubRepository implements SyncStandaloneParticipant

        // Setup basic mocks using base class method
        setupBasicMocks()

        // Call sync through the interface
        val result = syncParticipant.sync()

        // Verify sync completes (returns false when no new content is available)
        assert(!result) { "Sync should return false when no new content is available (empty responses)" }
    }

    /**
     * Test SyncCoordinator protocol conformance with successful sync
     */
    @Test
    fun testSyncCoordinatorIntegrationSuccess() = runTest {
        // Verify that repository implements the interface

        // Configure mock for successful protocol-based sync
        setupBasicMocks()

        // Call sync through the protocol interface (as SyncCoordinator would)
        val protocolSyncResult = syncParticipant.sync()

        // Verify protocol-based sync completes (returns false when no new content is available)
        // Note: sync returns true when new content is available, false when no new content
        assert(!protocolSyncResult) { "Protocol sync should return false when no new content is available (empty responses)" }

        // Verify the correct API calls were made through protocol interface
        verifyAllApiCallsMadeOnce()
    }

    /**
     * Test SyncCoordinator protocol conformance with failure handling
     */
    @Test
    fun testSyncCoordinatorIntegrationFailure() = runTest {
        // Configure mock for failed sync
        configureMockForFailure()

        // Call sync through the protocol interface
        val protocolFailureResult = syncParticipant.sync()

        // Verify protocol correctly reports failure
        assert(!protocolFailureResult) { "Protocol sync should return false on failure" }
    }

    /**
     * Test SyncCoordinator protocol conformance with concurrent sync coalescing
     */
    @Test
    fun testSyncCoordinatorIntegrationCoalescing() = runTest {
        // Configure mock for successful sync with delay
        configureMockForSuccess()

        // Start multiple concurrent protocol-based syncs
        val sync1Job = async { syncParticipant.sync() }
        val sync2Job = async { syncParticipant.sync() }
        val sync3Job = async { syncParticipant.sync() }

        val results = listOf(sync1Job.await(), sync2Job.await(), sync3Job.await())

        // All should return the same result (coalesced)
        // This is the key test - coalescing means all concurrent calls return the same result
        assert(results.all { it == results.first() }) {
            "All coalesced syncs should return the same result, but got: $results"
        }

        // Verify that coalescing worked by checking subscriptions was only called once
        // (can't check all APIs since the sync may not complete all steps with delays)
        verify(mockEngageApiService, times(1)).getSubscriptions()
    }

    /**
     * Test sync participant return values and status reporting
     *
     * Verifies that sync() returns:
     * - false when no new content is available (empty responses)
     * - false when sync fails
     * - consistent values across multiple calls
     *
     * Note: sync returns true only when hasNewContent (new posts/categories/articles are synced).
     * With empty mock responses, sync returns false (no new content).
     */
    @Test
    fun testSyncParticipantReturnValues() = runTest {
        // Test sync returns false when no new data is available
        setupBasicMocks()

        val noDataResult = syncParticipant.sync()
        assert(!noDataResult) { "Sync should return false when no new posts are available" }

        // Test sync returns false when APIs fail
        configureMockForFailure()

        val failureResult = syncParticipant.sync()
        assert(!failureResult) { "Sync should return false when APIs fail" }

        // Test that return values are consistent across multiple calls
        setupBasicMocks()

        val firstCall = syncParticipant.sync()
        val secondCall = syncParticipant.sync()
        val thirdCall = syncParticipant.sync()

        assert(firstCall == secondCall) { "Consecutive sync calls should return consistent values" }
        assert(secondCall == thirdCall) { "Consecutive sync calls should return consistent values" }
        assert(!firstCall) { "All calls should return false when no new data is available" }
    }

    /**
     * Test isolation from main SDK sync operations
     *
     * Verifies that:
     * - Sync Engage API resources sync operates independently of the legacy GraphQL sync system
     * - Has isolated sync state and coalescing
     * - Failures don't affect other components
     */
    @Test
    fun testIsolationFromMainSyncOperations() = runTest {
        // Test that sync operates independently
        setupBasicMocks()

        // Perform sync
        val commHubResult = syncParticipant.sync()

        // Result depends on content availability (false for empty responses)
        assert(!commHubResult) { "Sync should return false when no new content is available (empty responses)" }

        // Verify sync made the expected API calls
        verify(mockEngageApiService, atLeastOnce()).getSubscriptions()
        verify(mockEngageApiService, atLeastOnce()).getPosts(eq("test-device-id"), eq(null))

        // Test that sync state is isolated
        // Multiple concurrent Hub syncs should coalesce independently
        clearInvocations(mockEngageApiService)
        configureMockForSuccess()

        // Start multiple concurrent syncs
        val isolatedSync1 = async { syncParticipant.sync() }
        val isolatedSync2 = async { syncParticipant.sync() }
        val isolatedSync3 = async { syncParticipant.sync() }

        val isolatedResults = listOf(isolatedSync1.await(), isolatedSync2.await(), isolatedSync3.await())

        // All should return the same result due to coalescing within the Rover Engage sync participant.
        assert(isolatedResults.all { it == isolatedResults.first() }) {
            "syncs should coalesce independently"
        }

        // Verify only one set of Engage API calls was made due to coalescing
        verify(mockEngageApiService, times(1)).getSubscriptions()

        // Test that sync failures are isolated
        clearInvocations(mockEngageApiService)
        configureMockForFailure()

        val failureResult = syncParticipant.sync()
        assert(!failureResult) { "Communication Hub sync failure should be isolated" }

        // Verify failure doesn't crash or affect system state
        // (if we get here, the test passed - no exceptions were thrown)
    }

}
