plugins {
    id("keyhack.android.library")
}

// AIDL contract shared with KeyHack Online (milestone M6, ADR required).
android {
    buildFeatures.aidl = true
}
