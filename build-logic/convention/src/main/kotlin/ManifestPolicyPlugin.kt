import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.register

/** Configuration for [ManifestPolicyPlugin]; see [ManifestPolicy] for the rules. */
abstract class ManifestPolicyExtension {
    abstract val allowedPermissions: SetProperty<String>
    abstract val allowedExported: SetProperty<String>
    abstract val requiredPermissions: MapProperty<String, String>

    /** Per product flavor, the permissions its release builds may hold instead of [allowedPermissions]. */
    abstract val flavorAllowedPermissions: MapProperty<String, List<String>>
}

/**
 * Registers `verify<Variant>ManifestPolicy` for release variants and makes
 * both `check` and `assemble<Variant>` depend on it, so a forbidden permission
 * or exported component can never reach a release APK.
 */
class ManifestPolicyPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            val ext = extensions.create<ManifestPolicyExtension>("manifestPolicy")
            pluginManager.withPlugin("com.android.application") {
                val components = extensions.getByType(ApplicationAndroidComponentsExtension::class.java)
                components.onVariants(components.selector().withBuildType("release")) { variant ->
                    val cap = variant.name.replaceFirstChar(Char::uppercaseChar)
                    val verify =
                        tasks.register<VerifyManifestPolicyTask>("verify${cap}ManifestPolicy") {
                            group = "verification"
                            description = "Fails if the merged ${variant.name} manifest breaks the permission policy."
                            mergedManifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
                            allowedPermissions.set(
                                ext.flavorAllowedPermissions.getting(variant.flavorName.orEmpty())
                                    .map { it.toSet() }
                                    .orElse(ext.allowedPermissions),
                            )
                            allowedExported.set(ext.allowedExported)
                            requiredPermissions.set(ext.requiredPermissions)
                            report.set(layout.buildDirectory.file("reports/manifest-policy/${variant.name}.txt"))
                        }
                    tasks.named { it == "check" || it == "assemble$cap" || it == "bundle$cap" }
                        .configureEach { dependsOn(verify) }
                }
            }
        }
}

@CacheableTask
abstract class VerifyManifestPolicyTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val mergedManifest: RegularFileProperty

    @get:Input
    abstract val allowedPermissions: SetProperty<String>

    @get:Input
    abstract val allowedExported: SetProperty<String>

    @get:Input
    abstract val requiredPermissions: MapProperty<String, String>

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun verify() {
        val policy =
            ManifestPolicy(
                allowedPermissions = allowedPermissions.get(),
                allowedExported = allowedExported.get(),
                requiredPermissions = requiredPermissions.get(),
            )
        val manifest = mergedManifest.get().asFile
        val violations = policy.check(manifest)
        val out = report.get().asFile
        out.parentFile.mkdirs()
        if (violations.isNotEmpty()) {
            out.writeText("FAILED\n" + violations.joinToString("\n"))
            throw GradleException(
                "Manifest policy violations in $manifest:\n" + violations.joinToString("\n") { "  - $it" },
            )
        }
        out.writeText("OK\n")
    }
}
