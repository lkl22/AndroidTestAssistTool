package com.lkl.commonlib.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Looper
import android.util.Log
import android.widget.Toast
import java.io.FileOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.*


/**
 * 未捕获异常处理类
 *
 * @author likunlun
 * @since 2018/10/17
 */
class CrashHandler
/**
 * 保证只有一个CrashHandler实例
 */
private constructor() : Thread.UncaughtExceptionHandler {
    companion object {

        const val TAG = "CrashHandler"

        // CrashHandler实例
        private var mInstance: CrashHandler? = null

        /**
         * 获取CrashHandler实例 ,单例模式
         */
        val instance: CrashHandler
            @Synchronized get() {
                if (null == mInstance) {
                    mInstance = CrashHandler()
                }
                return mInstance!!
            }
    }

    /**
     * 系统默认UncaughtExceptionHandler
     */
    private var mDefaultHandler: Thread.UncaughtExceptionHandler? = null

    /**
     * context
     */
    private var mContext: Context? = null

    /**
     * 用来存储设备信息和异常信息
     */
    private val infos = HashMap<String, String>()

    /**
     * 格式化时间
     */
    private val format = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)

    fun init(context: Context) {
        mContext = context
        // 获取系统默认的UncaughtException处理器
        mDefaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        //设置该CrashHandler为系统默认的
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    /**
     * uncaughtException 回调函数，当UncaughtException发生时会转入该函数来处理
     */
    override fun uncaughtException(thread: Thread, ex: Throwable) {
        if (!handleException(ex) && mDefaultHandler != null) {
            //如果用户没有处理则让系统默认的异常处理器来处理
            mDefaultHandler!!.uncaughtException(thread, ex)
        } else {
            // 发送进程销毁广播
            val curProcessName = SystemUtils.getCurrentProcessName()
            mContext!!.sendBroadcast(Intent(curProcessName))
            android.os.Process.killProcess(android.os.Process.myPid())
        }

    }

    /**
     * 自定义错误处理,收集错误信息 发送错误报告等操作均在此完成.
     *
     * @param ex
     * @return 处理了该异常返回true, 否则false
     */
    private fun handleException(ex: Throwable?): Boolean {
        if (ex == null) {
            return false
        }
        //收集设备参数信息
        collectDeviceInfo(mContext!!)
        //添加自定义信息
        addCustomInfo()
        //使用Toast来显示异常信息
        object : Thread() {
            override fun run() {
                Looper.prepare()
                //在此处处理出现异常的情况
                Toast.makeText(mContext, "程序开小差了呢..", Toast.LENGTH_SHORT).show()
                Looper.loop()
            }
        }.start()
        //保存日志文件
        LogUtils.d(TAG, "异常保存文件名：" + saveCrashInfo2File(ex)!!)
        return true
    }


    /**
     * 收集设备参数信息
     *
     * @param ctx
     */
    private fun collectDeviceInfo(ctx: Context) {
        //获取versionName,versionCode
        try {
            val pm = ctx.packageManager
            val pi = pm.getPackageInfo(ctx.packageName, PackageManager.GET_ACTIVITIES)
            if (pi != null) {
                val versionName = if (pi.versionName == null) "null" else pi.versionName
                val versionCode = pi.versionCode.toString()
                infos["versionName"] = versionName
                infos["versionCode"] = versionCode
            }
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "an error occurred when collect package info", e)
        }

        //获取所有系统信息
        val fields = Build::class.java.declaredFields
        for (field in fields) {
            try {
                field.isAccessible = true
                infos[field.name] = field.get(null).toString()
                LogUtils.d(TAG, field.name + " : " + field.get(null))
            } catch (e: Exception) {
                LogUtils.w(TAG, "an error occurred when collect crash info ${e.message}")
            }

        }
    }

    /**
     * 添加自定义参数
     */
    private fun addCustomInfo() {
        Log.i(TAG, "addCustomInfo: 程序出错了...")
    }

    /**
     * 保存错误信息到文件中
     *
     * @param ex
     * @return 返回文件名称, 便于将文件传送到服务器
     */
    private fun saveCrashInfo2File(ex: Throwable): String? {
        // 删除旧的Cache文件，只保留10个
        FileUtils.deleteOldFiles(FileUtils.abnormalLogCacheDir, 9)

        val sb = StringBuilder()
        for ((key, value) in infos) {
            sb.append(key).append("=").append(value).append("\n")
        }

        val writer = StringWriter()
        val printWriter = PrintWriter(writer)
        ex.printStackTrace(printWriter)
        var cause: Throwable? = ex.cause
        while (cause != null) {
            cause.printStackTrace(printWriter)
            cause = cause.cause
        }
        printWriter.close()
        val result = writer.toString()
        sb.append(result)
        try {
            val time = format.format(Date())
            val fileName = "$time.log"
            val path = FileUtils.abnormalLogCacheDir

            val fos = FileOutputStream(path + fileName)
            fos.write(sb.toString().toByteArray())
            LogUtils.d(TAG, "saveCrashInfo2File: $sb")
            fos.close()
            return fileName
        } catch (e: Exception) {
            LogUtils.w(TAG, "an error occurred while writing file... ${e.message}")
        }

        return null
    }
}