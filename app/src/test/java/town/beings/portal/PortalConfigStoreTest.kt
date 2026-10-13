package town.beings.portal

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PortalConfigStoreTest {
    @Test
    fun serializesOnlyPrivateWorkspaceAndConnectLink() {
        val content = PortalTomlSerializer.serialize(
            "/data/user/0/town.beings.portal/files/workspace",
            "https://echo.beings.town/frank/?token=abc\\\"next",
        )
        assertEquals(
            "workspace = \"/data/user/0/town.beings.portal/files/workspace\"\n" +
                "connect = \"https://echo.beings.town/frank/?token=abc\\\\\\\"next\"\n",
            content,
        )
        assertFalse(content.contains("portal_mcp_token"))
        assertFalse(content.contains("source_dir"))
        assertFalse(content.contains("workspace = \"./workspace\""))
    }

    @Test
    fun saveOverwritesObsoleteConfigurationAndReadsLink() {
        val file = File.createTempFile("portal-config", ".toml")
        try {
            file.writeText("connect = \"obsolete\"\nportal_mcp_token = \"old\"\n")
            val store = PortalConfigStore(file, File("/data/user/0/town.beings.portal/files/workspace"))
            val link = "https://echo.beings.town/frank/?token=abc"
            store.save(link)
            val content = file.readText()
            assertEquals(link, store.readConnectLink())
            assertTrue(content.contains("connect = \"$link\""))
            assertFalse(content.contains("portal_mcp_token"))
            assertFalse(content.contains("connect_link"))
        } finally {
            file.delete()
        }
    }
}
