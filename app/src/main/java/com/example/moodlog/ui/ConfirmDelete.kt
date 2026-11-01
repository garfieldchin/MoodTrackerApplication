package com.example.moodlog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.moodlog.ui.theme.*

/**
 * 统一的删除确认。
 *
 * 只有"删了找不回来"的东西才走这里：情绪记录（连带那天写的字）、我话事一条、
 * 我遇见一笔、饮食一顿、一次体重、紧急联系人。
 * 标签不走 —— 它删了还能加回来，为一件事能反悔的东西再问一遍，只是添堵。
 *
 * 弹窗里一定带着被删内容的那一行预览：看不见内容的确认，等于闭着眼签字。
 * 也不做「下次不再提示」—— 这个 app 里没有任何一处删除是高频到需要免确认的。
 */
@Composable
fun ConfirmDeleteDialog(
    preview: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    title: String = "删掉这一条？",
    tail: String = "删了就找不回来。"
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(title, fontWeight = FontWeight.SemiBold, color = SafeTitle)
        },
        text = {
            Column {
                if (preview.isNotBlank()) {
                    Text(
                        preview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = SubText,
                        lineHeight = MaterialTheme.typography.bodyMedium.lineHeight,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ChipBg, RoundedCornerShape(10.dp))
                            .padding(horizontal = 11.dp, vertical = 9.dp)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    tail,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGray
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("删掉", color = DangerRed, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("不删") }
        }
    )
}
