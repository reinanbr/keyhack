package com.reinanbr.keyhack.keyboard.view

import android.graphics.Rect
import android.os.Bundle
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.customview.widget.ExploreByTouchHelper
import com.reinanbr.keyhack.keyboard.R
import com.reinanbr.keyhack.keyboard.model.KeyAction
import com.reinanbr.keyhack.keyboard.model.ShiftState
import kotlin.math.roundToInt

/** Exposes each key as a virtual view so TalkBack can explore and activate keys. */
internal class KeyboardAccessibilityHelper(private val view: KeyboardView) : ExploreByTouchHelper(view) {
    override fun getVirtualViewAt(x: Float, y: Float): Int {
        val g = view.geometry ?: return INVALID_ID
        val index = g.keyIndexAt(x, y - view.keyTopOffset)
        return if (index < 0) INVALID_ID else index
    }

    override fun getVisibleVirtualViews(virtualViewIds: MutableList<Int>) {
        val g = view.geometry ?: return
        virtualViewIds.addAll(g.keys.indices)
    }

    // ExploreByTouchHelper still requires bounds in parent, which is deprecated.
    @Suppress("DEPRECATION")
    override fun onPopulateNodeForVirtualView(virtualViewId: Int, node: AccessibilityNodeInfoCompat) {
        val placed = view.geometry?.keys?.getOrNull(virtualViewId)
        if (placed == null) {
            // ExploreByTouchHelper requires bounds and text even for stale ids.
            node.contentDescription = ""
            node.setBoundsInParent(Rect())
            return
        }
        val res = view.resources
        node.contentDescription =
            when (placed.key.action) {
                KeyAction.Shift ->
                    res.getString(
                        when (view.shiftState) {
                            ShiftState.OFF -> R.string.keyhack_a11y_shift
                            ShiftState.ONCE -> R.string.keyhack_a11y_shift_on
                            ShiftState.LOCKED -> R.string.keyhack_a11y_caps_lock
                        },
                    )

                KeyAction.Delete -> res.getString(R.string.keyhack_a11y_delete)

                KeyAction.Enter -> res.getString(R.string.keyhack_a11y_enter)

                KeyAction.Space -> res.getString(R.string.keyhack_a11y_space)

                is KeyAction.Character -> view.labelFor(placed)
            }
        val b = placed.bounds
        val top = view.keyTopOffset
        node.setBoundsInParent(
            Rect(b.left.roundToInt(), (b.top + top).roundToInt(), b.right.roundToInt(), (b.bottom + top).roundToInt()),
        )
        node.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK)
    }

    override fun onPerformActionForVirtualView(virtualViewId: Int, action: Int, arguments: Bundle?): Boolean {
        if (action != AccessibilityNodeInfoCompat.ACTION_CLICK) return false
        view.activateKey(virtualViewId)
        invalidateVirtualView(virtualViewId)
        return true
    }
}
