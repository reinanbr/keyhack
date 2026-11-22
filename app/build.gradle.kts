plugins {
    id("keyhack.android.application")
    id("keyhack.compose")
    id("keyhack.manifest-policy")
}

android {
    namespace = "com.reinanbr.keyhack"

    defaultConfig {
        applicationId = "com.reinanbr.keyhack"
        versionCode = 2
        versionName = "0.2.0"
    }

    // "online" has GIF search, the only network use (ADR 0004); "offline"
    // leaves out :net, so the APK has no HTTP client and no INTERNET
    // permission. Same package: install one or the other.
    flavorDimensions += "network"
    productFlavors {
        create("online") {
            dimension = "network"
            isDefault = true
        }
        create("offline") {
            dimension = "network"
            versionNameSuffix = "-offline"
        }
    }

    // Stored uncompressed so the emoji font is memory-mapped, not inflated into RAM.
    androidResources {
        noCompress += "ttf"
    }
}

// Threat model: the keyboard must not be able to leak keystrokes through
// components or permissions nobody reviewed. This gate runs on the merged
// release manifest (all libraries included) and fails `check` and
// `assembleRelease`. INTERNET is allowed only for GIF search (ADR 0004),
// and never in the offline build. Changing these lists needs an ADR.
manifestPolicy {
    allowedPermissions.set(setOf("android.permission.VIBRATE", "android.permission.INTERNET"))
    flavorAllowedPermissions.put("offline", listOf("android.permission.VIBRATE"))
    allowedExported.set(
        setOf(
            "com.reinanbr.keyhack.ime.KeyHackImeService",
            "com.reinanbr.keyhack.MainActivity",
            // WhatsApp reads the user's sticker packs from it (ADR 0006).
            "com.reinanbr.keyhack.stickers.WhatsAppStickerProvider",
        ),
    )
    requiredPermissions.set(
        mapOf(
            "com.reinanbr.keyhack.ime.KeyHackImeService" to "android.permission.BIND_INPUT_METHOD",
            "com.reinanbr.keyhack.stickers.WhatsAppStickerProvider" to "com.whatsapp.sticker.READ",
        ),
    )
}

dependencies {
    implementation(project(":ime"))
    implementation(project(":emoji"))
    implementation(project(":core"))
    implementation(project(":data"))
    implementation(project(":stickers"))
    implementation(project(":clipboard"))
    implementation(project(":devtools"))
    implementation(project(":suggest"))
    implementation(project(":backup"))
    implementation(project(":theme"))
    implementation(project(":media"))
    "onlineImplementation"(project(":net"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
}
