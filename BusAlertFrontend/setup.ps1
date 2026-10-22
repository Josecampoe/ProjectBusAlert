$ErrorActionPreference = "Stop"

$workspace = $PSScriptRoot
Set-Location $workspace

# Create directories
New-Item -ItemType Directory -Force "gradle/wrapper" | Out-Null
New-Item -ItemType Directory -Force "shared/src/main/java/com/busalert/shared" | Out-Null
New-Item -ItemType Directory -Force "mobile/src/main/java/com/busalert/mobile" | Out-Null
New-Item -ItemType Directory -Force "wear/src/main/java/com/busalert/wear" | Out-Null
New-Item -ItemType Directory -Force "wear/src/main/res/mipmap" | Out-Null
New-Item -ItemType Directory -Force "mobile/src/main/res/values" | Out-Null

# settings.gradle.kts
@"
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "BusAlert"
include(":mobile", ":wear", ":shared")
"@ | Out-File -FilePath "settings.gradle.kts" -Encoding utf8

# build.gradle.kts
@"
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
"@ | Out-File -FilePath "build.gradle.kts" -Encoding utf8

# gradle.properties
@"
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
android.nonTransitiveRClass=true
"@ | Out-File -FilePath "gradle.properties" -Encoding utf8

# gradle-wrapper.properties
@"
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.4-bin.zip
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
"@ | Out-File -FilePath "gradle/wrapper/gradle-wrapper.properties" -Encoding utf8

# shared/build.gradle.kts
@"
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
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
"@ | Out-File -FilePath "shared/build.gradle.kts" -Encoding utf8

# shared/AndroidManifest.xml
@"
<?xml version="1.0" encoding="utf-8"?>
<manifest package="com.busalert.shared" />
"@ | Out-File -FilePath "shared/src/main/AndroidManifest.xml" -Encoding utf8

# mobile/build.gradle.kts
@"
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.busalert.mobile"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.busalert"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.10"
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
    implementation(project(":shared"))
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.compose.ui:ui:1.6.3")
    implementation("androidx.compose.ui:ui-tooling-preview:1.6.3")
    implementation("androidx.compose.material3:material3:1.2.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("com.google.android.gms:play-services-wearable:18.1.0")
}
"@ | Out-File -FilePath "mobile/build.gradle.kts" -Encoding utf8

# mobile/AndroidManifest.xml
@"
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />
    <application
        android:allowBackup="true"
        android:label="BusAlert Mobile"
        android:supportsRtl="true"
        android:theme="@android:style/Theme.DeviceDefault.Light.NoActionBar">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
"@ | Out-File -FilePath "mobile/src/main/AndroidManifest.xml" -Encoding utf8

# mobile MainActivity.kt
@"
package com.busalert.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Text("BusAlert Mobile")
        }
    }
}
"@ | Out-File -FilePath "mobile/src/main/java/com/busalert/mobile/MainActivity.kt" -Encoding utf8

# wear/build.gradle.kts
@"
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.busalert.wear"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.busalert.wear"
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.10"
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
    implementation(project(":shared"))
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("com.google.android.gms:play-services-wearable:18.1.0")
    
    // Compose for Wear OS
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.compose.ui:ui:1.6.3")
    implementation("androidx.compose.ui:ui-tooling-preview:1.6.3")
    implementation("androidx.wear.compose:compose-material:1.3.0")
    implementation("androidx.wear.compose:compose-foundation:1.3.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
}
"@ | Out-File -FilePath "wear/build.gradle.kts" -Encoding utf8

# wear/AndroidManifest.xml
@"
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-feature android:name="android.hardware.type.watch" />
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.INTERNET" />
    <application
        android:allowBackup="true"
        android:label="BusAlert Wear"
        android:supportsRtl="true"
        android:theme="@android:style/Theme.DeviceDefault">
        <uses-library android:name="com.google.android.wearable" android:required="true" />
        <meta-data
            android:name="com.google.android.wearable.standalone"
            android:value="true" />
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
"@ | Out-File -FilePath "wear/src/main/AndroidManifest.xml" -Encoding utf8

# wear MainActivity.kt
@"
package com.busalert.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.wear.compose.material.Text

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Text("BusAlert Wear")
        }
    }
}
"@ | Out-File -FilePath "wear/src/main/java/com/busalert/wear/MainActivity.kt" -Encoding utf8

Write-Host "Project scaffolded successfully"
