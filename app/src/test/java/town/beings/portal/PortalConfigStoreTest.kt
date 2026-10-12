package town.beings.portal

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PortalConfigStoreTest {
    @Test
    fun serializesTomlStringsWithEscaping() {
        val content = PortalTomlSerializer.serialize("relay\\\"line\nnext", "mcp\ttoken")
        assertEquals(
            "workspace = \"./workspace\"\n" +
                "connect = \"relay\\\\\\\"line\\nnext\"\n" +
                "portal_mcp_token = \"mcp\\ttoken\"\n",
            content,
        )
    }

    @Test
    fun skipsEmptyOptionalValues() {
        val content = PortalTomlSerializer.serialize("  ", "")
        assertEquals("workspace = \"./workspace\"\n", content)
        assertFalse(content.contains("token ="))
        assertFalse(content.contains("connect ="))
    }

    @Test
    fun saveOverwritesExistingConfiguration() {
        val file = File.createTempFile("portal-config", ".toml")
        try {
            file.writeText("token = \"obsolete\"\n")
            PortalConfigStore(file).save("relay", null)
            val content = file.readText()
            assertTrue(content.contains("connect = \"relay\""))
            assertFalse(content.contains("token ="))
            assertEquals("workspace = \"./workspace\"\nconnect = \"relay\"\n", content)
        } finally {
            file.delete()
        }
    }
}
