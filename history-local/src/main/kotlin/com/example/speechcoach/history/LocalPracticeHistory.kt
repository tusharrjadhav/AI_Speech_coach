package com.example.speechcoach.history

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDoneException
import android.database.sqlite.SQLiteOpenHelper
import android.os.ParcelFileDescriptor
import com.example.speechcoach.core.Attempt
import com.example.speechcoach.core.AttemptLabel
import com.example.speechcoach.core.AudioClip
import com.example.speechcoach.core.PracticeHistory
import com.example.speechcoach.core.Scenario
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class LocalPracticeHistory(context: Context, name: String = "practice-history.db") :
    SQLiteOpenHelper(context.applicationContext, name, null, 1), PracticeHistory {
    private val json = Json { ignoreUnknownKeys = true }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE attempts(id TEXT PRIMARY KEY, saved INTEGER NOT NULL, scenario TEXT NOT NULL, snapshot TEXT NOT NULL, wav BLOB NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        error("Unsupported history migration: $oldVersion to $newVersion")
    }

    override suspend fun list(): List<AttemptLabel> = withContext(Dispatchers.IO) {
        readableDatabase.rawQuery("SELECT id,saved,scenario FROM attempts ORDER BY saved DESC,id DESC", null).use {
            buildList { while (it.moveToNext()) add(AttemptLabel(it.getString(0), it.getLong(1), Scenario.valueOf(it.getString(2)))) }
        }
    }

    override suspend fun get(id: String): Attempt? = withContext(Dispatchers.IO) {
        readableDatabase.compileStatement("SELECT snapshot FROM attempts WHERE id=?").use {
            it.bindString(1, id)
            val snapshot = try { it.simpleQueryForString() } catch (_: SQLiteDoneException) { return@withContext null }
            json.decodeFromString<Attempt>(snapshot)
        }
    }

    override suspend fun save(attempt: Attempt, clip: AudioClip): Unit = withContext(Dispatchers.IO) {
        val source = File(clip.path)
        require(source.length() in 45..3_840_044L) { "Expected a nonempty practice WAV up to two minutes." }
        // One row makes metadata and audio atomic; the active recording remains independently replaceable.
        val values = ContentValues().apply {
            put("id", attempt.id)
            put("saved", attempt.savedAt)
            put("scenario", attempt.scenario.name)
            put("snapshot", json.encodeToString(Attempt.serializer(), attempt))
            put("wav", source.readBytes())
        }
        check(writableDatabase.insertWithOnConflict("attempts", null, values, SQLiteDatabase.CONFLICT_REPLACE) != -1L) {
            "The attempt could not be saved."
        }
    }

    // A two-minute WAV can exceed CursorWindow. Open it as a descriptor, not a cursor or a temp file.
    override suspend fun readAudio(id: String): ByteArray = withContext(Dispatchers.IO) {
        readableDatabase.compileStatement("SELECT wav FROM attempts WHERE id=?").use {
            it.bindString(1, id)
            val fd = checkNotNull(it.simpleQueryForBlobFileDescriptor()) { "No saved recording for this attempt." }
            ParcelFileDescriptor.AutoCloseInputStream(fd).use { stream -> stream.readBytes() }
        }
    }

    override suspend fun delete(id: String): Unit = withContext(Dispatchers.IO) {
        check(writableDatabase.delete("attempts", "id=?", arrayOf(id)) == 1) { "The saved attempt was not found." }
    }
}
