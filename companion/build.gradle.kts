plugins {
    id("keyhack.android.application")
    id("keyhack.compose")
    id("keyhack.manifest-policy")
}

android {
    namespace = "com.reinanbr.keyhack.online"

    defaultConfig {
        applicationId = "com.reinanbr.keyhack.online"
        versionCode = 1
        versionName = "0.1.0"
    }
}

// KeyHack Online holds INTERNET and nothing else. It has no IME service; the
// signature-protected IPC service arrives in M6 (with an ADR).
manifestPolicy {
    allowedPermissions.set(setOf("android.permission.INTERNET"))
    allowedExported.set(setOf("com.reinanbr.keyhack.online.MainActivity"))
}

dependencies {
    implementation(project(":online-api"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
}
