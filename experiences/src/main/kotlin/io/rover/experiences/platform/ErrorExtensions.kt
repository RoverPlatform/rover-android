package io.rover.experiences.platform

fun Throwable.debugExplanation(withStackTrace: Boolean = false): String {
    val description = "${toString()}${if (cause != null) " caused by ${cause?.debugExplanation(withStackTrace = withStackTrace)}" else "" }"
    val stackTrace = stackTrace.joinToString("\n")

    return if (withStackTrace) "$description\n\n$stackTrace" else description
}
