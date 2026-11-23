package com.arashiplayer.vault

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.arashiplayer.ui.theme.GlassCyan
import kotlin.math.hypot

/**
 * 液态玻璃九宫格手势解锁组件
 */
@Composable
fun PatternLockGrid(
    modifier: Modifier = Modifier,
    onPatternComplete: (List<Int>) -> Unit
) {
    val selected = remember { mutableStateListOf<Int>() }
    var currentTouch by remember { mutableStateOf<Offset?>(null) }

    Canvas(
        modifier = modifier
            .size(260.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        selected.clear()
                        currentTouch = offset
                        findHitNode(offset, size.width.toFloat(), size.height.toFloat())?.let {
                            selected.add(it)
                        }
                    },
                    onDrag = { change, _ ->
                        currentTouch = change.position
                        findHitNode(change.position, size.width.toFloat(), size.height.toFloat())?.let { idx ->
                            if (idx !in selected) selected.add(idx)
                        }
                    },
                    onDragEnd = {
                        currentTouch = null
                        if (selected.size >= 4) {
                            onPatternComplete(selected.toList())
                        }
                        selected.clear()
                    },
                    onDragCancel = {
                        currentTouch = null
                        selected.clear()
                    }
                )
            }
    ) {
        val centers = getNodeCenters(size.width, size.height)

        // 连线
        for (i in 0 until selected.size - 1) {
            drawLine(
                color = GlassCyan.copy(alpha = 0.85f),
                start = centers[selected[i]],
                end = centers[selected[i + 1]],
                strokeWidth = 8f,
                cap = StrokeCap.Round
            )
        }
        if (selected.isNotEmpty() && currentTouch != null) {
            drawLine(
                color = GlassCyan.copy(alpha = 0.45f),
                start = centers[selected.last()],
                end = currentTouch!!,
                strokeWidth = 6f,
                cap = StrokeCap.Round
            )
        }

        // 九宫格节点
        centers.forEachIndexed { idx, center ->
            val isSelected = idx in selected
            drawCircle(
                color = if (isSelected) GlassCyan.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.10f),
                radius = 30f,
                center = center
            )
            drawCircle(
                color = if (isSelected) GlassCyan else Color.White.copy(alpha = 0.55f),
                radius = if (isSelected) 12f else 8f,
                center = center
            )
        }
    }
}

private fun getNodeCenters(width: Float, height: Float): List<Offset> {
    val stepX = width / 3f
    val stepY = height / 3f
    val list = mutableListOf<Offset>()
    for (row in 0..2) {
        for (col in 0..2) {
            list.add(Offset(stepX * col + stepX / 2f, stepY * row + stepY / 2f))
        }
    }
    return list
}

private fun findHitNode(touch: Offset, width: Float, height: Float): Int? {
    val centers = getNodeCenters(width, height)
    centers.forEachIndexed { idx, c ->
        if (hypot(touch.x - c.x, touch.y - c.y) <= 44f) return idx
    }
    return null
}
