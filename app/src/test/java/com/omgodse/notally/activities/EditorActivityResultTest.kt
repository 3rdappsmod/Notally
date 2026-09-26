package com.omgodse.notally.activities

import android.app.Activity
import android.app.Application
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import com.omgodse.notally.miscellaneous.Constants
import com.omgodse.notally.room.NotallyDatabase
import com.omgodse.notally.viewmodels.NotallyModel
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
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28, 37])
class EditorActivityResultTest {
    @Before fun setUp() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun returnedLabelsSurvivePendingNoteRestoration() = runTest {
        val app = RuntimeEnvironment.getApplication()
        val database = NotallyDatabase.getDatabase(app)
        database.clearAllTables()
        val seed = NotallyModel(app)
        seed.setState(0)
        seed.title = "Existing note"
        seed.labels.value = arrayListOf("old")
        val id = seed.saveNote()

        // A fresh activity/model represents return after losing the old process.
        val intent = Intent(app, MakeList::class.java).putExtra(Constants.SelectedBaseNote, id)
        val controller = Robolectric.buildActivity(MakeList::class.java, intent)
            .create().start().resume()
        val activity = controller.get()
        val initialization = ReflectionHelpers.getField<Job>(activity, "initialization")
        assertFalse(initialization.isCompleted)
        val launcher = ReflectionHelpers.getField<ActivityResultLauncher<Intent>>(activity, "selectLabelsLauncher")
        launcher.launch(Intent(activity, SelectLabels::class.java))
        val request = shadowOf(activity).nextStartedActivityForResult
        val result = Intent().putStringArrayListExtra(SelectLabels.SELECTED_LABELS, arrayListOf("new"))
        assertTrue(activity.activityResultRegistry.dispatchResult(request.requestCode, Activity.RESULT_OK, result))

        initialization.join()
        testScheduler.runCurrent()
        assertEquals(id, activity.model.id)
        assertEquals("Existing note", activity.model.title)
        assertEquals(listOf("new"), activity.model.labels.value)
        activity.model.saveNote()
        assertEquals(listOf("new"), database.getBaseNoteDao().get(id)?.labels)
        assertEquals(1, database.getBaseNoteDao().getAllNotes().size)
        controller.pause().stop().destroy()
    }
}
