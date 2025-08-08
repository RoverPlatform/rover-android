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
    id("maven-publish")
    id("kotlin-parcelize")
    alias(libs.plugins.kotlin.ksp)
}

val roverSdkVersion: String by rootProject.extra

val composeBomVersion: String by rootProject.extra
val media3Version: String = "1.0.2"

kotlin {
    jvmToolchain(11)
}

android {
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()

        buildConfigField("String", "VERSION_NAME", "\"${roverSdkVersion}\"")
        buildConfigField("int", "API_VERSION", "2")
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

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    buildFeatures {
        viewBinding = true
        compose = true
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }

    namespace = "io.rover.experiences"
}

dependencies {
    implementation(libs.bundles.kotlin)
    implementation(project(":core"))
    implementation(libs.androidx.transition)
    // Until java.util.concurrent.Flow appears in Android SDK, import:
    api(libs.reactive.streams)
    api(libs.androidx.lifecycle.extensions)

    // region AndroidX
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.transition)
    implementation(libs.androidx.recyclerview)
    implementation(libs.material)
    implementation(libs.androidx.vectordrawable)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.swiperefreshlayout)
    api(libs.androidx.coordinatorlayout)
    // endregion AndroidX

    // region Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose.ui)
    implementation(libs.compose.lifecycle.runtime)
    debugImplementation(libs.bundles.compose.debug)
    implementation(libs.compose.lifecycle.viewmodel)
    implementation(libs.androidx.navigation.compose)
    // endregion Compose

    // region Test
    testImplementation(libs.junit)
    androidTestImplementation(libs.bundles.android.testing)
    // endregion Test

    // region Square
    ksp(libs.moshi.kotlin.codegen)
    implementation(libs.bundles.json)
    implementation(libs.bundles.networking)
    // endregion Square

    // region Sugar
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    // endregion Sugar

    // Coil
    implementation(libs.bundles.coil)

    // Exoplayer
    implementation(libs.bundles.media3)

    implementation(libs.zxing.core)
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])

                groupId = "io.rover.sdk"
                artifactId = "experiences"
                version = roverSdkVersion

                pom {
                    name.set("Rover SDK Experiences Module")
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
