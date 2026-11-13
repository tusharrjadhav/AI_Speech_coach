package com.example.speechcoach

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

enum class LocalModel(
    val label: String,
    val fileName: String,
    val size: Long,
    val sha256: String,
    val url: String,
) {
    SPEECH(
        "Speech recognition",
        "parakeet_tdt_0.6b_v3_5s_i8_stateful.litertlm",
        614_588_752,
        "66c5a74cf6c2c1d2b990901f6cece5bb53e3214dbceafa0989900a6f0eeb3504",
        "https://huggingface.co/litert-community/parakeet-tdt-0.6b-v3/resolve/cd95f7bb97fd0757ab1bd6d5eff513cfcd7a2a10/parakeet_tdt_0.6b_v3_5s_i8_stateful.litertlm",
    ),
    COACH(
        "Gemma 4 coaching",
        "gemma-4-E2B-it.litertlm",
        2_588_147_712,
        "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c",
        "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/2d74ac41a33d7da82f39f7d5720a04c68514a103/gemma-4-E2B-it.litertlm",
    ),
}

class ModelStore(private val directory: File) {
    constructor(context: Context) : this(File(context.noBackupFilesDir, "models"))

    init {
        check(directory.isDirectory || directory.mkdirs()) { "Could not create the model directory." }
    }

    fun file(model: LocalModel): File = File(directory, model.fileName)

    fun isReady(model: LocalModel): Boolean = file(model).isFile && file(model).length() == model.size

    suspend fun download(model: LocalModel, progress: (Long, Long) -> Unit) =
        downloadFile(model.fileName, model.size, model.sha256, model.url, progress)

    internal suspend fun downloadFile(
        fileName: String,
        size: Long,
        sha256: String,
        url: String,
        progress: (Long, Long) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val target = File(directory, fileName)
        if (target.isFile && target.length() == size) return@withContext
        val partial = File(directory, "$fileName.part")
        if (partial.length() > size && !partial.delete()) {
            throw IOException("Could not remove the invalid partial model.")
        }
        var offset = partial.length()
        if (offset < size) {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 30_000
                connection.readTimeout = 60_000
                connection.setRequestProperty("Accept-Encoding", "identity")
                if (offset > 0) connection.setRequestProperty("Range", "bytes=$offset-")
                val response = connection.responseCode
                if (response == HttpURLConnection.HTTP_OK) {
                    offset = 0
                } else if (response == HttpURLConnection.HTTP_PARTIAL) {
                    val range = connection.getHeaderField("Content-Range")
                    if (range == null || !range.startsWith("bytes $offset-")) {
                        throw IOException("The model server returned an invalid resume range.")
                    }
                } else {
                    throw IOException("Model download failed (HTTP $response).")
                }
                FileOutputStream(partial, offset > 0).use { output ->
                    connection.inputStream.use { input ->
                        val buffer = ByteArray(64 * 1024)
                        var total = offset
                        var lastReported = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            if (total > size) throw IOException("The model download exceeded its expected size.")
                            output.write(buffer, 0, count)
                            if (total - lastReported >= 512 * 1024 || total == size) {
                                progress(total, size)
                                lastReported = total
                            }
                        }
                    }
                }
            } finally {
                connection.disconnect()
            }
        }
        if (partial.length() != size) throw IOException("Model download was interrupted; retry to resume.")
        progress(size, size)
        val digest = MessageDigest.getInstance("SHA-256")
        partial.inputStream().use { input ->
            val buffer = ByteArray(128 * 1024)
            while (true) {
                coroutineContext.ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (actual != sha256) {
            if (!partial.delete()) throw IOException("Model checksum failed and the partial file could not be removed.")
            throw IOException("Model checksum failed; retry the download.")
        }
        if (target.exists() && !target.delete()) throw IOException("Could not replace the invalid model file.")
        if (!partial.renameTo(target)) throw IOException("Could not save the verified model.")
    }
}
