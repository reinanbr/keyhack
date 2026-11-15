plugins {
    `kotlin-dsl`
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.android.gradle.plugin)
    implementation(libs.compose.gradle.plugin)
    implementation(libs.detekt.gradle.plugin)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "keyhack.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "keyhack.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("compose") {
            id = "keyhack.compose"
            implementationClass = "ComposeConventionPlugin"
        }
        register("manifestPolicy") {
            id = "keyhack.manifest-policy"
            implementationClass = "ManifestPolicyPlugin"
        }
    }
}
