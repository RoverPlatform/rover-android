package io.rover.ticketmaster

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.rover.campaigns.core.data.graphql.safeOptString
import io.rover.campaigns.ticketmaster.TicketmasterManager.Member
import io.rover.campaigns.ticketmaster.decodeJson
import io.rover.campaigns.ticketmaster.encodeJson
import junit.framework.Assert.assertEquals
import org.json.JSONObject
import org.junit.Test

private const val ID = "id"
private const val EMAIL = "example email"
private const val FIRST_NAME = "example first name"

class TicketMasterManagerTest {
    @Test
    fun `getNonNullPropertiesMapWithoutID() returns all non null except ID properties`() {
        val member = Member(ID, EMAIL, FIRST_NAME)
        val expectedMap = mapOf(
            member::email.name to EMAIL,
            member::firstName.name to FIRST_NAME
        )

        assertEquals(expectedMap, member.getNonNullPropertiesMapWithoutId())
    }

    @Test
    fun `getNonNullPropertiesMapWithoutID() returns empty with all null properties`() {
        val member = Member(null, null, null)
        val expectedMap = mapOf<String, String>()

        assertEquals(expectedMap, member.getNonNullPropertiesMapWithoutId())
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
