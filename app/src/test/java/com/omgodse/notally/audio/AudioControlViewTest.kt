package com.omgodse.notally.audio

import android.app.Activity
import android.app.Application
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import com.google.android.material.slider.Slider
import com.omgodse.notally.R
import com.omgodse.notally.databinding.ActivityPlayAudioBinding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [26, 37])
class AudioControlViewTest {
    @Test
    fun replayFollowsPlayerEvenWhenStartInitiallyReportsTheEnd() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        activity.setTheme(R.style.AppTheme)
        val binding = ActivityPlayAudioBinding.inflate(LayoutInflater.from(activity))
        activity.setContentView(binding.root)
        controller.visible()
        shadowOf(Looper.getMainLooper()).idle()
        val view = binding.AudioControlView
        assertTrue(view.isAttachedToWindow)
        assertTrue(view.isShown)
        view.dispatchWindowVisibilityChanged(View.VISIBLE)
        val slider = view.findViewById<Slider>(R.id.Progress)
        view.setDuration(60_000)
        var position = 60_000
        view.positionProvider = { position }
        view.setCurrentPosition(position)
        view.setStarted(false)
        assertEquals(60_000f, slider.value, 0f)

        // A device may still report the old end position immediately after start().
        view.setStarted(true)
        position = 100
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
        assertEquals(100f, slider.value, 0f)
        position = 500
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
        assertEquals(500f, slider.value, 0f)

        view.setStarted(false)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        assertEquals(500f, slider.value, 0f)
        controller.pause().stop().destroy()
    }
}
