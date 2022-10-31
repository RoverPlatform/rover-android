// Top-level build file where you can add configuration options common to all sub-projects/modules.

val roverCampaignsVersion by extra("3.10.0")
val kotlinVersion by extra("1.7.10")

buildscript {
    repositories {
        google()
        mavenCentral()
    }

    dependencies {
        classpath("com.android.tools.build:gradle:7.3.0")
        // somehow `kotlinVersion` isn't in scope here. wtf.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.7.10")
        classpath("com.google.gms:google-services:4.3.14")
    }
}

plugins {
    id("com.android.application") version "7.1.1" apply false
    id("com.android.library") version "7.1.1" apply false
    id("org.jetbrains.kotlin.android") version "1.7.10" apply false
}


task("printVersionNumber") {
    doLast {
        println("Rover Campaigns Android SDK version $roverCampaignsVersion")
        // GitHub Actions detects this syntax on stdout and sets an output variable (`VERSION` in this case)
        // that we can use later on within the workflow.

        // Groovy: println "::set-output name=VERSION::${roverCampaignsVersion}"
        println("::set-output name=VERSION::$roverCampaignsVersion")
    }
}
