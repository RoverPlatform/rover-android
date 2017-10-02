package io.rover.rover

import org.jetbrains.spek.api.dsl.TestBody
import java.lang.AssertionError

/**
 * When doing assertions in Spek tests, please wrap your assertion code with this function.
 *
 * Rationale: for some unknown reason, Android Studio stable (currently 2.3.3) with the JUnit 4
 * compatibility runner for JUnit 5 (used by Spek), junit-platform-runner, seems to not
 * display assertion errors in the tests properly.  This just wraps any code that would
 * emit such errors in a simple containing exception as a temporary workaround.  This will
 * be removed once JUnit 5 support goes stable in Android Studio and the JUnit 4
 * compatibility stuff is no longer required.
 */
fun TestBody.junit4ReportingWorkaround(block: () -> Unit) {
    try {
        block()
    } catch (e: AssertionError) {
        throw RuntimeException(e)
    }
}
