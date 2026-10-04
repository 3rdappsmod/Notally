package com.omgodse.notally.room

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [37])
class ModifiedDateMigrationTest {
    @Test fun versionFiveNotesKeepTheirContentsAndCreationDate() {
        val app = RuntimeEnvironment.getApplication()
        val name = "migration-check.db"
        app.deleteDatabase(name)
        val path = app.getDatabasePath(name)
        path.parentFile!!.mkdirs()
        val schema = JSONObject(javaClass.classLoader!!.getResource("schema-v5.json")!!.readText()).getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.getJSONArray("indices")
                for (j in 0 until indices.length()) old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
            old.execSQL("INSERT INTO BaseNote VALUES (1,'NOTE','NOTES','DEFAULT','old title',0,1234,'[]','old body','[]','[]','[]','[]',NULL)")
            old.version = 5
        }
        val db = Room.databaseBuilder(app, NotallyDatabase::class.java, name)
            .addMigrations(NotallyDatabase.Companion.Migration6).allowMainThreadQueries().build()
        try {
            val note = requireNotNull(db.getBaseNoteDao().get(1))
            assertEquals("old body", note.body)
            assertEquals(1234L, note.timestamp)
            assertEquals(1234L, note.modifiedTimestamp)
        } finally { db.close() }
    }
}
