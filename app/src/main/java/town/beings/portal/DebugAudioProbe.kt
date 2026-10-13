package town.beings.portal

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.MediaStore

/**
 * Debug probe: MediaStore query + in-process file read test.
 * Actions: DEBUG_LIST_AUDIO (query+list), DEBUG_COPY (copy newest call_rec mp3 to files/rec_copy.mp3)
 */
class DebugAudioProbe : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "town.beings.portal.DEBUG_COPY" -> copyNewest(context)
            else -> listAudio(context)
        }
    }

    private fun listAudio(context: Context) {
        val sb = StringBuilder()
        try {
            val cr = context.contentResolver
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.DATE_ADDED
            )
            cr.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, null, null, "date_added DESC")?.use { c ->
                var i = 0
                while (c.moveToNext() && i < 50) {
                    sb.append("row ").append(i).append(": id=").append(c.getLong(0))
                        .append(" name=").append(c.getString(1))
                        .append(" path=").append(c.getString(2))
                        .append(" size=").append(c.getLong(3))
                        .append(" added=").append(c.getLong(4)).append('\n')
                    i++
                }
                if (i == 0) sb.append("NO ROWS in MediaStore external audio\n")
            } ?: sb.append("query returned null cursor (no permission?)\n")

            listOf("/sdcard/MIUI/sound_recorder/call_rec/", "/sdcard/MIUI/sound_recorder/app_rec/").forEach { dir ->
                val d = java.io.File(dir)
                val files = d.listFiles()
                sb.append("dir ").append(dir).append(" exists=").append(d.exists())
                    .append(" canRead=").append(d.canRead())
                    .append(" entries=").append(files?.size ?: -1).append('\n')
                files?.take(10)?.forEach { f -> sb.append("  file ").append(f.name).append(" size=").append(f.length()).append('\n') }
            }
        } catch (e: Exception) {
            sb.append("ERROR: ").append(e.javaClass.simpleName).append(": ").append(e.message).append('\n')
        }
        writeOut(context, sb.toString())
    }

    private fun copyNewest(context: Context) {
        val result: ByteArray = try {
            val dir = java.io.File("/sdcard/MIUI/sound_recorder/call_rec/")
            val newest = dir.listFiles { f -> f.extension == "mp3" }?.maxByOrNull { it.lastModified() }
                ?: throw java.io.IOException("no mp3 in call_rec")
            newest.readBytes().also { android.util.Log.i("DebugAudioProbe", "copied ${newest.name} bytes=${it.size}") }
        } catch (e: Exception) {
            android.util.Log.e("DebugAudioProbe", "copy failed", e)
            "ERROR: ${e.message}".toByteArray()
        }
        writeOut(context, String(result.take(64).toByteArray()) + "\n===BINARY===\n")
        context.openFileOutput("rec_copy.mp3", Context.MODE_PRIVATE).use { it.write(result) }
    }

    private fun writeOut(context: Context, text: String) {
        android.util.Log.i("DebugAudioProbe", text)
        try {
            context.openFileOutput("audio_probe.txt", Context.MODE_PRIVATE).use { it.write(text.toByteArray()) }
        } catch (e: Exception) {
            android.util.Log.e("DebugAudioProbe", "write file failed", e)
        }
    }
}