plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

// The AdMob ids of the design. They point at the public test ids of Google,
// so the app shows ads out of the box; the owner replaces them with the ids
// of the own AdMob account.
val admobAppId = "ca-app-pub-3940256099942544~3347511713"
val adUnitBanner = "ca-app-pub-3940256099942544/6300978111"
val adUnitCard = "ca-app-pub-3940256099942544/6300978111"
    val adUnitAppOpen = "ca-app-pub-3940256099942544/9257395921"

/**
 * Whether the app carries a real firebase configuration.
 *
 * The repository ships a placeholder google-services.json so that the build
 * works without credentials. The placeholder project is rejected by the sdk,
 * so firebase is only started once the owner drops in the own file.
 */
val firebaseConfigured = run {
    val file = file("google-services.json")
    if (!file.exists()) {
        false
    } else {
        runCatching {
            val number = Regex("\"project_number\"\\s*:\\s*\"([^\"]*)\"")
                .find(file.readText())
                ?.groupValues
                ?.get(1)
            !number.isNullOrBlank() && number.trim('0').isNotEmpty()
        }.getOrDefault(false)
    }
}

android {
    namespace = "com.peng.pdfreader"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.peng.pdfreader"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // The AdMob ids of the design, see the top of the file.
        manifestPlaceholders["admobAppId"] = admobAppId
        buildConfigField("String", "ADMOB_APP_ID", "\"$admobAppId\"")
        buildConfigField("String", "AD_UNIT_ID_BANNER", "\"$adUnitBanner\"")
        buildConfigField("String", "AD_UNIT_ID_CARD", "\"$adUnitCard\"")
buildConfigField("String", "AD_UNIT_ID_APP_OPEN", "\"$adUnitAppOpen\"")
        // The firebase sdk is only started with a real configuration.
        buildConfigField("boolean", "FIREBASE_CONFIGURED", firebaseConfigured.toString())
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    // The engine logs through android.util.Log, the JVM has no such class.
    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    // The preview samples live in the assets of the app, the unit tests of the
    // parsers read them from there.
    sourceSets.getByName("test") {
        resources.directories.add("src/main/assets")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

kotlin {
    compilerOptions {
        // The measurement library of Google is built with a newer Kotlin than
        // the one of the project; the metadata check would reject it even
        // though none of its new features is used.
        freeCompilerArgs.add("-Xskip-metadata-version-check")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.google.material)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.lifecycle.runtime.ktx)
implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)

    // The advertising and the analytics of the design.
    implementation(libs.play.services.ads)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
implementation(libs.firebase.messaging)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
}

/**
 * The upload of the crash symbol files needs the credentials of the own
 * Firebase project. As long as the placeholder configuration is used the task
 * stays switched off, so the release build works out of the box.
 */
tasks.matching { it.name.contains("CrashlyticsMappingFile") || it.name.contains("CrashlyticsUpload") }
    .configureEach {
        enabled = firebaseConfigured
    }
