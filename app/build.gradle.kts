plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.fieldflow"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.fieldflow"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0-v1"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    compileOnly(files("/home/machinerg/Android/Sdk/platforms/android-36.1/android.jar"))
    implementation(project(":core:overlay"))
    implementation(project(":core:audio"))
    implementation(project(":core:stt"))
    implementation(project(":core:watcher"))
    implementation(project(":core:data"))
    implementation(project(":core:transform"))
    implementation(project(":core:appfunctions"))
    implementation(project(":core:export"))

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
