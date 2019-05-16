package io.rover.helpers

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

infix fun Any?.shouldEqual(theOther: Any?) = assertEquals(theOther, this)

infix fun Any?.shouldBeInstanceOf(className: Class<*>) =
    assertTrue(className.isInstance(this), "Expected $this to be an instance of $className")

infix fun Long.shouldBeLessThan(theOther: Long) =
    assertTrue(this < theOther, "Expected $this to be less than $theOther")