package com.example.moodlog.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.ContactsContract
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.moodlog.data.NightPrefs
import com.example.moodlog.ui.theme.NightMode
import com.example.moodlog.ui.theme.applyNightMode
import com.example.moodlog.BuildConfig
import com.example.moodlog.R
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import java.time.LocalDate
import com.example.moodlog.data.Contact
import com.example.moodlog.data.MeStore
import com.example.moodlog.data.buildDayDigest
import com.example.moodlog.data.toPlainText
import com.example.moodlog.util.DATE_CN_FMT
import com.example.moodlog.util.logicalDate
import com.example.moodlog.data.QuoteStore
import com.example.moodlog.ui.theme.CardBg
import com.example.moodlog.ui.theme.LifeDimColors
import com.example.moodlog.ui.theme.PrimaryPurple
import com.example.moodlog.ui.theme.TextGray
import com.example.moodlog.ui.theme.TextMain
import com.example.moodlog.util.exportCsv
import com.example.moodlog.util.exportLifeCsv
import com.example.moodlog.util.logicalToday
import com.example.moodlog.util.saveDrawableToGallery
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Switch
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import android.app.Activity
import com.example.moodlog.data.BackupManager
import com.example.moodlog.data.BackupCrypto
import com.example.moodlog.data.BackupPrefs
import com.example.moodlog.data.PassphraseStore
import com.example.moodlog.data.HapticPrefs
import com.example.moodlog.data.LossMode
import com.example.moodlog.data.LossPrefs
import com.example.moodlog.MainActivity

@Composable
fun MeScreen(
    repo: MoodRepository,
    padding: PaddingValues,
    onOpenSummary: () -> Unit,
    onOpenSafety: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    var quoteTick by remember { mutableIntStateOf(0) }
    var editingQuotes by remember { mutableStateOf(false) }
    val quote = remember(quoteTick) { QuoteStore.today(context) }
    val mine = remember(quoteTick) { QuoteStore.mine(context) }

    // 双击进「今天的总结」。单击还是打开加句子的框 ——
    // 单击会先等 280 毫秒，等不到第二次点击才动手，所以双击不会被单击抢走。
    val scope = rememberCoroutineScope()
    var tapJob by remember { mutableStateOf<Job?>(null) }
    val doubleTap = Modifier.pointerInput(Unit) {
        detectTapGestures(onTap = {
            val pending = tapJob
            if (pending != null) {
                pending.cancel()
                tapJob = null
                onOpenSummary()
            } else {
                tapJob = scope.launch {
                    delay(280)
                    tapJob = null
                    editingQuotes = true
                }
            }
        })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            // 一级页不再自己补状态栏：Scaffold 给的 padding 里已经含了，再补一次就是双份 ——
            // 顶上空出一大块，看着像排版错了。
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        // 右上角那颗齿轮：导出、备份、深色模式这类「调 app 自己」的东西都收进去了，
        // 不跟「每天一句」「找人说话」抢位置。真正天天要按的东西留在这一页。
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "我",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "设置",
                tint = TextGray,
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .tap(onClick = onOpenSettings)
                    .padding(2.dp)
            )
        }
        Spacer(modifier = Modifier.height(14.dp))

        // —— 每天一句 ——
        Card(
            modifier = Modifier.fillMaxWidth().then(doubleTap),
            shape = RoundedCornerShape(17.dp),
            colors = CardDefaults.cardColors(containerColor = ChipBg),
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        QuoteStore.slotLabel(),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextGray
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        "加你自己那句 ›",
                        style = MaterialTheme.typography.labelSmall,
                        color = PrimaryPurple,
                        modifier = Modifier.tap { editingQuotes = true }
                    )
                }
                Spacer(modifier = Modifier.height(7.dp))
                Text(
                    quote,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 27.sp,
                    color = SubText
                )
                Spacer(modifier = Modifier.height(7.dp))
                Text(
                    "${QuoteStore.nextChangeText()} · 双击这句看今天的总结",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGray
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // —— 需要找人说话 ——
        val wechat = remember { wechatIntent(context) != null }
        var wechatHint by remember { mutableStateOf("") }
        var gptHint by remember { mutableStateOf("") }
        var copyHint by remember { mutableStateOf("") }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardBg),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "需要找人说话",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "直接跳到你熟悉的地方，不用在这里再存一遍号码。",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGray
                )
                Spacer(modifier = Modifier.height(10.dp))
                // 四个动作竖着排，等高、等距 —— 之前两个挤在一行里，看着像第三个按钮的附属品。
                ActionRow(text = "打开通讯录", onClick = { openContacts(context) })
                Spacer(modifier = Modifier.height(9.dp))
                ActionRow(
                    text = "打开微信",
                    onClick = {
                        val ok = openWeChat(context)
                        wechatHint = if (ok) "" else "没能打开微信。这台手机上可能没装，或者被系统限制了 —— 可以先打开通讯录。"
                    }
                )
                Spacer(modifier = Modifier.height(9.dp))
                ActionRow(text = "打开 ChatGPT", onClick = { gptHint = openChatGPT(context) })
                Spacer(modifier = Modifier.height(9.dp))
                ActionRow(
                    text = "复制今天这一段",
                    purple = true,
                    onClick = {
                        val dayRecords = repo.records.filter {
                            it.timestamp.logicalDate() == logicalToday()
                        }.sortedBy { it.timestamp }
                        val dayEvents = repo.events.filter {
                            it.timestamp.logicalDate() == logicalToday()
                        }
                        val dayEncounters = repo.encounters.filter {
                            it.timestamp.logicalDate() == logicalToday()
                        }
                        val dayMoves = repo.moves.filter {
                            it.timestamp.logicalDate() == logicalToday()
                        }.sortedBy { it.timestamp }
                        val text = buildDayDigest(
                            dayLabel = logicalToday().format(DATE_CN_FMT),
                            records = dayRecords,
                            events = dayEvents,
                            encounters = dayEncounters,
                            moves = dayMoves,
                            stances = repo.stancesOf(logicalToday().toString()),
                            dims = repo.dimsOf(logicalToday()),
                            goal = MeStore.goal(context),
                            note = MeStore.note(context, logicalToday().toString())
                        ).toPlainText()
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE)
                            as? ClipboardManager
                        cm?.setPrimaryClip(ClipData.newPlainText("心锚 · 今天", text))
                        // 复制了什么写清楚：多少次情绪、几件我话事。看不见的东西等于没复制。
                        copyHint = "已经复制好：${dayRecords.size} 次情绪 · " +
                            "${dayEvents.size} 件我话事 · ${dayEncounters.size} 笔我遇见 · " +
                            "${dayMoves.size} 次运动 · " +
                            "约 ${text.length} 字。过去粘贴就能开始聊。"
                    }
                )
                if (copyHint.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        copyHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = SuccessGreen,
                        lineHeight = 18.sp
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                // 不再弹一闪而过的提示：成不成写在这里，一眼看得到。
                Text(
                    text = sequenceOf(wechatHint, gptHint).firstOrNull { it.isNotEmpty() } ?: (
                        if (wechat) "检测到微信和 ChatGPT 可用 —— 直接开窗过去，不用输任何东西。"
                        else "微信没检测到。可以先打开通讯录，或者用 ChatGPT。"
                        ),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (wechatHint.isEmpty() && gptHint.isEmpty() && wechat) SuccessGreen
                    else TextGray,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "不读取、不上传你的任何联系人；复制出去的文字只经过这台手机的剪贴板。",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGray,
                    lineHeight = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // —— 紧急联系人 ——
        EmergencyContactCard(repo)

        // —— 赞助作者 ——（商店上架包会通过 BuildConfig.SPONSOR_ENABLED=false 关掉这个入口）
        if (BuildConfig.SPONSOR_ENABLED) {
            Spacer(modifier = Modifier.height(12.dp))
            SponsorCard()
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "一天从早上六点开始 —— 夜里睡不着的那几个小时，会归在前一天里。",
            style = MaterialTheme.typography.bodySmall,
            color = DisabledC,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        )

        // 兜底入口：放在整页最底下，不抢位置。真正急的时候有更近的路。
        Text(
            text = "安全底座 ›",
            style = MaterialTheme.typography.bodySmall,
            color = DisabledC,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .tap(onClick = onOpenSafety)
                .padding(vertical = 8.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
    }

    if (editingQuotes) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { editingQuotes = false },
            title = { Text("加一句你自己的", fontWeight = FontWeight.SemiBold) },
            text = {
                Column {
                    Text(
                        "只陈述事实，不打分，不鼓劲。最难的时候有用的，往往是从前某个时刻你自己说过的一句话。",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextGray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("只有你懂的那句话…") },
                        shape = RoundedCornerShape(12.dp),
                        minLines = 2
                    )
                    if (mine.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "你已经加的（会和其他句子一起轮）：",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextGray
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        mine.forEach { line ->
                            Row(
                                modifier = Modifier.padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    line,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    "删除",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextGray,
                                    modifier = Modifier.tap {
                                        QuoteStore.remove(context, line)
                                        quoteTick++
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                        Haptics.tap()
                    QuoteStore.add(context, text)
                    text = ""
                    quoteTick++
                }) { Text("加上", color = PrimaryPurple) }
            },
            dismissButton = { TextButton(onClick = { Haptics.tap(); editingQuotes = false }) { Text("关闭") } }
        )
    }
}

/**
 * 赞助作者。放整页最下面，不抢位置：这个 app 不收费、没有内购，
 * 想请作者喝一杯的时候它在这儿，不想的时候它只是一行小字。
 *
 * 付款发生在微信 / 支付宝里，app 内不出现任何「解锁」「回馈」——
 * 付了钱之后 app 也不会有任何变化，这是它能保持「只是赞助」的全部理由。
 */
@Composable
private fun SponsorCard() {
    var show by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().tap { show = true },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "赞助作者",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text("›", color = TextGray)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "不收费、没有内购。如果它陪你熬过几夜，可以请作者喝一杯 —— 不赞助也完全不影响任何东西。",
                style = MaterialTheme.typography.bodySmall,
                color = TextGray,
                lineHeight = 18.sp
            )
        }
    }

    if (show) {
        SponsorDialog(onDismiss = { show = false })
    }
}

/**
 * 两张码分开两页，一页一张 —— 挤在一屏里那两张都得缩小，
 * 而收款码缩小了就不好扫了。想存下来的话，下面那个按钮把它放进你的相册。
 */
@Composable
private fun SponsorDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var page by remember { mutableIntStateOf(0) }

    val resId = if (page == 0) R.drawable.qr_wechat else R.drawable.qr_alipay
    val name = if (page == 0) "微信收款码" else "支付宝收款码"
    val hint = if (page == 0) {
        "在微信里「扫一扫」，或者存到相册后用「扫一扫 · 相册」认这张图。"
    } else {
        "在支付宝里「扫一扫」，或者存到相册后用「扫一扫 · 相册」认这张图。"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("请作者喝一杯", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // 两页：微信 / 支付宝
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ChipBg, RoundedCornerShape(12.dp))
                        .padding(4.dp)
                ) {
                    listOf("微信", "支付宝").forEachIndexed { i, label ->
                        val on = page == i
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (on) CardBg else Color.Transparent)
                                .tap { page = i }
                                .padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (on) PrimaryPurple else TextGray
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Image(
                    painter = painterResource(resId),
                    contentDescription = name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    name,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGray
                )

                Spacer(modifier = Modifier.height(10.dp))
                TextButton(onClick = {
                        Haptics.tap()
                    val ok = saveDrawableToGallery(context, resId, name)
                    Toast.makeText(
                        context,
                        if (ok) "存好了，在相册的「心锚」里" else "没能存下来 —— 可以直接对着屏幕扫",
                        Toast.LENGTH_SHORT
                    ).show()
                }) { Text("保存这张图", color = PrimaryPurple, fontWeight = FontWeight.SemiBold) }

                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGray,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "付完之后 app 里什么都不会变 —— 没有解锁、没有回馈、没有去广告。它只是赞助。",
                    style = MaterialTheme.typography.bodySmall,
                    color = DisabledC,
                    lineHeight = 18.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭", color = PrimaryPurple) }
        }
    )
}

/** 拉起系统通讯录。只做跳转，不申请 READ_CONTACTS、不读取任何联系人。 */
private fun openContacts(context: Context) {
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }.onFailure {
        Toast.makeText(context, "打不开通讯录", Toast.LENGTH_SHORT).show()
    }
}

/**
 * 找微信，两条路都试一遍。
 *
 * 安卓从某一版起规定：一个 app 默认看不见手机上装了什么别的 app，
 * 得先在清单里报备「我要找它」（<queries>）。没报备的时候，查询回来的答案永远是「没装」——
 * 不是用户手机的问题。补了报备之后这里就能拿到意图；再不行就用通用的方式试一次。
 */
private fun wechatIntent(context: Context): Intent? {
    val pm = context.packageManager
    pm.getLaunchIntentForPackage("com.tencent.mm")?.let { return it }
    val alt = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)
        .setPackage("com.tencent.mm")
    return alt.takeIf { pm.queryIntentActivities(alt, 0).isNotEmpty() }
}

private fun openWeChat(context: Context): Boolean {
    val intent = wechatIntent(context) ?: return false
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return runCatching { context.startActivity(intent); true }.getOrDefault(false)
}

/**
 * 找 ChatGPT：装了 app 就开窗过去，没装就用浏览器打开网页版。
 * 返回一句话给界面写明成不成 —— 一闪而过的提示等于没有提示。
 *
 * 内置聊天为什么不在这里：那需要把 API key 存进 app、要把你在最难的时候写的东西
 * 发给第三方。复制今天那一段 + 一个入口，已经解决了九成。
 */
private fun chatgptIntent(context: Context): Intent? {
    val pm = context.packageManager
    pm.getLaunchIntentForPackage(CHATGPT_PACKAGE)?.let { return it }
    val alt = Intent(Intent.ACTION_VIEW, Uri.parse("https://chatgpt.com"))
    return alt.takeIf { pm.queryIntentActivities(alt, 0).isNotEmpty() }
}

private const val CHATGPT_PACKAGE = "com.openai.chatgpt"

private fun openChatGPT(context: Context): String {
    val intent = chatgptIntent(context)
        ?: return "这台手机上没有 ChatGPT，也没有可用的浏览器。可以先打开通讯录。"
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val ok = runCatching { context.startActivity(intent); true }.getOrDefault(false)
    return if (ok) "已经过去了。粘贴刚才复制的那一段，就能直接开始聊。"
    else "没能打开 ChatGPT。"
}

private fun toast(context: Context, name: String?, what: String) {
    Toast.makeText(
        context,
        if (name == null) "$what 导出失败（需要 Android 10 以上）" else "已导出到下载目录：$name",
        Toast.LENGTH_LONG
    ).show()
}

// ------------------------------------------------------------------ 紧急联系人

/**
 * 三个位子，名字和号码都是自己填的。
 *
 * 为什么不去读通讯录里的人：那要申请读联系人的权限，等于把整本通讯录摊给这个 app。
 * 这里只留你愿意留下的那三个号。真到要用它的那一刻，要的是一眼能看到、点一下就拨出去 ——
 * 不是先去几十个名字里翻。
 */
@Composable
private fun EmergencyContactCard(repo: MoodRepository) {
    val context = LocalContext.current
    var editingSlot by remember { mutableIntStateOf(0) }
    val contacts = repo.contacts

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "紧急联系人",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "只存在这台手机里，不上传、不同步。安全底座走到第 3 步时会直接列出来。",
                style = MaterialTheme.typography.bodySmall,
                color = TextGray,
                lineHeight = 18.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            (1..3).forEach { slot ->
                val c = contacts.firstOrNull { it.slot == slot }
                ContactRow(
                    slot = slot,
                    contact = c,
                    onEdit = { editingSlot = slot },
                    onCall = { c?.phone?.let { dialTo(context, it) } }
                )
                if (slot < 3) Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    if (editingSlot != 0) {
        val slot = editingSlot
        val current = contacts.firstOrNull { it.slot == slot }
        ContactEditor(
            slot = slot,
            name = current?.name.orEmpty(),
            phone = current?.phone.orEmpty(),
            onDismiss = { editingSlot = 0 },
            onSave = { n, p ->
                repo.upsertContact(Contact(slot, n.trim(), p.trim()))
                editingSlot = 0
            },
            onClear = {
                repo.deleteContact(slot)
                editingSlot = 0
            }
        )
    }
}

@Composable
private fun ContactRow(
    slot: Int,
    contact: Contact?,
    onEdit: () -> Unit,
    onCall: () -> Unit
) {
    val filled = contact != null && (contact.name.isNotBlank() || contact.phone.isNotBlank())
    Surface(
        onClick = onEdit,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(13.dp),
        color = CardBg,
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (filled) contact.name.ifBlank { "第 $slot 位" } else "＋ 加第 $slot 位",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (filled) SubText else TextGray
                )
                if (filled && contact.phone.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        contact.phone,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextGray
                    )
                }
            }
            if (filled && contact.phone.isNotBlank()) {
                Text(
                    "拨打",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = PrimaryPurple,
                    modifier = Modifier
                        .tap(onClick = onCall)
                        .padding(start = 10.dp, top = 4.dp, bottom = 4.dp)
                )
            }
            Text(
                if (filled) "改" else "›",
                style = MaterialTheme.typography.labelMedium,
                color = PrimaryPurple,
                modifier = Modifier.padding(start = 12.dp)
            )
        }
    }
}

@Composable
private fun ContactEditor(
    slot: Int,
    name: String,
    phone: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
    onClear: () -> Unit
) {
    var textName by remember(name) { mutableStateOf(name) }
    var textPhone by remember(phone) { mutableStateOf(phone) }
    var confirmClear by remember { mutableStateOf(false) }
    val filled = name.isNotBlank() || phone.isNotBlank()

    if (confirmClear) {
        ConfirmDeleteDialog(
            preview = listOf(name, phone).filter { it.isNotBlank() }.joinToString("　"),
            title = "删掉这一位？",
            tail = "号码只存在这台手机上，删了要重新填一次。",
            onConfirm = { onClear() },
            onDismiss = { confirmClear = false }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("第 $slot 位", fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                Text(
                    "不用想「该填谁」。想到谁就填谁 —— 家人、朋友、同事都行。",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGray,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = textName,
                    onValueChange = { textName = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("名字（可以不写）") },
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = textPhone,
                    onValueChange = { textPhone = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("号码") },
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { Haptics.tap(); onSave(textName, textPhone) }) {
                Text("存下", color = PrimaryPurple)
            }
        },
        dismissButton = {
            if (filled) {
                TextButton(onClick = { Haptics.tap(); confirmClear = true }) { Text("删掉这位", color = TextGray) }
            } else {
                TextButton(onClick = onDismiss) { Text("先不填") }
            }
        }
    )
}

private fun dialTo(context: Context, phone: String) {
    val p = phone.trim()
    if (p.isBlank()) return
    runCatching {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(p)))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }.onFailure {
        Toast.makeText(context, "没能拨出去。检查一下这个号码。", Toast.LENGTH_SHORT).show()
    }
}

/** 一行一个动作：同高、同距，不再把两个按钮并排塞进一行。 */
@Composable
private fun ActionRow(
    text: String,
    purple: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = if (purple) ChipBg else CardBg,
        border = BorderStroke(1.dp, if (purple) CardBorder else CardBorder)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 13.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (purple) PrimaryPurple else SubText
            )
        }
    }
}

// ------------------------------------------------------------------ 云备份（加密）

/**
 * 把全部记录加密后存到你自己的云盘同步文件夹。不联网、不经第三方。
 * 手动「立即备份」每次都要输密码；「自动每日备份」会把密码存在本机 Keystore 里免输。
 * 恢复时：选文件 → 输密码 → 确认覆盖 → 重启 App。
 */
@Composable
private fun BackupCard(repo: MoodRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var folderUri by remember { mutableStateOf(BackupPrefs.folderUri(context)) }
    var autoOn by remember { mutableStateOf(BackupPrefs.autoBackup(context)) }
    var lastBackup by remember { mutableStateOf(BackupPrefs.lastBackupTime(context)) }
    var status by remember { mutableStateOf("") }

    var showBackupPass by remember { mutableStateOf(false) }
    var showAutoPass by remember { mutableStateOf(false) }
    var showRestorePass by remember { mutableStateOf(false) }
    var showRestoreConfirm by remember { mutableStateOf(false) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var restoredBytes by remember { mutableStateOf<ByteArray?>(null) }

    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            BackupPrefs.setFolderUri(context, uri)
            folderUri = uri
            status = "已选好备份文件夹，可以备份了。"
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pendingRestoreUri = uri
            showRestorePass = true
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "云备份（加密）",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "把全部记录加密后，存到你自己的云盘同步文件夹（比如 Google Drive 在手机上的同步目录）。" +
                    "卸载、换手机都不丢。不联网、不经过任何第三方。",
                style = MaterialTheme.typography.bodySmall,
                color = TextGray,
                lineHeight = 18.sp
            )
            Spacer(modifier = Modifier.height(10.dp))

            if (folderUri != null) {
                Text("备份文件夹：${folderName(context, folderUri!!)}", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(4.dp))
            }
            if (lastBackup > 0) {
                Text("上次备份：${formatTime(lastBackup)}", style = MaterialTheme.typography.bodySmall, color = TextGray)
                Spacer(modifier = Modifier.height(4.dp))
            }
            if (status.isNotEmpty()) {
                Text(status, style = MaterialTheme.typography.bodySmall, color = SuccessGreen, lineHeight = 18.sp)
                Spacer(modifier = Modifier.height(4.dp))
            }
            Spacer(modifier = Modifier.height(6.dp))

            ActionRow(
                text = if (folderUri == null) "选择备份文件夹" else "更换备份文件夹",
                onClick = { folderLauncher.launch(null) }
            )
            Spacer(modifier = Modifier.height(9.dp))
            ActionRow(
                text = "立即备份（加密）",
                purple = true,
                onClick = {
                    if (folderUri == null) { status = "先选一个备份文件夹。"; return@ActionRow }
                    showBackupPass = true
                }
            )
            Spacer(modifier = Modifier.height(9.dp))
            ActionRow(
                text = "从备份恢复",
                onClick = { restoreLauncher.launch(arrayOf("application/octet-stream", "*/*")) }
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("自动每日备份", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.weight(1f))
                Switch(checked = autoOn, onCheckedChange = { on ->
                    Haptics.tap()
                    if (on && folderUri == null) { status = "先选备份文件夹，再开自动备份。"; return@Switch }
                    if (on) {
                        showAutoPass = true
                    } else {
                        autoOn = false
                        BackupPrefs.setAutoBackup(context, false)
                        PassphraseStore.clear(context)
                        status = "已关闭自动备份。"
                    }
                })
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "开启后，每天首次打开 App 自动备份一次（密码只存在本机，用于免输）。恢复仍需你手输密码。",
                style = MaterialTheme.typography.bodySmall,
                color = TextGray,
                lineHeight = 18.sp
            )
        }
    }

    // 手动备份：输入密码
    if (showBackupPass) {
        PassDialog(
            title = "备份密码",
            hint = "这个密码用来加密备份文件。换手机恢复时也要输它 —— 记在只有你知道的地方。",
            confirmText = "备份",
            onCancel = { showBackupPass = false },
            onConfirm = { p ->
                showBackupPass = false
                scope.launch(Dispatchers.IO) {
                    runCatching {
                        val db = repo.getDbFile()
                        if (!db.exists()) throw IllegalStateException("还没有数据可备份。")
                        repo.flushWal()
                        val enc = BackupCrypto.encrypt(db, p)
                        val name = BackupManager.writeEncryptedToTree(context, folderUri!!, enc)
                        BackupPrefs.setLastBackupTime(context, System.currentTimeMillis())
                        withContext(Dispatchers.Main) {
                            lastBackup = BackupPrefs.lastBackupTime(context)
                            status = "已备份：$name"
                        }
                    }.onFailure { e ->
                        withContext(Dispatchers.Main) { status = "备份失败：${e.message}" }
                    }
                }
            }
        )
    }

    // 自动备份：设置本机密码
    if (showAutoPass) {
        PassDialog(
            title = "自动备份密码",
            hint = "这个密码用来加密每日自动备份。会存在本机 Keystore 里（设备绑定，重装后需重设）。恢复时你要手输它。",
            confirmText = "开启并备份一次",
            onCancel = { showAutoPass = false },
            onConfirm = { p ->
                showAutoPass = false
                scope.launch(Dispatchers.IO) {
                    runCatching {
                        val db = repo.getDbFile()
                        if (!db.exists()) throw IllegalStateException("还没有数据可备份。")
                        repo.flushWal()
                        val enc = BackupCrypto.encrypt(db, p)
                        val name = BackupManager.writeEncryptedToTree(context, folderUri!!, enc)
                        PassphraseStore.save(context, p)
                        BackupPrefs.setAutoBackup(context, true)
                        BackupPrefs.setLastBackupTime(context, System.currentTimeMillis())
                        withContext(Dispatchers.Main) {
                            autoOn = true
                            lastBackup = BackupPrefs.lastBackupTime(context)
                            status = "自动备份已开启，刚完成一次：$name"
                        }
                    }.onFailure { e ->
                        withContext(Dispatchers.Main) { status = "开启失败：${e.message}" }
                    }
                }
            }
        )
    }

    // 恢复：输入密码解密
    if (showRestorePass) {
        PassDialog(
            title = "恢复密码",
            hint = "输入备份时设的那个密码。输错或文件损坏都会失败。",
            confirmText = "解密",
            onCancel = { showRestorePass = false },
            onConfirm = { p ->
                showRestorePass = false
                scope.launch(Dispatchers.IO) {
                    runCatching {
                        val enc = BackupManager.readEncryptedUri(context, pendingRestoreUri!!)
                        val plain = BackupCrypto.decrypt(enc, p)
                        withContext(Dispatchers.Main) {
                            restoredBytes = plain
                            showRestoreConfirm = true
                        }
                    }.onFailure {
                        withContext(Dispatchers.Main) { status = "解密失败（密码错或文件损坏）。" }
                    }
                }
            }
        )
    }

    // 恢复：确认覆盖并重启
    if (showRestoreConfirm) {
        ConfirmDeleteDialog(
            preview = "全部现有记录将被备份里的内容替换",
            title = "恢复会覆盖现有数据",
            tail = "这一步不可撤销。确定要用这个备份替换掉手机上现在的所有记录吗？",
            onConfirm = {
                showRestoreConfirm = false
                scope.launch(Dispatchers.IO) {
                    runCatching {
                        repo.flushWal()
                        repo.closeDb()
                        BackupManager.replaceDatabase(repo.getDbFile(), restoredBytes!!)
                        withContext(Dispatchers.Main) { restartApp(context) }
                    }.onFailure { e ->
                        withContext(Dispatchers.Main) { status = "恢复失败：${e.message}" }
                    }
                }
            },
            onDismiss = { showRestoreConfirm = false }
        )
    }
}

@Composable
private fun PassDialog(
    title: String,
    hint: String,
    confirmText: String,
    onCancel: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var pass by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title, fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                Text(hint, style = MaterialTheme.typography.bodySmall, color = TextGray, lineHeight = 18.sp)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = pass,
                    onValueChange = { pass = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("输入备份密码") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                    Haptics.tap()
                val p = pass.trim()
                if (p.length < 4) return@TextButton
                onConfirm(p)
            }) { Text(confirmText, color = PrimaryPurple) }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("取消") } }
    )
}

private fun folderName(context: Context, uri: Uri): String =
    androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)?.name ?: uri.toString()

private fun formatTime(ts: Long): String =
    java.time.Instant.ofEpochMilli(ts).atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))

private fun restartApp(context: Context) {
    val intent = Intent(context, MainActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
    context.startActivity(intent)
    (context as? Activity)?.finish()
}

// ---------------------------------------------------------------- 设置（二级页）

/**
 * 【设置】。从【我】右上角那颗齿轮进来。
 *
 * 这里只放「调 app 自己」的东西：深色模式、导出、备份。
 * 天天要按的那些（每天一句、找人说话、紧急联系人）留在【我】页 ——
 * 二级页越干净，真要用的时候找得越快。
 */
@Composable
fun SettingsPage(
    repo: MoodRepository,
    padding: PaddingValues,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var hapticOn by remember { mutableStateOf(HapticPrefs.on(context)) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            // 二级页是盖在【我】那一屏上的：底色铺满，底下那一屏才不会从边上透出来。
            .background(PageBg)
            .blockClicks()
            .padding(padding)
            // 二级页拿到的 padding 是 0，状态栏那块只能自己让开；再往下 12dp 是呼吸 ——
            // 不然「‹ 返回」就贴在左上角那个时间的正下方。三个二级页共用 SubPageTopBar。
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 18.dp)
            .padding(top = 12.dp, bottom = 10.dp)
    ) {
        SubPageTopBar(title = "设置", hint = "偏好与数据", onBack = onBack)
        Spacer(modifier = Modifier.height(4.dp))

        // 「我正在经历失去重要的人」那个开关不在这里了 —— 它搬到了【思念】那一格的最顶上，
        // 就在「想念TA」那几根尺子前面。它决定的是那几根尺子问什么，就该摆在它们旁边。

        // —— 点击震动 ——
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardBg),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "点击震动",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "按下去手上有一下回音。关掉之后除了不震，一切都照常。",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextGray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    // 试一下：走的是和真按钮同一条路，试出来的就是真实手感。
                    // 万一某台机器确实震不动，至少能在这里确认是它不震，不是没做。
                    Text(
                        "按一下试试 ›",
                        style = MaterialTheme.typography.labelMedium,
                        color = PrimaryPurple,
                        modifier = Modifier.tap { Haptics.test() }
                    )
                }
                Switch(
                    checked = hapticOn,
                    onCheckedChange = {
                        hapticOn = it
                        HapticPrefs.set(context, it)
                        Haptics.tap()
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // —— 深色模式 ——
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardBg),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "深色模式",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "默认是「自动」：跟着这个 app 自己的夜晚走 —— 18:00 到次日 05:59。" +
                        "夜里那几个小时最要紧，所以时段是它自己定的，不看系统的日落。",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGray,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ChipBg, RoundedCornerShape(12.dp))
                        .padding(4.dp)
                ) {
                    listOf("自动", "浅色", "深色").forEachIndexed { i, label ->
                        val on = NightMode.mode == i
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (on) CardBg else Color.Transparent)
                                .tap {
                                    NightPrefs.set(context, i)
                                    // 立刻换，不等下一次对表。
                                    applyNightMode(context)
                                }
                                .padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (on) PrimaryPurple else TextGray
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    when (NightMode.mode) {
                        NightMode.ON -> "现在是钉住的深色 —— 白天也是。"
                        NightMode.OFF -> "现在是钉住的浅色 —— 夜里也是。"
                        else -> if (NightMode.isDark) "现在是夜里，深色开着。" else "现在是白天，浅色。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = SuccessGreen
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // —— 导出 ——
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardBg),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "导出",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "导到手机的「下载」目录。想带给咨询师看的时候用得上。",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGray
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row {
                    TextButton(onClick = {
                            Haptics.tap()
                        val name = exportCsv(context, repo.records)
                        toast(context, name, "情绪")
                    }) { Text("情绪 CSV", color = PrimaryPurple) }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = {
                            Haptics.tap()
                        val name = exportLifeCsv(context, repo.events, repo.dims, repo.moves)
                        toast(context, name, "生活")
                    }) { Text("生活 CSV", color = LifeDimColors[0]) }
                }
                Spacer(modifier = Modifier.height(6.dp))
                // 「数据」那块删了，这句话不能跟着删 —— 它关系到你会不会突然丢掉全部日记。
                Text(
                    "日记、图片、录音都存在这台手机的 app 私有目录里，卸载会一起删掉。要留底就用导出。",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGray,
                    lineHeight = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        BackupCard(repo)
        Spacer(modifier = Modifier.height(20.dp))
    }
}
