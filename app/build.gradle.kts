plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val dataOwner: String = (project.findProperty("dataOwner") as String?) ?: System.getenv("DATA_OWNER") ?: "OWNER"
val keystorePassword: String = System.getenv("KEYSTORE_PASSWORD")?.takeIf { it.isNotEmpty() }
    ?: rootProject.file("keystore/heslo.txt").takeIf { it.exists() }?.readText()?.trim() ?: "anglictina"
val buildNumber: Int = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "cz.anglictina.kurz"
    compileSdk = 34

    defaultConfig {
        applicationId = "cz.anglictina.kurz"
        minSdk = 26
        targetSdk = 34
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
        buildConfigField("String", "DATA_URL", "\"https://raw.githubusercontent.com/$dataOwner/anglictina/main/data/\"")
    }

    signingConfigs {
        create("release") {
            storeFile = file("../keystore/anglictina.jks")
            storePassword = keystorePassword
            keyAlias = "anglictina"
            keyPassword = keystorePassword
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    lint { checkReleaseBuilds = false; abortOnError = false }
}

// Obsah kurzu (JSON z ../data) se přibalí do aplikace, aby fungovala i bez připojení.
val copyCourseData by tasks.registering(Copy::class) {
    from("../data") { include("**/*.json") }
    into(layout.buildDirectory.dir("generated/courseAssets/data"))
}
android.sourceSets["main"].assets.srcDir(layout.buildDirectory.dir("generated/courseAssets"))
tasks.named("preBuild") { dependsOn(copyCourseData) }

dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(bom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")
    implementation("androidx.navigation:navigation-compose:2.8.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
