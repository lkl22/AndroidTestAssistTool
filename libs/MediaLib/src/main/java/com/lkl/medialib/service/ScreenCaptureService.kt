package com.lkl.medialib.service

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.Bundle
import com.lkl.commonlib.util.LogUtils
import com.lkl.medialib.constant.ScreenCapture
import com.lkl.medialib.manager.ScreenCaptureManager

class ScreenCaptureService : Service() {
    companion object {
        const val TAG = "ScreenCaptureService"
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val resultCode = intent.getIntExtra(ScreenCapture.KEY_RESULT_CODE, -1)
        val resultData = intent.getParcelableExtra<Intent>(ScreenCapture.KEY_DATA)
        resultData?.apply {
            ScreenCaptureManager.instance.startRecord(resultCode, this)
            LogUtils.e(TAG, "startRecord.")
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun createNotificationChannel() {
//        Notification.Builder builder = new Notification.Builder(this.getApplicationContext()); //获取一个Notification构造器
//        Intent nfIntent = new Intent(this, MainActivity.class); //点击后跳转的界面，可以设置跳转数据
//
//        builder.setContentIntent(PendingIntent.getActivity(this, 0, nfIntent, 0)) // 设置PendingIntent
//                .setLargeIcon(BitmapFactory.decodeResource(this.getResources(), R.mipmap.ic_launcher)) // 设置下拉列表中的图标(大图标)
//                //.setContentTitle("SMI InstantView") // 设置下拉列表里的标题
//                .setSmallIcon(R.mipmap.ic_launcher) // 设置状态栏内的小图标
//                .setContentText("is running......") // 设置上下文内容
//                .setWhen(System.currentTimeMillis()); // 设置该通知发生的时间
//
//        /*以下是对Android 8.0的适配*/
//        //普通notification适配
//        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            builder.setChannelId("notification_id");
//        }
//        //前台服务notification适配
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            NotificationManager notificationManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
//            NotificationChannel channel = new NotificationChannel("notification_id", "notification_name", NotificationManager.IMPORTANCE_LOW);
//            notificationManager.createNotificationChannel(channel);
//        }
//
//        Notification notification = builder.build(); // 获取构建好的Notification
//        notification.defaults = Notification.DEFAULT_SOUND; //设置为默认的声音
//        startForeground(110, notification);
    }
}