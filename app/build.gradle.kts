plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
 namespace = "io.github.interrupt21h.droidrds"
 compileSdk = 35
 defaultConfig { applicationId = "io.github.interrupt21h.droidrds"; minSdk = 26; targetSdk = 35; versionCode = 1; versionName = "0.1.1-" + (System.getenv("DROIDRDS_BUILD_ID") ?: "local") }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
}
dependencies { implementation("androidx.core:core:1.15.0"); testImplementation("junit:junit:4.13.2") }
