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

package io.rover.ticketmaster

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.rover.sdk.core.data.graphql.safeOptString
import io.rover.sdk.ticketmaster.TicketmasterManager.Member
import io.rover.sdk.ticketmaster.decodeJson
import io.rover.sdk.ticketmaster.encodeJson
import junit.framework.Assert.assertEquals
import org.json.JSONObject
import org.junit.Test

private const val ID = "id"
private const val EMAIL = "example email"
private const val FIRST_NAME = "example first name"

class TicketMasterManagerTest {
    @Test
    fun `getNonNullPropertiesMap() returns all non null properties`() {
        val member = Member(ID, EMAIL, FIRST_NAME)
        val expectedMap = mapOf(
            member::email.name to EMAIL,
            member::firstName.name to FIRST_NAME,
            member::ticketmasterID.name to ID,
        )

        assertEquals(expectedMap, member.getNonNullPropertiesMap())
    }

    @Test
    fun `getNonNullPropertiesMapWithoutID() returns empty with all null properties`() {
        val member = Member(null, null, null)
        val expectedMap = mapOf<String, String>()

        assertEquals(expectedMap, member.getNonNullPropertiesMap())
    }

    @Test
    fun `encodeJson() adds properties to JSONObject`() {
        val member = Member(ID, EMAIL, FIRST_NAME)
        val mockJsonObject = mock<JSONObject>()

        member.encodeJson(mockJsonObject)

        verify(mockJsonObject).put(member::ticketmasterID.name, ID)
        verify(mockJsonObject).put(member::firstName.name, FIRST_NAME)
        verify(mockJsonObject).put(member::email.name, EMAIL)
    }

    @Test fun `decodeJson() creates Member with all properties successfully`() {
        val expectedMember = Member(ID, EMAIL, FIRST_NAME)
        val mockJsonObject = mock<JSONObject>()

        whenever(mockJsonObject.safeOptString("ticketmasterID")).thenReturn(ID)
        whenever(mockJsonObject.safeOptString("email")).thenReturn(EMAIL)
        whenever(mockJsonObject.safeOptString("firstName")).thenReturn(FIRST_NAME)

        assertEquals(expectedMember, Member.decodeJson(mockJsonObject))
    }

    @Test fun `decodeJson() creates Member from legacy hostID`() {
        val expectedMember = Member(ID, null, null)
        val mockJsonObject = mock<JSONObject>()

        whenever(mockJsonObject.safeOptString("hostID")).thenReturn(ID)

        assertEquals(expectedMember, Member.decodeJson(mockJsonObject))
    }

    @Test
    fun `decodeJson() creates Member from legacy teamID`() {
        val expectedMember = Member(ID, null, null)
        val mockJsonObject = mock<JSONObject>()

        whenever(mockJsonObject.safeOptString("teamID")).thenReturn(ID)

        assertEquals(expectedMember, Member.decodeJson(mockJsonObject))
    }

    fun `decodeJson() creates Member with null properties`() {
        val expectedMember = Member(null, null, null)

        val mockJsonObject = mock<JSONObject>()

        assertEquals(expectedMember, Member.decodeJson(mockJsonObject))
    }
}
