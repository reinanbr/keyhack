plugins {
    id("keyhack.android.library")
}

dependencies {
    api(project(":core"))
    api(project(":theme"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.customview)
}
