package com.example.moodlog.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.moodlog.ui.theme.PrimaryPurple
import com.example.moodlog.ui.theme.TextGray

/**
 * 三个二级页（设置 / 全部记录 / 今天的总结）共用的顶栏。
 *
 * 以前各写各的，于是【我】页叠了两份状态栏留白、设置页一份都不够，
 * 「‹ 返回」直接顶到左上角那个时间下面。现在只有这一处定义间距：
 *
 *   状态栏 → 12dp 呼吸 → 44dp 高的顶栏行（返回键垂直居中在里头，手指好按）→ 大标题。
 */
@Composable
fun SubPageTopBar(
    title: String,
    hint: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .tap(onBack)
                    .padding(end = 18.dp, top = 10.dp, bottom = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    "‹ 返回",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PrimaryPurple
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            if (hint.isNotBlank()) {
                Text(
                    hint,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextGray
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(14.dp))
    }
}
