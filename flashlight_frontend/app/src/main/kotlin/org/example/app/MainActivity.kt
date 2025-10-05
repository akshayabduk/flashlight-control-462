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

    // Use nullable reference to avoid IllegalState before setContentView/binding
    private var toggleButton: Button? = null
    private var cameraManager: CameraManager? = null
    private var backFlashCameraId: String? = null
    private var torchOn: Boolean = false
    private var torchControllable: Boolean = false

    private val cameraPermission = Manifest.permission.CAMERA

    private val torchCallback: CameraManager.TorchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == backFlashCameraId) {
                torchOn = enabled
                torchControllable = true
                updateUi()
            }
        }

        override fun onTorchModeUnavailable(cameraId: String) {
            if (cameraId == backFlashCameraId) {
                torchControllable = false
                updateUi()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        toggleButton = findViewById(R.id.toggleButton)
        cameraManager = getSystemService(CameraManager::class.java)

        // Initial scan for a usable back camera with flash
        findBackCameraWithFlash()
        registerTorchCallbackSafe(true)

        toggleButton?.setOnClickListener { handleToggleClick() }

        updateUi()
    }

    override fun onStart() {
        super.onStart()
        // Re-scan in case camera availability changed (USB, multiple users, permissions toggled)
        findBackCameraWithFlash()
        registerTorchCallbackSafe(true)
        updateUi()
    }

    override fun onResume() {
        super.onResume()
        updateUi()
    }

    override fun onPause() {
        super.onPause()
        // Conservatively turn off when app goes to background to avoid leaving flashlight on
        turnTorchSafe(false)
    }

    override fun onStop() {
        super.onStop()
        // Unregister to avoid leaks
        registerTorchCallbackSafe(false)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Ensure callbacks are unregistered and clear view refs for safety
        registerTorchCallbackSafe(false)
        toggleButton = null
    }

    private fun handleToggleClick() {
        val desired = !torchOn
        try {
            turnTorchInternal(desired)
        } catch (se: SecurityException) {
            // Request permission only if actually needed on this device
            requestCameraPermissionIfNeeded()
        } catch (_: Throwable) {
            Toast.makeText(this, getString(R.string.flash_unavailable), Toast.LENGTH_SHORT).show()
            // Ensure UI reflects inability to toggle
            updateUi()
        }
    }

    private fun requestCameraPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val granted = ContextCompat.checkSelfPermission(this, cameraPermission) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, cameraPermission)) {
                    Toast.makeText(this, getString(R.string.permission_required), Toast.LENGTH_LONG).show()
                }
                ActivityCompat.requestPermissions(this, arrayOf(cameraPermission), REQ_CAMERA)
                // Immediately reflect that action is pending; disable if not controllable
                updateUi()
            } else {
                retryToggleAfterPermission()
            }
        } else {
            Toast.makeText(this, getString(R.string.flash_unavailable), Toast.LENGTH_SHORT).show()
            updateUi()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_CAMERA) {
            // Re-scan cameras as permission can change availability information
            findBackCameraWithFlash()
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                retryToggleAfterPermission()
            } else {
                Toast.makeText(this, getString(R.string.permission_denied), Toast.LENGTH_SHORT).show()
                // Update UI immediately to reflect denial
                updateUi()
            }
        }
    }

    private fun retryToggleAfterPermission() {
        try {
            turnTorchInternal(!torchOn)
        } catch (_: Throwable) {
            Toast.makeText(this, getString(R.string.flash_unavailable), Toast.LENGTH_SHORT).show()
            updateUi()
        }
    }

    private fun turnTorchSafe(on: Boolean) {
        try {
            turnTorchInternal(on)
        } catch (_: SecurityException) {
            // Ignore during lifecycle changes if permission is not present
            updateUi()
        } catch (_: Throwable) {
            // Ignore in lifecycle
            updateUi()
        }
    }

    private fun turnTorchInternal(on: Boolean) {
        val mgr = cameraManager ?: return
        val id = backFlashCameraId ?: return
        mgr.setTorchMode(id, on)
        // Optimistically update UI; callback will confirm state
        torchOn = on
        updateUi()
    }

    private fun findBackCameraWithFlash() {
        val mgr = cameraManager ?: return
        backFlashCameraId = null
        torchControllable = false

        try {
            for (cameraId in mgr.cameraIdList) {
                val chars = mgr.getCameraCharacteristics(cameraId)
                val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                val isBack = facing == CameraCharacteristics.LENS_FACING_BACK
                if (hasFlash && isBack) {
                    backFlashCameraId = cameraId
                    torchControllable = true
                    break
                }
            }
        } catch (_: Throwable) {
            backFlashCameraId = null
            torchControllable = false
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
        val btn = toggleButton ?: return
        val available = backFlashCameraId != null && torchControllable
        btn.isEnabled = available
        btn.text = when {
            !available -> getString(R.string.flash_unavailable)
            torchOn -> getString(R.string.toggle_off)
            else -> getString(R.string.toggle_on)
        }
        btn.alpha = if (available) 1.0f else 0.5f
        btn.contentDescription = btn.text
        btn.visibility = View.VISIBLE
    }

    companion object {
        private const val REQ_CAMERA = 1001
    }
}
