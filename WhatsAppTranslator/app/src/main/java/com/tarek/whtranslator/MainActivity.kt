package com.tarek.whtranslator

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.tarek.whtranslator.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSpinners()
        loadSavedSettings()
        setupButtons()
    }

    private fun setupSpinners() {
        val langAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, Prefs.LANGUAGES)
        langAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerSourceLang.adapter = langAdapter
        binding.spinnerTargetLang.adapter = langAdapter

        val modelAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, Prefs.MODELS)
        modelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerModel.adapter = modelAdapter
    }

    private fun loadSavedSettings() {
        val apiKey = Prefs.getApiKey(this)
        if (apiKey.isNotEmpty()) binding.etApiKey.setText(apiKey)

        val sourceLang = Prefs.getSourceLang(this)
        val targetLang = Prefs.getTargetLang(this)
        val model = Prefs.getModel(this)

        val sourceIdx = Prefs.LANGUAGES.indexOfFirst { it.startsWith(sourceLang) }.coerceAtLeast(0)
        val targetIdx = Prefs.LANGUAGES.indexOf(targetLang).coerceAtLeast(1)
        val modelIdx = Prefs.MODELS.indexOf(model).coerceAtLeast(0)

        binding.spinnerSourceLang.setSelection(sourceIdx)
        binding.spinnerTargetLang.setSelection(targetIdx)
        binding.spinnerModel.setSelection(modelIdx)
    }

    private fun setupButtons() {
        binding.btnAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            binding.tvStatus.text = "Busca 'WA Traductor' en la lista y actívalo"
        }

        binding.btnOverlay.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
                binding.tvStatus.text = "Activa el permiso de 'Mostrar sobre otras apps'"
            } else {
                binding.tvStatus.text = "Permiso de overlay ya concedido"
            }
        }

        binding.btnSave.setOnClickListener {
            val apiKey = binding.etApiKey.text?.toString()?.trim() ?: ""
            if (apiKey.isEmpty()) {
                Toast.makeText(this, "Por favor ingresa tu API Key de Gemini", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val sourceLang = Prefs.langDisplayToApi(binding.spinnerSourceLang.selectedItem.toString())
            val targetLang = binding.spinnerTargetLang.selectedItem.toString()
            val model = binding.spinnerModel.selectedItem.toString()

            Prefs.save(this, apiKey, sourceLang, targetLang, model)
            Toast.makeText(this, "Configuración guardada", Toast.LENGTH_SHORT).show()
            binding.tvStatus.text = "Todo listo. Abre WhatsApp, escribe y toca el boton de traducir."
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
    }

    private fun updatePermissionStatus() {
        val hasOverlay = Settings.canDrawOverlays(this)
        binding.btnOverlay.text = if (hasOverlay)
            "Overlay: ACTIVO"
        else
            "Permitir ventana emergente (overlay)"
    }
}
