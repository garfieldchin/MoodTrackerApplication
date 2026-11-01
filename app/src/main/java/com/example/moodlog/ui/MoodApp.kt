package com.example.moodlog.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.moodlog.ui.theme.*
import com.example.moodlog.data.LossMode
import com.example.moodlog.data.MoodRepository
import com.example.moodlog.ui.theme.LifeDimColors
import com.example.moodlog.ui.theme.MissColor
import com.example.moodlog.ui.theme.PageBg
import com.example.moodlog.ui.theme.PrimaryPurple
import com.example.moodlog.ui.theme.SoftPurple
import com.example.moodlog.util.logicalTimeString
import com.example.moodlog.util.logicalToday
import com.example.moodlog.util.toTimeString
import java.time.LocalDate

@Composable
fun MoodApp(repo: MoodRepository) {
    val context = LocalContext.current

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var pendingDiaryId by rememberSaveable { mutableLongStateOf(0L) }
    var safetyRecordId by rememberSaveable { mutableLongStateOf(0L) }
    var showSafety by rememberSaveable { mutableStateOf(false) }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showSummary by rememberSaveable { mutableStateOf(false) }
    // 【成为 · 我的感受】那份记录页。它和【思念】那份是同一套，
    // 但底部不带导出（导出只在【我】），间距也收一档。
    var showDimHistory by rememberSaveable { mutableStateOf(false) }
    // 六维正在看哪一天（null = 今天）。从二级页里按「这天的六维」跳过去时用它。
    var dimsDay by rememberSaveable { mutableStateOf<String?>(null) }
    // 总结页可以看今天，也可以往回翻。存字符串，翻页只是换这一行。
    var summaryDay by rememberSaveable { mutableStateOf(logicalToday().toString()) }

    fun openSummary(day: LocalDate = logicalToday()) {
        summaryDay = day.toString()
        showSummary = true
    }

    // 思念 / 情绪 = 接住难受的那一刻。
    //   这一格的名字跟着开关走：开着叫「思念」—— 那是它最初的来处，也是这个 app 的特色，
    //   记的是因失去重要的人而起的那股劲；关掉之后那三条关于 TA 的收走，剩下的是通用情绪，
    //   名字跟着回到「情绪」。名字是给当下这个人的，不是给产品的。
    // 生活 = 身体 / 话事 / 遇见：这一天做过的事。
    // 成为 = 我的感受（六维）+ 我的今天（这一天的一句话，以及这段时间）。
    val tabs = listOf(
        (if (LossMode.on) "思念" else "情绪") to "🌙",
        "生活" to "🌞",
        "成为" to "⭐️",
        "我" to "⚓"
    )
    val tabColors = listOf(MissColor, LifeDimColors[0], PrimaryPurple, SoftPurple)

    // 记下之后的那一层颜色铺在最上面（见 SaveBurst）：它自己不吃点击。
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = PageBg,
            bottomBar = {
                NavigationBar(containerColor = CardBg, tonalElevation = 2.dp) {
                    tabs.forEachIndexed { index, (name, icon) ->
                        val on = tab == index
                        NavigationBarItem(
                            selected = on,
                            onClick = { Haptics.tap(); tab = index },
                            icon = { TabIcon(emoji = icon, dim = !on) },
                            label = { Text(name) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = tabColors[index],
                                selectedTextColor = tabColors[index],
                                indicatorColor = tabColors[index].copy(alpha = 0.14f)
                            )
                        )
                    }
                }
            }
        ) { padding ->
            when (tab) {
                0 -> RecordScreen(
                    repo = repo,
                    padding = padding,
                    onSaved = { id -> pendingDiaryId = id },
                    onSafety = { id ->
                        safetyRecordId = id ?: 0L
                        showSafety = true
                    },
                    onOpenHistory = { showHistory = true }
                )
                1 -> LifeScreen(repo, padding)
                2 -> BecomeScreen(
                    repo = repo,
                    padding = padding,
                    onOpenSummary = { openSummary(it) },
                    viewDay = dimsDay?.let { LocalDate.parse(it) },
                    onViewDay = { dimsDay = it?.toString() },
                    onOpenAll = { showDimHistory = true }
                )
                else -> MeScreen(
                    repo = repo,
                    padding = padding,
                    onOpenSummary = { openSummary() },
                    onOpenSafety = { safetyRecordId = 0L; showSafety = true },
                    onOpenSettings = { showSettings = true }
                )
            }
        }
        SaveBurst()
    }

    // 侧滑 / 返回：先收二级页，再回到第一格。一次只收一层，不会一下退到底。
    BackHandler(
        enabled = showSafety || showHistory || showDimHistory || showSummary ||
            showSettings || tab != 0
    ) {
        when {
            showSafety -> showSafety = false
            showHistory -> showHistory = false
            showDimHistory -> showDimHistory = false
            showSummary -> showSummary = false
            showSettings -> showSettings = false
            else -> tab = 0
        }
    }

    // 保存之后：要不要写点什么。永远可以跳过。
    if (pendingDiaryId != 0L) {
        val record = repo.records.firstOrNull { it.id == pendingDiaryId }
        if (record != null) {
            DiaryDialog(
                title = "记一笔？",
                // 时刻按逻辑日写：凌晨两点的那一笔，这里是 26:xx，不是 02:xx。
                subtitle = "这一笔是 " +
                    record.timestamp.logicalTimeString() +
                    (
                        if (record.timestamp.logicalTimeString() != record.timestamp.toTimeString())
                            "（挂钟 ${record.timestamp.toTimeString()}）"
                        else ""
                        ) +
                    " 记的。想写点什么就写，有没有字都能按保存；不想留这一笔，按左下取消。",
                initial = record.diary,
                // 取消 = 连这一笔一起取消。刚记错了、或者不想留，就从这里退掉。
                cancelLabel = "取消这一笔",
                onCancel = {
                    repo.delete(pendingDiaryId)
                    pendingDiaryId = 0L
                },
                // 点外面/返回只是关掉：这一笔留着，只是不写字。删数据不该由手指一滑造成。
                onDismiss = {
                    pendingDiaryId = 0L
                    // 框关掉之后才闪：这一笔已经落库了，让你看见它落地。
                    SaveFeedback.fire(MissColor, "记下了 ✓")
                },
                onSave = { text ->
                    repo.update(record.copy(diary = text.trim()))
                    pendingDiaryId = 0L
                    SaveFeedback.fire(MissColor, "记下了 ✓")
                }
            )
        } else {
            // 这一笔已经不在库里了（比如从别处删掉）：别追着一条不存在的记录问。
            pendingDiaryId = 0L
        }
    }

    if (showSafety) {
        SafetyScreen(
            repo = repo,
            padding = PaddingValues(0.dp),
            recordId = if (safetyRecordId == 0L) null else safetyRecordId,
            onDone = { showSafety = false }
        )
    }

    if (showHistory) {
        HistoryScreen(
            repo = repo,
            padding = PaddingValues(0.dp),
            onBack = { showHistory = false }
        )
    }

    if (showDimHistory) {
        HistoryScreen(
            repo = repo,
            padding = PaddingValues(0.dp),
            onBack = { showDimHistory = false },
            showCsv = false,
            compact = true,
            dimsAnswered = { day -> repo.dimsOf(day) != null },
            onOpenDims = { day ->
                dimsDay = day.toString()
                showDimHistory = false
                tab = 2
            }
        )
    }

    if (showSummary) {
        SummaryScreen(
            repo = repo,
            padding = PaddingValues(0.dp),
            day = LocalDate.parse(summaryDay),
            onDayChange = { summaryDay = it.toString() },
            onBack = { showSummary = false }
        )
    }

    if (showSettings) {
        SettingsPage(
            repo = repo,
            padding = PaddingValues(0.dp),
            onBack = { showSettings = false }
        )
    }
}

/** 底栏那个图标。没选中的那一格压低一点透明度 —— 选没选中一眼就分得出来。 */
@Composable
private fun TabIcon(emoji: String, dim: Boolean) {
    Text(
        text = emoji,
        fontSize = 18.sp,
        textAlign = TextAlign.Center,
        color = Color.Unspecified,
        modifier = Modifier.graphicsLayer { alpha = if (dim) 0.45f else 1f }
    )
}
