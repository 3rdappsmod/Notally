package com.omgodse.notally.widget

import android.app.Application
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.TextView
import com.omgodse.notally.R
import com.omgodse.notally.room.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28, 37])
class WidgetTest {
    private lateinit var app: Application
    private lateinit var database: NotallyDatabase

    @Before fun setUp() {
        app = RuntimeEnvironment.getApplication()
        database = NotallyDatabase.getDatabase(app)
        database.clearAllTables()
    }

    private fun note(items: List<ListItem> = listOf(ListItem("first", false), ListItem("second", true))) = BaseNote(
        0, Type.LIST, Folder.NOTES, Color.DEFAULT, "Checklist", false, 0,
        emptyList(), "", emptyList(), items, emptyList(), emptyList(), null,
    )

    @Test fun staleClicksDoNotCrashOrChangeAnotherItem() = runTest {
        val dao = database.getBaseNoteDao()
        val id = dao.insert(note())
        assertFalse(dao.updateChecked(id, -1, true))
        assertFalse(dao.updateChecked(id, 2, true))
        assertFalse(dao.updateChecked(id, 0, true, "removed item"))
        assertFalse(requireNotNull(dao.get(id)).items[0].checked)
        assertTrue(dao.updateChecked(id, 0, true, "first"))
        assertTrue(requireNotNull(dao.get(id)).items[0].checked)
        dao.delete(id)
        assertFalse(dao.updateChecked(id, 0, false))
    }

    @Test fun concurrentCheckboxUpdatesBothSurvive() = runTest {
        val dao = database.getBaseNoteDao()
        val id = dao.insert(note(listOf(ListItem("first", false), ListItem("second", false))))
        listOf(
            async(Dispatchers.IO) { dao.updateChecked(id, 0, true, "first") },
            async(Dispatchers.IO) { dao.updateChecked(id, 1, true, "second") },
        ).awaitAll().forEach { assertTrue(it) }
        assertTrue(requireNotNull(dao.get(id)).items.all { it.checked })
    }

    @Test fun legacyFactoryRefreshesAndHandlesDeletedRows() = runTest {
        val dao = database.getBaseNoteDao()
        val id = dao.insert(note())
        val factory = WidgetFactory(app, id)
        withContext(Dispatchers.IO) { factory.onDataSetChanged() }
        assertEquals(3, factory.count)
        val header = requireNotNull(factory.getViewAt(0)).apply(app, FrameLayout(app))
        assertEquals("Checklist", header.findViewById<TextView>(R.id.Title).text.toString())
        assertNull(factory.getViewAt(3))
        assertNull(factory.getViewAt(-1))
        dao.delete(id)
        withContext(Dispatchers.IO) { factory.onDataSetChanged() }
        assertEquals(0, factory.count)
        assertNull(factory.getViewAt(0))
    }

    @Test
    @Config(sdk = [37])
    fun directCollectionContainsHeaderAndCheckedRows() {
        val collection = requireNotNull(WidgetProvider.createCollection(WidgetViews(app, note())))
        assertEquals(3, collection.itemCount)
        assertEquals(3, collection.viewTypeCount)
        assertFalse(collection.hasStableIds())
        val row = collection.getItemView(2).apply(app, FrameLayout(app))
        val checkbox = row.findViewById<CheckBox>(R.id.CheckBox)
        assertEquals("second", checkbox.text.toString())
        assertTrue(checkbox.isChecked)
        val empty = requireNotNull(WidgetProvider.createCollection(WidgetViews(app, null)))
        assertEquals(0, empty.itemCount)
    }

    @Test
    @Config(sdk = [37])
    fun oversizedCollectionsUseLegacyPathWithoutTruncatingContent() {
        val many = WidgetViews(app, note(List(201) { ListItem("row $it", false) }))
        assertNull(WidgetProvider.createCollection(many))
        assertEquals(202, many.count)
        val huge = note().copy(type = Type.NOTE, body = "x".repeat(300_000))
        assertNull(WidgetProvider.createCollection(WidgetViews(app, huge)))
    }
}
