package space.ourmosaic.app.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.zIndex
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun rememberReorderableState(
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    onDragEnd: () -> Unit = {}
): ReorderableState {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    return remember(scope, haptic) { ReorderableState(scope, haptic, onMove, onDragEnd) }
}

class ReorderableState(
    private val scope: CoroutineScope,
    private val haptic: HapticFeedback,
    private val onMove: (Int, Int) -> Unit,
    private val onDragEnd: () -> Unit
) {
    var draggedKey by mutableStateOf<Any?>(null)
        private set

    var settlingKey by mutableStateOf<Any?>(null)
        private set

    var dragOffset by mutableStateOf(Offset.Zero)
    val settleOffset = Animatable(Offset.Zero, Offset.VectorConverter)

    private val itemPositions = mutableMapOf<Any, Float>()
    private val itemHeights = mutableMapOf<Any, Float>()
    private val keysInOrder = mutableStateListOf<Any>()

    fun updateKeys(keys: List<Any>) {
        if (draggedKey == null && settlingKey == null) {
            if (keysInOrder.toList() != keys) {
                keysInOrder.clear()
                keysInOrder.addAll(keys)
            }
        }
    }

    private fun onDragStart(key: Any) {
        draggedKey = key
        dragOffset = Offset.Zero
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    private fun onDrag(dragAmount: Offset) {
        val key = draggedKey ?: return
        dragOffset += dragAmount
        checkIntersections(key)
    }

    private fun onDragStop() {
        val key = draggedKey ?: return
        settlingKey = key
        draggedKey = null
        val finalOffset = dragOffset
        dragOffset = Offset.Zero
        scope.launch {
            settleOffset.snapTo(finalOffset)
            settleOffset.animateTo(Offset.Zero, spring(stiffness = Spring.StiffnessMediumLow))
            settlingKey = null
            onDragEnd()
        }
    }

    private fun checkIntersections(draggedKey: Any) {
        var draggedIdx = keysInOrder.indexOf(draggedKey)
        if (draggedIdx == -1) return

        var changed = true
        while (changed) {
            changed = false
            val currentY = dragOffset.y
            val draggedItemPos = itemPositions[draggedKey] ?: break
            val draggedItemHeight = itemHeights[draggedKey] ?: 0f
            val draggedMiddle = draggedItemPos + currentY + draggedItemHeight / 2f

            // Check item above
            if (draggedIdx > 0) {
                val prevKey = keysInOrder[draggedIdx - 1]
                val prevPos = itemPositions[prevKey] ?: 0f
                val prevHeight = itemHeights[prevKey] ?: 0f
                if (draggedMiddle < prevPos + prevHeight / 2f) {
                    swap(draggedIdx, draggedIdx - 1, draggedKey, prevKey)
                    draggedIdx--
                    changed = true
                    continue
                }
            }

            // Check item below
            if (draggedIdx < keysInOrder.size - 1) {
                val nextKey = keysInOrder[draggedIdx + 1]
                val nextPos = itemPositions[nextKey] ?: 0f
                val nextHeight = itemHeights[nextKey] ?: 0f
                if (draggedMiddle > nextPos + nextHeight / 2f) {
                    swap(draggedIdx, draggedIdx + 1, draggedKey, nextKey)
                    draggedIdx++
                    changed = true
                    continue
                }
            }
        }
    }

    private fun swap(from: Int, to: Int, keyFrom: Any, keyTo: Any) {
        val draggedPos = itemPositions[keyFrom] ?: return
        val neighborPos = itemPositions[keyTo] ?: return
        val draggedHeight = itemHeights[keyFrom] ?: 0f
        val neighborHeight = itemHeights[keyTo] ?: 0f

        val newDraggedPos: Float
        val newNeighborPos: Float

        if (to > from) { // Moving DOWN
            newDraggedPos = neighborPos + neighborHeight - draggedHeight
            newNeighborPos = draggedPos
        } else { // Moving UP
            newDraggedPos = neighborPos
            newNeighborPos = draggedPos + draggedHeight - neighborHeight
        }

        val delta = newDraggedPos - draggedPos

        itemPositions[keyFrom] = newDraggedPos
        itemPositions[keyTo] = newNeighborPos
        dragOffset = dragOffset.copy(y = dragOffset.y - delta)

        keysInOrder.removeAt(from)
        keysInOrder.add(to, keyFrom)
        
        onMove(from, to)
    }

    fun Modifier.reorderableItem(index: Int, key: Any): Modifier = this
        .onGloballyPositioned { coords ->
            itemPositions[key] = coords.positionInParent().y
            itemHeights[key] = coords.size.height.toFloat()
        }
        .zIndex(if (draggedKey == key || settlingKey == key) 1f else 0f)
        .graphicsLayer {
            if (draggedKey == key) {
                translationY = dragOffset.y
                scaleX = 1.05f
                scaleY = 1.05f
                shadowElevation = 10f
            } else if (settlingKey == key) {
                translationY = settleOffset.value.y
                scaleX = 1.05f
                scaleY = 1.05f
                shadowElevation = 10f
            }
        }
        .pointerInput(key) {
            detectDragGesturesAfterLongPress(
                onDragStart = { onDragStart(key) },
                onDrag = { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount)
                },
                onDragEnd = { onDragStop() },
                onDragCancel = { onDragStop() }
            )
        }
}
