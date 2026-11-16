plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.peng.pdfreader.appcore"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 26
    }

    buildFeatures {
        buildConfig = true
    }

    // The engine logs through android.util.Log, the JVM has no such class.
    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    // The preview samples live with the app, the unit tests of the parsers read
    // them from there.
    sourceSets.getByName("test") {
        resources.srcDir("../app/src/main/assets")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    api(libs.androidx.appcompat)
    api(libs.google.material)
    api(libs.androidx.recyclerview)
    api(libs.androidx.constraintlayout)
    api(libs.androidx.fragment.ktx)
    api(libs.androidx.viewpager2)
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.junit)
}
