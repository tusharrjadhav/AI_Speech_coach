package com.example.speechcoach

import android.media.MediaDataSource
import android.media.MediaPlayer
import java.io.IOException

/** Main-thread transport shared by live and archived recordings. */
class RecordingPlayback(
    private val onPlaying: (Boolean) -> Unit,
    private val onError: (Exception) -> Unit,
) {
    private var player: MediaPlayer? = null

    fun play(path: String, startMillis: Long = 0) = start(startMillis) { setDataSource(path) }
    fun play(wav: ByteArray, startMillis: Long = 0) = start(startMillis) {
        // SQLite blob descriptors aren't portable media sources. Serve the bounded WAV without a disk copy.
        setDataSource(object : MediaDataSource() {
            override fun getSize() = wav.size.toLong()
            override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
                if (size == 0) return 0
                if (position !in 0 until getSize()) return -1
                val start = position.toInt()
                val length = minOf(size, wav.size - start)
                wav.copyInto(buffer, offset, start, start + length)
                return length
            }
            override fun close() = Unit
        })
    }

    private fun start(offset: Long, source: MediaPlayer.() -> Unit) {
        stop()
        try {
            val next = MediaPlayer()
            player = next
            next.source()
            next.setOnPreparedListener {
                if (player === it) {
                    if (offset > 0) it.seekTo(offset, MediaPlayer.SEEK_CLOSEST)
                    it.start()
                    onPlaying(true)
                }
            }
            next.setOnCompletionListener { if (player === it) stop() }
            next.setOnErrorListener { failed, what, extra ->
                if (player === failed) {
                    stop()
                    onError(IOException("MediaPlayer error $what ($extra)."))
                }
                true
            }
            next.prepareAsync()
        } catch (e: Exception) {
            stop()
            onError(e)
        }
    }

    fun stop() {
        player?.release()
        player = null
        onPlaying(false)
    }
}
