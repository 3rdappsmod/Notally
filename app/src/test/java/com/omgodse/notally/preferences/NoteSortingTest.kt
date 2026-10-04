package com.omgodse.notally.preferences

import com.omgodse.notally.room.*
import org.junit.Assert.*
import org.junit.Test

class NoteSortingTest {
    private fun note(id: Long, title: String, created: Long, modified: Long, pinned: Boolean = false) = BaseNote(
        id, Type.NOTE, Folder.NOTES, Color.DEFAULT, title, pinned, created, emptyList(), "", emptyList(),
        emptyList(), emptyList(), emptyList(), null, modified,
    )
    @Test fun eachCriterionKeepsPinnedNotesFirstAndUsesStableTies() {
        val notes = listOf(note(1,"Z",100,300), note(2,"A",200,200), note(3,"Pinned",0,0,true))
        assertEquals(listOf(3L,2L,1L), NotesSort.sort(notes, NotesSort.titleSort, SortDirection.ascending).map { it.id })
        assertEquals(listOf(3L,2L,1L), NotesSort.sort(notes, NotesSort.created, SortDirection.descending).map { it.id })
        assertEquals(listOf(3L,1L,2L), NotesSort.sort(notes, NotesSort.modified, SortDirection.descending).map { it.id })
        assertEquals(listOf(3L,2L,1L), NotesSort.sort(notes, NotesSort.modified, SortDirection.ascending).map { it.id })
    }
}
