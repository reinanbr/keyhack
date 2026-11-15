plugins {
    id("keyhack.android.library")
}

dependencies {
    implementation(project(":core"))
    implementation(project(":keyboard"))
    implementation(project(":security"))
    implementation(libs.androidx.core.ktx)
}
