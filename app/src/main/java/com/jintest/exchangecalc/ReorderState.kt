package com.jintest.exchangecalc

import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset

/**
 * 카드를 길게 눌러 끌어서 순서를 바꾸는 상태.
 * 끌리는 카드의 중심이 다른 카드 위로 가면 자리를 바꾸고, 화면 아래 삭제 영역에 놓으면 지운다.
 * 키가 [MOVABLE]로 시작하는 카드만 움직이거나 자리를 내줄 수 있다 (거주국 카드, ＋ 카드는 고정).
 */
class ReorderState(val gridState: LazyGridState) {

    var draggingKey by mutableStateOf<String?>(null)
        private set
    var overTrash by mutableStateOf(false)
        private set

    /** 삭제 영역의 위쪽 경계 (그리드 기준 y 픽셀). 화면에서 정해 준다. */
    var trashTop = Float.MAX_VALUE

    private var delta by mutableStateOf(Offset.Zero)
    private var startOffset = Offset.Zero
    private var startPointer = Offset.Zero

    private fun info(key: Any): LazyGridItemInfo? =
        gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }

    private val LazyGridItemInfo.topLeft get() = Offset(offset.x.toFloat(), offset.y.toFloat())

    /** 길게 누른 위치(그리드 기준)의 카드를 집는다. 움직일 수 없는 카드면 false. */
    fun start(pointer: Offset): Boolean {
        val item = gridState.layoutInfo.visibleItemsInfo.firstOrNull {
            (it.key as? String)?.startsWith(MOVABLE) == true &&
                pointer.x >= it.offset.x && pointer.x <= it.offset.x + it.size.width &&
                pointer.y >= it.offset.y && pointer.y <= it.offset.y + it.size.height
        } ?: return false
        startOffset = item.topLeft
        startPointer = pointer
        delta = Offset.Zero
        overTrash = false
        draggingKey = item.key as String
        return true
    }

    /** 끌리는 카드를 손가락 위치에 그리기 위한 이동량 (현재 자리 기준). */
    fun translation(key: String): Offset {
        val item = info(key) ?: return Offset.Zero
        return startOffset + delta - item.topLeft
    }

    fun drag(amount: Offset, onMove: (from: String, to: String) -> Unit) {
        val key = draggingKey ?: return
        delta += amount
        val item = info(key) ?: return
        val center = startOffset + delta + Offset(item.size.width / 2f, item.size.height / 2f)
        val target = gridState.layoutInfo.visibleItemsInfo.firstOrNull {
            val k = it.key as? String ?: return@firstOrNull false
            k != key && k.startsWith(MOVABLE) &&
                center.x >= it.offset.x && center.x <= it.offset.x + it.size.width &&
                center.y >= it.offset.y && center.y <= it.offset.y + it.size.height
        }
        if (target != null) onMove(key.removePrefix(MOVABLE), (target.key as String).removePrefix(MOVABLE))
        overTrash = startPointer.y + delta.y > trashTop
    }

    fun end(onRemove: (code: String) -> Unit) {
        val key = draggingKey
        if (key != null && overTrash) onRemove(key.removePrefix(MOVABLE))
        cancel()
    }

    fun cancel() {
        draggingKey = null
        overTrash = false
        delta = Offset.Zero
    }

    companion object {
        const val MOVABLE = "c:"
    }
}
