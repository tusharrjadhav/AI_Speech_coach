package com.example.speechcoach

import com.sun.net.httpserver.HttpServer
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ModelStoreTest {
    @get:Rule val folder = TemporaryFolder()
    private val payload = "hello world".toByteArray()
    private val hash = MessageDigest.getInstance("SHA-256").digest(payload).joinToString("") { "%02x".format(it) }

    @Test
    fun resumesPartialDownloadAndVerifiesBeforeSaving() {
        val directory = folder.newFolder()
        File(directory, "model.part").writeBytes(payload.copyOfRange(0, 5))
        var range: String? = null
        server { exchange ->
            range = exchange.requestHeaders.getFirst("Range")
            exchange.responseHeaders.add("Content-Range", "bytes 5-10/11")
            exchange.sendResponseHeaders(206, 6)
            exchange.responseBody.use { it.write(payload, 5, 6) }
        }.use { server ->
            runBlocking { ModelStore(directory).downloadFile("model", 11, hash, server.url) { _, _ -> } }
        }
        assertEquals("bytes=5-", range)
        assertArrayEquals(payload, File(directory, "model").readBytes())
        assertFalse(File(directory, "model.part").exists())
    }

    @Test
    fun restartsIfServerIgnoresRange() {
        val directory = folder.newFolder()
        File(directory, "model.part").writeBytes(payload.copyOfRange(0, 5))
        server { exchange ->
            exchange.sendResponseHeaders(200, payload.size.toLong())
            exchange.responseBody.use { it.write(payload) }
        }.use { server ->
            runBlocking { ModelStore(directory).downloadFile("model", 11, hash, server.url) { _, _ -> } }
        }
        assertArrayEquals(payload, File(directory, "model").readBytes())
    }

    @Test
    fun rejectsAndRemovesAnIncorrectChecksum() {
        val directory = folder.newFolder()
        server { exchange ->
            exchange.sendResponseHeaders(200, payload.size.toLong())
            exchange.responseBody.use { it.write(payload) }
        }.use { server ->
            assertThrows(IOException::class.java) {
                runBlocking { ModelStore(directory).downloadFile("model", 11, "wrong", server.url) { _, _ -> } }
            }
        }
        assertFalse(File(directory, "model").exists())
        assertFalse(File(directory, "model.part").exists())
    }

    @Test
    fun importsVerifiedModelWithoutDisturbingPartialDownload() {
        val directory = folder.newFolder()
        val target = File(directory, "model").apply { writeText("old model") }
        val partial = File(directory, "model.part").apply { writeText("partial") }
        val progress = mutableListOf<Pair<Long, Long>>()

        runBlocking {
            ModelStore(directory).importFile("model", 11, hash, { payload.inputStream() }) { done, total ->
                progress.add(done to total)
            }
        }

        assertArrayEquals(payload, target.readBytes())
        assertEquals("partial", partial.readText())
        assertEquals(11L to 11L, progress.last())
        assertEquals(setOf("model", "model.part"), directory.listFiles()!!.map { it.name }.toSet())
    }

    @Test
    fun rejectsIncorrectImportedContentAndPreservesInstalledModel() {
        val directory = folder.newFolder()
        val target = File(directory, "model").apply { writeText("old model") }
        val store = ModelStore(directory)

        for (invalid in listOf("short".toByteArray(), "hello worle".toByteArray(), "too long for a model".toByteArray())) {
            assertThrows(IOException::class.java) {
                runBlocking { store.importFile("model", 11, hash, { invalid.inputStream() }) { _, _ -> } }
            }
            assertEquals("old model", target.readText())
            assertEquals(setOf("model"), directory.listFiles()!!.map { it.name }.toSet())
        }
    }

    @Test
    fun cleansUpFailedLocalReads() {
        val directory = folder.newFolder()
        val target = File(directory, "model").apply { writeText("old model") }
        val broken = object : InputStream() {
            override fun read(): Int = throw IOException("Read failed")
        }
        assertThrows(IOException::class.java) {
            runBlocking { ModelStore(directory).importFile("model", 11, hash, { broken }) { _, _ -> } }
        }
        assertEquals("old model", target.readText())
        assertEquals(setOf("model"), directory.listFiles()!!.map { it.name }.toSet())
    }

    @Test
    fun cancelledImportKeepsTheInstalledModel() = runBlocking {
        val directory = folder.newFolder()
        val target = File(directory, "model").apply { writeText("old model") }
        lateinit var job: Job
        job = launch(start = CoroutineStart.LAZY) {
            ModelStore(directory).importFile("model", 11, hash, { payload.inputStream() }) { _, _ ->
                job.cancel()
            }
        }
        job.start()
        job.join()
        assertEquals("old model", target.readText())
        assertEquals(setOf("model"), directory.listFiles()!!.map { it.name }.toSet())
    }

    @Test
    fun removesInterruptedImportsWithoutTouchingDownloadsOrInstalledModels() = runBlocking {
        val directory = folder.newFolder()
        val name = LocalModel.SPEECH.fileName
        val installed = File(directory, name).apply { writeText("installed") }
        val download = File(directory, "$name.part").apply { writeText("partial") }
        val interrupted = File(directory, "$name.import").apply { writeText("interrupted") }
        val store = ModelStore(directory)

        assertEquals(true, store.hasInterruptedImports())
        store.removeInterruptedImports()

        assertFalse(interrupted.exists())
        assertEquals("installed", installed.readText())
        assertEquals("partial", download.readText())
        assertFalse(store.hasInterruptedImports())
    }

    private fun server(handler: (com.sun.net.httpserver.HttpExchange) -> Unit): TestServer {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/", handler)
        server.start()
        return TestServer(server)
    }

    private class TestServer(private val server: HttpServer) : AutoCloseable {
        val url = "http://127.0.0.1:${server.address.port}/model"
        override fun close() = server.stop(0)
    }
}
