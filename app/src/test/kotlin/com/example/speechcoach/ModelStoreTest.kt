package com.example.speechcoach

import com.sun.net.httpserver.HttpServer
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.security.MessageDigest
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
