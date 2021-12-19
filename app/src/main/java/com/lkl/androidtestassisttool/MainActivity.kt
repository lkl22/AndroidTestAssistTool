package com.lkl.androidtestassisttool

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.lkl.commonlib.util.DisplayUtils
import com.lkl.commonlib.util.FileUtils
import com.lkl.commonlib.util.LogUtils
import com.lkl.medialib.util.ScreenRecordService
import java.io.File

class MainActivity : AppCompatActivity() {
    companion object {
        const val TAG = "MainActivity"
        const val LOCAL_REQUEST_CODE = 1000
    }

    private var mProjectionManager: MediaProjectionManager? = null

    private var mediaRecord: ScreenRecordService? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mProjectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        val captureIntent = mProjectionManager?.createScreenCaptureIntent()
        startActivityForResult(captureIntent, LOCAL_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val mediaProjection: MediaProjection? =
            mProjectionManager?.getMediaProjection(resultCode, data!!)
        if (mediaProjection == null) {
            LogUtils.e(TAG, "media projection is null")
            return
        }
        val file = File(FileUtils.videoDir + "xx.mp4") //录屏生成文件
        mediaRecord = ScreenRecordService(
            DisplayUtils.getScreenWidthPixels(this),
            DisplayUtils.getScreenHeightPixels(this),
            6000000,
            DisplayUtils.getDensityDpi(this),
            mediaProjection,
            file.absolutePath
        )
        mediaRecord?.start()
    }

    fun startEncode(view: android.view.View) {}

    fun startMuxer(view: android.view.View) {
        mediaRecord?.quit()
    }

}