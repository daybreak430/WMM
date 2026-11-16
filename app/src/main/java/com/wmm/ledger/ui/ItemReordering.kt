package com.wmm.ledger.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import com.mohamedrejeb.compose.dnd.DragAndDropState
import com.mohamedrejeb.compose.dnd.reorder.reorderableItem
import com.mohamedrejeb.compose.dnd.rememberDragAndDropState

@Composable
internal fun rememberItemOrder(ids: List<String>): ItemOrder = remember(ids) { ItemOrder(ids) }

internal class ItemOrder(private val saved: List<String>) {
    var ids by mutableStateOf(saved)
        private set
    var saving by mutableStateOf(false)
        private set
    var dragging by mutableStateOf(false)
        private set

    fun start() { dragging = true }

    fun move(id: String, target: String, visible: Set<String>) {
        if (id == target || id !in visible || target !in visible) return
        val subset = ids.filter { it in visible }.toMutableList()
        val destination = subset.indexOf(target)
        subset.add(destination, subset.removeAt(subset.indexOf(id)))
        // 仅调整参与拖拽的条目，折叠区内未展示的条目保持原序位。
        val reordered = subset.iterator()
        ids = ids.map { if (it in visible) reordered.next() else it }
    }

    fun finish(save: (List<String>, () -> Unit) -> Unit) {
        dragging = false
        if (ids == saved) return
        saving = true
        save(ids) {
            ids = saved
            saving = false
        }
    }
}

@Composable
internal fun rememberReorderDragState() = rememberDragAndDropState<String>(dragAfterLongPress = true)

@Composable
internal fun ReorderableLedgerItem(
    id: String,
    order: ItemOrder,
    visibleIds: Set<String>,
    dragState: DragAndDropState<String>,
    save: (List<String>, () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val visibleOrder = order.ids.filter { it in visibleIds }
    val position = visibleOrder.indexOf(id)
    val enabled = !order.saving && visibleOrder.size > 1
    val previous = visibleOrder.getOrNull(position - 1)
    val next = visibleOrder.getOrNull(position + 1)
    val accessibility = if (enabled && !order.dragging) Modifier.semantics {
        customActions = listOfNotNull(
            previous?.let { target -> CustomAccessibilityAction("向前移动") {
                order.move(id, target, visibleIds)
                order.finish(save)
                true
            } },
            next?.let { target -> CustomAccessibilityAction("向后移动") {
                order.move(id, target, visibleIds)
                order.finish(save)
                true
            } },
        )
    } else Modifier
    Box(
        modifier.then(accessibility)
            .graphicsLayer { alpha = if (dragState.isDragging(id)) 0f else 1f }
            .reorderableItem(
                key = id,
                data = id,
                state = dragState,
                enabled = enabled,
                canDrop = !order.saving,
                onDragStart = {
                    order.start()
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                },
                onDragEnter = { dragged -> order.move(dragged.data, id, visibleIds) },
                onDragEnd = { order.finish(save) },
                draggableContent = { Box(Modifier.clearAndSetSemantics {}, propagateMinConstraints = true) { content() } },
            ),
        propagateMinConstraints = true,
    ) { content() }
}
