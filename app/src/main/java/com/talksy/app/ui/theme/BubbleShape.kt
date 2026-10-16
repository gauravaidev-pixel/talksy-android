package com.talksy.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * A rounded message bubble with a small tail on the bottom corner (Telegram look).
 * When [tail] is true the shape reserves a little space on the bottom corner side;
 * callers should keep the bottom of their content clear (see ChatScreen padding).
 */
class BubbleShape(
    private val isMine: Boolean,
    private val tail: Boolean,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.width
        val h = size.height
        val r = with(density) { 16.dp.toPx() }
        val rs = with(density) { 5.dp.toPx() }
        val tw = with(density) { 9.dp.toPx() }
        val th = with(density) { 9.dp.toPx() }

        val left = if (tail && !isMine) tw else 0f
        val right = if (tail && isMine) w - tw else w
        val bottom = if (tail) h - th else h

        val bodyShape = RoundedCornerShape(
            topStart = 16.dp, topEnd = 16.dp,
            bottomStart = if (isMine) 16.dp else 5.dp,
            bottomEnd = if (isMine) 5.dp else 16.dp,
        )
        val bodySize = Size((right - left).coerceAtLeast(1f), bottom.coerceAtLeast(1f))
        val bodyLocal = Path().apply {
            addOutline(bodyShape.createOutline(bodySize, layoutDirection, density))
        }
        val body = Path().apply { addPath(bodyLocal, Offset(left, 0f)) }

        if (!tail) return Outline.Generic(body)

        val t = Path().apply {
            if (isMine) {
                moveTo(right, bottom)
                cubicTo(w - tw * 0.2f, h - th * 0.4f, w - tw * 0.5f, h, w, h)
                lineTo(right - tw, h)
                lineTo(right - tw, bottom)
            } else {
                moveTo(left, bottom)
                cubicTo(tw * 0.2f, h - th * 0.4f, tw * 0.5f, h, 0f, h)
                lineTo(left + tw, h)
                lineTo(left + tw, bottom)
            }
            close()
        }
        val out = Path().apply { op(body, t, PathOperation.Union) }
        return Outline.Generic(out)
    }
}

fun bubbleShape(isMine: Boolean, tail: Boolean): Shape = BubbleShape(isMine, tail)
