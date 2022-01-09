package com.lkl.androidtestassisttool

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.widget.EditText
import com.lkl.commonlib.BaseApplication
import com.lkl.commonlib.base.BaseActivity
import com.lkl.commonlib.util.*
import com.lkl.medialib.constant.ScreenCapture
import com.lkl.medialib.manager.ScreenCaptureManager
import com.lkl.medialib.manager.VideoAddTimestampManager
import com.lkl.medialib.service.ScreenCaptureService
import pub.devrel.easypermissions.AfterPermissionGranted
import pub.devrel.easypermissions.AppSettingsDialog
import pub.devrel.easypermissions.EasyPermissions
import java.util.*

class MainActivity : BaseActivity() {
    companion object {
        const val TAG = "MainActivity"
        const val SCREEN_CAPTURE_REQUEST_CODE = 1000

        private const val RC_WRITE_EXTERNAL_STORAGE_PERM = 120
    }

    private var tipEt: EditText? = null

    private var cacheSize = ScreenCapture.DEFAULT_CACHE_SIZE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        tipEt = findViewById(R.id.tipEt)
        val safeIntent = SafeIntent(intent)
        cacheSize =
            safeIntent.getIntExtra(ScreenCapture.KEY_CACHE_SIZE, ScreenCapture.DEFAULT_CACHE_SIZE)
        requestStoragePermission()

        val mDisplayMetrics = DisplayUtils.getDisplayMetrics()
        LogUtils.e(TAG, "${mDisplayMetrics.widthPixels} * ${mDisplayMetrics.heightPixels}")
    }

    @AfterPermissionGranted(RC_WRITE_EXTERNAL_STORAGE_PERM)
    fun requestStoragePermission() {
        if (hasPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            // Have permission, do the thing!
            startActivityForResult(
                ScreenCaptureManager.instance.createScreenCaptureIntent(),
                SCREEN_CAPTURE_REQUEST_CODE
            )
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
                    service.putExtra(ScreenCapture.KEY_CACHE_SIZE, cacheSize)
                    startForegroundService(service)
                } else {
                    ScreenCaptureManager.instance.startRecord(resultCode, this, cacheSize)
                }
            }
        }
    }

    fun startEncode(view: android.view.View) {
        VideoAddTimestampManager().startTransform(
            if (TextUtils.isEmpty(tipEt?.text.toString()))
                "/sdcard/Android/data/com.lkl.androidtestassisttool/cache/video/2022-01-09_16:41:52.mp4"
            else
                tipEt?.text.toString()
        )
    }

    fun startMuxer(view: android.view.View) {
        val fileName = FileUtils.videoDir + DateUtils.nowTime.replace(" ", "_") +
                BitmapUtils.VIDEO_FILE_EXT
        val curTime = System.currentTimeMillis()
        ScreenCaptureManager.instance.startMuxer(fileName, curTime - 10 * 1000, curTime)
        tipEt?.setText(fileName)
    }
}