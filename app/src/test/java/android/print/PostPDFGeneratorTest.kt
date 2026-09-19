package android.print

import android.app.Application
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28, 37])
class PostPDFGeneratorTest {
    @Test
    fun failedWriteDeletesPartialFileClosesDescriptorAndReportsOnce() {
        val file = File(RuntimeEnvironment.getApplication().cacheDir, "failed.pdf")
        val adapter = FakeAdapter(failWrite = true)
        val result = Result()
        PostPDFGenerator.print(file, adapter, result)
        assertFalse(file.exists())
        assertEquals(1, result.failures)
        assertEquals(0, result.successes)
        assertEquals(1, adapter.finishes)
        assertClosed(adapter.descriptor)
    }

    @Test
    fun successfulWriteClosesDescriptorAndFinishesAdapter() {
        val file = File(RuntimeEnvironment.getApplication().cacheDir, "success.pdf")
        file.writeText("old contents must be truncated")
        val adapter = FakeAdapter(failWrite = false)
        val result = Result()
        PostPDFGenerator.print(file, adapter, result)
        assertEquals("PDF", file.readText())
        assertEquals(1, result.successes)
        assertEquals(0, result.failures)
        assertEquals(1, adapter.finishes)
        assertClosed(adapter.descriptor)
    }

    private fun assertClosed(descriptor: ParcelFileDescriptor) {
        assertThrows(IllegalStateException::class.java) { descriptor.fd }
    }

    private class Result : PostPDFGenerator.OnResult {
        var successes = 0
        var failures = 0
        override fun onSuccess(file: File) { successes++ }
        override fun onFailure(message: CharSequence?) { failures++ }
    }

    private class FakeAdapter(private val failWrite: Boolean) : PrintDocumentAdapter() {
        var finishes = 0
        lateinit var descriptor: ParcelFileDescriptor
        override fun onLayout(old: PrintAttributes?, new: PrintAttributes?, cancellation: CancellationSignal?,
            callback: LayoutResultCallback, extras: Bundle?) {
            callback.onLayoutFinished(PrintDocumentInfo.Builder("test.pdf").build(), true)
        }
        override fun onWrite(pages: Array<out PageRange>?, destination: ParcelFileDescriptor,
            cancellation: CancellationSignal?, callback: WriteResultCallback) {
            descriptor = destination
            // Do not close the descriptor: ownership remains with the generator.
            java.io.FileOutputStream(destination.fileDescriptor).apply { write("PDF".toByteArray()); flush() }
            if (failWrite) {
                callback.onWriteFailed("failed")
                callback.onWriteFinished(pages) // Late callbacks must be ignored.
            } else callback.onWriteFinished(pages)
        }
        override fun onFinish() { finishes++ }
    }
}
