pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // needed to retrieve the Rover Experiences SDK
        maven {
            url = java.net.URI.create("https://judoapp.github.io/judo-maven/maven")
        }
    }
}

include(":core", ":location", ":notifications", ":debug", ":ticketmaster", ":advertising", ":experiences")
rootProject.name = "Rover Campaigns Android"
include(":example-app")
