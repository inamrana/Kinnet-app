package com.inamulhaq.i230839

import android.os.Bundle
import android.view.SurfaceView
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.video.VideoCanvas

/** Video call — an Agora video call with your camera in the corner card. */
class VideoCallActivity : CallScreenActivity() {

    override val isVideo = true
    override val layout = R.layout.activity_video_call
    private var cameraOn = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onTap(R.id.btn_flip_camera) { engine?.switchCamera() }
        onTap(R.id.btn_camera) { toggleCamera() }
    }

    override fun setupLocalVideo(engine: RtcEngine) {
        val surface = SurfaceView(this).apply { setZOrderMediaOverlay(true) }
        findViewById<FrameLayout>(R.id.local_video).addView(surface)
        engine.setupLocalVideo(VideoCanvas(surface, VideoCanvas.RENDER_MODE_HIDDEN, 0))
        engine.startPreview()
    }

    override fun onRemoteJoined(uid: Int) {
        val container = findViewById<FrameLayout>(R.id.remote_video)
        container.removeAllViews()
        val surface = SurfaceView(this)
        container.addView(surface)
        engine?.setupRemoteVideo(VideoCanvas(surface, VideoCanvas.RENDER_MODE_HIDDEN, uid))
        findViewById<View>(R.id.video_waiting).visibility = View.GONE
    }

    private fun toggleCamera() {
        cameraOn = !cameraOn
        engine?.muteLocalVideoStream(!cameraOn)
        engine?.enableLocalVideo(cameraOn)
        findViewById<View>(R.id.local_video).visibility = if (cameraOn) View.VISIBLE else View.INVISIBLE
        findViewById<ImageView>(R.id.btn_camera_icon).alpha = if (cameraOn) 1f else 0.5f
        toast(if (cameraOn) "Camera on" else "Camera off")
    }
}
