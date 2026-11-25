package com.example.speechcoach.coaching

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun interface TextInference {
    suspend fun generate(prompt: String): String
}

internal class LiteRtInference(private val model: File, private val cache: File) : TextInference {
    override suspend fun generate(prompt: String): String = withContext(Dispatchers.IO) {
        require(model.isFile) { "Download the optional coaching model first." }
        val engine = Engine(EngineConfig(modelPath = model.absolutePath, backend = Backend.CPU(), cacheDir = cache.absolutePath))
        engine.initialize()
        try {
            engine.createConversation().use { conversation ->
                conversation.sendMessage(prompt).contents.contents.filterIsInstance<Content.Text>()
                    .joinToString("") { it.text }.trim().also {
                        check(it.isNotBlank()) { "The coaching model returned an empty response." }
                    }
            }
        } finally {
            engine.close()
        }
    }
}
