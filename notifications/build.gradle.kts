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
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.ksp)
    id("kotlin-parcelize")
    id("maven-publish")
}

val roverSdkVersion: String by rootProject.extra


kotlin {
    jvmToolchain(11)
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    
    buildFeatures {
        compose = true
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }

    namespace = "io.rover.notifications"
}

dependencies {
    implementation(libs.androidx.appcompat)

    implementation(libs.androidx.legacy.support.v4)

    implementation(libs.androidx.recyclerview)
    implementation(libs.material)
    implementation(libs.androidx.vectordrawable)

    implementation(libs.androidx.lifecycle.extensions)
    implementation(libs.bundles.kotlin)
    implementation(libs.kotlinx.coroutines.reactive)

    // Room dependencies for Communication Hub
    implementation(libs.bundles.room)
    ksp(libs.room.compiler)
    
    // Moshi KSP processor for Communication Hub DTOs
    ksp(libs.moshi.kotlin.codegen)

    // JSON parsing for Communication Hub API
    implementation(libs.bundles.json)
    
    // Networking for Communication Hub API
    implementation(libs.bundles.networking)

    // Compose dependencies for Communication Hub UI
    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.browser)
    implementation(libs.compose.ui)

    implementation(libs.androidx.compose.material3)
    implementation(libs.compose.material)
    implementation(libs.compose.activity)
    implementation(libs.compose.lifecycle.viewmodel)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.coil.compose)
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.compose.adaptive.layout.android)
    implementation(libs.androidx.compose.adaptive.navigation.android)
    debugImplementation(libs.androidx.ui.tooling)
    implementation(libs.compose.ui.tooling.preview)

    implementation(project(":core"))

    testImplementation(libs.junit.legacy)

    testImplementation(libs.hamkrest)

    androidTestImplementation(libs.bundles.android.testing)
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])

                groupId = "io.rover.sdk"
                artifactId = "notifications"
                version = roverSdkVersion

                pom {
                    name.set("Rover SDK Notifications Module")
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
