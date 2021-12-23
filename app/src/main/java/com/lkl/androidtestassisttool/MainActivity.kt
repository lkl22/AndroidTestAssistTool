package com.lkl.androidtestassisttool

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.EditText
import com.lkl.commonlib.base.BaseActivity
import com.lkl.commonlib.util.BitmapUtils
import com.lkl.commonlib.util.DateUtils
import com.lkl.commonlib.util.FileUtils
import com.lkl.commonlib.util.LogUtils
import com.lkl.medialib.constant.ScreenCapture
import com.lkl.medialib.constant.VideoConfig
import com.lkl.medialib.manager.ScreenCaptureManager
import com.lkl.medialib.service.ScreenCaptureService
import com.lkl.medialib.util.VideoMuxerCore
import pub.devrel.easypermissions.AfterPermissionGranted
import pub.devrel.easypermissions.AppSettingsDialog
import pub.devrel.easypermissions.EasyPermissions
import java.util.*

class MainActivity : BaseActivity() {
    companion object {
        const val TAG = "MainActivity"
        const val SCREEN_CAPTURE_REQUEST_CODE = 1000

        private const val RC_WRITE_EXTERNAL_STORAGE_PERM = 120
        private const val RC_RECORD_AUDIO_PERM = 121
    }

    private var tipEt: EditText? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        tipEt = findViewById(R.id.tipEt)

        requestStoragePermission()
    }

    @AfterPermissionGranted(RC_WRITE_EXTERNAL_STORAGE_PERM)
    fun requestStoragePermission() {
        if (hasPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            // Have permission, do the thing!
            requestRecordPermission()
        } else {
            // Ask for one permission
            EasyPermissions.requestPermissions(
                this,
                getString(R.string.rationale_write_external_storage),
                RC_WRITE_EXTERNAL_STORAGE_PERM,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }
    }


    @AfterPermissionGranted(RC_RECORD_AUDIO_PERM)
    fun requestRecordPermission() {
        if (hasPermissions(Manifest.permission.RECORD_AUDIO)) {
            // Have permission, do the thing!
            startActivityForResult(
                ScreenCaptureManager.instance.createScreenCaptureIntent(),
                SCREEN_CAPTURE_REQUEST_CODE
            )
        } else {
            // Ask for one permission
            EasyPermissions.requestPermissions(
                this,
                getString(R.string.rationale_record_audio),
                RC_RECORD_AUDIO_PERM,
                Manifest.permission.RECORD_AUDIO
            )
        }
    }

    override fun onPermissionsGranted(requestCode: Int, perms: List<String>) {
        LogUtils.d(TAG, "onPermissionsGranted:" + requestCode + ":" + perms.size)
    }

    override fun onPermissionsDenied(requestCode: Int, perms: List<String>) {
        LogUtils.d(TAG, "onPermissionsDenied:" + requestCode + ":" + perms.size)

        // (Optional) Check whether the user denied any permissions and checked "NEVER ASK AGAIN."
        // This will display a dialog directing them to enable the permission in app settings.
        if (EasyPermissions.somePermissionPermanentlyDenied(this, perms)) {
            AppSettingsDialog.Builder(this)
                .setTitle("权限申请")
                .setRationale("没有请求的权限，此应用可能无法正常工作。打开应用程序设置界面以修改应用程序权限。")
                .build()
                .show()
        } else {
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SCREEN_CAPTURE_REQUEST_CODE) {
            data?.apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val service = Intent(this@MainActivity, ScreenCaptureService::class.java)
                    service.putExtra(ScreenCapture.KEY_RESULT_CODE, resultCode)
                    service.putExtra(ScreenCapture.KEY_DATA, data)
                    startForegroundService(service)
                } else {
                    ScreenCaptureManager.instance.startRecord(resultCode, this)
                }
            }
        }
    }

    fun startEncode(view: android.view.View) {}

    fun startMuxer(view: android.view.View) {
        val fileName = FileUtils.videoDir + DateUtils.nowTime.replace(" ", "_") +
                BitmapUtils.VIDEO_FILE_EXT
        Thread(
            VideoMuxerCore(
                System.currentTimeMillis(),
                VideoConfig.FPS,
                ScreenCaptureManager.instance.getMediaFormat(),
                fileName
            ), "Video Muxer Thread"
        ).start()

        tipEt?.setText(fileName)
    }
}