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

// Top-level build file where you can add configuration options common to all sub-projects/modules.

// The version number for the build SDK modules and testbench app.
val roverSdkVersion by extra("4.10.0")

// Definitions of several core shared dependencies:
val kotlinVersion by extra("1.8.20") // NB: when changing this one check the two duplicates of this number below
val composeBomVersion by extra("2024.09.02")
val composeKotlinCompilerExtensionVersion by extra("1.4.5")

buildscript {
    repositories {
        google()
        mavenCentral()
    }

    dependencies {
        classpath("com.android.tools.build:gradle:8.2.2")
        // Kotlin version duplicated here because of goofy kts scoping rules.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.8.20")
        classpath("com.google.gms:google-services:4.3.15")
        classpath("com.karumi:shot:6.0.0")
    }
}

plugins {
    id("com.android.application") version "7.1.1" apply false
    id("com.android.library") version "7.1.1" apply false
    // Kotlin version duplicated here because of goofy kts scoping rules.
    id("org.jetbrains.kotlin.android") version "1.8.20" apply false
    id("com.google.firebase.appdistribution") version "5.0.0" apply false
    id("com.google.firebase.crashlytics") version "3.0.1" apply false
}

task("printVersionNumber") {
    doLast {
        println("Rover Android SDK version $roverSdkVersion")
        // GitHub Actions detects this syntax on stdout and sets an output variable (`VERSION` in this case)
        // that we can use later on within the workflow.

        // Groovy: println "::set-output name=VERSION::${roverSdkVersion}"
        println("::set-output name=VERSION::$roverSdkVersion")
    }
}
