plugins {
    id("com.android.application")
}

val pleosEnabled = providers.gradleProperty("gildongmu.pleos").orNull == "true"

android {
    namespace = "app.gildongmu.host"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.gildongmu.host"
        // Pleos Connect requires API 28 or later.
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    // One app, two ways of talking to a car:
    //   mock  - a scripted drive and Android's own speech services; needs no car and no vendor SDK
    //   pleos - the Pleos Connect SDK; exists only when gildongmu.pleos=true in gradle.properties
    flavorDimensions += "platform"
    productFlavors {
        create("mock") {
            dimension = "platform"
            isDefault = true
        }
        if (pleosEnabled) {
            create("pleos") {
                dimension = "platform"
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            // Signed with the debug key so that a release APK can be built and installed without
            // setting up a keystore. Replace before distributing through an app market.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")

    if (pleosEnabled) {
        "pleosImplementation"("ai.pleos.playground:Vehicle:2.3.2")
        "pleosImplementation"("ai.pleos.playground:NaviHelper:3.0.5")
        "pleosImplementation"("ai.pleos.playground:FusedLocation:1.1.3")
        "pleosImplementation"("ai.pleos.playground:TextToSpeech:3.3.1")
        "pleosImplementation"("ai.pleos.playground:SpeechToText:3.3.1")
    }
}
