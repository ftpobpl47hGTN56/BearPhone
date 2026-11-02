package com.bearphone.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardDoubleArrowDown
import androidx.compose.material.icons.filled.KeyboardDoubleArrowUp
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bearphone.app.ui.theme.bearFrame
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Сколько мс полоса и кнопки остаются видимыми после остановки прокрутки. */
private const val HIDE_DELAY_MS = 2000L

/** true, пока идёт прокрутка, и ещё [HIDE_DELAY_MS] после неё. */
@Composable
private fun rememberRecentlyScrolled(scrolling: Boolean): Boolean {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(scrolling) {
        if (scrolling) visible = true
        else { delay(HIDE_DELAY_MS); visible = false }
    }
    return visible
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawThumb(
    color: androidx.compose.ui.graphics.Color, top: Float, height: Float, width: Dp,
) {
    val w = width.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(size.width - w - 3.dp.toPx(), top),
        size = Size(w, height),
        cornerRadius = CornerRadius(w / 2, w / 2),
    )
}

/**
 * Полоса прокрутки цвета акцента для LazyColumn.
 * Появляется при свайпе и плавно гаснет после остановки.
 */
@Composable
fun Modifier.lazyScrollbar(state: LazyListState, width: Dp = 4.dp): Modifier {
    val color = MaterialTheme.colorScheme.primary
    val show = rememberRecentlyScrolled(state.isScrollInProgress)
    val alpha by animateFloatAsState(if (show) 1f else 0f, tween(if (show) 120 else 600), label = "scrollbar")

    return drawWithContent {
        drawContent()
        if (alpha <= 0f) return@drawWithContent
        val info = state.layoutInfo
        val total = info.totalItemsCount
        val visible = info.visibleItemsInfo
        if (total == 0 || visible.isEmpty() || visible.size >= total) return@drawWithContent

        // Высота элементов разная — оцениваем по среднему видимых
        val avg = visible.sumOf { it.size }.toFloat() / visible.size
        val viewport = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
        val content = avg * total
        if (content <= viewport) return@drawWithContent
        val first = visible.first()
        val scrolled = first.index * avg - first.offset
        val thumb = (viewport * viewport / content).coerceAtLeast(48.dp.toPx()).coerceAtMost(size.height)
        val fraction = (scrolled / (content - viewport)).coerceIn(0f, 1f)
        drawThumb(color.copy(alpha = 0.85f * alpha), fraction * (size.height - thumb), thumb, width)
    }
}

/** То же для обычной Column с verticalScroll (экран настроек). */
@Composable
fun Modifier.columnScrollbar(state: ScrollState, width: Dp = 4.dp): Modifier {
    val color = MaterialTheme.colorScheme.primary
    val show = rememberRecentlyScrolled(state.isScrollInProgress)
    val alpha by animateFloatAsState(if (show) 1f else 0f, tween(if (show) 120 else 600), label = "scrollbar")

    return drawWithContent {
        drawContent()
        if (alpha <= 0f || state.maxValue == 0 || state.maxValue == Int.MAX_VALUE) return@drawWithContent
        val viewport = size.height
        val content = viewport + state.maxValue
        val thumb = (viewport * viewport / content).coerceAtLeast(48.dp.toPx())
        val fraction = state.value.toFloat() / state.maxValue
        // У Column со скроллом drawWithContent рисуется в координатах видимой области
        drawThumb(color.copy(alpha = 0.85f * alpha), fraction * (viewport - thumb), thumb, width)
    }
}

/**
 * Кнопки "в начало" / "в конец" справа по центру списка.
 * Видны, пока идёт прокрутка (и чуть после), и только для длинных списков.
 */
@Composable
fun BoxScope.ScrollJumpButtons(state: LazyListState, minItems: Int = 15) {
    val scope = rememberCoroutineScope()
    val show = rememberRecentlyScrolled(state.isScrollInProgress)
    val total by remember { derivedStateOf { state.layoutInfo.totalItemsCount } }
    val canUp by remember { derivedStateOf { state.canScrollBackward } }
    val canDown by remember { derivedStateOf { state.canScrollForward } }

    JumpColumn(
        visible = show && total >= minItems,
        canUp = canUp,
        canDown = canDown,
        onUp = { scope.launch { state.scrollToItem(0) } },
        onDown = { scope.launch { state.scrollToItem((total - 1).coerceAtLeast(0)) } },
    )
}

@Composable
fun BoxScope.ScrollJumpButtons(state: ScrollState) {
    val scope = rememberCoroutineScope()
    val show = rememberRecentlyScrolled(state.isScrollInProgress)
    JumpColumn(
        visible = show && state.maxValue > 0,
        canUp = state.value > 0,
        canDown = state.value < state.maxValue,
        onUp = { scope.launch { state.scrollTo(0) } },
        onDown = { scope.launch { state.scrollTo(state.maxValue) } },
    )
}

@Composable
private fun BoxScope.JumpColumn(
    visible: Boolean, canUp: Boolean, canDown: Boolean, onUp: () -> Unit, onDown: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(150)),
        exit = fadeOut(tween(400)),
        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.primary,
            )
            FilledTonalIconButton(
                onClick = onUp, enabled = canUp, colors = colors,
                modifier = Modifier.size(40.dp).bearFrame(CircleShape)
            ) { Icon(Icons.Filled.KeyboardDoubleArrowUp, "В начало") }
            FilledTonalIconButton(
                onClick = onDown, enabled = canDown, colors = colors,
                modifier = Modifier.size(40.dp).bearFrame(CircleShape)
            ) { Icon(Icons.Filled.KeyboardDoubleArrowDown, "В конец") }
        }
    }
}
