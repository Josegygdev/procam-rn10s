package com.procam.rn10s

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.switchmaterial.SwitchMaterial
import com.procam.rn10s.camera.CameraController
import com.procam.rn10s.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraController: CameraController

    private val requiredPermissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    )

    private var isRecording = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (hasAllPermissions()) {
            setupCamera()
        } else {
            ActivityCompat.requestPermissions(this, requiredPermissions, REQUEST_CODE_PERMISSIONS)
        }

        setupControls()
    }

    private fun hasAllPermissions() = requiredPermissions.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS && hasAllPermissions()) {
            setupCamera()
        }
    }

    private fun setupCamera() {
        cameraController = CameraController(this, this, binding.previewView)
        cameraController.onLogSupportKnown = { supported ->
            runOnUiThread {
                binding.logStatusText.text = if (supported) {
                    getString(R.string.log_supported)
                } else {
                    getString(R.string.log_unsupported)
                }
            }
        }
        cameraController.start()
    }

    private fun setupControls() {
        binding.isoSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) cameraController.setIso(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.shutterSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) cameraController.setShutterDenominator(progress.coerceAtLeast(8))
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.wbSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) cameraController.setWhiteBalanceKelvin(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.logToggle.setOnCheckedChangeListener { _: SwitchMaterial, isChecked ->
            cameraController.setLogProfileEnabled(isChecked)
        }

        binding.recordButton.setOnClickListener {
            if (!isRecording) {
                cameraController.startRecording { }
                binding.recordButton.text = getString(android.R.string.cancel)
            } else {
                cameraController.stopRecording()
                binding.recordButton.text = "Grabar"
            }
            isRecording = !isRecording
        }
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
    }
}
