package dev.andes.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class JavaLibraryPlanTest {
    private fun artifact(name: String): JSONObject {
        val (group, module, version) = name.split(':')
        val path = "${group.replace('.', '/')}/$module/$version/$module-$version.jar"
        return JSONObject().put("name", name).put("downloads", JSONObject().put("artifact", JSONObject()
            .put("path", path).put("url", "https://libraries.minecraft.net/$path").put("size", 123).put("sha1", "a".repeat(40))))
    }
    private fun version() = JSONObject().put("id", "1.20.4").put("libraries", JSONArray()
        .put(artifact("com.google.guava:guava:32.1.2-jre"))
        .put(artifact("com.google.guava:failureaccess:1.0.1")))
    @Test fun actualOfficialMetadataSelects36JavaArtifacts() {
        val data = requireNotNull(javaClass.getResourceAsStream("/library-metadata-1.20.4.json")).bufferedReader().use { it.readText() }
        val selected = JavaLibraryPlan.select(JSONObject(data))
        assertEquals(36, selected.size)
        assertEquals(55695981L, selected.sumOf { it.getJSONObject("downloads").getJSONObject("artifact").getLong("size") })
    }
    @Test fun selectsGuavaAndRequiredHelperWithoutDesktopClassifiers() {
        val version = version()
        version.getJSONArray("libraries").put(artifact("org.lwjgl:lwjgl:3.3.2"))
            .put(artifact("example:desktop:1").put("rules", JSONArray().put(JSONObject().put("action", "allow").put("os", JSONObject().put("name", "linux")))))
            .put(artifact("example:native:1").put("natives", JSONObject().put("linux", "natives-linux")))
        assertEquals(2, JavaLibraryPlan.select(version).size)
    }
    @Test fun refusesMissingHelperAndUnknownVersion() {
        val missing = version(); missing.getJSONArray("libraries").remove(1)
        assertThrows(IllegalArgumentException::class.java) { JavaLibraryPlan.select(missing) }
        assertThrows(IllegalArgumentException::class.java) { JavaLibraryPlan.select(version().put("id", "other")) }
    }
    @Test fun rejectsRedirectHostsTraversalMismatchesAndOversizeArtifacts() {
        for (field in listOf("url", "path", "sha1", "size")) {
            val version = version(); val value = version.getJSONArray("libraries").getJSONObject(0).getJSONObject("downloads").getJSONObject("artifact")
            value.put(field, when(field) { "url" -> "https://attacker.example/file.jar"; "path" -> "../../outside.jar"; "sha1" -> "invalid"; else -> 128L * 1024 * 1024 })
            assertThrows(IllegalArgumentException::class.java) { JavaLibraryPlan.select(version) }
        }
    }
    @Test fun rejectsDuplicateCoordinatesAndUnruledNativeClassifier() {
        val duplicate = version(); duplicate.getJSONArray("libraries").put(artifact("com.google.guava:failureaccess:1.0.1"))
        assertThrows(IllegalArgumentException::class.java) { JavaLibraryPlan.select(duplicate) }
        val native = version(); native.getJSONArray("libraries").put(JSONObject().put("name", "example:native:1:linux-aarch64"))
        assertThrows(IllegalArgumentException::class.java) { JavaLibraryPlan.select(native) }
    }
}
