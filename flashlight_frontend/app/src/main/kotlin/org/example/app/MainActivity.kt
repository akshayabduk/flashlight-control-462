package org.example.app

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * PUBLIC_INTERFACE
 * MainActivity provides a single-button UI to toggle the device flashlight (torch).
 * It conservatively requests CAMERA permission only if required by the device or OS,
 * uses CameraManager to find a back-facing camera with flash, and keeps the UI in sync
 * with the actual torch state via a TorchCallback.
 */
class MainActivity : Activity() {

    private lateinit var toggleButton: Button
    private var cameraManager: CameraManager? = null
    private var backFlashCameraId: String? = null
    private var torchOn: Boolean = false
    private var torchAvailable: Boolean = false

    private val cameraPermission = Manifest.permission.CAMERA

    private val torchCallback: CameraManager.TorchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == backFlashCameraId) {
                torchOn = enabled
                // If we receive a state change callback for this camera, consider torch controllable.
                torchAvailable = true
                updateUi()
            }
        }

        override fun onTorchModeUnavailable(cameraId: String) {
            if (cameraId == backFlashCameraId) {
                torchAvailable = false
                updateUi()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        toggleButton = findViewById(R.id.toggleButton)

        cameraManager = getSystemService(CameraManager::class.java)

        findBackCameraWithFlash()
        registerTorchCallbackSafe(true)

        toggleButton.setOnClickListener {
            handleToggleClick()
        }

        updateUi()
    }

    override fun onStart() {
        super.onStart()
        registerTorchCallbackSafe(true)
        updateUi()
    }

    override fun onResume() {
        super.onResume()
        updateUi()
    }

    override fun onPause() {
        super.onPause()
        // To avoid leaving flashlight on when app goes to background, turn it off conservatively.
        turnTorchSafe(false)
    }

    override fun onStop() {
        super.onStop()
        // Unregister to avoid leaks.
        registerTorchCallbackSafe(false)
    }

    private fun handleToggleClick() {
        // Try to toggle without asking for permission unless needed (SecurityException).
        val desired = !torchOn
        try {
            turnTorchInternal(desired)
        } catch (se: SecurityException) {
            // Permission might be required on this device.
            requestCameraPermissionIfNeeded()
        } catch (t: Throwable) {
            Toast.makeText(this, getString(R.string.flash_unavailable), Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestCameraPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, cameraPermission) != PackageManager.PERMISSION_GRANTED) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, cameraPermission)) {
                    Toast.makeText(this, getString(R.string.permission_required), Toast.LENGTH_LONG).show()
                }
                ActivityCompat.requestPermissions(this, arrayOf(cameraPermission), REQ_CAMERA)
            } else {
                // Permission already granted, retry operation
                retryToggleAfterPermission()
            }
        } else {
            // On older devices (not in our minSdk), fallback message.
            Toast.makeText(this, getString(R.string.flash_unavailable), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_CAMERA) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                retryToggleAfterPermission()
            } else {
                Toast.makeText(this, getString(R.string.permission_denied), Toast.LENGTH_SHORT).show()
                updateUi()
            }
        }
    }

    private fun retryToggleAfterPermission() {
        // After permission granted, attempt to toggle to the opposite of current state
        try {
            turnTorchInternal(!torchOn)
        } catch (_: Throwable) {
            // If still fails, inform user.
            Toast.makeText(this, getString(R.string.flash_unavailable), Toast.LENGTH_SHORT).show()
        }
    }

    private fun turnTorchSafe(on: Boolean) {
        try {
            turnTorchInternal(on)
        } catch (_: SecurityException) {
            // Silently ignore on backgrounding if permission needed.
        } catch (_: Throwable) {
            // Ignore in lifecycle.
        }
    }

    private fun turnTorchInternal(on: Boolean) {
        val mgr = cameraManager ?: return
        val id = backFlashCameraId ?: return
        mgr.setTorchMode(id, on)
        // Optimistically update; TorchCallback will finalize state.
        torchOn = on
        updateUi()
    }

    private fun findBackCameraWithFlash() {
        val mgr = cameraManager ?: return
        backFlashCameraId = null
        torchAvailable = false

        try {
            for (cameraId in mgr.cameraIdList) {
                val chars = mgr.getCameraCharacteristics(cameraId)
                val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                val isBack = facing == CameraCharacteristics.LENS_FACING_BACK
                if (hasFlash == true && isBack) {
                    backFlashCameraId = cameraId
                    torchAvailable = true
                    break
                }
            }
        } catch (_: Throwable) {
            backFlashCameraId = null
            torchAvailable = false
        }
    }

    private fun registerTorchCallbackSafe(register: Boolean) {
        val mgr = cameraManager ?: return
        if (register) {
            try {
                mgr.registerTorchCallback(torchCallback, null)
            } catch (_: Throwable) {
                // Ignore
            }
        } else {
            try {
                mgr.unregisterTorchCallback(torchCallback)
            } catch (_: Throwable) {
                // Ignore
            }
        }
    }

    private fun updateUi() {
        val available = (backFlashCameraId != null) && (torchAvailable || true)
        toggleButton.isEnabled = available
        toggleButton.text = if (torchOn) getString(R.string.toggle_off) else getString(R.string.toggle_on)
        toggleButton.alpha = if (available) 1.0f else 0.5f
        toggleButton.contentDescription = toggleButton.text
        toggleButton.visibility = View.VISIBLE
        if (!available) {
            // Provide a hint if unavailable
            toggleButton.text = getString(R.string.flash_unavailable)
        }
    }

    companion object {
        private const val REQ_CAMERA = 1001
    }
}
