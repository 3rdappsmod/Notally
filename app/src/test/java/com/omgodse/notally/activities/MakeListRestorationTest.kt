package com.omgodse.notally.activities

import android.app.Application
import android.os.Bundle
import android.os.Looper
import android.view.View
import com.omgodse.notally.recyclerview.viewholder.MakeListVH
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
@Config(application = Application::class, sdk = [28, 37])
class MakeListRestorationTest {
    @Before
    fun setUp() { Dispatchers.setMain(StandardTestDispatcher()) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    private suspend fun initialized(activity: MakeList) {
        ReflectionHelpers.getField<Job>(activity, "initialization").join()
    }

    private fun layout(activity: MakeList) {
        val root = activity.binding.root
        root.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 1080, 1920)
        root.viewTreeObserver.dispatchOnPreDraw()
    }

    @Test
    fun savedSecondRowAndSelectionRestoreAfterActivityRecreation() = runTest {
        val controller = Robolectric.buildActivity(MakeList::class.java).create().start().resume().visible()
        val old = controller.get()
        initialized(old)
        old.model.items[0].body = "first row"
        old.model.items.add(ListItem("second row text", false))
        old.binding.RecyclerView.adapter!!.notifyDataSetChanged()
        old.model.saveNote()
        layout(old)
        shadowOf(Looper.getMainLooper()).idle()
        layout(old)
        val editor = (old.binding.RecyclerView.findViewHolderForAdapterPosition(1) as MakeListVH).binding.EditText
        assertTrue(editor.requestFocus())
        assertSame(editor, old.binding.RecyclerView.findFocus())
        editor.setSelection(3, 8)
        val saved = Bundle()
        controller.saveInstanceState(saved)
        assertEquals(1, saved.getInt("listEditorPosition", -1))
        assertEquals(3, saved.getInt("listSelectionStart", -1))
        assertEquals(8, saved.getInt("listSelectionEnd", -1))
        controller.pause().stop().destroy()

        val restored = Robolectric.buildActivity(MakeList::class.java)
            .create(saved).start().restoreInstanceState(saved).resume().visible()
        val activity = restored.get()
        initialized(activity)
        testScheduler.runCurrent()
        shadowOf(Looper.getMainLooper()).idle()
        layout(activity)
        val target = (activity.binding.RecyclerView.findViewHolderForAdapterPosition(1) as MakeListVH).binding.EditText
        assertTrue(target.hasFocus())
        assertEquals(3, target.selectionStart)
        assertEquals(8, target.selectionEnd)
        assertEquals("second row text", target.text.toString())
        assertEquals(2, activity.model.items.size)
        restored.pause().stop().destroy()
    }
}
