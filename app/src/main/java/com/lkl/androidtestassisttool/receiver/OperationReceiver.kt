package com.lkl.androidtestassisttool.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lkl.androidtestassisttool.utils.PublicIPUtil
import com.lkl.commonlib.util.*
import com.lkl.medialib.manager.ScreenCaptureManager
import java.util.*

/**
 * 自定义广播，处理 adb shell 指令请求
 */
class OperationReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "OperationReceiver"

        private const val EXTRA_KEY_TYPE = "type"

        private const val TYPE_IS_EVN_READY = "isEvnReady"
        private const val TYPE_START_MUXER = "startMuxer"
        private const val TYPE_IS_FINISHED_MUXER = "isFinishedMuxer"
        private const val TYPE_GET_MUXER_VIDEO = "getMuxerVideo"
        private const val TYPE_RM_FINISHED_MUXER = "rmFinishedMuxer"

        private const val TYPE_GET_PUBLIC_IP_ADDR = "getPublicIpAddr"

        private const val EXTRA_KEY_TIMESTAMP = "timestamp"

        // 视频总时长，默认30s
        private const val EXTRA_KEY_TOTAL_TIME = "totalTime"

        // 是否清除缓存的pubic ip
        private const val EXTRA_KEY_CLEAR_PUBLIC_IP = "clearPublicIp"

        private var myPublicIp: String = ""
    }

    override fun onReceive(context: Context, intent: Intent) {
        val params = SafeIntent(intent)
        val type = params.getStringExtra(EXTRA_KEY_TYPE)
        var resData = ""
        when (type) {
            TYPE_IS_EVN_READY -> {
                resData += ScreenCaptureManager.instance.isEnvReady()
            }
            TYPE_START_MUXER -> {
                resData += startMuxer(params)
            }
            TYPE_GET_MUXER_VIDEO -> {
                val timestamp = params.getLongExtra(EXTRA_KEY_TIMESTAMP, System.currentTimeMillis())
                resData += ScreenCaptureManager.instance.getMuxerVideo(timestamp)
            }
            TYPE_IS_FINISHED_MUXER -> {
                val timestamp = params.getLongExtra(EXTRA_KEY_TIMESTAMP, System.currentTimeMillis())
                resData += ScreenCaptureManager.instance.isFinishedMuxerTask(timestamp)
            }
            TYPE_RM_FINISHED_MUXER -> {
                val timestamp = params.getLongExtra(EXTRA_KEY_TIMESTAMP, System.currentTimeMillis())
                resData += ScreenCaptureManager.instance.removeFinishedMuxerTask(timestamp)
            }

            TYPE_GET_PUBLIC_IP_ADDR -> {
                val clearPubicIp = params.getBooleanExtra(EXTRA_KEY_CLEAR_PUBLIC_IP, false)
                if (clearPubicIp) {
                    myPublicIp = ""
                }
                resData = myPublicIp
                PublicIPUtil.getPublicIp { ip -> myPublicIp = ip }
            }
        }
        setResult(0, resData, null)
    }

    private fun startMuxer(params: SafeIntent) {
        val timestamp = params.getLongExtra(EXTRA_KEY_TIMESTAMP, System.currentTimeMillis())
        val totalTime = params.getIntExtra(EXTRA_KEY_TOTAL_TIME, 30)
        ScreenCaptureManager.instance.startMuxer(
            timestamp - totalTime * 1000,
            timestamp,
            object : ScreenCaptureManager.Callback {
                override fun muxerFinished(filePath: String) {
                }
            }
        )
        LogUtils.e(TAG, "timestamp: $timestamp totalTime: $totalTime")
    }
}