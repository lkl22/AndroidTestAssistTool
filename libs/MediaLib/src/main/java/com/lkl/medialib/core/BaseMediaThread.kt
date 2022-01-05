package com.lkl.medialib.core

import com.lkl.commonlib.util.LogUtils
import java.util.concurrent.atomic.AtomicBoolean

/**
 * media操作线程基类
 *
 * @author likunlun
 * @since 2021/12/23
 */
abstract class BaseMediaThread(private val threadName: String) : Thread(threadName) {
    companion object {
        private const val TAG = "BaseMediaThread"
    }

    private val mQuit = AtomicBoolean(false)

    override fun run() {
        try {
            LogUtils.i(TAG, "$threadName prepare.")
            prepare()
            LogUtils.i(TAG, "$threadName drain.")
            while (!mQuit.get()) {
                drain()
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        } finally {
            LogUtils.i(TAG, "$threadName release.")
            release()
        }
    }

    /**
     * stop task
     */
    fun quit() {
        mQuit.set(true)
    }

    /**
     * 线程中断等待时长
     *
     * @param millis 等待时长 ms
     */
    fun waitTime(millis: Long) {
        try {
            sleep(millis)
        } catch (ex: InterruptedException) {
            ex.printStackTrace()
        }
    }

    /**
     * 处理 Media 流前的准备工作
     */
    protected abstract fun prepare()

    /**
     * 循环处理流数据
     */
    protected abstract fun drain()

    /**
     * 处理结束资源的释放
     */
    protected abstract fun release()
}