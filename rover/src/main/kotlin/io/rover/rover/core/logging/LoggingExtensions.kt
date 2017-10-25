package io.rover.rover.core.logging

import android.util.Log

/**
 * A very simple facade wrapped around the Android logger using Kotlin extension methods.
 */
interface SimpleLogger {
    fun e(message: String)
    fun w(message: String)
    fun v(message: String)
    fun i(message: String)
    fun d(message: String)
}

val Any.log: SimpleLogger
    get() {
        val logTag = "Rover::${this.javaClass.simpleName}"

        return object : SimpleLogger {

            override fun e(message: String) {
                Log.e(logTag, message)
            }

            override fun w(message: String) {
                Log.w(logTag, message)
            }

            override fun v(message: String) {
                Log.v(logTag, message)
            }

            override fun i(message: String) {
                Log.i(logTag, message)
            }

            override fun d(message: String) {
                Log.d(logTag, message)
            }
        }
    }



