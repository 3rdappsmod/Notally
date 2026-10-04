package com.omgodse.notally.preferences

import android.content.Context
import com.omgodse.notally.R
import com.omgodse.notally.room.BaseNote
import java.text.Collator

/** Adapted from NotallyX 5b9f9680, keeping pinned groups and deterministic ties. */
object NotesSort : ListInfo {
    const val created = "created"
    const val modified = "modified"
    const val titleSort = "title"
    override val title = R.string.notes_sorted_by
    override val key = "notesSort"
    override val defaultValue = created
    override fun getEntryValues() = arrayOf(created, modified, titleSort)
    override fun getEntries(context: Context) = arrayOf(
        context.getString(R.string.creation_date), context.getString(R.string.modified_date), context.getString(R.string.title),
    )

    fun sort(notes: List<BaseNote>, criterion: String, direction: String): List<BaseNote> {
        val collator = Collator.getInstance()
        return notes.sortedWith { a, b ->
            if (a.pinned != b.pinned) return@sortedWith if (a.pinned) -1 else 1
            val order = when (criterion) {
                titleSort -> collator.compare(a.title, b.title)
                modified -> a.modifiedTimestamp.compareTo(b.modifiedTimestamp)
                else -> a.timestamp.compareTo(b.timestamp)
            }
            if (order == 0) a.id.compareTo(b.id) else if (direction == SortDirection.ascending) order else -order
        }
    }
}

object SortDirection : ListInfo {
    const val ascending = "ascending"
    const val descending = "descending"
    override val title = R.string.sort_direction
    override val key = "sortDirection"
    override val defaultValue = descending
    override fun getEntryValues() = arrayOf(ascending, descending)
    override fun getEntries(context: Context) = arrayOf(context.getString(R.string.ascending), context.getString(R.string.descending))
}
