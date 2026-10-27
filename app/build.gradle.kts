import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

// Monetization config (spec 09), from gradle.properties or -P. Empty IDs fall back to Google's test units.
fun prop(name: String) = providers.gradleProperty("luminux.$name").getOrElse("")
val admobAppId = prop("admobAppId")
val admobRewardedId = prop("admobRewardedId")
val admobNativeId = prop("admobNativeId")
val newsUrl = prop("newsUrl")
val playLicenseKey = prop("playLicenseKey")
val proFreeForTesting = prop("proFreeForTesting").toBoolean()
/** Acts as if Pro were bought: no ads, no pin limit, the viewer's Pro actions. For trying Pro before Play has the product. */
val proUnlockedForTesting = prop("proUnlockedForTesting").toBoolean()
/** A Play internal-testing bundle: Google's test ads and an unverified Pro are allowed (production stays strict). */
val internalTesting = prop("internalTesting").toBoolean()
val testAdmobAppId = "ca-app-pub-3940256099942544~3347511713"

// The Play upload key, from a git-ignored keystore.properties (docs/11-release.md). Without it, local release builds are
// signed with the debug key so the minified build can still be installed and tried; a Play bundle refuses to build.
val keystoreFile = rootProject.file("keystore.properties")
val keystore = Properties().apply { if (keystoreFile.exists()) keystoreFile.inputStream().use(::load) }

android {
    namespace = "com.buddhanag.luminux"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.buddhanag.luminux"
        minSdk = 33
        targetSdk = 37
        // Every Play upload needs a higher code: -Pluminux.versionCode=2, 3, …
        versionCode = prop("versionCode").ifEmpty { "1" }.toInt()
        versionName = "0.1.0"
        buildConfigField("String", "ADMOB_REWARDED_ID", "\"$admobRewardedId\"")
        buildConfigField("String", "ADMOB_NATIVE_ID", "\"$admobNativeId\"")
        buildConfigField("String", "NEWS_URL", "\"$newsUrl\"")
        buildConfigField("String", "PLAY_LICENSE_KEY", "\"$playLicenseKey\"")
        buildConfigField("boolean", "PRO_FREE_FOR_TESTING", "$proFreeForTesting")
        buildConfigField("boolean", "PRO_UNLOCKED_FOR_TESTING", "$proUnlockedForTesting")
        // Debug only: a UMP hashed device id (from logcat) puts that device in the EEA for the consent form test.
        buildConfigField("String", "UMP_TEST_DEVICE", "\"${prop("umpTestDevice")}\"")
    }

    signingConfigs {
        if (keystoreFile.exists()) create("upload") {
            storeFile = rootProject.file(keystore.getProperty("storeFile"))
            storePassword = keystore.getProperty("storePassword")
            keyAlias = keystore.getProperty("keyAlias")
            keyPassword = keystore.getProperty("keyPassword")
        }
    }

    buildTypes {
        debug {
            manifestPlaceholders["admobAppId"] = testAdmobAppId
        }
        release {
            manifestPlaceholders["admobAppId"] = admobAppId.ifEmpty { testAdmobAppId }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("upload") ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// AdMob suspends accounts whose owner sees their own live ads, so Play uploads must carry real IDs and test builds must not.
// Only the bundle is checked: a local release APK with test IDs is fine (like iOS, which checks archives only).
val checkReleaseAdIds by tasks.registering {
    val values = mapOf("luminux.admobAppId" to admobAppId, "luminux.admobRewardedId" to admobRewardedId)
    val licenseKey = playLicenseKey
    val freeForTesting = proFreeForTesting
    val unlockedForTesting = proUnlockedForTesting
    val internal = internalTesting
    val signed = keystoreFile.exists()
    doLast {
        val problems = buildList {
            if (!signed) add("keystore.properties is missing, so the bundle would be signed with the debug key (docs/11-release.md)")
            if (freeForTesting) add("luminux.proFreeForTesting must be off for a Play upload")
            if (unlockedForTesting) add("luminux.proUnlockedForTesting must be off for a Play upload")
            // Test ads are what an internal-testing build should show: the owner never sees their own live ads.
            if (!internal) {
                values.forEach { (key, value) ->
                    if (value.isEmpty()) add("$key is empty")
                    else if ("3940256099942544" in value) add("$key is still Google's test ID")
                }
                if (licenseKey.isEmpty()) add("luminux.playLicenseKey is empty, so purchases can't be verified")
            }
        }
        // The native unit isn't checked: the news panel is parked.
        if (problems.isNotEmpty()) throw GradleException(problems.joinToString("\n"))
    }
}
tasks.matching { it.name == "bundleRelease" }.configureEach { dependsOn(checkReleaseAdIds) }

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
    implementation(libs.androidx.exifinterface)
    implementation(libs.telephoto.zoomable.image.coil3)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui.compose)
    implementation(libs.mlkit.subject.segmentation)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.billing.ktx)
    implementation(libs.play.services.ads)
    implementation(libs.ump)
    implementation(libs.androidx.browser)
    implementation(libs.glance.appwidget)
    implementation(libs.work.runtime)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
