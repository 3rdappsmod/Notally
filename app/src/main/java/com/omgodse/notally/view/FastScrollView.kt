package com.omgodse.notally.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.SeekBar
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import androidx.core.content.ContextCompat
import com.omgodse.notally.R

/** A dedicated right-hand drag lane, separate from editable text and list gestures. */
class FastScrollView(context: Context, attrs: AttributeSet? = null) : View(context, attrs), ViewTreeObserver.OnPreDrawListener {
    private var target: View? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density = resources.displayMetrics.density
    private var fraction = 0f
    private var dragging = false
    private var grabOffset = 0f
    private val thumbHeight get() = minOf(height.toFloat(), 48 * density)
    private val travel get() = (height - thumbHeight).coerceAtLeast(0f)

    init {
        isFocusable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = "${context.getString(R.string.jump_to_top)} / ${context.getString(R.string.jump_to_bottom)}"
    }

    fun bind(target: View) {
        require(target is NestedScrollView || target is RecyclerView)
        this.target = target
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnPreDrawListener(this)
    }

    override fun onDetachedFromWindow() {
        viewTreeObserver.removeOnPreDrawListener(this)
        dragging = false
        super.onDetachedFromWindow()
    }

    override fun onPreDraw(): Boolean {
        val view = target
        val scrollable = view != null && view.isShown &&
            (view.canScrollVertically(-1) || view.canScrollVertically(1))
        val nextVisibility = if (scrollable) VISIBLE else INVISIBLE
        if (visibility != nextVisibility) visibility = nextVisibility
        if (!scrollable) dragging = false
        if (!dragging) {
            val next = when (view) {
                is NestedScrollView -> if (nestedRange(view) > 0) view.scrollY.toFloat() / nestedRange(view) else 0f
                is RecyclerView -> {
                    val range = view.computeVerticalScrollRange() - view.computeVerticalScrollExtent()
                    if (!view.canScrollVertically(-1)) 0f
                    else if (!view.canScrollVertically(1)) 1f
                    else if (range > 0) view.computeVerticalScrollOffset().toFloat() / range else 0f
                }
                else -> 0f
            }.coerceIn(0f, 1f)
            if (next != fraction) { fraction = next; invalidate() }
        }
        return true
    }

    private fun nestedRange(view: NestedScrollView): Int =
        ((view.getChildAt(0)?.height ?: 0) - view.height + view.paddingTop + view.paddingBottom).coerceAtLeast(0)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        paint.color = ContextCompat.getColor(context, R.color.fast_scroll_thumb)
        val center = width / 2f
        val halfWidth = (if (dragging) 5 else 4) * density
        val top = fraction * travel
        canvas.drawRoundRect(center - halfWidth, top, center + halfWidth, top + thumbHeight, halfWidth, halfWidth, paint)
    }

    private fun scrollToFraction(value: Float) {
        fraction = value.coerceIn(0f, 1f)
        when (val view = target) {
            is NestedScrollView -> {
                view.fling(0)
                view.scrollTo(0, (nestedRange(view) * fraction).toInt())
            }
            is RecyclerView -> {
                view.stopScroll()
                val count = view.adapter?.itemCount ?: 0
                if (count > 0) {
                    val position = ((count - 1) * fraction).toInt()
                    when (val manager = view.layoutManager) {
                        is LinearLayoutManager -> manager.scrollToPositionWithOffset(position, 0)
                        is StaggeredGridLayoutManager -> manager.scrollToPositionWithOffset(position, 0)
                    }
                }
            }
        }
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (visibility != VISIBLE || target == null) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragging = true
                parent?.requestDisallowInterceptTouchEvent(true)
                val top = fraction * travel
                grabOffset = if (event.y in top..(top + thumbHeight)) event.y - top else thumbHeight / 2
                scrollToFraction(if (travel > 0) (event.y - grabOffset) / travel else 0f)
            }
            MotionEvent.ACTION_MOVE -> if (dragging) {
                scrollToFraction(if (travel > 0) (event.y - grabOffset) / travel else 0f)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
                parent?.requestDisallowInterceptTouchEvent(false)
                invalidate()
                if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
            }
            else -> return false
        }
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = SeekBar::class.java.name
        info.rangeInfo = AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_FLOAT, 0f, 1f, fraction)
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS)
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        when (action) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> scrollToFraction(fraction + 0.1f)
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> scrollToFraction(fraction - 0.1f)
            AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id -> {
                if (arguments?.containsKey(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE) != true) return false
                val value = arguments.getFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE)
                if (!value.isFinite()) return false
                scrollToFraction(value)
            }
            else -> return super.performAccessibilityAction(action, arguments)
        }
        return true
    }
}
