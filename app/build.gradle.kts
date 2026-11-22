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

    // Stored uncompressed so the emoji font is memory-mapped, not inflated into RAM.
    androidResources {
        noCompress += "ttf"
    }
}

// Threat model: the keyboard must not be able to leak keystrokes through
// components or permissions nobody reviewed. This gate runs on the merged
// release manifest (all libraries included) and fails `check` and
// `assembleRelease`. INTERNET is allowed only for GIF search (ADR 0004).
// Changing either list needs an ADR.
manifestPolicy {
    allowedPermissions.set(setOf("android.permission.VIBRATE", "android.permission.INTERNET"))
    allowedExported.set(
        setOf(
            "com.reinanbr.keyhack.ime.KeyHackImeService",
            "com.reinanbr.keyhack.MainActivity",
        ),
    )
    requiredPermissions.set(
        mapOf("com.reinanbr.keyhack.ime.KeyHackImeService" to "android.permission.BIND_INPUT_METHOD"),
    )
}

dependencies {
    implementation(project(":ime"))
    implementation(project(":core"))
    implementation(project(":data"))
    implementation(project(":stickers"))
    implementation(project(":clipboard"))
    implementation(project(":devtools"))
    implementation(project(":theme"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
}
