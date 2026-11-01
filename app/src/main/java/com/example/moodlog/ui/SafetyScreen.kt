package com.example.moodlog.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.moodlog.ui.theme.*
import com.example.moodlog.data.MoodRepository
import com.example.moodlog.ui.theme.DangerRed
import com.example.moodlog.ui.theme.PageBg
import com.example.moodlog.ui.theme.SafeBg
import com.example.moodlog.ui.theme.SafeBorder
import com.example.moodlog.ui.theme.SafeTitle
import com.example.moodlog.ui.theme.TextGray

private val BODY_ACTIONS = listOf(
    "冷水冲手腕，或者用冷水洗脸",
    "吸气 4 秒 · 停 4 秒 · 呼气 6 秒，做六个来回",
    "站起来走动五分钟，换个房间也行",
    "喝一杯水，或者吃点东西",
    "把该吃的药吃了"
)

/**
 * 屏 6 · 安全底座。
 * 它不是一次记录——进来什么都不用填，走完「我安全了」才会往那一条上补一个时间戳。
 */
@Composable
fun SafetyScreen(
    repo: MoodRepository,
    padding: PaddingValues,
    recordId: Long?,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    var step by rememberSaveable { mutableIntStateOf(0) }
    var startedAt by rememberSaveable { mutableLongStateOf(0L) }
    if (startedAt == 0L) startedAt = System.currentTimeMillis()

    val contacts = repo.contacts

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBg)
            .blockClicks()
            .padding(padding)
            .verticalScroll(rememberScrollState())
    ) {
        // 让开状态栏
        Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "安全底座",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onDone) { Text("先出去", color = TextGray) }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "你进来了，这就够了。\n这里不用记录任何东西，跟着往下走就行。",
            style = MaterialTheme.typography.bodyMedium,
            color = TextGray,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(14.dp))

        StepCard(
            index = 1,
            title = "先做一件身体上的事",
            body = "恐慌是身体的事，先让身体下来，脑子才有地方站。",
            state = stepState(step, 0),
            onEnter = { step = 1 }
        ) {
            Column {
                BODY_ACTIONS.forEach { action ->
                    Row(
                        modifier = Modifier.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(SafeTitle)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            action,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        StepCard(
            index = 2,
            title = "离开这里",
            body = "不用走远。离开那个让你更难受的位置、那个房间、那个对话框就够了。",
            state = stepState(step, 1),
            onEnter = { step = 2 }
        ) {
            Text(
                "门、窗、楼道、楼下，哪里都行。手机可以先放桌上。",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        StepCard(
            index = 3,
            title = "给一个人打电话",
            body = "不用解释来龙去脉，也不用说得漂亮。听到一个人的声音就够了。",
            state = stepState(step, 2),
            onEnter = { step = 3 }
        ) {
            if (contacts.isEmpty() || contacts.all { it.phone.isBlank() }) {
                Text(
                    "还没有填联系人。可以先在「我」里存一个号码——哪怕是家人、朋友、同事。",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGray
                )
            } else {
                contacts.filter { it.phone.isNotBlank() }.forEach { c ->
                    Surface(
                        onClick = { dial(context, c.phone) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = CardBg,
                        border = BorderStroke(1.dp, SafeBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    c.name.ifBlank { "联系人 ${c.slot}" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(c.phone, style = MaterialTheme.typography.bodySmall, color = TextGray)
                            }
                            Text("拨打 →", color = SafeTitle, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "没人接也没关系，这不代表什么。",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGray
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardBg),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "我安全了",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "不是「完全不难受了」才算。能松开一点，就算。",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGray
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                            Haptics.tap()
                        val id = recordId
                        if (id != null && id != 0L) repo.markSafe(id)
                        onDone()
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GainGreen)
                ) {
                    Text("我安全了", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        Text(
            "如果现在有伤害自己或别人的冲动，请联系当地急救或报警电话。" +
                "这个 app 不预置号码——你比我更清楚该打给谁。",
            style = MaterialTheme.typography.bodySmall,
            color = DangerRed,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

private fun stepState(current: Int, mine: Int): Int = when {
    current > mine -> 2      // 已完成
    current == mine -> 1     // 当前
    else -> 0                // 还没到
}

@Composable
private fun StepCard(
    index: Int,
    title: String,
    body: String,
    state: Int,
    onEnter: () -> Unit,
    content: @Composable () -> Unit
) {
    val active = state == 1
    val done = state == 2
    val bg = if (active) SafeBg else CardBg
    val border = if (active || done) SafeBorder else CardBorder

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bg),
        border = BorderStroke(1.dp, border),
        elevation = CardDefaults.cardElevation(defaultElevation = if (active) 1.dp else 0.dp)
    ) {
        Column(modifier = Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(if (done) GainGreen else if (active) SafeTitle else CardBorder),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (done) "✓" else index.toString(),
                        color = CardBg,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(body, style = MaterialTheme.typography.bodySmall, color = TextGray)

            if (active) {
                Spacer(modifier = Modifier.height(10.dp))
                content()
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        onClick = onEnter,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = SafeTitle,
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 11.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                when (index) {
                                    1 -> "做了一件"
                                    2 -> "我离开了这里"
                                    else -> "打完了，或者先跳过"
                                },
                                color = CardBg,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            } else if (!done) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "上一步走完再说。",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextGray
                )
            }
        }
    }
}

private fun dial(context: Context, phone: String) {
    runCatching {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(phone.trim())))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
