package dev.andes.core

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

/** Original rule-free Java artifact selection for the 1.20.4 ART experiment.
 * Android is never treated as desktop Linux. LWJGL is supplied by our separate adapter.
 * A selected JAR's Java classes still require compatibility testing; this is not a native ABI claim.
 */
object JavaLibraryPlan {
    fun select(version: JSONObject): List<JSONObject> {
        require(version.getString("id") == "1.20.4")
        val libraries = version.getJSONArray("libraries")
        require(libraries.length() <= 256)
        val result = (0 until libraries.length()).map { libraries.getJSONObject(it) }.filter {
            !it.has("rules") && !it.has("natives") && !it.getString("name").startsWith("org.lwjgl:")
        }
        require(result.map { it.getString("name") }.distinct().size == result.size)
        for (library in result) {
            val coordinate = library.getString("name")
            require(coordinate.matches(Regex("[A-Za-z0-9_.-]+:[A-Za-z0-9_.-]+:[A-Za-z0-9_.-]+")))
            val (group, name, release) = coordinate.split(':')
            require(listOf(group, name, release).none { it == "." || it == ".." })
            val path = "${group.replace('.', '/')}/$name/$release/$name-$release.jar"
            val artifact = library.getJSONObject("downloads").getJSONObject("artifact")
            require(artifact.getString("path") == path)
            val url = URI(artifact.getString("url"))
            require(url.toASCIIString() == "https://libraries.minecraft.net/$path")
            require(artifact.getString("sha1").matches(Regex("[a-f0-9]{40}")))
            require(artifact.getLong("size") in 1..64L * 1024 * 1024)
        }
        require(result.any { it.getString("name") == "com.google.guava:guava:32.1.2-jre" })
        require(result.any { it.getString("name") == "com.google.guava:failureaccess:1.0.1" })
        return result
    }
}
