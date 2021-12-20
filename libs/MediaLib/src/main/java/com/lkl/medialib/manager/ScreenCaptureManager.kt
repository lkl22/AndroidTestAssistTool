package com.lkl.medialib.manager

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import com.lkl.commonlib.BaseApplication
import com.lkl.commonlib.util.DisplayUtils
import com.lkl.medialib.util.ScreenRecordService

class ScreenCaptureManager {
    companion object {
        val instance: ScreenCaptureManager by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
            ScreenCaptureManager()
        }
    }

    private val mProjectionManager: MediaProjectionManager = BaseApplication.context
        .getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

    private val displayMetrics = DisplayUtils.getDisplayMetrics(BaseApplication.context)

    private var mediaRecord : ScreenRecordService?=null

    fun createScreenCaptureIntent(): Intent {
        return mProjectionManager.createScreenCaptureIntent()
    }

    fun startRecord(resultCode: Int, data: Intent) {
        mediaRecord = ScreenRecordService(
            displayMetrics.widthPixels,
            displayMetrics.heightPixels,
            displayMetrics.densityDpi,
            mediaProjection = mProjectionManager.getMediaProjection(resultCode, data)
        )
        mediaRecord?.start()
    }
}