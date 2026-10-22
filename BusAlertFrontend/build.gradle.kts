import java.util.Properties

buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.3.0")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.22")
    }
}

/*
 * Configuración del build fuera del código.
 * Cada valor se busca primero como variable de entorno y luego en
 * local.properties (que no se sube a git). Ver README → "Configuración".
 */
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun busAlertSetting(propertyName: String, envName: String, default: String = ""): String =
    System.getenv(envName)?.takeIf { it.isNotBlank() }
        ?: localProperties.getProperty(propertyName)?.takeIf { it.isNotBlank() }
        ?: default

extra["busalert.debug.backendUrl"] =
    busAlertSetting("busalert.debug.backendUrl", "BUSALERT_DEBUG_BACKEND_URL", "http://10.0.2.2:3000/")
extra["busalert.release.backendUrl"] = busAlertSetting("busalert.release.backendUrl", "BUSALERT_RELEASE_BACKEND_URL")
extra["busalert.apiKey"] = busAlertSetting("busalert.apiKey", "BUSALERT_API_KEY")
extra["busalert.useMock"] = busAlertSetting("busalert.useMock", "BUSALERT_USE_MOCK", "false")
