package com.example.moodlog.ui

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.moodlog.data.HapticPrefs

/**
 * 记下那一刻的反馈。
 *
 * 这个 app 不打分、不给徽章，「记录本身即干预」是它唯一的回报机制 ——
 * 所以按下保存之后的那一下不能在界面上无声无息：震一下，整屏染一层很淡的颜色，
 * 中间那句「记下了」浮出来又退掉，约半秒钟，然后什么都不留。
 *
 * 它是个全局状态：任何一屏存下东西都能点一下火，不用把回调一层层传下去。
 */
object SaveFeedback {
    /** 每点一次火加一，用来触发一次新的动画。 */
    var nonce by mutableIntStateOf(0)
        private set
    var tint: Color by mutableStateOf(Color.Transparent)
        private set
    var note: String by mutableStateOf("")
        private set

    fun fire(tint: Color, note: String) {
        this.tint = tint
        this.note = note
        nonce++
    }
}

/**
 * 真去震的那一段。
 *
 * 三条路，按顺序试，谁先成了算谁的 ——
 * 「代码跑了、手上一无所有」这件事已经发生过一次，所以这里宁可多试一条：
 *
 *  1. 系统预设的那一下（EFFECT_TICK / EFFECT_HEAVY_CLICK，Android 10 起）。
 *     强度是这台手机自己调过的，比自己写一个 20ms 的一下可靠得多 ——
 *     自己拼的短震被马达吞掉，正是最常出现的那种「没震」。
 *  2. 自己拼的一次性短震，振幅写死 255（-1 是「设备默认」，而相当多国产机的
 *     「默认」低到几乎感觉不到）。
 *  3. 连马达都没有（或者上面两条都失败了）：退回系统的 View 触感接口。
 *
 * 时长也别给太短：10ms 上下的一下，很多线性马达根本不响应。
 */
@SuppressLint("NewApi")
private fun vibrate(context: Context, ms: Long, strong: Boolean): Boolean {
    val v: Vibrator = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }.getOrNull() ?: return false

    if (!runCatching { v.hasVibrator() }.getOrDefault(false)) return false

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val effect = runCatching {
            VibrationEffect.createPredefined(
                if (strong) VibrationEffect.EFFECT_HEAVY_CLICK else VibrationEffect.EFFECT_TICK
            )
        }.getOrNull()
        if (effect != null && runCatching { v.vibrate(effect) }.isSuccess) return true
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        if (runCatching { v.vibrate(VibrationEffect.createOneShot(ms, 255)) }.isSuccess) return true
    }
    @Suppress("DEPRECATION")
    return runCatching { v.vibrate(longArrayOf(0, ms), -1) }.isSuccess
}

/** 最后一条路：走系统的 View 触感接口。它认系统的「触感反馈」开关，所以只当兜底。 */
private fun fallbackFeedback(view: View?, strong: Boolean): Boolean {
    val v = view ?: return false
    val what = if (strong) HapticFeedbackConstants.LONG_PRESS else HapticFeedbackConstants.KEYBOARD_TAP
    return runCatching { v.performHapticFeedback(what) }.getOrDefault(false)
}

/** 保存、删除那一下。短、轻，像钢笔按下那一下 —— 不是通知那种嗡嗡。 */
fun buzz(context: Context) {
    Haptics.save()
}

/**
 * 三档手感。
 *
 * 以前只有一种（30ms），而它其实是「存下来了」的专用音 ——
 * 选一个标签、翻一个月、收起一行，按下去了手指是没有回音的，
 * 于是很多操作看着点了、又像没点上，人会重复按第二次。
 *
 *   tap  20ms  一切选项：按钮、标签、开关、翻页、返回、折叠、切 tab。
 *   pick 28ms  值真的变了才给：卷轴卡到一格、滑尺落定、滚轴跳一格。带 70ms 节流 ——
 *              一根尺子从头拖到尾会经过几十格，全震就变成噪音，也就不再是反馈了。
 *   save 45ms  记下 / 改好 / 删掉，以及随之而来的那层染色。
 *
 * 三档都给足 20ms 以上：十毫秒出头的那一下，多数手机的马达根本不响应，
 * 结果是代码跑了、手上一无所有 ——「没做出来」和「做了但感觉不到」对用的人来说是同一件事。
 *
 * 无效点击不在这里：禁用的按钮、未来的日期、已经选中的那一项再点一次，
 * 它们根本不会走到这里 —— 手上没回音，本身就是「这里按不动」这条信息。
 */
object Haptics {
    /** Application context，MainActivity 里注入一次。拿它只是为了不把 context 一层层传下去。 */
    lateinit var app: Context

    /** 兜底用的那个 View（MainActivity 的 decorView）。没有也不影响，只是少一条路。 */
    var view: View? = null

    private var lastAt = 0L

    private fun fire(ms: Long, gap: Long, strong: Boolean = false) {
        if (!::app.isInitialized) return
        if (!HapticPrefs.on(app)) return
        val now = android.os.SystemClock.uptimeMillis()
        // 节流只对落点档起作用（gap > 0）；轻点和保存每次都给。
        if (gap > 0 && now - lastAt < gap) return
        lastAt = now
        if (!vibrate(app, ms, strong)) fallbackFeedback(view, strong)
    }

    fun tap() = fire(30, 0)
    fun pick() = fire(35, 70)
    fun save() = fire(50, 0, strong = true)

    /** 设置里「按一下试试」用的：走的是同一条路，试出来的就是真实手感。 */
    fun test() = fire(60, 0, strong = true)
}

/**
 * 一次「闪一下」。
 *
 * 震动这件事，能不能震、震多强，最后是手机说了算 —— 系统把触感关掉、马达弱、
 * 或者只是今天不想震，代码这头都拦不住。所以看得见的那一下不能省：
 * 手指按下去，被按的那块自己亮一下、缩一点，再退回去。它不依赖任何系统设置，
 * 只要屏幕亮着就一定看得见。
 *
 * 用法两步：rememberPulse() 拿一个；按下的地方 pulse.bump()；
 * 要亮的那块拿 pulse.alpha() 当亮度（1 → 0，约 0.4 秒）。
 */
class Pulse {
    var nonce by mutableIntStateOf(0)
        private set

    fun bump() {
        nonce++
    }
}

@Composable
fun rememberPulse(): Pulse = remember { Pulse() }

/** 一段从 1 淡到 0 的亮度。没 bump 过的时候恒为 0 —— 第一帧不会无缘无故亮一下。 */
@Composable
fun Pulse.alpha(durationMs: Int = 420): Float {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(nonce) {
        if (nonce > 0) {
            anim.snapTo(1f)
            anim.animateTo(0f, tween(durationMillis = durationMs, easing = FastOutLinearInEasing))
        }
    }
    return anim.asState().value
}

/** 把底色往某个颜色上染一点点。amount 为 0 时原样返回。 */
fun blend(base: Color, tint: Color, amount: Float): Color =
    if (amount <= 0f) base else lerp(base, tint, amount.coerceIn(0f, 1f))

/**
 * 盖在上面那一层吃掉落在空白处的点击。
 *
 * 二级页是叠在原来那一屏上的（不是把它换掉），只铺底色不拦点击的话，
 * 点在空白处会穿过去按到下面那一屏的按钮 —— 底下那屏还活着，只是看不见了。
 * 这里只吃空白处：子控件在它上面，先被子控件接走。
 */
fun Modifier.blockClicks(): Modifier =
    this.clickable(interactionSource = null, indication = null, onClick = {})

/** 整块缩放到某个倍数。闪一下的时候拿它做那一记轻微的下沉 / 顶起。 */
fun Modifier.zoom(scale: Float): Modifier =
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }

/** 手指按住时缩一点，松手弹回来。看得见的「按到了」。 */
fun Modifier.pressScale(pressed: Boolean, scale: Float = 0.96f): Modifier =
    this.zoom(if (pressed) scale else 1f)

/**
 * 可点的东西统一走这里：点击 + 水波 + 轻震。
 *
 * 全项目几十处 clickable 各写各的，一定会漏 —— 漏掉的那几处就是「按了没反应」的那几处。
 * 统一之后，新增一个按钮顺手就是 .tap，不用记着还要补一行震动。
 */
fun Modifier.tap(onClick: () -> Unit): Modifier =
    this.clickable { Haptics.tap(); onClick() }

fun Modifier.tap(enabled: Boolean, onClick: () -> Unit): Modifier =
    this.clickable(enabled = enabled) { Haptics.tap(); onClick() }

/**
 * 记下之后的那一层颜色。
 *
 * 铺在最上面，但不吃点击 —— 它只负责闪一下就走，手指该落到哪儿还是落到哪儿。
 */
@Composable
fun SaveBurst() {
    val nonce = SaveFeedback.nonce
    if (nonce == 0) return
    var play by remember(nonce) { mutableStateOf(true) }
    val a by animateFloatAsState(
        targetValue = if (play) 1f else 0f,
        animationSpec = tween(durationMillis = 560),
        label = "saveBurst"
    )
    LaunchedEffect(nonce) { play = false }
    if (a <= 0.01f) return

    Box(
        modifier = Modifier
            .fillMaxSize()
            // 只染色不拦事：没有任何点击修饰，手势会穿过去落到下面那一屏。
            .background(SaveFeedback.tint.copy(alpha = 0.13f * a)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer {
                    alpha = a
                    scaleX = 0.88f + 0.12f * a
                    scaleY = scaleX
                }
                .clip(RoundedCornerShape(20.dp))
                .background(SaveFeedback.tint.copy(alpha = 0.92f * a))
                .padding(horizontal = 18.dp, vertical = 10.dp)
        ) {
            Text(
                SaveFeedback.note,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
    }
}
