import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    kotlin("plugin.serialization") version "2.0.20"
    id("com.google.devtools.ksp")

}

val envProperties = Properties()
val envFile = rootProject.file(".env")
if (envFile.exists()) {
    envProperties.load(FileInputStream(envFile))
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}

val supabaseUrl = envProperties.getProperty("SUPABASE_URL")
    ?: localProperties.getProperty("SUPABASE_URL")
    ?: "https://questphone.supabase.co"
val supabaseApiKey = envProperties.getProperty("SUPABASE_API_KEY")
    ?: localProperties.getProperty("SUPABASE_API_KEY")
    ?: "public-anon-key"
val apiUrl = envProperties.getProperty("API_URL")
    ?: localProperties.getProperty("API_URL")
    ?: "https://questphone.app"

android {
    namespace = "nethical.questphone.backend"
    compileSdk = 36

    defaultConfig {
        minSdk = 26

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_API_KEY", "\"$supabaseApiKey\"")
        buildConfigField("String", "API_URL", "\"$apiUrl\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.hilt.common)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation(libs.ktor.client.okhttp)
    implementation (libs.guava)
    implementation (libs.kotlinx.serialization.json)

    implementation (libs.androidx.work.runtime.ktx)
    implementation(project(":data"))
    implementation(project(":core"))

}