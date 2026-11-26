package io.github.lrosa2.carcare.backup

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/** Reads and writes backup files through the system document picker (spec "Import, export, backup"). */
object BackupIo {
    /** Real files are a few dozen KB; anything this big is not a CarCare backup. */
    private const val MAX_BYTES = 5 * 1024 * 1024

    suspend fun read(context: Context, uri: Uri): BackupFile = withContext(Dispatchers.IO) {
        val bytes = try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val buf = input.readNBytesCompat(MAX_BYTES + 1)
                if (buf.size > MAX_BYTES) throw BackupException("File is too large to be a CarCare backup.")
                buf
            } ?: throw BackupException("Could not open the file.")
        } catch (e: IOException) {
            throw BackupException("Could not read the file: ${e.message}", e)
        }
        BackupCodec.decode(bytes.decodeToString())
    }

    suspend fun write(context: Context, uri: Uri, file: BackupFile) = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(BackupCodec.encode(file).toByteArray()) }
                ?: throw BackupException("Could not open the file for writing.")
        } catch (e: IOException) {
            throw BackupException("Could not write the file: ${e.message}", e)
        }
    }

    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (out.size() < limit) {
            val n = read(buf, 0, minOf(buf.size, limit - out.size()))
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }
}
