plugins {
    id("keyhack.android.library")
}

dependencies {
    implementation(project(":core"))
    implementation(project(":keyboard"))
    implementation(project(":layouts"))
    implementation(project(":suggest"))
    implementation(project(":emoji"))
    implementation(project(":media"))
    implementation(project(":commit"))
    implementation(project(":devtools"))
    implementation(project(":data"))
    implementation(project(":theme"))
    implementation(project(":security"))
    implementation(libs.androidx.core.ktx)
}
