import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Convention for the KeyHack app module. Release builds are
 * minified, strip android.util.Log (see config/proguard/strip-logging.pro) and
 * leave out the dependency-info block so the APK can be built reproducibly.
 */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("com.android.application")
            extensions.configure<ApplicationExtension> {
                configureAndroidCommon(this)
                defaultConfig.targetSdk = KeyHackBuild.TARGET_SDK

                // Release signing comes from CI secrets via the environment,
                // never from the repo. Without them the release APK is unsigned.
                val keystore =
                    providers.environmentVariable("KEYHACK_KEYSTORE").orNull?.let(::file)
                if (keystore != null && keystore.isFile) {
                    signingConfigs.create("release") {
                        storeFile = keystore
                        storePassword = providers.environmentVariable("KEYHACK_KEYSTORE_PASSWORD").get()
                        keyAlias = providers.environmentVariable("KEYHACK_KEY_ALIAS").get()
                        keyPassword = providers.environmentVariable("KEYHACK_KEY_PASSWORD").get()
                    }
                }

                buildTypes.getByName("release") {
                    signingConfig = signingConfigs.findByName("release")
                    isMinifyEnabled = true
                    isShrinkResources = true
                    proguardFiles(
                        getDefaultProguardFile("proguard-android-optimize.txt"),
                        rootProject.file("config/proguard/strip-logging.pro"),
                        "proguard-rules.pro",
                    )
                }

                // Reproducible builds: no encrypted dependency blob in the APK
                // (F-Droid requirement) and no VCS metadata.
                dependenciesInfo {
                    includeInApk = false
                    includeInBundle = false
                }
                buildTypes.configureEach { vcsInfo.include = false }

                packaging.resources.excludes +=
                    setOf("META-INF/*.version", "META-INF/**/LICENSE*", "kotlin-tooling-metadata.json", "DebugProbesKt.bin")
            }
            configureDetekt()
        }
}
