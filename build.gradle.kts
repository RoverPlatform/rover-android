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
val roverSdkVersion by extra(libs.versions.roverSdkVersion.get())

// Definitions of several core shared dependencies:
val kotlinVersion by extra(libs.versions.kotlin.get())
val composeBomVersion by extra(libs.versions.composeBom.get())

buildscript {
    repositories {
        google()
        mavenCentral()
    }

    dependencies {
        classpath(libs.shot)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.firebase.appdistribution) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
    alias(libs.plugins.dokka) apply false
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
