package com.omgodse.notally.view

import android.app.Application
import androidx.core.graphics.Insets
import androidx.core.view.WindowInsetsCompat
import android.view.ContextThemeWrapper
import android.widget.LinearLayout
import com.omgodse.notally.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [26, 30, 37])
class NavigationViewTest {
    @Suppress("DEPRECATION")
    @Test
    fun systemBarPaddingWorksAtMinimumSdkAndApi30() {
        val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.AppTheme)
        val view = NavigationView(context, Robolectric.buildAttributeSet().build())
        val content = LinearLayout(context).apply { id = R.id.LinearLayout }
        view.addView(content)
        ReflectionHelpers.callInstanceMethod<Unit>(view, "onFinishInflate")
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, 24, 0, 0))
            .build()
        view.onApplyWindowInsets(requireNotNull(insets.toWindowInsets()))
        assertEquals(24, content.paddingTop)
    }
}
