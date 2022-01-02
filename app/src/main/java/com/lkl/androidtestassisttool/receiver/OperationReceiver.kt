package com.lkl.androidtestassisttool.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lkl.commonlib.util.SafeIntent
import com.lkl.medialib.manager.ScreenCaptureManager

/**
 * 自定义广播，处理 adb shell 指令请求
 */
class OperationReceiver : BroadcastReceiver() {
    companion object {
        private const val EXTRA_KEY_TYPE = "type"
        private const val IS_EVN_READY = "isEvnReady"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val params = SafeIntent(intent)
        val type = params.getStringExtra(EXTRA_KEY_TYPE)
        var resData = ""
        if (IS_EVN_READY == type) {
            resData += ScreenCaptureManager.instance.isEnvReady()
        }
        setResult(0, resData, null)
    }
}