package com.omgodse.notally.viewmodels

import android.app.AlarmManager
import android.app.Application
import android.content.Context
import android.os.Bundle
import com.omgodse.notally.miscellaneous.Constants
import com.omgodse.notally.room.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class NotallyModelTest {
    private lateinit var app: Application
    private lateinit var database: NotallyDatabase
    private lateinit var model: NotallyModel

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        app = RuntimeEnvironment.getApplication()
        database = NotallyDatabase.getDatabase(app)
        database.clearAllTables()
        model = NotallyModel(app)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun newReminderUsesPersistedIdAndCanBeCancelled() = runTest {
        model.setState(0)
        val reminder = Reminder(System.currentTimeMillis() + 60000, Frequency.ONCE)
        model.setReminder(reminder).join()
        val manager = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        assertEquals(reminder, database.getBaseNoteDao().get(model.id)?.reminder)
        val alarm = shadowOf(manager).scheduledAlarms.single()
        val intent = shadowOf(alarm.operation).savedIntent
        val scheduledId = intent.getLongExtra(Constants.SelectedBaseNote, 0)
        assertTrue(scheduledId > 0)
        assertEquals(model.id, scheduledId)
        assertEquals(reminder, database.getBaseNoteDao().get(scheduledId)?.reminder)
        model.deleteReminder().join()
        assertNull(database.getBaseNoteDao().get(scheduledId)?.reminder)
        assertTrue(shadowOf(manager).scheduledAlarms.isEmpty())
    }

    @Test
    fun twoNewReminderOnlyNotesKeepDistinctAlarmsAndRows() = runTest {
        val other = NotallyModel(app)
        model.setState(0)
        other.setState(0)
        val reminder = Reminder(System.currentTimeMillis() + 60000, Frequency.ONCE)
        model.setReminder(reminder).join()
        other.setReminder(reminder).join()
        model.saveNote(discardEmptyDraft = true)
        other.saveNote(discardEmptyDraft = true)
        val manager = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val ids = shadowOf(manager).scheduledAlarms.map {
            shadowOf(it.operation).savedIntent.getLongExtra(Constants.SelectedBaseNote, 0)
        }.toSet()
        assertEquals(setOf(model.id, other.id), ids)
        assertEquals(2, database.getBaseNoteDao().getAllNotes().size)
    }

    @Test
    fun clearingExistingTextPersistsInsteadOfRestoringOldText() = runTest {
        model.setState(0)
        model.title = "old title"
        model.body.append("old body")
        val id = model.saveNote()
        val reopened = NotallyModel(app)
        reopened.setState(id)
        reopened.title = ""
        reopened.body.clear()
        reopened.saveNote(discardEmptyDraft = true)
        val saved = requireNotNull(database.getBaseNoteDao().get(id))
        assertEquals("", saved.title)
        assertEquals("", saved.body)
    }

    @Test
    fun clearingExistingChecklistPersists() = runTest {
        model.type = Type.LIST
        model.setState(0)
        model.items.add(ListItem("task", false))
        val id = model.saveNote()
        val reopened = NotallyModel(app)
        reopened.setState(id)
        reopened.items.clear()
        reopened.saveNote(discardEmptyDraft = true)
        assertTrue(requireNotNull(database.getBaseNoteDao().get(id)).items.isEmpty())
    }

    @Test
    fun idCapturedBeforeBackgroundSaveRestoresTheSameNoteInANewModel() = runTest {
        model.setState(0)
        model.body.append("draft survives")
        // Match the Activity ordering: the Bundle captures ID BEFORE async save.
        val state = Bundle().apply { putLong("id", model.id) }
        assertTrue(state.getLong("id") > 0)
        model.saveNote()
        val restored = NotallyModel(app)
        restored.setState(state.getLong("id"))
        assertEquals("draft survives", restored.body.toString())
        restored.saveNote()
        assertEquals(1, database.getBaseNoteDao().getAllNotes().size)
    }

    @Test
    fun emptyDraftSurvivesBackgroundSaveButIsDiscardedOnExit() = runTest {
        model.setState(0)
        val id = model.id
        model.saveNote()
        assertNotNull(database.getBaseNoteDao().get(id))
        model.saveNote(discardEmptyDraft = true)
        assertNull(database.getBaseNoteDao().get(id))
        model.saveNote() // A late lifecycle callback must not resurrect the row.
        assertNull(database.getBaseNoteDao().get(id))
    }

    @Test
    fun attachmentOnlyDraftIsKept() = runTest {
        model.setState(0)
        model.images.value = listOf(Image("image.png", "image/png"))
        model.saveNote(discardEmptyDraft = true)
        assertEquals(1, database.getBaseNoteDao().get(model.id)?.images?.size)
        model.images.value = emptyList()
        model.audios.value = listOf(Audio("audio.m4a", 1000, 1))
        model.saveNote(discardEmptyDraft = true)
        assertEquals(1, database.getBaseNoteDao().get(model.id)?.audios?.size)
    }

    @Test
    fun metadataOnlyDraftIsKept() = runTest {
        model.setState(0)
        model.labels.value = arrayListOf("important")
        model.saveNote(discardEmptyDraft = true)
        assertNotNull(database.getBaseNoteDao().get(model.id))
    }

    @Test
    fun overlappingSavesDoNotCreateDuplicateRows() = runTest {
        model.setState(0)
        model.body.append("one note")
        val ids = List(10) { async { model.saveNote() } }.awaitAll()
        assertEquals(setOf(model.id), ids.toSet())
        assertEquals(1, database.getBaseNoteDao().getAllNotes().size)
    }
}
