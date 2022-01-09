package com.lkl.medialib.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.lkl.commonlib.util.LogUtils
import com.lkl.medialib.R
import com.lkl.medialib.constant.ScreenCapture
import com.lkl.medialib.manager.ScreenCaptureManager

/**
 * 手机屏幕录制Service
 *
 * @author likunlun
 * @since 2022/01/05
 */
class ScreenCaptureService : Service() {
    companion object {
        const val TAG = "ScreenCaptureService"

        private const val NOTIFICATION_CHANNEL_ID = "ScreenCaptureId"
        private const val NOTIFICATION_CHANNEL_NAME = "ScreenCaptureName"
        private const val NOTIFICATION_ID = 1000
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val resultCode = intent.getIntExtra(ScreenCapture.KEY_RESULT_CODE, -1)
        val cacheSize = intent.getIntExtra(ScreenCapture.KEY_CACHE_SIZE, ScreenCapture.DEFAULT_CACHE_SIZE)
        val resultData = intent.getParcelableExtra<Intent>(ScreenCapture.KEY_DATA)
        resultData?.apply {
            ScreenCaptureManager.instance.startRecord(resultCode, this, cacheSize)
            LogUtils.e(TAG, "startRecord.")
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            if (notificationManager.getNotificationChannel(NOTIFICATION_CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    NOTIFICATION_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT
                )
                notificationManager.createNotificationChannel(channel)
            }
        }

        val builder =
            NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID) //获取一个Notification构造器
                .setContentTitle("ScreenCapture") // 设置下拉列表里的标题
                .setSmallIcon(R.mipmap.icon_launcher) // 设置状态栏内的小图标
                .setContentText("is running......") // 设置上下文内容
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setWhen(System.currentTimeMillis()) // 设置该通知发生的时间
        LogUtils.d(TAG, "startForeground")
        startForeground(NOTIFICATION_ID, builder.build())
    }
}