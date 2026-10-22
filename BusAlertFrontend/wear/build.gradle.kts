plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Valores definidos en el build.gradle.kts raíz (local.properties o variables de entorno).
fun setting(name: String): String = rootProject.extra[name] as String
fun quoted(value: String): String = "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

val releaseBackendUrl = setting("busalert.release.backendUrl")

android {
    namespace = "com.busalert.wear"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.busalert.wear"
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "BACKEND_API_KEY", quoted(setting("busalert.apiKey")))
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        debug {
            buildConfigField("String", "BACKEND_BASE_URL", quoted(setting("busalert.debug.backendUrl")))
            buildConfigField("boolean", "USE_MOCK_BACKEND", setting("busalert.useMock").toBoolean().toString())
        }
        release {
            buildConfigField("String", "BACKEND_BASE_URL", quoted(releaseBackendUrl))
            // La demo nunca se habilita en release.
            buildConfigField("boolean", "USE_MOCK_BACKEND", "false")
            isMinifyEnabled = false
        }
    }
    bundle {
        // El idioma se elige dentro de la app: todos los idiomas deben venir en el APK.
        language { enableSplit = false }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.9"
    }
    lint {
        // Los avisos también rompen el build. GradleDependency se desactiva porque
        // las versiones nuevas exigen otra toolchain (ver docs/DECISIONES.md, D-002).
        warningsAsErrors = true
        abortOnError = true
        disable += "GradleDependency"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
    }
}

// Un APK release sin backend HTTPS no sirve: se detiene el build con un mensaje claro.
val validateReleaseConfig by tasks.registering {
    doLast {
        require(releaseBackendUrl.startsWith("https://")) {
            "Define busalert.release.backendUrl (https://...) en local.properties o BUSALERT_RELEASE_BACKEND_URL."
        }
    }
}
tasks.configureEach {
    if (name == "preReleaseBuild") dependsOn(validateReleaseConfig)
}

dependencies {
    implementation(project(":shared"))
    implementation("androidx.core:core-ktx:1.12.0")

    // Compose for Wear OS
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.compose.ui:ui:1.6.3")
    implementation("androidx.compose.ui:ui-tooling-preview:1.6.3")
    debugImplementation("androidx.compose.ui:ui-tooling:1.6.3")
    implementation("androidx.wear.compose:compose-material:1.3.0")
    implementation("androidx.wear.compose:compose-foundation:1.3.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")

    // Ubicación (FusedLocationProvider) y su integración con corrutinas
    implementation("com.google.android.gms:play-services-location:21.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("app.cash.turbine:turbine:1.0.0")

    // Pruebas de UI de Compose en la JVM (Robolectric), sin emulador.
    testImplementation("org.robolectric:robolectric:4.11.1")
    testImplementation("androidx.test.ext:junit:1.1.5")
    testImplementation("androidx.compose.ui:ui-test-junit4:1.6.3")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.6.3")
}
