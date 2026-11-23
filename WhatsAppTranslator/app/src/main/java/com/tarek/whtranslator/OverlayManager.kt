package com.tarek.whtranslator

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.ArrayAdapter
import android.widget.Toast
import com.tarek.whtranslator.databinding.OverlayTranslatorBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var overlayView: View? = null
    private var binding: OverlayTranslatorBinding? = null
    private var targetNode: AccessibilityNodeInfo? = null

    fun show(originalText: String, node: AccessibilityNodeInfo?) {
        if (overlayView != null) {
            binding?.etOriginalText?.setText(originalText)
            targetNode = node?.let { AccessibilityNodeInfo.obtain(it) }
            return
        }

        targetNode = node?.let { AccessibilityNodeInfo.obtain(it) }

        val inflater = LayoutInflater.from(context)
        binding = OverlayTranslatorBinding.inflate(inflater)
        overlayView = binding!!.root

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
        }

        windowManager.addView(overlayView, params)

        setupOverlayContent(originalText)
    }

    private fun setupOverlayContent(originalText: String) {
        val b = binding ?: return

        // Idiomas
        val langAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, Prefs.LANGUAGES)
        langAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        b.spinnerOverlaySource.adapter = langAdapter
        b.spinnerOverlayTarget.adapter = langAdapter

        val sourceLang = Prefs.getSourceLang(context)
        val targetLang = Prefs.getTargetLang(context)
        val sourceIdx = Prefs.LANGUAGES.indexOfFirst { it.startsWith(sourceLang) }.coerceAtLeast(0)
        val targetIdx = Prefs.LANGUAGES.indexOf(targetLang).coerceAtLeast(1)
        b.spinnerOverlaySource.setSelection(sourceIdx)
        b.spinnerOverlayTarget.setSelection(targetIdx)

        b.etOriginalText.setText(originalText)

        b.btnClose.setOnClickListener { dismiss() }

        b.btnTranslate.setOnClickListener {
            val text = b.etOriginalText.text?.toString()?.trim() ?: ""
            if (text.isEmpty()) {
                Toast.makeText(context, "El texto está vacío", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val src = Prefs.langDisplayToApi(b.spinnerOverlaySource.selectedItem.toString())
            val tgt = b.spinnerOverlayTarget.selectedItem.toString()
            performTranslation(text, src, tgt)
        }

        b.btnReplace.setOnClickListener {
            val translated = b.etTranslatedText.text?.toString() ?: ""
            if (translated.isNotEmpty()) {
                replaceTextInWhatsApp(translated)
                dismiss()
            } else {
                Toast.makeText(context, "Primero traduce el texto", Toast.LENGTH_SHORT).show()
            }
        }

        b.btnCopyTranslation.setOnClickListener {
            val translated = b.etTranslatedText.text?.toString() ?: ""
            if (translated.isNotEmpty()) {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("traduccion", translated))
                Toast.makeText(context, "Copiado al portapapeles", Toast.LENGTH_SHORT).show()
            }
        }

        // Auto-traducir al abrir si hay texto
        if (originalText.isNotBlank()) {
            val src = Prefs.langDisplayToApi(Prefs.LANGUAGES[sourceIdx])
            performTranslation(originalText, src, targetLang)
        }
    }

    private fun performTranslation(text: String, sourceLang: String, targetLang: String) {
        val b = binding ?: return
        b.progressBar.visibility = View.VISIBLE
        b.etTranslatedText.setText("")
        b.btnReplace.isEnabled = false

        val apiKey = Prefs.getApiKey(context)
        val model = Prefs.getModel(context)

        CoroutineScope(Dispatchers.IO).launch {
            val result = GeminiClient.translate(text, sourceLang, targetLang, apiKey, model)
            withContext(Dispatchers.Main) {
                b.progressBar.visibility = View.GONE
                result.onSuccess { translation ->
                    b.etTranslatedText.setText(translation)
                    b.btnReplace.isEnabled = true
                }.onFailure { error ->
                    Toast.makeText(context, "Error: ${error.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun replaceTextInWhatsApp(newText: String) {
        val node = targetNode ?: return
        try {
            node.refresh()
            val args = Bundle()
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText)
            val success = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            if (!success) {
                // Fallback: copiar al portapapeles
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("traduccion", newText))
                mainHandler.post {
                    Toast.makeText(
                        context,
                        "Copiado al portapapeles. Pega en WhatsApp manualmente.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        } catch (e: Exception) {
            mainHandler.post {
                Toast.makeText(context, "Error al reemplazar: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun dismiss() {
        overlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) { /* ya fue removida */ }
            overlayView = null
            binding = null
        }
        targetNode?.recycle()
        targetNode = null
    }

    fun isShowing() = overlayView != null
}
