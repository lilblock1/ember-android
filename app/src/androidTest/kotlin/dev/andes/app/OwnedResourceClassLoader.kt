package dev.andes.app

import java.io.File
import java.net.URL
import java.util.Collections
import java.util.Enumeration
import java.util.zip.ZipFile

/** Original resources stay in verified private JARs; bytecode comes from the private DEX.
 * This parent defines no game classes and never loads desktop native libraries.
 */
internal class OwnedResourceClassLoader(parent: ClassLoader, private val jars: List<File>) : ClassLoader(parent) {
    override fun findResource(name: String): URL? = resources(name).firstOrNull()
    override fun findResources(name: String): Enumeration<URL> = Collections.enumeration(resources(name))
    private fun resources(name: String): List<URL> {
        if (listOf(".so", ".dll", ".dylib", ".jnilib").any { name.endsWith(it, ignoreCase = true) }) return emptyList()
        if (name.startsWith('/') || name.contains('\\') || name.split('/').any { it == ".." } || name.contains('!')) return emptyList()
        return jars.filter { file -> ZipFile(file).use { it.getEntry(name)?.isDirectory == false } }
            .map { file -> URL("jar:${file.toURI().toASCIIString()}!/$name") }
    }
}
