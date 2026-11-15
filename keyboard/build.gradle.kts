plugins {
    id("keyhack.android.library")
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.customview)
}
