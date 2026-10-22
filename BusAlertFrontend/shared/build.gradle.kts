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

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
