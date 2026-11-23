package com.tarek.whtranslator

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.ImageButton
import android.widget.Toast

class TranslationAccessibilityService : AccessibilityService() {

    private lateinit var overlayManager: OverlayManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var floatingBtn: View? = null
    private var lastInputNode: AccessibilityNodeInfo? = null
    private var lastText: String = ""
    private val WHATSAPP_PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b")

    override fun onServiceConnected() {
        super.onServiceConnected()
        overlayManager = OverlayManager(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        if (Settings.canDrawOverlays(this)) {
            showFloatingButton()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString() ?: return
        if (pkg !in WHATSAPP_PACKAGES) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                val node = event.source ?: return
                val text = event.text.joinToString("").trim()
                if (text.isNotEmpty() && text != lastText) {
                    lastText = text
                    lastInputNode?.recycle()
                    lastInputNode = AccessibilityNodeInfo.obtain(node)
                    // Muestra botón flotante si hay texto suficiente
                    if (text.length >= 3) {
                        mainHandler.post { floatingBtn?.visibility = View.VISIBLE }
                    }
                }
                node.recycle()
            }

            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // Si el usuario sale de WhatsApp, ocultamos el overlay
                if (pkg !in WHATSAPP_PACKAGES) {
                    mainHandler.post {
                        floatingBtn?.visibility = View.GONE
                        overlayManager.dismiss()
                    }
                }
            }

            AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                val node = event.source
                if (node != null && node.className?.contains("EditText") == true) {
                    lastInputNode?.recycle()
                    lastInputNode = AccessibilityNodeInfo.obtain(node)
                    val nodeText = node.text?.toString() ?: ""
                    if (nodeText.isNotBlank()) lastText = nodeText
                    node.recycle()
                }
            }
        }
    }

    private fun showFloatingButton() {
        if (floatingBtn != null) return
        val wm = windowManager ?: return

        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.floating_translate_btn, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = 16
            y = 220
        }

        view.setOnClickListener {
            val currentText = getCurrentWhatsAppText() ?: lastText
            if (currentText.isNotBlank()) {
                overlayManager.show(currentText, lastInputNode)
            } else {
                Toast.makeText(this, "Escribe algo en WhatsApp primero", Toast.LENGTH_SHORT).show()
            }
        }

        view.visibility = View.GONE
        wm.addView(view, params)
        floatingBtn = view
    }

    private fun getCurrentWhatsAppText(): String? {
        val node = lastInputNode ?: return null
        return try {
            node.refresh()
            node.text?.toString()?.trim()
        } catch (e: Exception) {
            null
        }
    }

    override fun onInterrupt() {
        mainHandler.post {
            overlayManager.dismiss()
            floatingBtn?.let {
                try { windowManager?.removeView(it) } catch (e: Exception) { }
                floatingBtn = null
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        onInterrupt()
        lastInputNode?.recycle()
        lastInputNode = null
    }
}
