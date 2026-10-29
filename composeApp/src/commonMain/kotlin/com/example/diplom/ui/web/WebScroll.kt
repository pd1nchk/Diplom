package com.example.diplom.ui.web

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.diplom.ui.platform.isWebComposeClient

/**
 * Контейнер с рабочей вертикальной прокруткой на Wasm/JS.
 * [heightIn](min = maxHeight) задаёт границу viewport — иначе колесо мыши не работает.
 */
@Composable
fun WebScrollHost(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (!isWebComposeClient()) {
        Box(modifier) { content() }
        return
    }
    val scrollState = rememberScrollState()
    BoxWithConstraints(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = maxHeight)
                .verticalScroll(scrollState),
        ) {
            content()
        }
    }
}

@Composable
fun WebScrollColumn(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!isWebComposeClient()) {
        Column(modifier, content = content)
        return
    }
    val scrollState = rememberScrollState()
    BoxWithConstraints(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = maxHeight)
                .verticalScroll(scrollState),
            content = content,
        )
    }
}

/**
 * Прокрутка вкладок на Android/iOS. На вебе скролл даёт [WebScrollHost] у родителя.
 */
@Composable
fun Modifier.pageVerticalScroll(): Modifier {
    if (isWebComposeClient()) return this
    val scrollState = rememberScrollState()
    return this
        .fillMaxWidth()
        .verticalScroll(scrollState)
}
