// Plugins come from the included build-logic build (convention plugins), so
// only `base` is applied here. AGP 9 compiles Kotlin itself ("built-in
// Kotlin"): never apply org.jetbrains.kotlin.android.
plugins {
    base
}

tasks.named("check") {
    // Unit tests of the build logic itself (manifest policy guard).
    dependsOn(gradle.includedBuild("build-logic").task(":convention:test"))
}

tasks.register("verifyManifestPolicies") {
    group = "verification"
    description = "Checks the release manifests of both apps against the permission/export policy."
    dependsOn(":app:verifyReleaseManifestPolicy", ":companion:verifyReleaseManifestPolicy")
}
