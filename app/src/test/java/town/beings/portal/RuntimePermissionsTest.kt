package town.beings.portal

import org.junit.Assert.assertEquals
import org.junit.Test

class RuntimePermissionsTest {

    @Test
    fun api33AndAboveRequiresMediaAudioAndNotifications() {
        val expected = listOf(
            android.Manifest.permission.READ_MEDIA_AUDIO,
            android.Manifest.permission.POST_NOTIFICATIONS,
        )
        assertEquals(expected, RuntimePermissions.requiredForApi(33))
        assertEquals(expected, RuntimePermissions.requiredForApi(36))
    }

    @Test
    fun belowApi33RequiresLegacyExternalStorage() {
        val expected = listOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        assertEquals(expected, RuntimePermissions.requiredForApi(26))
        assertEquals(expected, RuntimePermissions.requiredForApi(31))
    }

    @Test
    fun recordingPermissionTracksApiLevel() {
        assertEquals(android.Manifest.permission.READ_MEDIA_AUDIO, RuntimePermissions.recordingPermissionForApi(33))
        assertEquals(android.Manifest.permission.READ_MEDIA_AUDIO, RuntimePermissions.recordingPermissionForApi(36))
        assertEquals(android.Manifest.permission.READ_EXTERNAL_STORAGE, RuntimePermissions.recordingPermissionForApi(26))
        assertEquals(android.Manifest.permission.READ_EXTERNAL_STORAGE, RuntimePermissions.recordingPermissionForApi(32))
    }
}