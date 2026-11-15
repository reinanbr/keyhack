import org.w3c.dom.Element
import java.io.File
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Pure policy check over a merged AndroidManifest.xml. It is a build gate: a
 * violation fails `check` and `assembleRelease`. See docs/adr/0002.
 *
 * Rules:
 *  1. Every `<uses-permission>` is in [allowedPermissions], or is a permission
 *     the same manifest declares under the app's own package with
 *     protectionLevel="signature".
 *  2. Every declared `<permission>` uses protectionLevel="signature".
 *  3. Every exported component (activity, activity-alias, service, receiver,
 *     provider) is in [allowedExported]. Components whose class name is in
 *     [requiredPermissions] must be guarded by that permission.
 *  4. `<application>` has allowBackup="false", sets dataExtractionRules, does
 *     not enable cleartext traffic or debuggable.
 */
data class ManifestPolicy(
    val allowedPermissions: Set<String>,
    val allowedExported: Set<String>,
    val requiredPermissions: Map<String, String> = emptyMap(),
) {
    fun check(manifest: File): List<String> = check(parse(manifest))

    fun check(root: Element): List<String> {
        val violations = mutableListOf<String>()
        val pkg = root.getAttribute("package")

        val declared =
            root.children("permission").associate { it.android("name") to it.android("protectionLevel") }
        declared.forEach { (name, level) ->
            if (level != "signature") {
                violations += "permission $name must use protectionLevel=\"signature\" (found \"$level\")"
            }
        }

        val used = root.children("uses-permission") + root.children("uses-permission-sdk-23")
        for (perm in used.map { it.android("name") }) {
            val ownSignature = pkg.isNotEmpty() && perm.startsWith("$pkg.") && declared[perm] == "signature"
            if (perm !in allowedPermissions && !ownSignature) {
                violations += "forbidden permission: $perm"
            }
        }

        val app = root.children("application").singleOrNull()
        if (app == null) {
            violations += "manifest has no <application>"
            return violations
        }
        if (app.android("allowBackup") != "false") violations += "application must set allowBackup=\"false\""
        if (app.android("dataExtractionRules").isEmpty()) {
            violations += "application must set dataExtractionRules"
        }
        if (app.android("usesCleartextTraffic") == "true") violations += "usesCleartextTraffic must not be true"
        if (app.android("debuggable") == "true") violations += "release manifest must not be debuggable"

        for (tag in COMPONENT_TAGS) {
            for (component in app.children(tag)) {
                val name = component.android("name")
                val exported = component.isExported(tag)
                if (exported && name !in allowedExported) {
                    violations += "exported $tag not in allowlist: $name"
                }
                val required = requiredPermissions[name]
                if (required != null && component.android("permission") != required) {
                    violations += "$tag $name must be guarded by $required"
                }
            }
        }
        return violations
    }

    private fun Element.isExported(tag: String): Boolean {
        val explicit = android("exported")
        if (explicit.isNotEmpty()) return explicit == "true"
        // targetSdk >= 31 forces an explicit value on components with intent
        // filters; providers default to not exported since API 17.
        return tag != "provider" && children("intent-filter").isNotEmpty()
    }

    companion object {
        private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
        private val COMPONENT_TAGS = listOf("activity", "activity-alias", "service", "receiver", "provider")

        fun parse(file: File): Element {
            val factory =
                DocumentBuilderFactory.newInstance().apply {
                    isNamespaceAware = true
                    setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
                    setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                    isExpandEntityReferences = false
                }
            return factory.newDocumentBuilder().parse(file).documentElement
        }

        private fun Element.android(attr: String): String = getAttributeNS(ANDROID_NS, attr)

        private fun Element.children(tag: String): List<Element> {
            val nodes = childNodes
            return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }.filter { it.tagName == tag }
        }
    }
}
