package com.lkl.medialib.manager

import android.content.Context
import android.content.Intent
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjectionManager
import com.lkl.commonlib.BaseApplication
import com.lkl.commonlib.util.DisplayUtils
import com.lkl.framedatacachejni.FrameDataCacheUtils
import com.lkl.medialib.bean.FrameData
import com.lkl.medialib.bean.MediaFormatParams
import com.lkl.medialib.core.ScreenCaptureThread

class ScreenCaptureManager {
    companion object {
        val instance: ScreenCaptureManager by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
            ScreenCaptureManager()
        }
    }

    private val mProjectionManager: MediaProjectionManager = BaseApplication.context
        .getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

    private val displayMetrics = DisplayUtils.getDisplayMetrics(BaseApplication.context)

    private var screenCaptureThread: ScreenCaptureThread? = null

    fun createScreenCaptureIntent(): Intent {
        return mProjectionManager.createScreenCaptureIntent()
    }

    fun startRecord(resultCode: Int, data: Intent) {
        screenCaptureThread = ScreenCaptureThread(
            MediaFormatParams(
                displayMetrics.widthPixels,
                displayMetrics.heightPixels,
                colorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
            ),
            displayMetrics.densityDpi,
            mProjectionManager.getMediaProjection(resultCode, data),
            object : ScreenCaptureThread.Callback {
                override fun prePrepare(mediaFormatParams: MediaFormatParams) {
                    FrameDataCacheUtils.initCache(
                        mediaFormatParams.frameRate,
                        mediaFormatParams.width,
                        mediaFormatParams.height
                    )
                }

                override fun putFrameData(frameData: FrameData) {
                    // 将编码好的H264数据存储到缓冲中
                    FrameDataCacheUtils.addFrameData(
                        frameData.timestamp,
                        frameData.isKeyFrame,
                        frameData.data,
                        frameData.length
                    )
                }
            }
        )
        screenCaptureThread?.start()
    }

    fun getMediaFormat(): MediaFormat? {
        return screenCaptureThread?.getMediaFormat()
    }
}