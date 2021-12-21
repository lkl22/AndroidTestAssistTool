package com.lkl.commonlib.util

import android.os.Handler
import android.os.Looper

/**
 * 线程处理工具类
 *
 * @author likunlun
 * @since 2021/12/21
 */
object ThreadUtils {
    private val sMainHandler = Handler(Looper.getMainLooper())

    /**
     * 判断当前是否在主线程
     *
     * @return true 主线程
     */
    val isMainThread: Boolean
        get() = Looper.getMainLooper() == Looper.myLooper()

    /**
     * 在主线程中执行
     *
     * @param runnable 要执行的 Runnable 对象
     */
    fun runOnMainThread(runnable: Runnable) {
        if (isMainThread) {
            runnable.run()
        } else {
            sMainHandler.post(runnable)
        }
    }
}