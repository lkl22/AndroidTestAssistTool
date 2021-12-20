package com.lkl.androidtestassisttool

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import com.lkl.commonlib.BaseApplication
import com.lkl.commonlib.util.BitmapUtils
import com.lkl.commonlib.util.DateUtils
import com.lkl.commonlib.util.DisplayUtils
import com.lkl.commonlib.util.FileUtils
import com.lkl.medialib.constant.VideoConfig
import com.lkl.medialib.manager.ScreenCaptureManager
import com.lkl.medialib.util.ScreenRecordService
import com.lkl.medialib.util.VideoMuxerCore
import java.util.*

class MainActivity : AppCompatActivity() {
    companion object {
        const val TAG = "MainActivity"
        const val SCREEN_CAPTURE_REQUEST_CODE = 1000
    }

    private var tipEt: EditText? = null

    private var mediaRecord: ScreenRecordService? = null



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        tipEt = findViewById(R.id.tipEt)

        startActivityForResult(
            ScreenCaptureManager.instance.createScreenCaptureIntent(),
            SCREEN_CAPTURE_REQUEST_CODE
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SCREEN_CAPTURE_REQUEST_CODE) {
            data?.apply {
                ScreenCaptureManager.instance.startRecord(resultCode, this)
            }
        }
    }

    fun startEncode(view: android.view.View) {}

    fun startMuxer(view: android.view.View) {
        val displayMetrics = DisplayUtils.getDisplayMetrics(BaseApplication.context)
        val fileName = FileUtils.videoDir + DateUtils.nowTime.replace(" ", "_") + BitmapUtils.VIDEO_FILE_EXT
        Thread(
            VideoMuxerCore(
                System.currentTimeMillis(), displayMetrics.widthPixels, displayMetrics.heightPixels,
                VideoConfig.FPS, ScreenRecordService.sMediaFormat,
                fileName
            ), "Video Muxer Thread"
        ).start()

        tipEt?.setText(fileName)
    }
}