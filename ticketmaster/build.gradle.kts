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

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.dokka)
    id("maven-publish")
}

val roverSdkVersion: String by rootProject.extra


kotlin {
    jvmToolchain(17)
}

android {
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles("proguard-rules.pro")
        }
    }

    sourceSets.getByName("main") {
        java.srcDir("src/main/java")
        java.srcDir("src/main/kotlin")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }

    namespace = "io.rover.ticketmaster"
}

// Create custom Javadoc JAR using Dokka
val dokkaJavadoc by tasks.getting(org.jetbrains.dokka.gradle.DokkaTask::class)

val javadocJar by tasks.registering(Jar::class) {
    dependsOn(dokkaJavadoc)
    from(dokkaJavadoc.outputDirectory)
    archiveClassifier.set("javadoc")
}

dependencies {
    dokkaPlugin("org.jetbrains.dokka:dokka-base:${libs.versions.dokka.get()}")
    implementation(project(":core"))

    //Ticketmaster Ignite SDK
    implementation(libs.ticketmaster.purchase)
    implementation(libs.ticketmaster.discoveryapi)

    testImplementation(libs.junit.legacy)
    // spek
    testImplementation(libs.mockito.kotlin)
    androidTestImplementation(libs.bundles.android.testing)
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                artifact(javadocJar)

                groupId = "io.rover.sdk"
                artifactId = "ticketmaster"
                version = roverSdkVersion

                pom {
                    name.set("Rover SDK Ticketmaster Integration Module")
                    description.set("From the Rover Android SDK")
                    url.set("https://github.com/roverplatform/rover-android")
                    licenses {
                        license {
                            name.set("Apache 2.0 License")
                            url.set("https://github.com/roverplatform/rover-android/blob/master/LICENSE")
                        }
                    }
                }
            }
        }

        repositories {
            maven {
                url = uri(
                    System.getenv("DEPLOY_MAVEN_PATH")
                        ?: layout.projectDirectory.dir("maven")
                )
            }
        }
    }
}
