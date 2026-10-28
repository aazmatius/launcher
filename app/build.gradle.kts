plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "ru.user.tvhome"
    compileSdk = 34
    defaultConfig { applicationId = "ru.user.tvhome"; minSdk = 21; targetSdk = 28; versionCode = 1; versionName = "0.1" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
