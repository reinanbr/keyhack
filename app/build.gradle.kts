plugins {
    id("keyhack.android.application")
    id("keyhack.compose")
    id("keyhack.manifest-policy")
}

android {
    namespace = "com.reinanbr.keyhack"

    defaultConfig {
        applicationId = "com.reinanbr.keyhack"
        versionCode = 1
        versionName = "0.1.0"
    }
}

// Threat model, adversary 1: the core must not be able to exfiltrate keystrokes.
// This gate runs on the merged release manifest (all libraries included) and
// fails `check` and `assembleRelease`. Changing either list needs an ADR.
manifestPolicy {
    allowedPermissions.set(setOf("android.permission.VIBRATE"))
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
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
}
