package app.fontflip.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.annotation.StringRes
import app.fontflip.R
import java.io.File

class FontImportException(@StringRes val res: Int) : Exception()

/** Biblioteca de fontes no armazenamento privado do app (sem permissão de armazenamento). */
class FontStore(private val context: Context) {
    private val dir = File(context.filesDir, "fonts").apply { mkdirs() }

    fun list(): List<File> =
        dir.listFiles { f -> f.extension.equals("ttf", ignoreCase = true) }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()

    fun delete(file: File) {
        file.delete()
    }

    fun importFont(uri: Uri): File {
        val base = (displayName(uri) ?: "font")
            .substringBeforeLast('.')
            .replace(Regex("[^\\p{L}\\p{N} _.-]"), "_")
            .take(60)
            .ifBlank { "font" }

        val tmp = File(dir, ".import.tmp")
        try {
            val input = context.contentResolver.openInputStream(uri) ?: throw FontImportException(R.string.err_import)
            input.use { src ->
                tmp.outputStream().use { dst ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val n = src.read(buffer)
                        if (n < 0) break
                        total += n
                        if (total > MAX_BYTES) throw FontImportException(R.string.err_too_big)
                        dst.write(buffer, 0, n)
                    }
                }
            }
            if (!isTtf(tmp)) throw FontImportException(R.string.err_invalid_font)

            var target = File(dir, "$base.ttf")
            var n = 2
            while (target.exists()) {
                target = File(dir, "$base ($n).ttf")
                n++
            }
            if (!tmp.renameTo(target)) throw FontImportException(R.string.err_import)
            return target
        } finally {
            tmp.delete()
        }
    }

    private fun displayName(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }

    /** TTF começa com 00 01 00 00 ou "true". */
    private fun isTtf(file: File): Boolean {
        val head = ByteArray(4)
        file.inputStream().use { if (it.read(head) != 4) return false }
        return head.contentEquals(byteArrayOf(0, 1, 0, 0)) || String(head, Charsets.ISO_8859_1) == "true"
    }

    private companion object {
        const val MAX_BYTES = 50L * 1024 * 1024
    }
}
