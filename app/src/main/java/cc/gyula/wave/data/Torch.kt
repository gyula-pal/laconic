package cc.gyula.wave.data

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The phone's flashlight, as the lock screen's corner button drives it. Its state follows Android's, so
 * the button stays right when the light is switched from quick settings or the lock screen. No permission
 * is needed to switch the torch.
 */
class Torch(private val context: Context) {
    private val cameras = context.getSystemService(CameraManager::class.java)

    /** The back camera with a flash; null on a phone without one. */
    private val cameraId: String? = try {
        cameras.cameraIdList.firstOrNull { id ->
            val c = cameras.getCameraCharacteristics(id)
            c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        }
    } catch (e: CameraAccessException) {
        null
    }

    private val state = MutableStateFlow(false)
    val on: StateFlow<Boolean> = state.asStateFlow()

    private val callback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(id: String, enabled: Boolean) {
            if (id == cameraId) state.value = enabled
        }

        // The camera app took the flash over; Android turns the torch off for it.
        override fun onTorchModeUnavailable(id: String) {
            if (id == cameraId) state.value = false
        }
    }

    init {
        if (cameraId != null) cameras.registerTorchCallback(callback, Handler(Looper.getMainLooper()))
    }

    fun toggle() {
        val id = cameraId ?: return Toast.makeText(context, "No flashlight", Toast.LENGTH_SHORT).show()
        try {
            cameras.setTorchMode(id, !state.value)
        } catch (e: CameraAccessException) {
            // In use by the camera app, or switched off by the system (too hot, battery saver).
            Toast.makeText(context, "Flashlight unavailable", Toast.LENGTH_SHORT).show()
        }
    }

    fun close() {
        if (cameraId != null) cameras.unregisterTorchCallback(callback)
    }
}
