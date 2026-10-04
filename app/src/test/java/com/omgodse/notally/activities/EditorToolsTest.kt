package com.omgodse.notally.activities

import android.app.Application
import android.os.Looper
import android.graphics.Typeface
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.View
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.omgodse.notally.room.ListItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [37], qualifiers = "w400dp-h800dp-mdpi")
class EditorToolsTest {
    @Before fun setUp() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }
    private fun layout(activity: TakeNote) {
        activity.binding.root.apply {
            measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
            layout(0, 0, 400, 800)
        }
    }

    @Test fun toolbarUndoRedoActuallyChangesTheEditableAndKeepsFormatting() = runTest {
        val controller = Robolectric.buildActivity(TakeNote::class.java).create().start().resume().visible()
        val activity = controller.get()
        ReflectionHelpers.getField<Job>(activity, "initialization").join()
        val editor = activity.binding.EnterBody
        editor.requestFocus()
        editor.text!!.append("hello")
        assertTrue(activity.binding.Undo.isEnabled)
        android.view.inputmethod.BaseInputConnection.setComposingSpans(editor.text!!)
        activity.binding.Undo.performClick()
        assertEquals("", editor.text.toString())
        assertEquals(-1, android.view.inputmethod.BaseInputConnection.getComposingSpanStart(editor.text!!))
        activity.binding.Redo.performClick()
        assertEquals("hello", editor.text.toString())
        val before = activity.model.editorState()
        editor.text!!.setSpan(StyleSpan(Typeface.BOLD), 0, 5, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        activity.model.recordEditorChange(before, activity.model.editorState())
        activity.model.history.undo()
        assertEquals(0, editor.text!!.getSpans(0, 5, StyleSpan::class.java).size)
        activity.model.history.redo()
        assertEquals(Typeface.BOLD, editor.text!!.getSpans(0, 5, StyleSpan::class.java).single().style)
        controller.pause().stop().destroy()
    }

    @Test fun keyboardOpeningRevealsCaretWithoutRotationAndJumpButtonsReachBothEnds() = runTest {
        val controller = Robolectric.buildActivity(TakeNote::class.java).create().start().resume().visible()
        val activity = controller.get()
        ReflectionHelpers.getField<Job>(activity, "initialization").join()
        val editor = activity.binding.EnterBody
        editor.requestFocus()
        editor.text!!.append((1..100).joinToString("\n") { "Line $it - long note" })
        editor.setSelection(editor.length())
        layout(activity)
        shadowOf(Looper.getMainLooper()).idle()
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, 24, 0, 24))
            .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, 300))
            .setVisible(WindowInsetsCompat.Type.ime(), true).build()
        ViewCompat.dispatchApplyWindowInsets(activity.binding.root, insets)
        layout(activity)
        shadowOf(Looper.getMainLooper()).idle()
        val position = IntArray(2)
        editor.getLocationOnScreen(position)
        val caretBottom = position[1] + editor.totalPaddingTop + editor.layout.getLineBottom(editor.layout.lineCount - 1) - editor.scrollY
        val viewport = IntArray(2)
        activity.binding.ScrollView.getLocationOnScreen(viewport)
        assertTrue("caret=$caretBottom viewport=${viewport[1] + activity.binding.ScrollView.height}", caretBottom <= viewport[1] + activity.binding.ScrollView.height)
        activity.binding.JumpTop.performClick()
        assertEquals(0, activity.binding.ScrollView.scrollY)
        activity.binding.JumpBottom.performClick()
        assertTrue(activity.binding.ScrollView.scrollY > 0)
        controller.pause().stop().destroy()
    }

    @Test fun checklistSnapshotsAreIndependentOfMutableRows() = runTest {
        val controller = Robolectric.buildActivity(MakeList::class.java).create().start().resume().visible()
        val activity = controller.get()
        ReflectionHelpers.getField<Job>(activity, "initialization").join()
        val model = activity.model
        model.items.clear()
        model.items.add(ListItem("first", false))
        val before = model.editorState()
        model.items[0].checked = true
        model.items.add(ListItem("second", false))
        model.recordEditorChange(before, model.editorState())
        model.history.undo()
        assertEquals(listOf(ListItem("first", false)), model.items)
        model.history.redo()
        assertEquals(listOf(ListItem("first", true), ListItem("second", false)), model.items)
        controller.pause().stop().destroy()
    }

    @Test fun rotationRetainsHistoryWithoutDuplicateWatchers() = runTest {
        val controller = Robolectric.buildActivity(TakeNote::class.java).create().start().resume().visible()
        ReflectionHelpers.getField<Job>(controller.get(), "initialization").join()
        controller.get().binding.EnterBody.text!!.append("before")
        val originalModel = controller.get().model
        val config = android.content.res.Configuration(controller.get().resources.configuration)
        config.orientation = android.content.res.Configuration.ORIENTATION_LANDSCAPE
        controller.configurationChange(config)
        val recreated = controller.get()
        ReflectionHelpers.getField<Job>(recreated, "initialization").join()
        assertSame(originalModel, recreated.model)
        recreated.binding.EnterBody.text!!.append(" after")
        recreated.binding.Undo.performClick()
        assertEquals("before", recreated.binding.EnterBody.text.toString())
        recreated.binding.Undo.performClick()
        assertEquals("", recreated.binding.EnterBody.text.toString())
        controller.pause().stop().destroy()
    }

    @Test fun linksSupportPhoneEmailAndSchemeLessWebPaths() {
        assertEquals("tel:+821012345678", TakeNote.getURLFrom("+821012345678"))
        assertEquals("mailto:user@example.com", TakeNote.getURLFrom("user@example.com"))
        assertEquals("https://example.com/path?q=1", TakeNote.getURLFrom("example.com/path?q=1"))
        assertEquals("https://example.com", TakeNote.getURLFrom("https://example.com"))
    }
}
