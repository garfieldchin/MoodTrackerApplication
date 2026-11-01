package com.example.moodlog.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.moodlog.ui.theme.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import com.example.moodlog.data.ConfirmLabel
import com.example.moodlog.data.EMOTION_FIELDS
import com.example.moodlog.data.IMPULSE_LABELS
import com.example.moodlog.data.LossMode
import com.example.moodlog.data.LossPrefs
import com.example.moodlog.data.EmotionSet
import com.example.moodlog.data.MoodRecord
import com.example.moodlog.data.MoodRepository
import com.example.moodlog.data.SafetyPrefs
import com.example.moodlog.data.UNANSWERED
import com.example.moodlog.ui.theme.CareBg
import com.example.moodlog.ui.theme.CareBorder
import com.example.moodlog.ui.theme.CareTitle
import com.example.moodlog.ui.theme.ConfirmColor
import com.example.moodlog.ui.theme.ContactColor
import com.example.moodlog.ui.theme.EmotionColors
import com.example.moodlog.ui.theme.MissColor
import com.example.moodlog.ui.theme.PanicColor
import com.example.moodlog.ui.theme.PrimaryPurple
import com.example.moodlog.ui.theme.SafeBg
import com.example.moodlog.ui.theme.SafeBorder
import com.example.moodlog.ui.theme.SafeTitle
import com.example.moodlog.ui.theme.TextGray
import com.example.moodlog.util.DATE_CN_FMT
import com.example.moodlog.util.DAY_START_HOUR
import com.example.moodlog.util.TIME_FMT
import com.example.moodlog.util.logicalDate
import com.example.moodlog.util.logicalTimeString
import com.example.moodlog.util.logicalToday
import com.example.moodlog.util.toLocalDateTime
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import java.time.LocalDate

private val PAGE_TABS = listOf("此刻", "趋势")

/** 与数据层 UNANSWERED 一个意思，只是滑块状态是 Float。 */
private const val UNANSWERED_F = -1f

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecordScreen(
    repo: MoodRepository,
    padding: PaddingValues,
    onSaved: (Long) -> Unit,
    onSafety: (Long?) -> Unit,
    onOpenHistory: () -> Unit
) {
    val pager = rememberPagerState(pageCount = { PAGE_TABS.size })
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
        SegmentedBar(
            items = PAGE_TABS,
            selected = pager.currentPage,
            onSelect = { scope.launch { pager.animateScrollToPage(it) } }
        )
        // 横向内容区可以左右滑动；顶上的胶囊和滑动是联动的，两条路都通。
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            if (page == 0) {
                RecordForm(
                    repo = repo,
                    onSaved = onSaved,
                    onSafety = onSafety
                )
            } else {
                OverviewScreen(
                    repo = repo,
                    padding = PaddingValues(0.dp),
                    onOpenHistory = onOpenHistory
                )
            }
        }
    }
}

@Composable
fun SegmentedBar(items: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 8.dp)
            .background(ChipBg, RoundedCornerShape(12.dp))
            .padding(4.dp)
    ) {
        items.forEachIndexed { index, name ->
            SegmentedItem(
                name = name,
                on = index == selected,
                onClick = { onSelect(index) }
            )
        }
    }
}

/** 胶囊里的一个格子。按下去亮一下，看得见这一下按到了。 */
@Composable
private fun RowScope.SegmentedItem(name: String, on: Boolean, onClick: () -> Unit) {
    val pulse = rememberPulse()
    val lit = pulse.alpha()
    Box(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(9.dp))
            .background(
                blend(
                    if (on) CardBg else Color.Transparent,
                    PrimaryPurple,
                    0.22f * lit
                )
            )
            .tap { pulse.bump(); onClick() }
            .padding(vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
            color = if (on) PrimaryPurple else TextGray
        )
    }
}

@Suppress("LongMethod")
@Composable
private fun RecordForm(
    repo: MoodRepository,
    onSaved: (Long) -> Unit,
    onSafety: (Long?) -> Unit
) {
    val context = LocalContext.current
    // 四条一开始都是「未答」（-1）。以前是 5/5/2/2 —— 那不是空白，是四个写好的答案，
    // 进去就已经替你说了一句「你现在中度想TA、中度慌」，之后每次拖都是在那个基础上改。
    var miss by rememberSaveable { mutableFloatStateOf(UNANSWERED_F) }
    var contact by rememberSaveable { mutableFloatStateOf(UNANSWERED_F) }
    var confirm by rememberSaveable { mutableFloatStateOf(UNANSWERED_F) }
    var panic by rememberSaveable { mutableFloatStateOf(UNANSWERED_F) }

    // 六支一开始全是「未答」。它们不是 0 分 ——
    // 0 分是「这次一点都不愤怒」那样一句很实在的话，没碰过就什么都不是。
    var eAbandoned by rememberSaveable { mutableFloatStateOf(UNANSWERED_F) }
    var eAnger by rememberSaveable { mutableFloatStateOf(UNANSWERED_F) }
    var eShame by rememberSaveable { mutableFloatStateOf(UNANSWERED_F) }
    var eFear by rememberSaveable { mutableFloatStateOf(UNANSWERED_F) }
    var eLonely by rememberSaveable { mutableFloatStateOf(UNANSWERED_F) }
    var eResent by rememberSaveable { mutableFloatStateOf(UNANSWERED_F) }

    var impulse by rememberSaveable { mutableIntStateOf(0) }
    var hint by rememberSaveable { mutableStateOf("") }
    var newEmotion by remember { mutableStateOf("") }
    var addingEmotion by remember { mutableStateOf(false) }
    // 第三条改名字（默认「想确认」）。
    var renaming by remember { mutableStateOf(false) }

    // 「先记下这一刻」按住时缩一点：这一下是按到了还是没按到，眼睛也要看得见。
    val saveInteraction = remember { MutableInteractionSource() }
    val savePressed by saveInteraction.collectIsPressedAsState()

    /**
     * 保存之后才问「后来怎么样了」。pendingId 挂着的就是那一条。
     * 顺序是死的：先写库 → 先弹日记 → 最后才在最底下问三态。
     * 不能反过来问 —— 反过来它会变成「保存之前的关卡」。
     */
    var pendingId by rememberSaveable { mutableLongStateOf(0L) }
    var pendingAt by rememberSaveable { mutableLongStateOf(0L) }

    // 这一波是什么时候开始的：恐慌第一次上到 5 分的那一刻。
    // 打开这一页的时刻会被钉住 —— 万一你在这中间跨过早上六点，这一笔不会跟着切到前一天。
    var openedAt by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }
    var episodeStart by rememberSaveable { mutableLongStateOf(0L) }

    val customTags = repo.emotionTags
    val customValues = remember { mutableStateMapOf<String, Float>() }

    val openedDT = openedAt.toLocalDateTime()
    // 没碰过恐慌那一条，就是 UNANSWERED —— 于是二级情绪、冲动等级都不会展开。
    // 这是有意的：慌不慌由你说，不该由一个写死的默认值替你说。
    val panicLevel = if (panic < 0f) UNANSWERED else panic.roundToInt()
    // 收起来的那三条不算「没答」—— 页面上根本没有它们，不该回头说你还欠三项。
    val shownFields = if (LossMode.on) listOf(miss, contact, confirm, panic) else listOf(panic)
    val unansweredCount = shownFields.count { it < 0f }
    // 两档门槛：5 分开始问「这一波里面是什么」；8 分才追加冲动等级、身体清单，以及存完那句「后来怎么样了」。
    // 真到 8 分的时候人已经在最难受的地方，不该再摊一整屏给他填；
    // 而一天里大量的中度慌（5–7 分）以前什么都没被接住 —— 它恰恰是还说得清楚的那种。
    val branchPanic = panicLevel >= 5
    val highPanic = panicLevel >= 8

    LaunchedEffect(branchPanic) {
        if (branchPanic && episodeStart == 0L) episodeStart = System.currentTimeMillis()
        if (!branchPanic) episodeStart = 0L
    }

    val emotionValues = listOf(eAbandoned, eAnger, eShame, eFear, eLonely, eResent)
    val emotionSetters: List<(Float) -> Unit> = listOf(
        { eAbandoned = it }, { eAnger = it }, { eShame = it },
        { eFear = it }, { eLonely = it }, { eResent = it }
    )
    // 每支配一个「退回未答」。手滑碰到的一下得有得救，不然就变成一句「这次是 0 分」。
    val emotionClearers: List<() -> Unit> = listOf(
        { eAbandoned = UNANSWERED_F }, { eAnger = UNANSWERED_F }, { eShame = UNANSWERED_F },
        { eFear = UNANSWERED_F }, { eLonely = UNANSWERED_F }, { eResent = UNANSWERED_F }
    )

    fun reset() {
        miss = UNANSWERED_F; contact = UNANSWERED_F
        confirm = UNANSWERED_F; panic = UNANSWERED_F
        eAbandoned = UNANSWERED_F; eAnger = UNANSWERED_F; eShame = UNANSWERED_F
        eFear = UNANSWERED_F; eLonely = UNANSWERED_F; eResent = UNANSWERED_F
        impulse = 0
        customValues.clear()
        episodeStart = 0L
        openedAt = System.currentTimeMillis()
    }

    /**
     * 收掉那张「后来怎么样了」。
     * 日记在按按钮的那一刻就已经弹过了 —— 这里再弹一次的话，同一次记录会问两遍。
     */
    fun closePending() {
        pendingId = 0L
        pendingAt = 0L
        reset()
    }

    fun answer(outcome: Int) {
        val id = pendingId
        repo.markOutcome(id, outcome)
        val minutes = repo.records.firstOrNull { it.id == id }?.toOutcomeMinutes
        hint = when (outcome) {
            2 -> if (minutes == null || minutes < 1) "记下了，不到 1 分钟你就站回来了。"
            else "从这次慌起来到站稳，这次用了 $minutes 分钟。"
            1 -> "记下了。撑着也是一种站稳。"
            else -> ""
        }
        if (outcome == 3) {
            closePending()
            onSafety(id)
        } else {
            closePending()
        }
    }

    // 弹窗里按了「取消这一笔」——那条记录已经从库里删掉了。
    // 挂着的「后来怎么样了」要跟着收起来，不能追着一条已经不存在的记录问。
    LaunchedEffect(pendingId, repo.records.size) {
        if (pendingId != 0L && repo.records.none { it.id == pendingId }) {
            pendingId = 0L
            pendingAt = 0L
            hint = ""
            reset()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 6.dp)
    ) {
        Text(
            text = "${logicalToday().format(DATE_CN_FMT)} ${System.currentTimeMillis().logicalTimeString()}",
            style = MaterialTheme.typography.bodyMedium,
            color = TextGray
        )
        if (openedDT.hour < DAY_START_HOUR) {
            Text(
                text = "这一笔归在上面的日子 · 实际是 ${openedDT.format(DATE_CN_FMT)} ${openedDT.format(TIME_FMT)}",
                style = MaterialTheme.typography.bodySmall,
                color = TextGray
            )
        }

        // 安全底座的临时入口：连着两天都很难熬才出现，当天点过就不再出现。
        // 它不是常驻入口——越难受，越应该先把这一刻记下来。
        if (showFloatingSafetyEntry(repo)) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "连着两天都很难熬。安全底座在这里 ›",
                style = MaterialTheme.typography.bodySmall,
                color = TextGray,
                modifier = Modifier
                    .fillMaxWidth()
                    .tap {
                        SafetyPrefs.markUsed(context)
                        onSafety(null)
                    }
                    .padding(vertical = 4.dp)
            )
        }

        // 存完之后只在这里留一行小的。真正的追问卡片在整页最底下 ——
        // 它不应该插在滑块前面，那样看着像是"还没存就先问你"。
        if (pendingId != 0L) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(11.dp))
                    .background(ConfirmColor.copy(alpha = 0.14f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "✓ ${pendingAt.logicalTimeString()} 这一笔已经记下了。",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = SuccessGreen
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    "往下滑 ›",
                    style = MaterialTheme.typography.labelSmall,
                    color = SuccessGreen
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 开关就摆在这一格的最顶上，由你决定这一格叫什么、问什么。
        // 不藏在设置里：它决定的就是眼前这几根尺子问什么，藏远了只剩「那三条怎么没了」。
        LossModeSwitch(context = context)

        Spacer(modifier = Modifier.height(8.dp))

        // 「失去重要的人」这一组。整组关掉之后，这三条一起收走 ——
        // 想念、想联系、想确认的宾语是 TA，正在经历失去的人才需要被问这三句；
        // 而恐慌是通用的，不在这一组里 —— 没在经历失去的人一样会慌。
        // 关掉只是不再问：以前记过的那些一条不少，全部记录和 CSV 里照旧能读到。
        if (LossMode.on) {
            MoodSliderCard(
                title = "想念TA", hint = "现在有多想TA",
                lowLabel = "完全不想", highLabel = "满脑子都是TA",
                value = miss, color = MissColor, onChange = { miss = it }
            )
            MoodSliderCard(
                title = "想联系", hint = "有多想现在就发消息或打电话",
                lowLabel = "不想联系", highLabel = "非常想立刻联系",
                value = contact, color = ContactColor, onChange = { contact = it }
            )
            // 第三条的名字归你：默认是「想确认」，也可以是「想接触」「想靠近」——
            // 同一条尺子在不同的人那里是不同的事，写死的名字会替你把那件事定义掉。
            MoodSliderCard(
                title = ConfirmLabel.name, hint = ConfirmLabel.hint,
                lowLabel = "不在意", highLabel = "必须马上确认",
                value = confirm, color = ConfirmColor,
                onChange = { confirm = it },
                onRename = { renaming = true }
            )

            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(CardBorder)
            )
        }

        MoodSliderCard(
            title = "恐慌", hint = "心里有多慌、多不安",
            lowLabel = "很安心", highLabel = "快要撑不住",
            value = panic, color = PanicColor, onChange = { panic = it }
        )

        if (branchPanic) {
            Spacer(modifier = Modifier.height(4.dp))
            EmotionBranchCard(
                values = emotionValues,
                setters = emotionSetters,
                clearers = emotionClearers,
                customTags = customTags,
                customValues = customValues,
                onRemoveTag = { repo.deleteEmotionTag(it) },
                onAddTag = { addingEmotion = true }
            )
            if (highPanic) {
                Spacer(modifier = Modifier.height(10.dp))
                ImpulseCard(impulse) { impulse = it }
                Spacer(modifier = Modifier.height(10.dp))
                BodyCareHintCard()
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    "先存下来。存完之后会再问你一句「后来怎么样了」，那时候可以跳过。",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGray
                )
            } else {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    "慌到 5 分了 —— 把这一波里真正有的挑出来，然后存下来。" +
                        "到 8 分才会再多问两句。",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGray
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            val alreadySaved = pendingId != 0L
            if (!alreadySaved) {
                Button(
                    onClick = {
                            Haptics.tap()
                        val id = save(
                            repo = repo,
                            openedAt = openedAt,
                            miss = miss, contact = contact, confirm = confirm, panicLevel = panicLevel,
                            emotions = emotionValues,
                            customEmotions = customTags.mapNotNull { t ->
                                customValues[t]?.takeIf { v -> v >= 0f }?.let { t to it.roundToInt() }
                            },
                            impulse = impulse,
                            startedAt = episodeStart
                        )
                        if (highPanic) {
                            // 存完立刻给日记，然后把「后来怎么样了」挂在整页最底下。
                            // 三态永远在保存之后，不在保存之前。
                            pendingId = id
                            pendingAt = System.currentTimeMillis()
                            hint = ""
                        } else {
                            reset()
                        }
                        // 存下先震一下：日记框马上会盖上来，那一下触感是此刻唯一能穿过去的。
                        buzz(context)
                        onSaved(id)
                    },
                    interactionSource = saveInteraction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .pressScale(savePressed, 0.97f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
                ) {
                    Text("先记下这一刻", fontWeight = FontWeight.SemiBold)
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(ChipBg)
                        .padding(vertical = 13.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "这一笔已经存好了 · 下面的可以不答",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MarkText,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        } else {
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = {
                        Haptics.tap()
                    val id = save(
                        repo = repo,
                        openedAt = openedAt,
                        miss = miss, contact = contact, confirm = confirm, panicLevel = panicLevel,
                        emotions = emotionValues,
                        customEmotions = customTags.map { it to (customValues[it] ?: 0f).roundToInt() },
                        impulse = impulse,
                        startedAt = 0L
                    )
                    reset()
                    buzz(context)
                    onSaved(id)
                },
                interactionSource = saveInteraction,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .pressScale(savePressed, 0.97f),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
            ) {
                Text("先记下这一刻", fontWeight = FontWeight.SemiBold)
            }
        }

        // 没答完照样能存。这句话只是说明「没答」是允许的，不是一句警告。
        if (unansweredCount > 0 && pendingId == 0L) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "还有 $unansweredCount 项没答 —— 可以先这样存，也可以之后在全部记录里改。",
                style = MaterialTheme.typography.bodySmall,
                color = TextGray
            )
        }

        if (hint.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(hint, style = MaterialTheme.typography.bodyMedium, color = PrimaryPurple)
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = when {
                pendingId != 0L ->
                    "二级情绪和冲动等级都不进总览。这一次没答「后来怎么样了」的话，今天还能在全部记录里补。"
                highPanic ->
                    "二级情绪和冲动等级都不进总览。存完之后会在最底下问一句「后来怎么样了」，那时候可以跳过。"
                branchPanic ->
                    "二级情绪不进总览，也不会现在多问你什么 —— 到 8 分才会。存完就结束了。"
                else ->
                    "保存后会问你要不要写点什么（最多 2000 字），也可以之后在全部记录里补上图片、视频或语音。"
            },
            style = MaterialTheme.typography.bodySmall,
            color = TextGray
        )

        // 这一句问的是之后的你，所以它落在整页的最后面。可以不答。
        if (pendingId != 0L) {
            Spacer(modifier = Modifier.height(16.dp))
            AftermathCard(
                caption = "这三个问的是过一会儿的你，不是刚才那个你。现在答不出来很正常。",
                onAnswer = { answer(it) },
                onSkip = { closePending() }
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
    }

    if (addingEmotion) {
        AlertDialog(
            onDismissRequest = { addingEmotion = false },
            title = { Text("加一种我自己的情绪") },
            text = {
                OutlinedTextField(
                    value = newEmotion,
                    onValueChange = { newEmotion = it },
                    singleLine = true,
                    placeholder = { Text("比如：心口发紧 / 想砸东西 / 觉得TA嫌我烦") },
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                        Haptics.tap()
                    repo.addEmotionTag(newEmotion)
                    newEmotion = ""
                    addingEmotion = false
                }) { Text("加上", color = PrimaryPurple) }
            },
            dismissButton = { TextButton(onClick = { Haptics.tap(); addingEmotion = false }) { Text("取消") } }
        )
    }

    // 改第三条的名字。改的只是这两个字：数字、记录、CSV、长图一条不少。
    if (renaming) {
        var nameText by remember { mutableStateOf(ConfirmLabel.name) }
        var hintText by remember { mutableStateOf(ConfirmLabel.hint) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("这一条叫什么") },
            text = {
                Column {
                    Text(
                        "默认叫「想确认」。同一条尺子在不同的人那里是不同的事 —— " +
                            "想接触、想靠近、想证实，叫什么都行。",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextGray
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = nameText,
                        onValueChange = { if (it.length <= 8) nameText = it },
                        singleLine = true,
                        label = { Text("名字（最多 8 个字）") },
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = hintText,
                        onValueChange = { if (it.length <= 24) hintText = it },
                        singleLine = true,
                        label = { Text("下面那句小字（可留空）") },
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "只改这一条的名字。以前记过的那些一条不少，全部记录和 CSV 里照旧能读到。",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextGray
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    Haptics.tap()
                    ConfirmLabel.set(context, nameText, hintText)
                    renaming = false
                    hint = "这一条现在叫「${ConfirmLabel.name}」。"
                }) { Text("改好", color = PrimaryPurple) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        Haptics.tap()
                        ConfirmLabel.reset(context)
                        renaming = false
                    }) { Text("改回默认", color = TextGray) }
                    TextButton(onClick = { Haptics.tap(); renaming = false }) { Text("取消") }
                }
            }
        )
    }
}

/**
 * 「后来怎么样了」的三个答案。
 * 它可以不答 —— 三个按钮之外永远有一条「先这样」。记录已经存好了，这句话问的是之后的你。
 */
@Composable
fun AftermathCard(
    caption: String,
    onAnswer: (Int) -> Unit,
    onSkip: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "后来怎么样了",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text("可以不答", style = MaterialTheme.typography.labelSmall, color = TextGray)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(caption, style = MaterialTheme.typography.bodySmall, color = TextGray)
            Spacer(modifier = Modifier.height(10.dp))
            OutcomeButton("还可以", GainGreen) { onAnswer(2) }
            OutcomeButton("勉强行", PrimaryPurple) { onAnswer(1) }
            OutcomeButton("要求助", DangerRed) { onAnswer(3) }
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
                Text("先这样", color = TextGray)
            }
        }
    }
}

/**
 * 六支里一支都没动 → 这一组等于没记，返回 null（库里那六列全是 NULL）。
 * 动过的写它的值（0 也照写），没动的留 null。
 */
private fun buildEmotionSet(values: List<Float>): EmotionSet? {
    val v = values.map { it.takeIf { x -> x >= 0f }?.roundToInt() }
    if (v.all { it == null }) return null
    return EmotionSet(
        abandoned = v[0], anger = v[1], shame = v[2],
        fear = v[3], loneliness = v[4], resentment = v[5]
    )
}

@Suppress("LongParameterList")
private fun save(
    repo: MoodRepository,
    openedAt: Long,
    miss: Float, contact: Float, confirm: Float, panicLevel: Int,
    emotions: List<Float>,
    customEmotions: List<Pair<String, Int>>,
    impulse: Int,
    startedAt: Long
): Long {
    val nowMillis = System.currentTimeMillis()
    // 打开这一页到按下这中间跨过了早上六点的话，仍然算打开那天的那一笔。
    val used = if (nowMillis.logicalDate() == openedAt.logicalDate()) nowMillis else openedAt
    val record = MoodRecord(
        timestamp = used,
        miss = miss.roundToInt(),
        contact = contact.roundToInt(),
        confirm = confirm.roundToInt(),
        panic = panicLevel,
        // 一支都没动就整组不写：那等于这次没进过这一块，不该在记录里留下六个空位。
        emotions = buildEmotionSet(emotions),
        customEmotions = customEmotions,
        impulse = impulse,
        outcome = 0,
        episodeAt = startedAt.takeIf { it > 0L },
        outcomeAt = null
    )
    return repo.insert(record)
}

@Composable
fun OutcomeButton(text: String, color: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .height(50.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color)
    ) {
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * 恐慌 8-10 分时的二级情绪：六个预设 + 你自己加的那几种。
 * 预设的六个永远在 —— 是它们把你引到「还想加哪一种」的。
 */
@Composable
private fun EmotionBranchCard(
    values: List<Float>,
    setters: List<(Float) -> Unit>,
    clearers: List<() -> Unit>,
    customTags: List<String>,
    customValues: MutableMap<String, Float>,
    onRemoveTag: (String) -> Unit,
    onAddTag: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CareBg),
        border = BorderStroke(1.dp, CareBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "这一波里面是什么",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = CareTitle
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "「想念」不在这里——它和上面的「想念TA」是同一件事。挑这次真正有的，没有的不动。",
                style = MaterialTheme.typography.bodySmall,
                color = TextGray
            )
            Spacer(modifier = Modifier.height(8.dp))
            EMOTION_FIELDS.forEachIndexed { index, (name, _) ->
                EmotionSliderRow(
                    title = name,
                    value = values[index],
                    color = EmotionColors[index],
                    onChange = setters[index],
                    onClear = clearers[index]
                )
            }
            customTags.forEachIndexed { index, tag ->
                val color = EmotionColors[(EMOTION_FIELDS.size + index) % EmotionColors.size]
                EmotionSliderRow(
                    title = tag,
                    value = customValues[tag] ?: UNANSWERED_F,
                    color = color,
                    onChange = { customValues[tag] = it },
                    onClear = { customValues.remove(tag) },
                    onRemove = { onRemoveTag(tag) }
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                onClick = onAddTag,
                shape = RoundedCornerShape(10.dp),
                color = CardBg,
                border = BorderStroke(1.dp, CareBorder)
            ) {
                Text(
                    "+ 加一种我自己的情绪",
                    style = MaterialTheme.typography.labelLarge,
                    color = CareTitle,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
}

/** 冲动等级：只存整数，不写任何内容。 */
@Composable
private fun ImpulseCard(level: Int, onChange: (Int) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SafeBg),
        border = BorderStroke(1.dp, SafeBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                "我有伤害自己或别人的冲动",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = SafeTitle
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IMPULSE_LABELS.forEachIndexed { index, label ->
                    ChoiceChip(
                        text = label,
                        selected = level == index,
                        color = SafeTitle,
                        modifier = Modifier.weight(1f),
                        onClick = { onChange(index) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "只记一个等级，不写内容。它不会出现在任何图表里，只在你需要时导出给咨询师。",
                style = MaterialTheme.typography.bodySmall,
                color = TextGray
            )
        }
    }
}

/**
 * 处理身体的动作清单。
 * 就地列出来，不做跳转——跳转会把人从记录流程里带走，这一笔可能就丢了。
 */
@Composable
private fun BodyCareHintCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CareBg),
        border = BorderStroke(1.dp, CareBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                "要不要花两分钟，先处理一下身体？",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = CareTitle
            )
            Spacer(modifier = Modifier.height(6.dp))
            BODY_ACTIONS.forEach { action ->
                Row(
                    modifier = Modifier.padding(vertical = 3.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(CareTitle)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        action,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextGray,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "做完再回来按下面那个按钮。",
                style = MaterialTheme.typography.bodySmall,
                color = CareTitle,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/** 连着两天都有 8 分以上的恐慌，才把安全底座的入口浮出来一行；当天点过就不再出现。 */
@Composable
private fun showFloatingSafetyEntry(repo: MoodRepository): Boolean {
    val context = LocalContext.current
    if (SafetyPrefs.usedToday(context)) return false
    val today = logicalToday()
    fun rough(day: java.time.LocalDate) =
        repo.records.any { it.timestamp.logicalDate() == day && it.panic >= 8 }
    return rough(today) && rough(today.minusDays(1))
}

private val BODY_ACTIONS = listOf(
    "冷水冲手腕，或者用冷水洗脸",
    "吸气 4 秒 · 停 4 秒 · 呼气 6 秒，做六个来回",
    "站起来走动五分钟，换个房间也行",
    "喝一杯水，或者吃点东西",
    "把该吃的药吃了"
)

@Composable
fun ChoiceChip(
    text: String,
    selected: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    // 按下去亮一下：选中本来就会换底色，但「按到了」这件事要当场看得见。
    val pulse = rememberPulse()
    val lit = pulse.alpha()
    Surface(
        onClick = { pulse.bump(); onClick() },
        modifier = modifier.zoom(1f - 0.03f * lit),
        shape = RoundedCornerShape(10.dp),
        color = blend(
            if (selected) color.copy(alpha = 0.16f) else ChipBg,
            color,
            0.35f * lit
        ),
        border = if (selected) BorderStroke(1.dp, color.copy(alpha = 0.5f)) else null
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) color else TextGray,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HintChips(items: List<String>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach {
            Surface(
                shape = RoundedCornerShape(100.dp),
                color = Color.Transparent,
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextGray,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 2.dp)
                )
            }
        }
    }
}

/**
 * 二级情绪的一行。
 *
 * 名字那一列宽度写死，右边给「×」也留了一份等宽的位子 ——
 * 自己加的那几个才带 ×，预设的六个不带，于是预设的条子会比自定义的长出一截，
 * 滑块的起点和终点都对不齐。现在两排共用同一套格子，一眼看下去是一列。
 */
private val EMOTION_NAME_WIDTH = 62.dp
/** 右边那一小块：有值时是数字（可点，退回未答），未答时是「未答」。两者同宽，切换时行不跳。 */
private val EMOTION_VALUE_WIDTH = 36.dp
private val EMOTION_TAIL_SLOT = 18.dp

/**
 * 二级情绪的一支。
 *
 * 只有两种样子：<b>有等级</b>或者<b>未答</b>。
 * 未答的时候整条是灰的，右边写着「未答」而不是 0 —— 0 是一句很实在的话（「这次一点都不愤怒」），
 * 空白不是。
 *
 * 右边那一小块是这支的开关：有值的时候点一下，这支退回「未答」（手滑碰到的那一下有救）；
 * 未答的时候它只是个标签，按它没反应 —— 给值请用滑轨。
 */
@Composable
fun EmotionSliderRow(
    title: String,
    value: Float,
    color: Color,
    onChange: (Float) -> Unit,
    onClear: () -> Unit,
    onRemove: (() -> Unit)? = null
) {
    val answered = value >= 0f
    val shown = if (answered) color else DisabledC
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (answered) TextMain else TextGray,
            maxLines = 1,
            modifier = Modifier.width(EMOTION_NAME_WIDTH)
        )
        Slider(
            value = if (answered) value else 0f,
            onValueChange = onChange,
            valueRange = 0f..10f,
            steps = 9,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = shown,
                activeTrackColor = shown,
                inactiveTrackColor = shown.copy(alpha = 0.18f),
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent
            )
        )
        if (answered) {
            Box(
                modifier = Modifier
                    .width(EMOTION_VALUE_WIDTH)
                    .clip(RoundedCornerShape(8.dp))
                    .background(color.copy(alpha = 0.14f))
                    .tap { Haptics.tap(); onClear() }
                    .padding(vertical = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = value.roundToInt().toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .width(EMOTION_VALUE_WIDTH)
                    .clip(RoundedCornerShape(8.dp))
                    .background(ChipBg)
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "未答",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextGray
                )
            }
        }
        // 预设的六个这里什么都不放，位子照样占着 —— 空位也是对齐的一部分。
        Box(
            modifier = Modifier.width(EMOTION_TAIL_SLOT),
            contentAlignment = Alignment.Center
        ) {
            if (onRemove != null) {
                Text(
                    text = "×",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextGray,
                    modifier = Modifier.tap(onClick = onRemove)
                )
            }
        }
    }
}

/**
 * 0–10 的刻度尺。
 *
 * 滑块本来就是 0–10 十一档，问题是看不出来：既没有刻度，又要拖过半格才跳一格，
 * 于是「轻轻往右挪一点」看着像没动。刻度上的数字可以直接点，一步到位。
 */
@Composable
fun ScaleRow(
    value: Float,
    color: Color,
    enabled: Boolean = true,
    onPick: (Float) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        for (i in 0..10) {
            val v = i.toFloat()
            val isEven = i % 2 == 0
            val picked = value.roundToInt() == i
            Column(
                modifier = Modifier
                    .weight(1f)
                    .then(if (enabled) Modifier.tap { onPick(v) } else Modifier),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .width(if (picked) 2.5.dp else 1.5.dp)
                        .height(if (isEven) 8.dp else 5.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(if (picked) color else if (isEven) DisabledC else CardBorder)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (isEven) i.toString() else "",
                    fontSize = 9.sp,
                    color = if (picked) color else TextGray,
                    fontWeight = if (picked) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

/**
 * 一条 0–10 的维度。
 *
 * value 传 UNANSWERED_F 表示还没答：整条是灰的、不画圆点、右侧写「未答」而不是 0。
 * 它和【成为 · 我的感受】那六根尺是同一个组件（Ruler.kt），手感一致，点数字也能跳。
 *//**
 * 「我正在经历失去重要的人」。
 *
 * 标题就是这一句话本身，不再一头一尾各写一遍 —— 它到底在问什么，看标题就够了。
 *
 * 它管的只有那三条宾语是 TA 的（想念TA / 想联系 / 想确认）。恐慌不在里头：
 * 没在经历失去的人一样会慌，那一条是通用的。
 * 关掉只是不再问 —— 以前记过的那几条一条不少，全部记录、CSV、长图里照常能读到。
 */
@Composable
private fun LossModeSwitch(context: android.content.Context) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, if (LossMode.on) MissColor.copy(alpha = 0.45f) else CardBorder)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "我正在经历失去重要的人",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = LossMode.on,
                    onCheckedChange = {
                        Haptics.tap()
                        LossMode.on = it
                        LossPrefs.set(context, it)
                    }
                )
            }
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                if (LossMode.on) {
                    "开着：下面会多问「想念TA / 想联系 / ${ConfirmLabel.name}」。\n" +
                        "关掉只是不再问 —— 以前记过的那几条一条不少。"
                } else {
                    "关着：下面只问慌不慌。\n" +
                        "打开之后，这一格会多问想念TA / 想联系 / ${ConfirmLabel.name}。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = TextGray
            )
        }
    }
}

@Composable
fun MoodSliderCard(
    title: String,
    hint: String,
    lowLabel: String,
    highLabel: String,
    value: Float,
    color: Color,
    onChange: (Float) -> Unit,
    /** 给了就有「改个名字」：这一条叫什么，由用的人说了算（现在只有第三条开这个口）。 */
    onRename: (() -> Unit)? = null
) {
    val answered = value >= 0f
    val shown = if (answered) color else DisabledC
    // 按下去那一下的可见回音：整块染一层自己的颜色、缩一点点，约 0.4 秒退干净。
    // 震动能不能震由手机说了算，这一下只要屏幕亮着就一定看得见。
    val pulse = rememberPulse()
    val lit = pulse.alpha()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .zoom(1f - 0.012f * lit),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = blend(CardBg, color, 0.20f * lit)),
        border = if (lit > 0.01f) BorderStroke(1.5.dp, color.copy(alpha = 0.55f * lit)) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(shown)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (answered) Color.Unspecified else TextGray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (answered) {
                    Surface(
                        color = blend(color.copy(alpha = 0.14f), color, 0.45f * lit),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = value.roundToInt().toString(),
                            modifier = Modifier
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                                .zoom(1f + 0.22f * lit),
                            color = color,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                } else {
                    Surface(
                        color = ChipBg,
                        shape = RoundedCornerShape(9.dp)
                    ) {
                        Text(
                            text = "未答",
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
                            color = TextGray,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 2.dp)
            ) {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGray,
                    maxLines = 2,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (onRename != null) {
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "改个名字 ›",
                        style = MaterialTheme.typography.labelSmall,
                        color = PrimaryPurple,
                        modifier = Modifier.tap(onClick = onRename)
                    )
                }
            }
            ScaleRuler(
                value = if (answered) value else 0f,
                color = color,
                showThumb = answered,
                onPick = { pulse.bump(); onChange(it) }
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(lowLabel, style = MaterialTheme.typography.labelSmall, color = TextGray)
                Text(highLabel, style = MaterialTheme.typography.labelSmall, color = TextGray)
            }
        }
    }
}
