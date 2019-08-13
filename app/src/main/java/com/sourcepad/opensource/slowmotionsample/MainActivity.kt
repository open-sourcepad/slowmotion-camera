package com.sourcepad.opensource.slowmotionsample

import android.Manifest
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.markodevcic.peko.PermissionResult
import com.markodevcic.peko.requestPermissionsAsync
import com.sourcepad.opensource.slowmotioncamera.CameraView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Move publishing dependencies somewhere
 */
class MainActivity : AppCompatActivity() {

    private lateinit var cameraView: CameraView
    private var adapter: FpsAdapter =
        FpsAdapter()
    private lateinit var recyclerView: RecyclerView
    private lateinit var currentFps: TextView
    private lateinit var record: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        cameraView = findViewById(R.id.camera)
        recyclerView = findViewById(R.id.recycler_view)
        recyclerView.adapter = adapter
        currentFps = findViewById(R.id.current_fps_tv)
        record = findViewById(R.id.record_btn)

        currentFps.setOnClickListener {
            recyclerView.visibility = View.VISIBLE
        }

        record.setOnClickListener {
            if(cameraView.isRecording()){
                record.text = "Record"

                cameraView.stopRecordingVideo {
                    Log.d("CameraView","File path:$it")
                }
            }
            else{
                cameraView.startRecordingVideo()
                record.text = "Stop"
            }
        }

        adapter.onClicked = {
            currentFps.text = "${it.lower} FPS"
            recyclerView.visibility = View.INVISIBLE
            cameraView.setFps(it)
        }

    }

    override fun onResume() {
        super.onResume()
        checkPermissionThenLaunch()
    }


    private fun checkPermissionThenLaunch() {
        CoroutineScope(Dispatchers.Main).launch {
            val result = requestPermissionsAsync(
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            )

            if (result is PermissionResult.Granted) {
                cameraView.onCameraOpened = {
                    adapter.items = cameraView.getAvailableFps()
                    currentFps.text = "${cameraView.getCurrentFps()} FPS"
                }
                cameraView.onResume()

            }
        }

    }
}
