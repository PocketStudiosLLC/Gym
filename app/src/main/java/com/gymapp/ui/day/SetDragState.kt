package com.gymapp.ui.day

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.LayoutCoordinates

/** Identifies one set on the Day screen. */
data class SetKey(val entryId: Long, val setIndex: Int)

data class DragPayload(val key: SetKey, val label: String)

/**
 * Shared state for dragging a set onto a set of another exercise to start a superset.
 * Positions are in root coordinates.
 */
@Stable
class SetDragState {
    var payload by mutableStateOf<DragPayload?>(null)
        private set
    var pointer by mutableStateOf(Offset.Zero)
        private set

    /** Bounds of every visible set row, used as drop targets. */
    val setTargets = mutableStateMapOf<SetKey, Rect>()

    /** Bounds of every visible entry header, used to auto-expand collapsed entries on hover. */
    val entryTargets = mutableStateMapOf<Long, Rect>()

    val active: Boolean get() = payload != null

    /** The set currently under the finger that would accept a drop. */
    val hoverTarget: SetKey? by derivedStateOf {
        val p = payload ?: return@derivedStateOf null
        setTargets.entries.firstOrNull { (k, r) -> k.entryId != p.key.entryId && r.contains(pointer) }?.key
    }

    /** The entry header currently under the finger (other than the dragged set's own entry). */
    val hoverEntry: Long? by derivedStateOf {
        val p = payload ?: return@derivedStateOf null
        entryTargets.entries.firstOrNull { (id, r) -> id != p.key.entryId && r.contains(pointer) }?.key
    }

    fun start(p: DragPayload, at: Offset) {
        payload = p
        pointer = at
    }

    fun move(by: Offset) {
        pointer += by
    }

    /** Ends the drag and returns (source, target) if it was dropped on a valid set. */
    fun end(): Pair<SetKey, SetKey>? {
        val p = payload ?: return null
        val target = hoverTarget
        payload = null
        return target?.let { p.key to it }
    }

    fun cancel() {
        payload = null
    }
}

/**
 * Tap → [onTap]. Long-press then drag → reports drag start (in root coordinates), movement and end.
 * Lets a parent list scroll normally when the finger moves before the long-press fires.
 */
suspend fun PointerInputScope.detectTapOrLongPressDrag(
    coordinates: () -> LayoutCoordinates?,
    onTap: () -> Unit,
    onDragStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var cancelled = false
        val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            waitForUpOrCancellation().also { if (it == null) cancelled = true }
        }
        when {
            up != null -> {
                up.consume()
                onTap()
            }
            cancelled -> Unit
            else -> {
                val coords = coordinates()
                val root = coords?.takeIf { it.isAttached }?.localToRoot(down.position) ?: down.position
                onDragStart(root)
                val completed = drag(down.id) { change ->
                    onDrag(change.positionChange())
                    change.consume()
                }
                if (completed) onDragEnd() else onDragCancel()
            }
        }
    }
}
