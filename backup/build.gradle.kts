plugins {
    id("keyhack.android.library")
}

dependencies {
    implementation(project(":core"))
    // Backups.read throws PasswordCipher.WrongPassword.
    api(project(":security"))
    implementation(project(":data"))
    implementation(project(":theme"))
    implementation(project(":suggest"))
    implementation(project(":emoji"))
}
