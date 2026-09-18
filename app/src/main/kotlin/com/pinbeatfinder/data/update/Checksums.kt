package com.pinbeatfinder.data.update

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/** Parsing of a `sha256sum`-style manifest and hashing of a downloaded file. Pure JVM. */
object Checksums {
    /** "hash  filename" lines → filename → lower-case hash. Tolerates `*` binary markers and blank lines. */
    fun parse(text: String): Map<String, String> =
        text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { line ->
                val parts = line.split(Regex("\\s+"), limit = 2)
                if (parts.size != 2 || parts[0].length != 64) null
                else parts[1].removePrefix("*").trim() to parts[0].lowercase()
            }
            .toMap()

    fun sha256(input: InputStream): String {
        val md = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(1 shl 16)
        input.use { s ->
            while (true) {
                val n = s.read(buf); if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun sha256(file: File): String = sha256(file.inputStream().buffered())

    /** Last path segment of a download URL, e.g. `pin-beat-finder-v0.16.0-release.apk`. */
    fun fileNameOf(url: String): String = url.substringBefore('?').substringAfterLast('/')
}
