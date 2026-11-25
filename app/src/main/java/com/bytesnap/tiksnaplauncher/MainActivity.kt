package com.bytesnap.tiksnaplauncher

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.provider.SearchRecentSuggestions
import android.app.SearchManager
import android.net.Uri
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.widget.Toast
import android.content.pm.ResolveInfo
import android.text.TextUtils
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private enum class LaunchTarget {
    TIKSNAP, GALAXY_STORE, GALLERY, PLAY_STORE, GOOGLE,
    PHONE, MESSAGES, CHROME, CAMERA, SEARCH, WEATHER
}

private data class DrawerApp(
    val label: String,
    val packageName: String,
    val icon: Drawable
)

class MainActivity : Activity() {
    private lateinit var launcherSurface: LauncherSurface

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val normal = loadAssetBitmap("normal_home_screen.png")?.let {
            cropBitmap(it, 20, 56, 341, 680)
        }
        val tiksnap = loadAssetBitmap("tiksnap_home_screen.png")?.let {
            cropBitmap(it, 0, 82, 1080, 2220)
        }

        launcherSurface = LauncherSurface(
            activity = this,
            normalReference = normal,
            tikSnapReference = tiksnap,
            drawerApps = loadDrawerApps(),
            launchTarget = ::launchTarget,
            search = { query -> openSearch(query) },
            openMenu = ::showLauncherMenu,
            pageChanged = ::updateSystemBars
        )

        setContentView(launcherSurface)
        updateSystemBars(0)
    }

    private fun loadAssetBitmap(name: String): Bitmap? {
        return try {
            assets.open(name).use { BitmapFactory.decodeStream(it) }
        } catch (_: Exception) {
            null
        }
    }

    private fun cropBitmap(source: Bitmap, left: Int, top: Int, right: Int, bottom: Int): Bitmap {
        val x = left.coerceIn(0, source.width - 1)
        val y = top.coerceIn(0, source.height - 1)
        val r = right.coerceIn(x + 1, source.width)
        val b = bottom.coerceIn(y + 1, source.height)
        return Bitmap.createBitmap(source, x, y, r - x, b - y)
    }

    private fun loadDrawerApps(): List<DrawerApp> {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return try {
            packageManager.queryIntentActivities(launcherIntent, 0)
                .mapNotNull { info ->
                    val packageName = info.activityInfo?.packageName ?: return@mapNotNull null
                    if (packageName == packageNameForThisApp()) return@mapNotNull null
                    DrawerApp(
                        label = info.loadLabel(packageManager)?.toString() ?: packageName,
                        packageName = packageName,
                        icon = info.loadIcon(packageManager)
                    )
                }
                .distinctBy { it.packageName }
                .sortedBy { it.label.lowercase() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun packageNameForThisApp(): String = applicationContext.packageName

    private fun updateSystemBars(page: Int) {
        val status = if (page == 0) Color.rgb(111, 35, 205) else Color.rgb(0, 96, 120)
        val navigation = if (page == 0) Color.rgb(83, 102, 199) else Color.rgb(0, 96, 120)
        window.statusBarColor = status
        window.navigationBarColor = navigation
        window.decorView.systemUiVisibility = 0
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
    }

    private fun launchTarget(target: LaunchTarget) {
        when (target) {
            LaunchTarget.TIKSNAP -> launchFirst(
                "com.bytesnap.tiksnap"
            )
            LaunchTarget.GALAXY_STORE -> launchFirst("com.sec.android.app.samsungapps")
            LaunchTarget.GALLERY -> launchFirst("com.sec.android.gallery3d")
            LaunchTarget.PLAY_STORE -> launchFirst("com.android.vending")
            LaunchTarget.GOOGLE -> launchFirst("com.google.android.googlequicksearchbox")
            LaunchTarget.PHONE -> launchFirst(
                "com.samsung.android.dialer",
                "com.google.android.dialer",
                "com.android.dialer"
            )
            LaunchTarget.MESSAGES -> launchFirst(
                "com.samsung.android.messaging",
                "com.google.android.apps.messaging"
            )
            LaunchTarget.CHROME -> launchFirst("com.android.chrome")
            LaunchTarget.CAMERA -> launchFirst(
                "com.sec.android.app.camera",
                "com.android.camera2"
            )
            LaunchTarget.SEARCH -> openSearch("")
            LaunchTarget.WEATHER -> openSearch("weather in Blacksburg")
        }
    }

    private fun launchFirst(vararg packageNames: String) {
        for (packageName in packageNames) {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    startActivity(launchIntent)
                    return
                } catch (_: Exception) {
                }
            }
        }
        Toast.makeText(this, "That app is not installed on this device.", Toast.LENGTH_SHORT).show()
    }

    private fun openSearch(query: String) {
        try {
            val intent = Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, query)
            startActivity(intent)
        } catch (_: Exception) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(query))))
            } catch (_: Exception) {
                launchFirst("com.google.android.googlequicksearchbox", "com.android.chrome")
            }
        }
    }

    private fun showLauncherMenu() {
        val options = arrayOf("Open TikSnap", "Google Search", "Launcher information")
        AlertDialog.Builder(this)
            .setTitle("TikSnapLauncher")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> launchTarget(LaunchTarget.TIKSNAP)
                    1 -> openSearch("")
                    2 -> AlertDialog.Builder(this)
                        .setTitle("TikSnapLauncher 1.0.0")
                        .setMessage(
                            "A two-page Android Home launcher. Swipe between the standard Samsung-style home screen and the TikSnap icon wall. Swipe up on the normal home screen to open the installed-app drawer."
                        )
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
            .show()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        launcherSurface.closeDrawer()
        launcherSurface.setPage(0)
    }

    @Deprecated("Deprecated in Android API")
    override fun onBackPressed() {
        if (launcherSurface.isDrawerOpen()) {
            launcherSurface.closeDrawer()
        } else if (launcherSurface.currentPage() == 1) {
            launcherSurface.setPage(0)
        }
    }
}

private class LauncherSurface(
    activity: Activity,
    private val normalReference: Bitmap?,
    private val tikSnapReference: Bitmap?,
    private val drawerApps: List<DrawerApp>,
    private val launchTarget: (LaunchTarget) -> Unit,
    private val search: (String) -> Unit,
    private val openMenu: () -> Unit,
    private val pageChanged: (Int) -> Unit
) : View(activity) {

    private val density = resources.displayMetrics.density
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 16f * density
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val smallTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 12f * density
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }
    private val drawerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var page = 0
    private var drawerOpen = false
    private var drawerScrollRow = 0
    private var downX = 0f
    private var downY = 0f

    init {
        isFocusable = true
        isClickable = true
        setBackgroundColor(Color.rgb(0, 96, 120))
    }

    fun currentPage(): Int = page
    fun isDrawerOpen(): Boolean = drawerOpen

    fun setPage(value: Int) {
        drawerOpen = false
        val next = value.coerceIn(0, 1)
        if (page != next) {
            page = next
            pageChanged(page)
        }
        invalidate()
    }

    fun closeDrawer() {
        drawerOpen = false
        drawerScrollRow = 0
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val reference = if (page == 0) normalReference else tikSnapReference
        if (reference != null) {
            canvas.drawBitmap(reference, null, RectF(0f, 0f, width.toFloat(), height.toFloat()), bitmapPaint)
        } else {
            canvas.drawColor(if (page == 0) Color.rgb(95, 65, 190) else Color.rgb(0, 96, 120))
            textPaint.textSize = 22f * density
            canvas.drawText(if (page == 0) "Home" else "TikSnap", 24f * density, 48f * density, textPaint)
        }
        if (drawerOpen) drawAppDrawer(canvas)
    }

    private fun drawAppDrawer(canvas: Canvas) {
        canvas.drawColor(Color.argb(248, 8, 22, 34))
        textPaint.textSize = 23f * density
        canvas.drawText("Apps", 22f * density, 38f * density, textPaint)

        val searchRect = RectF(16f * density, 54f * density, width - 16f * density, 98f * density)
        drawerPaint.color = Color.rgb(48, 60, 72)
        canvas.drawRoundRect(searchRect, 24f * density, 24f * density, drawerPaint)
        textPaint.textSize = 15f * density
        textPaint.color = Color.LTGRAY
        canvas.drawText("Search apps", 34f * density, 82f * density, textPaint)
        textPaint.color = Color.WHITE

        val columns = 4
        val cellWidth = width.toFloat() / columns
        val cellHeight = 102f * density
        val top = 116f * density
        val rows = (drawerApps.size + columns - 1) / columns
        val visibleRows = max(1, ((height - top) / cellHeight).toInt() + 1)
        val lastScroll = max(0, rows - visibleRows)
        drawerScrollRow = drawerScrollRow.coerceIn(0, lastScroll)

        val first = drawerScrollRow * columns
        val last = min(drawerApps.size, (drawerScrollRow + visibleRows) * columns)
        for (index in first until last) {
            val row = index / columns - drawerScrollRow
            val column = index % columns
            val centerX = column * cellWidth + cellWidth / 2f
            val cellTop = top + row * cellHeight
            val iconSize = 46f * density
            val iconTop = cellTop + 5f * density
            val icon = drawerApps[index].icon
            icon.setBounds(
                (centerX - iconSize / 2f).toInt(),
                iconTop.toInt(),
                (centerX + iconSize / 2f).toInt(),
                (iconTop + iconSize).toInt()
            )
            icon.draw(canvas)

            val availableWidth = cellWidth - 10f * density
            val label = TextUtils.ellipsize(
                drawerApps[index].label,
                smallTextPaint,
                availableWidth,
                TextUtils.TruncateAt.END
            ).toString()
            canvas.drawText(label, centerX, cellTop + 72f * density, smallTextPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                return true
            }
            MotionEvent.ACTION_UP -> {
                val dx = event.x - downX
                val dy = event.y - downY
                val horizontalThreshold = max(55f * density, width * 0.12f)
                val verticalThreshold = 70f * density

                if (drawerOpen) {
                    if (abs(dy) > verticalThreshold && abs(dy) > abs(dx)) {
                        if (dy < 0f) {
                            drawerScrollRow += max(1, (abs(dy) / (102f * density)).toInt())
                        } else if (drawerScrollRow > 0) {
                            drawerScrollRow = max(0, drawerScrollRow - max(1, (abs(dy) / (102f * density)).toInt()))
                        } else {
                            closeDrawer()
                        }
                        invalidate()
                    } else if (abs(dx) > horizontalThreshold) {
                        closeDrawer()
                    } else {
                        handleDrawerTap(event.x, event.y)
                    }
                    return true
                }

                if (abs(dx) > horizontalThreshold && abs(dx) > abs(dy)) {
                    setPage(if (dx < 0f) 1 else 0)
                } else if (dy < -verticalThreshold && abs(dy) > abs(dx) && page == 0) {
                    drawerOpen = true
                    drawerScrollRow = 0
                    invalidate()
                } else if (dy > verticalThreshold && abs(dy) > abs(dx) && page == 1) {
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
        if (y < 104f * density) {
            search("")
            return
        }
        val columns = 4
        val cellWidth = width.toFloat() / columns
        val cellHeight = 102f * density
        val top = 116f * density
        if (y < top) return
        val column = (x / cellWidth).toInt().coerceIn(0, columns - 1)
        val row = ((y - top) / cellHeight).toInt() + drawerScrollRow
        val index = row * columns + column
        if (index in drawerApps.indices) {
            val app = drawerApps[index]
            try {
                val intent = packageManager.getLaunchIntentForPackage(app.packageName)
                if (intent != null) context.startActivity(intent)
                else Toast.makeText(context, "Unable to open " + app.label, Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(context, "Unable to open " + app.label, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleHomeTap(x: Float, y: Float) {
        val nx = if (width == 0) 0f else x / width.toFloat()
        val ny = if (height == 0) 0f else y / height.toFloat()

        if (page == 1) {
            if (nx > 0.82f && ny < 0.09f) {
                openMenu()
                return
            }
            if (ny in 0.07f..0.70f) {
                val column = (nx * 4f).toInt().coerceIn(0, 3)
                val row = ((ny - 0.065f) / 0.126f).toInt()
                if (row in 0..4) launchTarget(LaunchTarget.TIKSNAP)
                return
            }
            if (ny in 0.80f..0.89f) {
                setPage(0)
                return
            }
            if (ny > 0.89f) {
                search("")
                return
            }
        } else {
            if (ny in 0.67f..0.82f) {
                val column = (nx * 4f).toInt().coerceIn(0, 3)
                val targets = arrayOf(
                    LaunchTarget.GALAXY_STORE,
                    LaunchTarget.GALLERY,
                    LaunchTarget.PLAY_STORE,
                    LaunchTarget.GOOGLE
                )
                launchTarget(targets[column])
                return
            }
            if (ny > 0.87f) {
                val column = (nx * 4f).toInt().coerceIn(0, 3)
                val targets = arrayOf(
                    LaunchTarget.PHONE,
                    LaunchTarget.MESSAGES,
                    LaunchTarget.CHROME,
                    LaunchTarget.CAMERA
                )
                launchTarget(targets[column])
                return
            }
            if (ny in 0.82f..0.87f) {
                setPage(1)
                return
            }
            if (ny in 0.54f..0.67f) {
                search("")
                return
            }
            if (ny in 0.28f..0.53f) {
                search("weather in Blacksburg")
                return
            }
        }
    }
}
