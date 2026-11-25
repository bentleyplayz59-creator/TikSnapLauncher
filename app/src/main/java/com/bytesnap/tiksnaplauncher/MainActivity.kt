package com.bytesnap.tiksnaplauncher

import android.app.Activity
import android.app.AlertDialog
import android.app.SearchManager
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextPaint
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private data class LauncherApp(val label: String, val packageName: String, val icon: Drawable)

class MainActivity : Activity() {
    private lateinit var surface: LauncherSurface

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(105, 54, 190)
        window.navigationBarColor = Color.rgb(28, 24, 44)
        window.decorView.systemUiVisibility = 0
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
        surface = LauncherSurface(
            activity = this,
            installedApps = loadApps(),
            openPackage = { packages -> openPackage(*packages.toTypedArray()) },
            openSearch = ::openSearch,
            showMenu = ::showMenu
        )
        setContentView(surface)
    }

    private fun loadApps(): List<LauncherApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return try {
            packageManager.queryIntentActivities(intent, 0)
                .mapNotNull { item ->
                    val activityInfo = item.activityInfo ?: return@mapNotNull null
                    if (activityInfo.packageName == packageName) return@mapNotNull null
                    LauncherApp(
                        item.loadLabel(packageManager)?.toString() ?: activityInfo.packageName,
                        activityInfo.packageName,
                        item.loadIcon(packageManager)
                    )
                }
                .distinctBy { it.packageName }
                .sortedBy { it.label.lowercase(Locale.getDefault()) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun openPackage(vararg packages: String) {
        for (name in packages) {
            val intent = packageManager.getLaunchIntentForPackage(name) ?: continue
            try {
                startActivity(intent)
                return
            } catch (_: Exception) { }
        }
        Toast.makeText(this, "That app isn't installed.", Toast.LENGTH_SHORT).show()
    }

    private fun openSearch(query: String) {
        try {
            startActivity(Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, query))
        } catch (_: Exception) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(query))))
            } catch (_: Exception) {
                openPackage("com.google.android.googlequicksearchbox", "com.android.chrome")
            }
        }
    }

    private fun showMenu() {
        AlertDialog.Builder(this)
            .setTitle("TikSnapLauncher")
            .setItems(arrayOf("Open TikSnap", "Search", "App drawer", "Launcher information")) { _, which ->
                when (which) {
                    0 -> openPackage("com.bytesnap.tiksnap")
                    1 -> openSearch("")
                    2 -> surface.openDrawer()
                    3 -> AlertDialog.Builder(this)
                        .setTitle("TikSnapLauncher")
                        .setMessage("A custom two-page Android Home launcher. Swipe between the standard home page and the TikSnap icon page. Swipe up to browse installed apps.")
                        .setPositiveButton("OK", null).show()
                }
            }.show()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        surface.closeDrawer()
        surface.setPage(0)
    }

    @Deprecated("Deprecated in Android API")
    override fun onBackPressed() {
        when {
            surface.isDrawerOpen() -> surface.closeDrawer()
            surface.currentPage() == 1 -> surface.setPage(0)
            else -> super.onBackPressed()
        }
    }
}

private class LauncherSurface(
    private val activity: Activity,
    private val installedApps: List<LauncherApp>,
    private val openPackage: (List<String>) -> Unit,
    private val openSearch: (String) -> Unit,
    private val showMenu: () -> Unit
) : View(activity) {
    private val density = resources.displayMetrics.density
    private fun dp(value: Float) = value * density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
    private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = dp(25f)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = dp(11f)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = dp(13f)
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val clockPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = dp(54f)
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    }
    private val mutedPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconCache = HashMap<String, Drawable>()
    private val handler = Handler(Looper.getMainLooper())
    private var page = 0
    private var drawer = false
    private var drawerScroll = 0
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private val tick = object : Runnable {
        override fun run() {
            if (page == 0 && !drawer) invalidate()
            handler.postDelayed(this, 30_000L)
        }
    }

    private val tikSnapIcon: Drawable? by lazy {
        try { activity.packageManager.getApplicationIcon("com.bytesnap.tiksnap") }
        catch (_: Exception) { activity.getDrawable(android.R.drawable.sym_def_app_icon) }
    }

    init {
        isFocusable = true
        isClickable = true
        handler.post(tick)
    }

    fun currentPage() = page
    fun isDrawerOpen() = drawer
    fun openDrawer() { drawer = true; drawerScroll = 0; invalidate() }
    fun closeDrawer() { drawer = false; drawerScroll = 0; invalidate() }

    fun setPage(value: Int) {
        page = value.coerceIn(0, 1)
        drawer = false
        activity.window.statusBarColor = if (page == 0) Color.rgb(105, 54, 190) else Color.rgb(0, 105, 120)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (drawer) {
            drawDrawer(canvas)
            return
        }
        if (page == 0) drawNormalHome(canvas) else drawTikSnapHome(canvas)
        drawPageIndicator(canvas)
    }

    private fun drawWallpaper(canvas: Canvas, second: Boolean) {
        val colors = if (second) intArrayOf(Color.rgb(0, 91, 111), Color.rgb(9, 139, 148), Color.rgb(25, 48, 89))
                     else intArrayOf(Color.rgb(109, 45, 189), Color.rgb(77, 75, 177), Color.rgb(27, 39, 78))
        canvas.drawColor(colors[0])
        paint.shader = android.graphics.LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), colors, null, android.graphics.Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null
        mutedPaint.color = Color.argb(24, 255, 255, 255)
        canvas.drawCircle(width * .92f, height * .22f, width * .55f, mutedPaint)
        mutedPaint.color = Color.argb(18, 255, 255, 255)
        canvas.drawCircle(width * .05f, height * .67f, width * .62f, mutedPaint)
    }

    private fun drawNormalHome(canvas: Canvas) {
        drawWallpaper(canvas, false)
        val side = dp(22f)
        val now = Date()
        clockPaint.textSize = dp(54f)
        canvas.drawText(SimpleDateFormat("h:mm", Locale.getDefault()).format(now), side, dp(100f), clockPaint)
        subPaint.textSize = dp(15f)
        canvas.drawText(SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(now), side + dp(3f), dp(126f), subPaint)

        // Weather card: tappable live search rather than a screenshot.
        val card = RectF(side, dp(151f), width - side, dp(222f))
        drawRounded(canvas, card, dp(18f), Color.argb(40, 255, 255, 255))
        subPaint.textSize = dp(14f)
        canvas.drawText("Weather", card.left + dp(16f), card.top + dp(27f), subPaint)
        titlePaint.textSize = dp(29f)
        canvas.drawText("Tap to check", card.left + dp(16f), card.top + dp(58f), titlePaint)
        titlePaint.textSize = dp(25f)
        canvas.drawText("☀", card.right - dp(54f), card.top + dp(53f), titlePaint)

        val startY = height * .43f
        val iconPackages = arrayOf(
            arrayOf("com.sec.android.app.samsungapps", "com.android.vending"),
            arrayOf("com.sec.android.gallery3d", "com.google.android.apps.photos"),
            arrayOf("com.android.vending"),
            arrayOf("com.google.android.googlequicksearchbox", "com.google.android.gms")
        )
        val labels = arrayOf("Galaxy Store", "Gallery", "Play Store", "Google")
        for (i in 0..3) {
            val x = width * (i + .5f) / 4f
            val app = findApp(*iconPackages[i])
            drawAppIcon(canvas, app?.icon ?: fallbackIcon(), x, startY, dp(48f))
            drawLabel(canvas, labels[i], x, startY + dp(64f))
        }

        drawSearchBar(canvas, height * .68f)
        drawDock(canvas)
    }

    private fun drawTikSnapHome(canvas: Canvas) {
        drawWallpaper(canvas, true)
        titlePaint.textSize = dp(21f)
        canvas.drawText("TikSnap", dp(22f), dp(42f), titlePaint)
        val columns = 4
        val rows = 5
        val top = dp(70f)
        val bottom = height * .86f
        val cellH = (bottom - top) / rows
        val iconSize = min(dp(52f), width / 7f)
        for (row in 0 until rows) {
            for (column in 0 until columns) {
                val cx = width * (column + .5f) / columns
                val cy = top + row * cellH + cellH * .42f
                drawAppIcon(canvas, tikSnapIcon ?: fallbackIcon(), cx, cy, iconSize)
                drawLabel(canvas, "TikSnap", cx, cy + iconSize * .78f)
            }
        }
        drawDock(canvas, tikSnapOnly = true)
    }

    private fun drawDock(canvas: Canvas, tikSnapOnly: Boolean = false) {
        val dock = RectF(dp(12f), height - dp(77f), width - dp(12f), height - dp(13f))
        drawRounded(canvas, dock, dp(25f), Color.argb(58, 16, 16, 28))
        val targets = if (tikSnapOnly) arrayOf("TikSnap", "TikSnap", "TikSnap", "TikSnap")
                      else arrayOf("Phone", "Messages", "Chrome", "Camera")
        val packages = if (tikSnapOnly) Array(4) { arrayOf("com.bytesnap.tiksnap") }
                       else arrayOf(
                           arrayOf("com.samsung.android.dialer", "com.google.android.dialer", "com.android.dialer"),
                           arrayOf("com.samsung.android.messaging", "com.google.android.apps.messaging"),
                           arrayOf("com.android.chrome"),
                           arrayOf("com.sec.android.app.camera", "com.android.camera2")
                       )
        for (i in 0..3) {
            val cx = width * (i + .5f) / 4f
            val icon = if (tikSnapOnly) tikSnapIcon ?: fallbackIcon() else findApp(*packages[i])?.icon ?: fallbackIcon()
            drawAppIcon(canvas, icon, cx, dock.centerY(), dp(43f))
        }
    }

    private fun drawSearchBar(canvas: Canvas, y: Float) {
        val rect = RectF(dp(18f), y, width - dp(18f), y + dp(48f))
        drawRounded(canvas, rect, dp(25f), Color.WHITE)
        mutedPaint.color = Color.rgb(66, 133, 244)
        canvas.drawCircle(rect.left + dp(23f), rect.centerY(), dp(9f), mutedPaint)
        mutedPaint.color = Color.rgb(234, 67, 53)
        canvas.drawCircle(rect.left + dp(28f), rect.centerY() - dp(4f), dp(5f), mutedPaint)
        mutedPaint.color = Color.rgb(251, 188, 5)
        canvas.drawCircle(rect.left + dp(28f), rect.centerY() + dp(4f), dp(5f), mutedPaint)
        val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.DKGRAY; textSize = dp(14f) }
        canvas.drawText("Search", rect.left + dp(48f), rect.centerY() + dp(5f), p)
        val mic = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(66, 133, 244); textSize = dp(20f) }
        canvas.drawText("●", rect.right - dp(30f), rect.centerY() + dp(7f), mic)
    }

    private fun drawPageIndicator(canvas: Canvas) {
        val y = height - dp(91f)
        for (i in 0..1) {
            mutedPaint.color = if (i == page) Color.WHITE else Color.argb(110, 255, 255, 255)
            canvas.drawCircle(width / 2f + (i - .5f) * dp(17f), y, dp(if (i == page) 4f else 3f), mutedPaint)
        }
    }

    private fun drawDrawer(canvas: Canvas) {
        canvas.drawColor(Color.rgb(19, 24, 37))
        titlePaint.textSize = dp(25f)
        canvas.drawText("Apps", dp(22f), dp(42f), titlePaint)
        val search = RectF(dp(16f), dp(57f), width - dp(16f), dp(103f))
        drawRounded(canvas, search, dp(24f), Color.rgb(47, 54, 69))
        subPaint.color = Color.LTGRAY
        canvas.drawText("Search apps", search.left + dp(18f), search.centerY() + dp(5f), subPaint)
        subPaint.color = Color.WHITE

        val columns = 4
        val cellW = width / columns.toFloat()
        val cellH = dp(98f)
        val top = dp(121f)
        val visible = max(1, ((height - top) / cellH).toInt() + 1)
        val rows = (installedApps.size + columns - 1) / columns
        drawerScroll = drawerScroll.coerceIn(0, max(0, rows - visible))
        val first = drawerScroll * columns
        val last = min(installedApps.size, (drawerScroll + visible) * columns)
        for (index in first until last) {
            val row = index / columns - drawerScroll
            val col = index % columns
            val cx = (col + .5f) * cellW
            val cy = top + row * cellH + dp(28f)
            drawAppIcon(canvas, installedApps[index].icon, cx, cy, dp(43f))
            drawLabel(canvas, installedApps[index].label, cx, cy + dp(36f))
        }
    }

    private fun launch(vararg packages: String) = openPackage(packages.toList())

    private fun findApp(vararg packages: String): LauncherApp? =
        packages.firstNotNullOfOrNull { pkg -> installedApps.firstOrNull { it.packageName == pkg } }

    private fun fallbackIcon(): Drawable =
        activity.getDrawable(android.R.drawable.sym_def_app_icon)!!

    private fun drawAppIcon(canvas: Canvas, icon: Drawable, cx: Float, cy: Float, size: Float) {
        val save = canvas.save()
        val bounds = RectF(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f)
        icon.setBounds(bounds.left.toInt(), bounds.top.toInt(), bounds.right.toInt(), bounds.bottom.toInt())
        icon.draw(canvas)
        canvas.restoreToCount(save)
    }

    private fun drawLabel(canvas: Canvas, text: String, cx: Float, baseline: Float) {
        val maxWidth = width / 4f - dp(8f)
        val fitted = android.text.TextUtils.ellipsize(text, labelPaint, maxWidth, android.text.TextUtils.TruncateAt.END).toString()
        canvas.drawText(fitted, cx, baseline, labelPaint)
    }

    private fun drawRounded(canvas: Canvas, rect: RectF, radius: Float, color: Int) {
        mutedPaint.color = color
        canvas.drawRoundRect(rect, radius, radius, mutedPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                downTime = System.currentTimeMillis()
                return true
            }
            MotionEvent.ACTION_UP -> {
                val dx = event.x - downX
                val dy = event.y - downY
                val threshold = max(dp(55f), width * .13f)
                if (drawer) {
                    if (abs(dy) > dp(35f) && abs(dy) > abs(dx)) {
                        drawerScroll = max(0, drawerScroll + if (dy < 0) max(1, (abs(dy) / dp(98f)).toInt()) else -max(1, (abs(dy) / dp(98f)).toInt()))
                        invalidate()
                    } else if (abs(dx) > threshold) closeDrawer()
                    else handleDrawerTap(event.x, event.y)
                    return true
                }
                if (abs(dx) > threshold && abs(dx) > abs(dy)) {
                    setPage(if (dx < 0) 1 else 0)
                } else if (dy < -dp(65f) && abs(dy) > abs(dx) && page == 0) {
                    openDrawer()
                } else if (dy > dp(65f) && abs(dy) > abs(dx) && page == 1) {
                    setPage(0)
                } else {
                    handleHomeTap(event.x, event.y)
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> return true
        }
        return true
    }

    private fun handleDrawerTap(x: Float, y: Float) {
        if (y < dp(110f)) {
            openSearch("")
            return
        }
        val columns = 4
        val cellW = width / columns.toFloat()
        val cellH = dp(98f)
        val top = dp(121f)
        if (y < top) return
        val col = (x / cellW).toInt().coerceIn(0, columns - 1)
        val row = ((y - top) / cellH).toInt() + drawerScroll
        val index = row * columns + col
        if (index in installedApps.indices) launch(installedApps[index].packageName)
    }

    private fun handleHomeTap(x: Float, y: Float) {
        val nx = x / width.toFloat()
        val ny = y / height.toFloat()
        if (page == 1) {
            if (ny > .87f) launch("com.bytesnap.tiksnap")
            else if (ny > .08f && ny < .84f) launch("com.bytesnap.tiksnap")
            else if (nx > .85f && ny < .08f) showMenu()
            return
        }
        if (ny < .25f && ny > .13f) {
            openSearch("weather")
            return
        }
        if (ny in .40f.. .60f) {
            val col = (nx * 4).toInt().coerceIn(0, 3)
            when (col) {
                0 -> launch("com.sec.android.app.samsungapps", "com.android.vending")
                1 -> launch("com.sec.android.gallery3d", "com.google.android.apps.photos")
                2 -> launch("com.android.vending")
                3 -> launch("com.google.android.googlequicksearchbox", "com.android.chrome")
            }
            return
        }
        if (ny in .62f.. .76f) {
            openSearch("")
            return
        }
        if (ny > .82f) {
            when ((nx * 4).toInt().coerceIn(0, 3)) {
                0 -> launch("com.samsung.android.dialer", "com.google.android.dialer", "com.android.dialer")
                1 -> launch("com.samsung.android.messaging", "com.google.android.apps.messaging")
                2 -> launch("com.android.chrome")
                3 -> launch("com.sec.android.app.camera", "com.android.camera2")
            }
        }
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(tick)
        super.onDetachedFromWindow()
    }
}
