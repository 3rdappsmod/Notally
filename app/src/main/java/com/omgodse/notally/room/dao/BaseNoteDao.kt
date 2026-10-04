package com.omgodse.notally.room.dao

import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.omgodse.notally.room.Audio
import com.omgodse.notally.room.BaseNote
import com.omgodse.notally.room.Color
import com.omgodse.notally.room.Folder
import com.omgodse.notally.room.IdReminder
import com.omgodse.notally.room.Image
import com.omgodse.notally.room.ListItem
import com.omgodse.notally.room.Type
import com.omgodse.notally.room.Reminder

@Dao
interface BaseNoteDao {

    @RawQuery
    fun query(query: SupportSQLiteQuery): Int


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(baseNote: BaseNote): Long

    @Insert
    suspend fun insert(baseNotes: List<BaseNote>)


    @Query(
        "INSERT INTO BaseNote (type, folder, color, title, pinned, timestamp, labels, body, spans, items, images, audios, reminder, modifiedTimestamp)\n" +
                "SELECT type, folder, color, title, pinned, timestamp, labels, body, spans, items, images, audios, NULL, modifiedTimestamp\n" +
                "FROM BaseNote WHERE id IN (:ids)"
    )
    suspend fun copy(ids: LongArray)


    @Query("DELETE FROM BaseNote WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM BaseNote WHERE id IN (:ids)")
    suspend fun delete(ids: LongArray)

    @Query("DELETE FROM BaseNote WHERE folder = :folder")
    suspend fun deleteFrom(folder: Folder)


    @Query("SELECT * FROM BaseNote WHERE folder = :folder ORDER BY pinned DESC, timestamp DESC")
    fun getFrom(folder: Folder): LiveData<List<BaseNote>>

    @Query("SELECT * FROM BaseNote WHERE folder = 'NOTES' ORDER BY pinned DESC, timestamp DESC")
    suspend fun getAllNotes(): List<BaseNote>

    @Query("SELECT * FROM BaseNote")
    fun getAll(): LiveData<List<BaseNote>>

    @Query("SELECT * FROM BaseNote WHERE id = :id")
    fun get(id: Long): BaseNote?

    @Query("SELECT images FROM BaseNote WHERE id = :id")
    fun getImages(id: Long): String


    @Query("SELECT images FROM BaseNote")
    fun getAllImages(): List<String>

    @Query("SELECT audios FROM BaseNote")
    fun getAllAudios(): List<String>


    @Query("SELECT id, reminder FROM BaseNote WHERE reminder IS NOT NULL")
    fun getAllReminders(): List<IdReminder>


    @Query("SELECT id FROM BaseNote WHERE folder = 'DELETED'")
    suspend fun getDeletedNoteIds(): LongArray

    @Query("SELECT id FROM BaseNote WHERE folder = 'DELETED' AND reminder IS NOT NULL")
    suspend fun getDeletedNoteReminderIds(): List<Long>

    @Query("SELECT images FROM BaseNote WHERE folder = 'DELETED'")
    suspend fun getDeletedNoteImages(): List<String>

    @Query("SELECT audios FROM BaseNote WHERE folder = 'DELETED'")
    suspend fun getDeletedNoteAudios(): List<String>


    @Query("UPDATE BaseNote SET folder = :folder, modifiedTimestamp = :modifiedTimestamp WHERE id IN (:ids)")
    suspend fun move(ids: LongArray, folder: Folder, modifiedTimestamp: Long = System.currentTimeMillis())


    @Query("UPDATE BaseNote SET color = :color, modifiedTimestamp = :modifiedTimestamp WHERE id IN (:ids)")
    suspend fun updateColor(ids: LongArray, color: Color, modifiedTimestamp: Long = System.currentTimeMillis())

    @Query("UPDATE BaseNote SET pinned = :pinned, modifiedTimestamp = :modifiedTimestamp WHERE id IN (:ids)")
    suspend fun updatePinned(ids: LongArray, pinned: Boolean, modifiedTimestamp: Long = System.currentTimeMillis())

    @Query("UPDATE BaseNote SET labels = :labels, modifiedTimestamp = :modifiedTimestamp WHERE id = :id")
    suspend fun updateLabels(id: Long, labels: List<String>, modifiedTimestamp: Long = System.currentTimeMillis())

    @Query("UPDATE BaseNote SET items = :items, modifiedTimestamp = :modifiedTimestamp WHERE id = :id")
    suspend fun updateItems(id: Long, items: List<ListItem>, modifiedTimestamp: Long = System.currentTimeMillis())

    @Query("UPDATE BaseNote SET images = :images, modifiedTimestamp = :modifiedTimestamp WHERE id = :id")
    suspend fun updateImages(id: Long, images: List<Image>, modifiedTimestamp: Long = System.currentTimeMillis())

    @Query("UPDATE BaseNote SET audios = :audios, modifiedTimestamp = :modifiedTimestamp WHERE id = :id")
    suspend fun updateAudios(id: Long, audios: List<Audio>, modifiedTimestamp: Long = System.currentTimeMillis())

    @Query("UPDATE BaseNote SET reminder = :reminder, modifiedTimestamp = :modifiedTimestamp WHERE id = :id")
    suspend fun updateReminder(id: Long, reminder: Reminder?, modifiedTimestamp: Long = System.currentTimeMillis())


    /** Ignore stale widget clicks, and serialize the read/modify/write of the item list. */
    @Transaction
    suspend fun updateChecked(id: Long, position: Int, checked: Boolean, expectedBody: String? = null): Boolean {
        val note = get(id) ?: return false
        if (note.type != Type.LIST) return false
        val item = note.items.getOrNull(position) ?: return false
        if (expectedBody != null && item.body != expectedBody) return false
        item.checked = checked
        updateItems(id, note.items)
        return true
    }


    /**
     * Since we store the labels as a JSON Array, it is not possible
     * to perform operations on it. Thus, we use the 'Like' query
     * which can return false positives sometimes.
     *
     * Take for example, a request for all base notes having the label
     * 'Important' The base notes which instead have the label 'Unimportant'
     * will also be returned. To prevent this, we use [LiveData.map] and
     * filter the result accordingly.
     */
    fun getBaseNotesByLabel(label: String): LiveData<List<BaseNote>> {
        val result = getBaseNotesByLabel(label, Folder.NOTES)
        return result.map { list -> list.filter { baseNote -> baseNote.labels.contains(label) } }
    }

    @Query("SELECT * FROM BaseNote WHERE folder = :folder AND labels LIKE '%' || :label || '%' ORDER BY pinned DESC, timestamp DESC")
    fun getBaseNotesByLabel(label: String, folder: Folder): LiveData<List<BaseNote>>


    suspend fun getListOfBaseNotesByLabel(label: String): List<BaseNote> {
        val result = getListOfBaseNotesByLabelImpl(label)
        return result.filter { baseNote -> baseNote.labels.contains(label) }
    }

    @Query("SELECT * FROM BaseNote WHERE labels LIKE '%' || :label || '%'")
    suspend fun getListOfBaseNotesByLabelImpl(label: String): List<BaseNote>


    fun getBaseNotesByKeyword(keyword: String, folder: Folder): LiveData<List<BaseNote>> {
        val result = getBaseNotesByKeywordImpl(keyword, folder)
        return result.map { list -> list.filter { baseNote -> matchesKeyword(baseNote, keyword) } }
    }

    @Query("SELECT * FROM BaseNote WHERE folder = :folder AND (title LIKE '%' || :keyword || '%' OR body LIKE '%' || :keyword || '%' OR items LIKE '%' || :keyword || '%' OR labels LIKE '%' || :keyword || '%') ORDER BY pinned DESC, timestamp DESC")
    fun getBaseNotesByKeywordImpl(keyword: String, folder: Folder): LiveData<List<BaseNote>>


    private fun matchesKeyword(baseNote: BaseNote, keyword: String): Boolean {
        if (baseNote.title.contains(keyword, true)) {
            return true
        }
        if (baseNote.body.contains(keyword, true)) {
            return true
        }
        for (label in baseNote.labels) {
            if (label.contains(keyword, true)) {
                return true
            }
        }
        for (item in baseNote.items) {
            if (item.body.contains(keyword, true)) {
                return true
            }
        }
        return false
    }
}