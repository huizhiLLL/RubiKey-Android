import java.io.File
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val releaseSigningPropertiesFile = listOf(
    rootProject.file("key.properties"),
    File(System.getProperty("user.home"), ".rubikey/rubikey-release.properties"),
).firstOrNull(File::isFile)
val releaseSigningProperties = Properties().apply {
    releaseSigningPropertiesFile?.inputStream()?.use(::load)
}
val requiredReleaseSigningKeys = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
val hasReleaseSigningConfig = requiredReleaseSigningKeys.all(releaseSigningProperties::containsKey)
val releaseStoreFile = releaseSigningProperties.getProperty("storeFile")?.let(::File)?.let { file ->
    if (file.isAbsolute) file else rootProject.file(file.path)
}

android {
    namespace = "com.huizhi.rubikey"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.huizhi.rubikey"
        minSdk = 31
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0-beta.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (hasReleaseSigningConfig) {
                storeFile = requireNotNull(releaseStoreFile)
                storePassword = releaseSigningProperties.getProperty("storePassword")
                keyAlias = releaseSigningProperties.getProperty("keyAlias")
                keyPassword = releaseSigningProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    doFirst {
        check(hasReleaseSigningConfig) {
            "缺少 Release 签名配置。请创建项目根目录的 key.properties，或使用 ~/.rubikey/rubikey-release.properties。"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.04.01")

    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
