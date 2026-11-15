import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Convention for every core library module. The namespace is derived from the
 * module path (":online-api" -> com.reinanbr.keyhack.onlineapi).
 */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("com.android.library")
            extensions.configure<LibraryExtension> {
                configureAndroidCommon(this)
                namespace = "${KeyHackBuild.NAMESPACE_ROOT}.${name.replace("-", "")}"
                defaultConfig.consumerProguardFiles("consumer-rules.pro")
            }
            configureDetekt()
        }
}
