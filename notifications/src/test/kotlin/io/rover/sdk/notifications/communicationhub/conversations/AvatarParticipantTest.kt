package io.rover.sdk.notifications.communicationhub.conversations

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.Test

class AvatarParticipantTest {

    @Test fun `single word name returns first letter uppercase`() {
        assertThat(initialsFor("alice"), equalTo("A"))
    }

    @Test fun `two word name returns two initials`() {
        assertThat(initialsFor("Sam Rivera"), equalTo("SR"))
    }

    @Test fun `three word name returns only first two initials`() {
        assertThat(initialsFor("John Paul Jones"), equalTo("JP"))
    }

    @Test fun `null name returns question mark`() {
        assertThat(initialsFor(null), equalTo("?"))
    }

    @Test fun `blank name returns question mark`() {
        assertThat(initialsFor("   "), equalTo("?"))
    }
}
