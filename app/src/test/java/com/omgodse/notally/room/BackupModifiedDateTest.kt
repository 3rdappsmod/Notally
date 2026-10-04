package com.omgodse.notally.room

import android.app.Application
import android.database.Cursor
import android.database.MatrixCursor
import com.omgodse.notally.viewmodels.BaseNoteModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [37])
class BackupModifiedDateTest {
    @Before fun setUp() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }
    @Test fun backupImportHandlesBothOldAndNewColumns() {
        val model = BaseNoteModel(RuntimeEnvironment.getApplication())
        for (modern in listOf(false, true)) {
            val columns = mutableListOf("type","folder","color","title","pinned","timestamp","labels","body","spans","items")
            val values = mutableListOf<Any>("NOTE","NOTES","DEFAULT","title",0,1234L,"[]","body","[]","[]")
            if (modern) { columns.add("modifiedTimestamp"); values.add(5678L) }
            MatrixCursor(columns.toTypedArray()).use { cursor ->
                cursor.addRow(values)
                cursor.moveToFirst()
                val note = ReflectionHelpers.callInstanceMethod<BaseNote>(model, "convertCursorToBaseNote",
                    ReflectionHelpers.ClassParameter.from(Cursor::class.java, cursor))
                assertEquals(1234L, note.timestamp)
                assertEquals(if (modern) 5678L else 1234L, note.modifiedTimestamp)
            }
        }
    }
}
