package com.omgodse.notally.view

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.omgodse.notally.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28, 37])
class FastScrollViewTest {
    private fun layout(root: View) {
        root.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 400, 600)
    }
    private fun touch(bar: FastScrollView, action: Int, y: Float) {
        val event = MotionEvent.obtain(0, 0, action, 16f, y, 0)
        assertTrue(bar.onTouchEvent(event))
        event.recycle()
    }
    @Test fun nestedContentDragsToBothEndsAndHidesForShortContent() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        activity.setTheme(R.style.AppTheme)
        val root = FrameLayout(activity)
        val scroll = NestedScrollView(activity)
        val content = View(activity).apply { minimumHeight = 4000 }
        scroll.addView(content, ViewGroup.LayoutParams(300, 4000))
        root.addView(scroll, FrameLayout.LayoutParams(368, 600))
        val bar = FastScrollView(activity)
        root.addView(bar, FrameLayout.LayoutParams(32, 600))
        bar.bind(scroll)
        activity.setContentView(root)
        layout(root)
        bar.onPreDraw()
        assertEquals(View.VISIBLE, bar.visibility)
        touch(bar, MotionEvent.ACTION_DOWN, 20f)
        touch(bar, MotionEvent.ACTION_MOVE, 600f)
        touch(bar, MotionEvent.ACTION_UP, 600f)
        assertEquals(3400, scroll.scrollY)
        touch(bar, MotionEvent.ACTION_DOWN, 580f)
        touch(bar, MotionEvent.ACTION_MOVE, 0f)
        touch(bar, MotionEvent.ACTION_UP, 0f)
        assertEquals(0, scroll.scrollY)
        content.minimumHeight = 100
        content.layoutParams = content.layoutParams.apply { height = 100 }
        layout(root)
        bar.onPreDraw()
        assertEquals(View.INVISIBLE, bar.visibility)
        controller.pause().stop().destroy()
    }
    @Test fun listAndGridReachLastItemAndReturnToStart() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        activity.setTheme(R.style.AppTheme)
        val root = FrameLayout(activity)
        val list = RecyclerView(activity)
        list.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun getItemCount() = 100
            override fun onCreateViewHolder(parent: ViewGroup, type: Int) = object : RecyclerView.ViewHolder(TextView(activity).apply {
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 80)
            }) {}
            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) { (holder.itemView as TextView).text = "Note $position" }
        }
        root.addView(list, FrameLayout.LayoutParams(368, 600))
        val bar = FastScrollView(activity)
        root.addView(bar, FrameLayout.LayoutParams(32, 600))
        bar.bind(list)
        activity.setContentView(root)
        for (manager in listOf(LinearLayoutManager(activity), StaggeredGridLayoutManager(2, RecyclerView.VERTICAL))) {
            list.layoutManager = manager
            layout(root)
            bar.onPreDraw()
            assertEquals(View.VISIBLE, bar.visibility)
            touch(bar, MotionEvent.ACTION_DOWN, 0f)
            touch(bar, MotionEvent.ACTION_MOVE, 600f)
            layout(root)
            touch(bar, MotionEvent.ACTION_UP, 600f)
            assertNotNull(list.findViewHolderForAdapterPosition(99))
            val args = Bundle().apply { putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, 0f) }
            assertTrue(bar.performAccessibilityAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id, args))
            layout(root)
            assertNotNull(list.findViewHolderForAdapterPosition(0))
        }
        controller.pause().stop().destroy()
    }
}
