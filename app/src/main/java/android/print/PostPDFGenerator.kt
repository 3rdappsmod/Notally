package android.print

import android.content.Context
import android.os.ParcelFileDescriptor
import android.util.Log
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.File

private const val TAG = "PostPDFGenerator"

/**
 * This class needs to be in android.print package to access the package private
 * methods of [PrintDocumentAdapter]
 */
object PostPDFGenerator {

    fun create(file: File, content: String, context: Context, onResult: OnResult) {
        val webView = WebView(context)
        var startedPrinting = false
        var completed = false
        val result = object : OnResult {
            private fun complete(): Boolean {
                if (completed) return false
                completed = true
                webView.post {
                    webView.stopLoading()
                    webView.destroy()
                }
                return true
            }

            override fun onSuccess(file: File) {
                if (complete()) onResult.onSuccess(file)
            }

            override fun onFailure(message: CharSequence?) {
                if (complete()) {
                    file.delete()
                    onResult.onFailure(message)
                }
            }
        }
        webView.webViewClient = object : WebViewClient() {

            override fun onPageFinished(view: WebView?, url: String?) {
                if (completed || startedPrinting) return
                startedPrinting = true
                val adapter = webView.createPrintDocumentAdapter(file.nameWithoutExtension)
                print(file, adapter, result)
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                Log.w(TAG, "WebView load error during PDF export: code=${error.errorCode}, description=${error.description}")
                if (request.isForMainFrame) result.onFailure(error.description)
            }
        }
        // Install callbacks before starting a potentially synchronous cached load.
        try {
            webView.loadDataWithBaseURL(null, content, "text/html", "utf-8", null)
        } catch (exception: Exception) {
            result.onFailure(exception.message)
        }
    }


    internal fun print(file: File, adapter: PrintDocumentAdapter, onResult: OnResult) {
        var completed = false
        var descriptor: ParcelFileDescriptor? = null
        var writing = false
        val result = object : OnResult {
            private fun complete(): Boolean {
                if (completed) return false
                completed = true
                runCatching { descriptor?.close() }
                    .onFailure { Log.w(TAG, "Could not close PDF descriptor", it) }
                runCatching { adapter.onFinish() }
                    .onFailure { Log.w(TAG, "Could not finish PDF adapter", it) }
                return true
            }

            override fun onSuccess(file: File) {
                if (complete()) onResult.onSuccess(file)
            }

            override fun onFailure(message: CharSequence?) {
                if (complete()) {
                    file.delete()
                    onResult.onFailure(message)
                }
            }
        }
        val onLayoutResult = object : PrintDocumentAdapter.LayoutResultCallback() {
            override fun onLayoutFailed(error: CharSequence?) = result.onFailure(error)

            override fun onLayoutCancelled() = result.onFailure(null)

            override fun onLayoutFinished(info: PrintDocumentInfo?, changed: Boolean) {
                if (completed || writing) return
                writing = true
                try {
                    descriptor = ParcelFileDescriptor.open(file,
                        ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE or
                            ParcelFileDescriptor.MODE_READ_WRITE)
                    adapter.onWrite(arrayOf(PageRange.ALL_PAGES), descriptor, null,
                        object : PrintDocumentAdapter.WriteResultCallback() {
                            override fun onWriteFailed(error: CharSequence?) = result.onFailure(error)
                            override fun onWriteCancelled() = result.onFailure(null)
                            override fun onWriteFinished(pages: Array<out PageRange>?) = result.onSuccess(file)
                        })
                } catch (exception: Exception) {
                    result.onFailure(exception.message)
                }
            }
        }
        try {
            adapter.onStart()
            adapter.onLayout(null, getPrintAttributes(), null, onLayoutResult, null)
        } catch (exception: Exception) {
            result.onFailure(exception.message)
        }
    }


    private fun getPrintAttributes(): PrintAttributes {
        val builder = PrintAttributes.Builder()
        builder.setMediaSize(PrintAttributes.MediaSize.ISO_A4)
        builder.setMinMargins(PrintAttributes.Margins(250, 250, 250, 250))
        builder.setResolution(PrintAttributes.Resolution("Standard", "Standard", 100, 100))
        return builder.build()
    }

    interface OnResult {

        fun onSuccess(file: File)

        fun onFailure(message: CharSequence?)
    }
}