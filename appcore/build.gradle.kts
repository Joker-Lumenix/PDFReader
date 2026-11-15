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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
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
}
