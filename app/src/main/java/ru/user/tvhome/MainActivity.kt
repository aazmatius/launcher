package ru.user.tvhome

import android.app.Activity
import android.app.ActivityManager
import android.content.*
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.media.tv.TvContract
import android.media.tv.TvInputManager
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*

class MainActivity : Activity() {
    private lateinit var scroll: ScrollView
    private lateinit var content: LinearLayout
    private var memView: TextView? = null
    private var first: View? = null
    private val prefs by lazy { getSharedPreferences("tvhome", MODE_PRIVATE) }
    private val dens by lazy { resources.displayMetrics.density }
    private fun px(v: Int) = (v * dens).toInt()
    private val defaults = listOf(
        "ru.kinopoisk.tv", "com.liskovsoft.smarttubetv", "com.liskovsoft.smarttubetv.beta",
        "net.gtvbox.videoplayer", "ru.rutube.app", "com.vk.tv", "mobi.zona"
    )
    private val pkgReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { build() }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.setBackgroundDrawable(ColorDrawable(0xFF10131A.toInt()))
        scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(40), px(20), px(40), px(20))
        }
        scroll.addView(content)
        setContentView(scroll)
        registerReceiver(pkgReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        })
        build()
    }

    override fun onDestroy() { unregisterReceiver(pkgReceiver); super.onDestroy() }
    override fun onResume() { super.onResume(); updateMem() }
    override fun onBackPressed() {}
    override fun onNewIntent(i: Intent?) {
        super.onNewIntent(i); scroll.scrollTo(0, 0); first?.requestFocus()
    }

    private fun bg(focused: Boolean) = GradientDrawable().apply {
        cornerRadius = px(14).toFloat()
        setColor(if (focused) 0xFF3A4A6B.toInt() else 0xFF1E2430.toInt())
        if (focused) setStroke(px(2), Color.WHITE)
    }

    private fun label(t: String, size: Float, color: Int) = TextView(this).apply {
        text = t; textSize = size; setTextColor(color)
    }

    private fun tile(name: String, icon: Drawable?, banner: Boolean, click: () -> Unit, long: (() -> Unit)? = null): View {
        val f = FrameLayout(this)
        f.layoutParams = LinearLayout.LayoutParams(px(190), px(108)).apply { setMargins(px(8), px(8), px(8), px(8)) }
        f.isFocusable = true; f.isClickable = true; f.background = bg(false)
        if (icon != null && banner) {
            f.addView(ImageView(this).apply { setImageDrawable(icon); scaleType = ImageView.ScaleType.CENTER_CROP })
        } else {
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(px(6), px(6), px(6), px(6)) }
            if (icon != null) col.addView(ImageView(this).apply { setImageDrawable(icon); layoutParams = LinearLayout.LayoutParams(px(48), px(48)) })
            col.addView(label(name, 14f, Color.WHITE).apply { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END; gravity = Gravity.CENTER })
            f.addView(col, FrameLayout.LayoutParams(-1, -1))
        }
        f.setOnClickListener { try { click() } catch (e: Exception) { Toast.makeText(this, "Не удалось открыть: $name", Toast.LENGTH_SHORT).show() } }
        if (long != null) f.setOnLongClickListener { long(); true }
        f.setOnFocusChangeListener { v, has ->
            v.animate().scaleX(if (has) 1.08f else 1f).scaleY(if (has) 1.08f else 1f).setDuration(100).start()
            v.background = bg(has)
        }
        if (first == null) first = f
        return f
    }

    private fun row(title: String, tiles: List<View>) {
        content.addView(label(title, 16f, 0xFF8A93A6.toInt()).apply { setPadding(px(8), px(14), 0, 0) })
        val line = LinearLayout(this).apply { setPadding(px(4), px(4), px(4), px(4)) }
        tiles.forEach { line.addView(it) }
        content.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false; clipToPadding = false; addView(line)
        })
    }

    private fun build() {
        content.removeAllViews(); first = null
        val pm = packageManager

        val head = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        head.addView(TextClock(this).apply { format24Hour = "HH:mm"; format12Hour = "HH:mm"; textSize = 36f; setTextColor(Color.WHITE) })
        memView = label("", 16f, 0xFF8A93A6.toInt()).apply {
            gravity = Gravity.END; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        head.addView(memView)
        content.addView(head)

        // Входы
        val inputs = ArrayList<View>()
        inputs.add(tile("ТВ (антенна)", null, false, {
            startActivity(Intent().setClassName("com.mediatek.wwtv.tvcenter", "com.mitv.livetv.nav.TurnkeyUiMainActivity"))
        }))
        try {
            val tm = getSystemService(Context.TV_INPUT_SERVICE) as TvInputManager
            for (i in tm.tvInputList.filter { it.isPassthroughInput }.sortedBy { it.id }) {
                val n = i.loadCustomLabel(this)?.toString() ?: i.loadLabel(this)?.toString() ?: i.id
                inputs.add(tile(n, null, false, {
                    startActivity(Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(i.id)))
                }))
            }
        } catch (e: Exception) { }
        row("Входы", inputs)

        // Приложения
        val apps = (queryApps(Intent.CATEGORY_LEANBACK_LAUNCHER) + queryApps(Intent.CATEGORY_LAUNCHER))
            .distinctBy { it.activityInfo.packageName }
            .sortedBy { it.loadLabel(pm).toString().lowercase() }
        val installed = apps.map { it.activityInfo.packageName }.toSet()
        val fav = prefs.getStringSet("fav", null) ?: defaults.filter { it in installed }.toSet()
        fun appTile(ri: android.content.pm.ResolveInfo): View {
            val p = ri.activityInfo.packageName
            val ban = ri.activityInfo.loadBanner(pm)
            return tile(ri.loadLabel(pm).toString(), ban ?: ri.loadIcon(pm), ban != null, {
                startActivity(Intent(Intent.ACTION_MAIN).setClassName(p, ri.activityInfo.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }, {
                val s = HashSet(fav); if (!s.remove(p)) s.add(p)
                prefs.edit().putStringSet("fav", s).apply()
                Toast.makeText(this, if (p in s) "Добавлено в избранное" else "Убрано из избранного", Toast.LENGTH_SHORT).show()
                build()
            })
        }
        row("Избранное (удерживай OK на приложении, чтобы добавить или убрать)", apps.filter { it.activityInfo.packageName in fav }.map { appTile(it) })

        // Система
        row("Система", listOf(
            tile("Настройки", null, false, { startActivity(Intent(Settings.ACTION_SETTINGS)) }),
            tile("Сеть", null, false, { startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }),
            tile("Штатный лаунчер", null, false, {
                startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage("com.google.android.tvlauncher"))
            }),
            tile("Освободить память", null, false, { freeMemory() })
        ))

        row("Все приложения", apps.map { appTile(it) })
        updateMem()
        first?.requestFocus()
    }

    private fun queryApps(cat: String) =
        packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(cat), 0)
            .filter { it.activityInfo.packageName != packageName }

    private fun memInfo(): ActivityManager.MemoryInfo {
        val mi = ActivityManager.MemoryInfo()
        (getSystemService(ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)
        return mi
    }

    private fun updateMem() {
        val mi = memInfo()
        memView?.text = "RAM свободно: ${mi.availMem shr 20} из ${mi.totalMem shr 20} МБ"
    }

    private fun freeMemory() {
        val before = memInfo().availMem
        val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        packageManager.getInstalledApplications(0)
            .filter { it.packageName != packageName && (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
            .forEach { am.killBackgroundProcesses(it.packageName) }
        content.postDelayed({
            updateMem()
            val gain = (memInfo().availMem - before) shr 20
            Toast.makeText(this, "Освобождено: ${if (gain > 0) gain else 0} МБ", Toast.LENGTH_SHORT).show()
        }, 1000)
    }
}
