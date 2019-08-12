package com.sourcepad.opensource.ezcamerasample

import android.Manifest
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.markodevcic.peko.PermissionResult
import com.markodevcic.peko.requestPermissionsAsync
import com.sourcepad.opensource.ezcamera.CameraView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Move publishing dependencies somewhere
 */
class MainActivity : AppCompatActivity() {

    private lateinit var cameraView: CameraView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        cameraView = findViewById(R.id.camera)


    }

    override fun onResume() {
        super.onResume()
        checkPermissionThenLaunch()
    }




    private fun checkPermissionThenLaunch(){
        CoroutineScope(Dispatchers.Main).launch {
            val result = requestPermissionsAsync(
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            )

            if (result is PermissionResult.Granted) {
                cameraView.onResume()
            }
        }

    }
}
