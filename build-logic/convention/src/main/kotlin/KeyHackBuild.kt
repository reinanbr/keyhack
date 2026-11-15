import com.android.build.api.dsl.CommonExtension
import dev.detekt.gradle.extensions.DetektExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

/** Shared build constants. Changing minSdk is a security decision (see docs/adr). */
object KeyHackBuild {
    // Current AndroidX (core 1.19, activity 1.13, Compose BOM 2026.09) requires
    // compileSdk 37. targetSdk (runtime behavior) stays at 36.
    const val COMPILE_SDK = 37
    const val TARGET_SDK = 36
    const val MIN_SDK = 28
    val JAVA_VERSION = JavaVersion.VERSION_17
    const val NAMESPACE_ROOT = "com.reinanbr.keyhack"
}

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

/** Settings common to application and library modules. */
internal fun Project.configureAndroidCommon(extension: CommonExtension) {
    extension.apply {
        compileSdk = KeyHackBuild.COMPILE_SDK
        defaultConfig.minSdk = KeyHackBuild.MIN_SDK
        compileOptions.sourceCompatibility = KeyHackBuild.JAVA_VERSION
        compileOptions.targetCompatibility = KeyHackBuild.JAVA_VERSION
        lint.apply {
            warningsAsErrors = false
            abortOnError = true
            checkDependencies = false
            // Shared baseline-free config; security-relevant checks are errors.
            error +=
                setOf(
                    "HardcodedDebugMode",
                    "ExportedService",
                    "ExportedContentProvider",
                    "ExportedReceiver",
                    "AllowBackup",
                    "SetJavaScriptEnabled",
                    "AddJavascriptInterface",
                    "WorldReadableFiles",
                    "WorldWriteableFiles",
                    "TrustAllX509TrustManager",
                    "UnsafeDynamicallyLoadedCode",
                )
            disable += setOf("LogNotTimber", "GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
        }
        testOptions.unitTests.isReturnDefaultValues = false
    }
    dependencies {
        add("testImplementation", libs.findLibrary("junit").get())
        add("testImplementation", libs.findLibrary("kotlin-test").get())
    }
}

internal fun Project.configureDetekt() {
    pluginManager.apply("dev.detekt")
    extensions.configure(DetektExtension::class.java) {
        buildUponDefaultConfig.set(true)
        // `./gradlew detekt -PdetektAutoCorrect` applies ktlint formatting fixes.
        autoCorrect.set(providers.gradleProperty("detektAutoCorrect").isPresent)
        parallel.set(true)
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
        source.setFrom(
            files("src/main/kotlin", "src/main/java", "src/test/kotlin", "src/androidTest/kotlin"),
        )
    }
    dependencies {
        add("detektPlugins", libs.findLibrary("detekt-ktlint-wrapper").get())
    }
}
