package com.omgodse.notally.room

import android.app.Application
import android.os.Parcel
import android.os.Parcelable
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Exercise generated Parcelize code used when attachments cross activity boundaries. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [26, 37])
class AttachmentParcelTest {
    @Test
    fun imageSurvivesParcelRoundTrip() {
        val image = Image("사진.png", "image/png")
        assertEquals(image, roundTrip(image))
    }

    @Test
    fun audioSurvivesParcelRoundTrip() {
        val audio = Audio("녹음.m4a", 123_456L, 1_789_876_543_210L)
        assertEquals(audio, roundTrip(audio))
    }

    @Suppress("DEPRECATION")
    private fun roundTrip(value: Parcelable): Parcelable? {
        val parcel = Parcel.obtain()
        try {
            parcel.writeParcelable(value, 0)
            parcel.setDataPosition(0)
            return parcel.readParcelable(value.javaClass.classLoader)
        } finally {
            parcel.recycle()
        }
    }
}
