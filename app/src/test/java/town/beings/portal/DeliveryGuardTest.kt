package town.beings.portal

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DeliveryGuardTest {
    @Test
    fun ledgerRoundTripsEntriesAndRepairsCorruptFile() {
        val directory = createTempDirectory("delivery-ledger").toFile()
        try {
            val ledgerFile = File(directory, "delivery_ledger.json")
            val ledger = DeliveryLedger(ledgerFile)
            ledger.markDiscovered("/recordings/a.m4a", "abc")
            ledger.markUploading("/recordings/a.m4a", "abc")
            ledger.markUploaded("/recordings/a.m4a", "abc")
            assertTrue(DeliveryLedger(ledgerFile).isUploaded("/recordings/a.m4a", "abc"))

            ledgerFile.writeText("this is not JSON")
            val repaired = DeliveryLedger(ledgerFile)
            assertFalse(repaired.isUploaded("/recordings/a.m4a", "abc"))
            assertEquals("{\"entries\":[]}", ledgerFile.readText())
            assertNotNull(directory.listFiles()?.singleOrNull { it.name.startsWith("delivery_ledger.json.corrupt-") })
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun uploadedFileIsNotPutAgainAndFailedFileIncrementsAttempts() {
        val directory = createTempDirectory("delivery-guard").toFile()
        try {
            val sourceDirectory = File(directory, "source").apply { mkdirs() }
            File(sourceDirectory, "call.m4a").writeText("recording")
            val ledger = DeliveryLedger(File(directory, "delivery_ledger.json"))
            val transport = FakeTransport()
            DeliveryGuard(sourceDirectory, ledger, transport).run()
            assertTrue(ledger.isUploaded(File(sourceDirectory, "call.m4a").absolutePath, "fb89b9c2b78261ef2ab506e30d9c84f7"))
            DeliveryGuard(sourceDirectory, ledger, transport).run()
            assertEquals(1, transport.putCount)

            val failedRecording = File(sourceDirectory, "failed.m4a").apply { writeText("retry") }
            transport.failure = IllegalStateException("relay unavailable")
            DeliveryGuard(sourceDirectory, ledger, transport).run()
            DeliveryGuard(sourceDirectory, ledger, transport).run()
            val md5 = "165e6d21e0a2cc9ebb32ca05f90e0fa7"
            assertEquals(DeliveryState.FAILED, ledger.entry(failedRecording.absolutePath, md5)?.state)
            assertEquals(2, ledger.entry(failedRecording.absolutePath, md5)?.attempts)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun retryDelayIsExponentialAndCappedAtTenMinutes() {
        assertEquals(30, DeliveryWorkScheduler.retryDelaySeconds(1))
        assertEquals(120, DeliveryWorkScheduler.retryDelaySeconds(3))
        assertEquals(600, DeliveryWorkScheduler.retryDelaySeconds(20))
    }

    private class FakeTransport : DeliveryTransport {
        var putCount = 0
        var failure: Throwable? = null

        override fun put(file: File, md5: String): Result<Unit> {
            putCount++
            return failure?.let { Result.failure(it) } ?: Result.success(Unit)
        }
    }
}
