package com.omgodse.notally.activities

import android.app.Application
import android.view.View
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.omgodse.notally.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [37])
class KeyboardInsetsTest {
    @Before fun setUp() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun verifyKeyboardSpace(view: View) {
        fun dispatch(imeHeight: Int) {
            val insets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, 24, 0, 24))
                .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, imeHeight))
                .setVisible(WindowInsetsCompat.Type.ime(), imeHeight > 0)
                .build()
            ViewCompat.dispatchApplyWindowInsets(view, insets)
        }
        val originalBottom = view.paddingBottom
        dispatch(600)
        assertEquals(originalBottom + 600, view.paddingBottom)
        // Repeated delivery must not accumulate padding.
        dispatch(600)
        assertEquals(originalBottom + 600, view.paddingBottom)
        // Hiding the keyboard restores just the system-bar space.
        dispatch(0)
        assertEquals(originalBottom + 24, view.paddingBottom)
    }

    @Test fun checklistKeepsContentAboveKeyboard() {
        val controller = Robolectric.buildActivity(MakeList::class.java).create()
        verifyKeyboardSpace(controller.get().binding.root)
        controller.destroy()
    }

    @Test fun searchKeepsResultsAboveKeyboard() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).create()
        verifyKeyboardSpace(controller.get().findViewById(R.id.RelativeLayout))
        controller.destroy()
    }
}
