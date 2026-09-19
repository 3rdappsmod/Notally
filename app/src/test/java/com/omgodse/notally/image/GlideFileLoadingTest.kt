package com.omgodse.notally.image

import android.app.Application
import android.graphics.drawable.Drawable
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.FutureTarget
import org.junit.Before
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowBitmapFactory
import java.io.File
import java.util.Base64
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Exercise the actual Glide engine with Robolectric platform decoding. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@Config(application = Application::class, sdk = [26, 37])
class GlideFileLoadingTest {
    private val app = RuntimeEnvironment.getApplication()
    private val executor = Executors.newSingleThreadExecutor()
    private val targets = mutableListOf<FutureTarget<Drawable>>()

    @Before
    fun requireValidImageData() {
        // Legacy Robolectric otherwise fabricates bitmaps for corrupt input.
        ShadowBitmapFactory.setAllowInvalidImageData(false)
    }

    @After
    fun cleanUp() {
        targets.forEach { Glide.with(app).clear(it) }
        executor.shutdownNow()
        Glide.get(app).clearMemory()
        Glide.tearDown()
    }

    private fun load(file: File): Drawable {
        val target = Glide.with(app)
            .load(file)
            .centerCrop()
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .skipMemoryCache(true)
            .submit(16, 16)
        targets.add(target)
        // FutureTarget.get must run off the UI thread, like a real decode request.
        return executor.submit<Drawable> { target.get(10, TimeUnit.SECONDS) }
            .get(15, TimeUnit.SECONDS)
    }

    @Test
    fun localPngCanBeDecodedAndCropped() {
        val file = File(app.cacheDir, "valid.png")
        file.writeBytes(Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGP4z8DwHwAFAAH/iZk9HQAAAABJRU5ErkJggg=="))
        val drawable = load(file)
        assertEquals(16, drawable.intrinsicWidth)
        assertEquals(16, drawable.intrinsicHeight)
    }

    @Test
    fun missingAttachmentReportsLoadFailure() {
        assertLoadFailure(File(app.cacheDir, "missing.png"))
    }

    @Test
    fun corruptAttachmentReportsLoadFailure() {
        val file = File(app.cacheDir, "corrupt.png")
        file.writeText("This is not an image")
        assertLoadFailure(file)
    }

    private fun assertLoadFailure(file: File) {
        val error = assertThrows(ExecutionException::class.java) { load(file) }
        assertTrue(generateSequence<Throwable>(error) { it.cause }.any { it is GlideException })
    }
}
