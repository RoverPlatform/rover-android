plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("maven-publish")
}

val roverSdkVersion: String by rootProject.extra
val kotlinVersion: String by rootProject.extra

android {
    compileSdk = 33

    defaultConfig {
        minSdk = 21
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

    namespace = "io.rover.location"
}

dependencies {
   implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinVersion")
   implementation("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")

   testImplementation("junit:junit:4.12")

   androidTestImplementation("androidx.test.ext:junit:1.1.3")
   androidTestImplementation("androidx.test.espresso:espresso-core:3.4.0")
   implementation(project(":core"))
    
   implementation("com.google.android.gms:play-services-nearby:18.3.0")
   implementation("com.google.android.gms:play-services-location:20.0.0")
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])

                groupId = "io.rover.sdk"
                artifactId = "location"
                version = roverSdkVersion

                pom {
                    name.set("Rover  SDK Location Module")
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
