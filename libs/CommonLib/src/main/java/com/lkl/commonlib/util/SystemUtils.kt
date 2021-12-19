package com.lkl.commonlib.util

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import com.lkl.commonlib.BaseApplication.Companion.context


/**
 * android系统工具类
 *
 * @author likunlun
 * @since 2021/12/19
 */
object SystemUtils {
    private const val TAG = "SystemUtils"

    /**
     * 判断指定App是否正在运行
     *
     * @param packageName 包名
     * @return true 指定的app正在运行
     */
    fun isAppRunning(packageName: String): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val tasks = am.getRunningTasks(100)
        tasks.forEach {
            if (packageName == it.baseActivity?.packageName) {
                return true
            }
        }
        return false
    }

    /**
     * 判断指定的服务进程是否正在运行
     *
     * @param processName 进程名
     * @return true 指定的服务进程正在运行
     */
    fun isServiceRunning(processName: String): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val services = am.getRunningServices(100)
        services.forEach {
            if (processName == it.process) {
                return it.started
            }
        }
        return false
    }

    /**
     * 判断指定的进程是否正在运行
     *
     * @param processName 进程名
     * @return true 正在运行
     */
    fun isProcessRunning(processName: String): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val processes = am.runningAppProcesses
        processes.forEach {
            if (processName == it.processName) {
                return true
            }
        }
        return false
    }

    /**
     * 获取当前的进程名称
     *
     * @return 进程名
     */
    fun getCurrentProcessName(): String? {
        val pid = android.os.Process.myPid()
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val processes = am.runningAppProcesses
        processes.forEach {
            if (pid == it.pid) {
                return it.processName
            }
        }
        return null
    }

    /**
     * 重启应用
     */
    fun restartApplication() {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        context.startActivity(intent)
    }

    /**
     * 系统重启
     *
     * @return true 重启成功
     */
    @JvmStatic
    fun rebootSystem(): Boolean {
        val reboot = Intent(Intent.ACTION_REBOOT)
        reboot.putExtra("nowait", 1)
        reboot.putExtra("interval", 1)
        reboot.putExtra("window", 0)
        try {
            context.sendBroadcast(reboot)
        } catch (e: Exception) {
            LogUtils.w(TAG, e)
            return false
        }
        return true
    }
}