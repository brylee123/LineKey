plugins { id("com.android.application") }
android {
    namespace = "org.linekey.keyboard"
    compileSdk = 35
    defaultConfig {
        applicationId = "org.linekey.keyboard"
        minSdk = 26
        targetSdk = 35
        versionCode = 11
        versionName = "0.7.2"
        testInstrumentationRunner = "org.linekey.keyboard.KeyboardSmokeTest"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildTypes { release { isMinifyEnabled = false } }
    lint { abortOnError = true }
}
dependencies {
    implementation(project(":core"))
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.startup:startup-runtime:1.1.1")
    implementation("io.noties.markwon:core:4.6.2")
    implementation("io.noties.markwon:ext-tables:4.6.2")
}
