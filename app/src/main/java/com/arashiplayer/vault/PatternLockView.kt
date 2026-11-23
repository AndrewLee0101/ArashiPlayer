package com.arashiplayer.vault

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.arashiplayer.ui.theme.LocalArashiTokens
import kotlin.math.hypot

@Composable
fun PatternLockGrid(
    modifier: Modifier = Modifier,
    onPatternComplete: (List<Int>) -> Unit
) {
    val tokens = LocalArashiTokens.current
    val selected = remember { mutableStateListOf<Int>() }
    var currentTouch by remember { mutableStateOf<Offset?>(null) }

    Canvas(
        modifier = modifier
            .size(250.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        selected.clear()
                        currentTouch = offset
                        findHitNode(offset, size.width.toFloat(), size.height.toFloat())?.let { selected.add(it) }
                    },
                    onDrag = { change, _ ->
                        currentTouch = change.position
                        findHitNode(change.position, size.width.toFloat(), size.height.toFloat())?.let { idx ->
                            if (idx !in selected) selected.add(idx)
                        }
                    },
                    onDragEnd = {
                        currentTouch = null
                        if (selected.size >= 4) onPatternComplete(selected.toList())
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
        for (i in 0 until selected.size - 1) {
            drawLine(tokens.accent.copy(alpha = 0.85f), centers[selected[i]], centers[selected[i + 1]], strokeWidth = 8f, cap = StrokeCap.Round)
        }
        if (selected.isNotEmpty() && currentTouch != null) {
            drawLine(tokens.accent.copy(alpha = 0.45f), centers[selected.last()], currentTouch!!, strokeWidth = 6f, cap = StrokeCap.Round)
        }
        centers.forEachIndexed { idx, center ->
            val isSelected = idx in selected
            drawCircle(if (isSelected) tokens.accent.copy(alpha = 0.24f) else tokens.textPrimary.copy(alpha = 0.10f), 28f, center)
            drawCircle(if (isSelected) tokens.accent else tokens.textPrimary.copy(alpha = 0.45f), if (isSelected) 12f else 8f, center)
        }
    }
}

private fun getNodeCenters(width: Float, height: Float): List<Offset> {
    val stepX = width / 3f
    val stepY = height / 3f
    val list = mutableListOf<Offset>()
    for (row in 0..2) for (col in 0..2) list.add(Offset(stepX * col + stepX / 2f, stepY * row + stepY / 2f))
    return list
}

private fun findHitNode(touch: Offset, width: Float, height: Float): Int? {
    getNodeCenters(width, height).forEachIndexed { idx, c -> if (hypot(touch.x - c.x, touch.y - c.y) <= 44f) return idx }
    return null
}
