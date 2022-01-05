package com.lkl.medialib.manager

import android.media.MediaFormat
import com.lkl.medialib.bean.FrameData
import com.lkl.medialib.core.VideoExtractorThread

class VideoAddTimestampManager {
    companion object {
        private const val TAG = "VideoAddTimestampManager"

        val instance: VideoAddTimestampManager by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
            VideoAddTimestampManager()
        }
    }

    fun startTransform(videoFilePath: String) {
        startExtractVideo(videoFilePath)
    }

    private fun startExtractVideo(videoFilePath: String) {
        VideoExtractorThread(videoFilePath, object : VideoExtractorThread.Callback {
            override fun preExtract(mimeType: String, mediaFormat: MediaFormat) {

            }

            override fun putExtractData(frameData: FrameData) {

            }
        }).start()
    }
}