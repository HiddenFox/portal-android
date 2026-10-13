package town.beings.portal

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PortalConfigStoreTest {
    @Test
    fun serializesPrivateWorkspaceConnectLinkAndKitsDir() {
        val content = PortalTomlSerializer.serialize(
            "/data/user/0/town.beings.portal/files/workspace",
            "https://echo.beings.town/frank/?token=abc\"next",
            "/data/user/0/town.beings.portal/files/.heart-portal/kits",
        )
        assertEquals(
            "workspace = \"/data/user/0/town.beings.portal/files/workspace\"\n" +
                "connect = \"https://echo.beings.town/frank/?token=abc\\\"next\"\n" +
                "kits_dir = \"/data/user/0/town.beings.portal/files/.heart-portal/kits\"\n",
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
            val kitsDir = File.createTempFile("portal-kits", "")
            kitsDir.delete()
            val store = PortalConfigStore(
                file,
                File("/data/user/0/town.beings.portal/files/workspace"),
                kitsDir,
            )
            val link = "https://echo.beings.town/frank/?token=abc"
            store.save(link)
            val content = file.readText()
            assertEquals(link, store.readConnectLink())
            assertTrue(content.contains("connect = \"$link\""))
            assertTrue(content.contains("kits_dir = \""))
            assertTrue(content.contains(kitsDir.name))
            assertTrue(kitsDir.isDirectory)
            assertFalse(content.contains("portal_mcp_token"))
            assertFalse(content.contains("connect_link"))
        } finally {
            file.delete()
        }
    }

    @Test
    fun ensureKitsDirUpgradesLegacyConfigWithoutKitsDirEntry() {
        val file = File.createTempFile("portal-config", ".toml")
        val kitsDir = File.createTempFile("portal-kits", "")
        kitsDir.delete()
        try {
            // Legacy config: no kits_dir line.
            file.writeText(
                "workspace = \"/data/user/0/town.beings.portal/files/workspace\"\n" +
                    "connect = \"https://echo.beings.town/frank/?token=abc\"\n",
            )
            val store = PortalConfigStore(
                file,
                File("/data/user/0/town.beings.portal/files/workspace"),
                kitsDir,
            )
            store.ensureKitsDir()
            val lines = file.readLines()
            assertEquals(3, lines.size)
            // Inserted directly after workspace, before connect.
            assertEquals("workspace = \"/data/user/0/town.beings.portal/files/workspace\"", lines[0])
            assertEquals("kits_dir = \"${kitsDir.absolutePath}\"", lines[1])
            assertTrue(kitsDir.isDirectory)
            // Idempotent: second run must not duplicate the entry.
            store.ensureKitsDir()
            assertEquals(3, file.readLines().size)
        } finally {
            file.delete()
            kitsDir.deleteRecursively()
        }
    }
}