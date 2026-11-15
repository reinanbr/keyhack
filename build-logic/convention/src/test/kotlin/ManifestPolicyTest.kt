import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ManifestPolicyTest {
    private val ime = "com.reinanbr.keyhack.ime.KeyHackImeService"
    private val launcher = "com.reinanbr.keyhack.MainActivity"
    private val policy =
        ManifestPolicy(
            allowedPermissions = setOf("android.permission.VIBRATE"),
            allowedExported = setOf(ime, launcher),
            requiredPermissions = mapOf(ime to "android.permission.BIND_INPUT_METHOD"),
        )

    private fun manifest(
        body: String = "",
        app: String = "",
        appAttrs: String = """android:allowBackup="false" android:dataExtractionRules="@xml/r"""",
    ): Element {
        val xml =
            """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.reinanbr.keyhack">
              $body
              <application $appAttrs>$app</application>
            </manifest>
            """.trimIndent()
        val f = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        return f.newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray())).documentElement
    }

    private val goodIme =
        """<service android:name="$ime" android:exported="true"
              android:permission="android.permission.BIND_INPUT_METHOD"/>"""

    @Test
    fun cleanManifestPasses() {
        val m = manifest("""<uses-permission android:name="android.permission.VIBRATE"/>""", goodIme)
        assertEquals(emptyList(), policy.check(m))
    }

    @Test
    fun internetIsRejected() {
        val m = manifest("""<uses-permission android:name="android.permission.INTERNET"/>""", goodIme)
        assertEquals(listOf("forbidden permission: android.permission.INTERNET"), policy.check(m))
    }

    @Test
    fun sdk23PermissionTagIsAlsoChecked() {
        val m = manifest("""<uses-permission-sdk-23 android:name="android.permission.RECORD_AUDIO"/>""", goodIme)
        assertEquals(listOf("forbidden permission: android.permission.RECORD_AUDIO"), policy.check(m))
    }

    @Test
    fun ownSignaturePermissionIsAllowed() {
        val perm = "com.reinanbr.keyhack.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
        val m =
            manifest(
                """<permission android:name="$perm" android:protectionLevel="signature"/>
                   <uses-permission android:name="$perm"/>""",
                goodIme,
            )
        assertEquals(emptyList(), policy.check(m))
    }

    @Test
    fun ownNonSignaturePermissionIsRejected() {
        val perm = "com.reinanbr.keyhack.SOMETHING"
        val m =
            manifest(
                """<permission android:name="$perm" android:protectionLevel="normal"/>
                   <uses-permission android:name="$perm"/>""",
                goodIme,
            )
        val v = policy.check(m)
        assertEquals(2, v.size, v.toString())
    }

    @Test
    fun foreignPackageSignaturePermissionIsRejected() {
        val perm = "com.evil.SIGNATURE"
        val m = manifest("""<uses-permission android:name="$perm"/>""", goodIme)
        assertEquals(listOf("forbidden permission: $perm"), policy.check(m))
    }

    @Test
    fun unlistedExportedComponentIsRejected() {
        val m = manifest(app = goodIme + """<receiver android:name="x.Leaky" android:exported="true"/>""")
        assertEquals(listOf("exported receiver not in allowlist: x.Leaky"), policy.check(m))
    }

    @Test
    fun implicitExportViaIntentFilterIsDetected() {
        val m =
            manifest(
                app = goodIme + """<activity android:name="x.A"><intent-filter><action android:name="a"/></intent-filter></activity>""",
            )
        assertEquals(listOf("exported activity not in allowlist: x.A"), policy.check(m))
    }

    @Test
    fun imeWithoutBindPermissionIsRejected() {
        val m = manifest(app = """<service android:name="$ime" android:exported="true"/>""")
        assertEquals(listOf("service $ime must be guarded by android.permission.BIND_INPUT_METHOD"), policy.check(m))
    }

    @Test
    fun backupMustBeDisabled() {
        val m = manifest(app = goodIme, appAttrs = """android:allowBackup="true"""")
        val v = policy.check(m)
        assertTrue(v.any { "allowBackup" in it }, v.toString())
        assertTrue(v.any { "dataExtractionRules" in it }, v.toString())
    }

    @Test
    fun cleartextAndDebuggableAreRejected() {
        val m =
            manifest(
                app = goodIme,
                appAttrs =
                    """android:allowBackup="false" android:dataExtractionRules="@xml/r"
                       android:usesCleartextTraffic="true" android:debuggable="true"""",
            )
        assertEquals(2, policy.check(m).size)
    }
}
