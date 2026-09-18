package com.pinbeatfinder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards against mistakes the Android resource merger only reports on CI: duplicate string
 * names within a file, and Hindi strings that are not in the default file (or vice versa).
 */
class ResourceSanityTest {
    private fun names(path: String): List<String> {
        val file = listOf(File(path), File("app/$path")).first { it.exists() }
        return Regex("""<string name="([^"]+)"""").findAll(file.readText()).map { it.groupValues[1] }.toList()
    }

    @Test
    fun `no duplicate string names`() {
        for (p in listOf("src/main/res/values/strings.xml", "src/main/res/values-hi/strings.xml")) {
            val all = names(p)
            val dupes = all.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
            assertTrue("$p has duplicate names: $dupes", dupes.isEmpty())
        }
    }

    @Test
    fun `hindi has exactly the default strings`() {
        val en = names("src/main/res/values/strings.xml").toSet()
        val hi = names("src/main/res/values-hi/strings.xml").toSet()
        assertEquals("Missing in Hindi: ${en - hi}", emptySet<String>(), en - hi)
        assertEquals("Only in Hindi: ${hi - en}", emptySet<String>(), hi - en)
    }
}
