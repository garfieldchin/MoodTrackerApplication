package com.example.moodlog.ui

import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 键盘顶上来的时候，把正在填的那个框带进视野。
 *
 * 为什么要在弹出键盘之后等一下：安卓先把窗口压矮、再把输入法推上来，这两步之间有一小段
 * 谁都不知道最终高度的时间。立刻滚动等于对着旧的高度算了一遍，键盘真正到位时又把它盖住了。
 * 等它落定再算一次，位置才是对的。
 *
 * 只关心「得到焦点」这一个时刻：打字过程中不再动，不然每敲一个字页面抖一下。
 */
@Composable
fun Modifier.scrollIntoViewOnFocus(): Modifier {
    val requester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    return this
        .bringIntoViewRequester(requester)
        .onFocusEvent { state ->
            if (state.isFocused) {
                scope.launch {
                    delay(300)
                    runCatching { requester.bringIntoView() }
                }
            }
        }
}
