package com.example.llmcar.service

import android.app.*
import android.content.Intent
import android.graphics.PixelFormat
import android.os.*
import android.view.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.*
import androidx.savedstate.*
import com.example.llmcar.LlmCarApp
import com.example.llmcar.MainActivity
import com.example.llmcar.R
import com.example.llmcar.ui.theme.LlmCarTheme
import kotlin.math.abs

class FloatingButtonService : Service(), SavedStateRegistryOwner, LifecycleOwner {

    private lateinit var wm: WindowManager
    private var view: View? = null
    private lateinit var params: WindowManager.LayoutParams
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        startForeground(NOTIF_ID, notif())
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        attach()
    }

    private fun attach() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 40; y = 300 }

        val cv = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setViewTreeLifecycleOwner(this@FloatingButtonService)
            setViewTreeSavedStateRegistryOwner(this@FloatingButtonService)
            setContent { LlmCarTheme { Bubble { openApp() } } }
        }
        view = cv
        wm.addView(view, params)
        touch(cv)
    }

    private fun touch(v: View) {
        var ix = 0; var iy = 0; var tx = 0f; var ty = 0f; var drag = false
        v.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    ix = params.x; iy = params.y; tx = e.rawX; ty = e.rawY
                    drag = false; true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - tx; val dy = e.rawY - ty
                    if (abs(dx) > 12 || abs(dy) > 12) drag = true
                    params.x = ix + dx.toInt(); params.y = iy + dy.toInt()
                    wm.updateViewLayout(v, params); true
                }
                MotionEvent.ACTION_UP -> { if (!drag) openApp() else snap(v); true }
                else -> false
            }
        }
    }

    private fun snap(v: View) {
        val w = resources.displayMetrics.widthPixels
        params.x = if (params.x + v.width / 2 < w / 2) 40 else w - v.width - 40
        params.x = params.x.coerceAtLeast(0)
        wm.updateViewLayout(v, params)
    }

    private fun openApp() {
        startActivity(Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
    }

    private fun notif(): Notification {
        val pi = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, LlmCarApp.CHANNEL_FLOATING)
            .setContentTitle(getString(R.string.floating_notification_title))
            .setContentText(getString(R.string.floating_notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pi).setOngoing(true).build()
    }

    override fun onDestroy() {
        view?.let { runCatching { wm.removeView(it) } }
        view = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object { const val NOTIF_ID = 1002 }
}

@Composable
private fun Bubble(onClick: () -> Unit) {
    Box(Modifier.size(64.dp), Alignment.Center) {
        Surface(
            shape = CircleShape,
            color = Color(0xFF7C4DFF),
            shadowElevation = 8.dp,
            onClick = onClick,
            modifier = Modifier.size(56.dp)
        ) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Icon(Icons.Default.AutoAwesome, "LLM", tint = Color.White)
            }
        }
    }
}