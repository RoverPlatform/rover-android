plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("maven-publish")
    id("kotlin-parcelize")
}

val roverSdkVersion: String by rootProject.extra
val kotlinVersion: String by rootProject.extra

android {
    compileSdk = 33

    defaultConfig {
        minSdk = 21

        buildConfigField("String", "VERSION_NAME", "\"${roverSdkVersion}\"")
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }

    namespace = "io.rover.experiences"
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinVersion")
    implementation("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")
    implementation(project(":core"))
    // TODO: should these use 'api' instead of compile? Unclear on guidance on transitive AndroidX
    // dependencies within libraries.
    implementation("androidx.appcompat:appcompat:1.0.0")
    implementation("androidx.legacy:legacy-support-v4:1.0.0")
    implementation("androidx.transition:transition:1.0.0")
    // Until java.util.concurrent.Flow appears in Android SDK, import:
    api("org.reactivestreams:reactive-streams:1.0.2")
    api("androidx.lifecycle:lifecycle-extensions:2.0.0")

    implementation("androidx.browser:browser:1.0.0")
    implementation("androidx.transition:transition:1.0.0")
    implementation("androidx.recyclerview:recyclerview:1.0.0")
    implementation("com.google.android.material:material:1.0.0")
    implementation("androidx.vectordrawable:vectordrawable:1.0.0")
    implementation("androidx.browser:browser:1.0.0")

    implementation("com.google.zxing:core:3.4.1")
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
                    name.set("Rover  SDK Experiences Module")
                    description.set("From the Rover  Android SDK")
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
