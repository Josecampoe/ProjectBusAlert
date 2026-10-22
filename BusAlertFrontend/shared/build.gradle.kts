plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.busalert.shared"
    compileSdk = 34
    defaultConfig {
        minSdk = 26
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
        unitTests.isReturnDefaultValues = true
    }
    // Contratos JSON generados por las pruebas del backend (../contracts).
    sourceSets["test"].resources.srcDir(rootProject.file("../contracts"))
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // HTTP hacia el backend BusAlert
    api("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")

    // Preferencias e historial del usuario
    api("androidx.datastore:datastore-preferences:1.1.1")
    implementation("com.google.code.gson:gson:2.10.1")

    // Sincronizacion reloj <-> telefono (Data Layer API)
    api("com.google.android.gms:play-services-wearable:18.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
