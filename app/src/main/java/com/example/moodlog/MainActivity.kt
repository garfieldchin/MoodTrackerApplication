package com.example.moodlog

import androidx.compose.runtime.LaunchedEffect
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.example.moodlog.data.BackupManager
import com.example.moodlog.data.MoodRepository
import com.example.moodlog.data.LossMode
import com.example.moodlog.data.LossPrefs
import com.example.moodlog.ui.MoodApp
import com.example.moodlog.ui.theme.MoodLogTheme
import com.example.moodlog.ui.theme.applyNightMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo = MoodRepository(this)
        // 全局震动的 context（见 ui/Feedback.kt）：只为不用把 context 一层层传下去。
        com.example.moodlog.ui.Haptics.app = applicationContext
        // 兜底那条路要用一个 View：马达这条路走不通时，退回系统的触感接口。
        com.example.moodlog.ui.Haptics.view = window.decorView
        // 第三条（默认「想确认」）的名字：改过的那次从盘上读回来。
        com.example.moodlog.data.ConfirmLabel.load(this)
        // 老用户第一次跑到这儿：库里记过想念/想联系/想确认的人，开关自动替他开着。
        LossPrefs.settleIfNeeded(this, repo)
        LossMode.on = LossPrefs.on(this)
        // 先把颜色定下来再画第一帧：不然夜里打开会先闪一下白屏。
        applyNightMode(this)
        // 若已开启自动备份，在后台做一次每日备份（距上次超过 24 小时才真正写盘）
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { BackupManager.autoBackupIfNeeded(this@MainActivity, repo) }
        }
        setContent {
            // 深色模式：自动档下每天 18:00 到次日 05:59（累进时间 29:59）启用；
            // 手动钉住的那两档不受时段影响。不看系统开关 ——
            // 这个 app 的「晚上」由它自己的逻辑日定义。
            LaunchedEffect(Unit) {
                while (true) {
                    applyNightMode(this@MainActivity)
                    // 睡到下一个整分再查，跨度最长不超过半分钟，边界切换不会迟太久。
                    delay(30_000L)
                }
            }
            MoodLogTheme {
                MoodApp(repo = repo)
            }
        }
    }
}
