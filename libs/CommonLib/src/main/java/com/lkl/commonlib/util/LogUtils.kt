package com.lkl.commonlib.util

import android.util.Log
import com.lkl.commonlib.BuildConfig
import java.io.*
import java.net.UnknownHostException

/**
 * log工具类
 *
 * @author lkl
 * @since 2021/12/19
 */
object LogUtils {
    private val isDebugModel = BuildConfig.DEBUG// 是否输出日志
    private const val isSaveDebugInfo = true// 是否保存调试日志
    private const val isSaveCrashInfo = true// 是否保存报错日志

    private const val tag = "TestAssist"
    private const val logLevel = Log.VERBOSE

    /**
     * Get The Current Function Name
     *
     * @return
     */
    private val funcName: String?
        get() {
            val sts = Thread.currentThread().stackTrace ?: return null
            for (st in sts) {
                if (st.isNativeMethod
                        || st.className == Thread::class.java.name
                        || st.className == this.javaClass.name) {
                    continue
                }
                return "[ ${Thread.currentThread().name} : ${st.fileName}:${st.lineNumber} ${st.methodName} ]"
            }
            return null
        }


    /**
     * The Log Level:i
     *
     * @param str
     */
    fun i(str: Any?) {
        i(tag, str)
    }

    /**
     * The Log Level:i
     *
     * @param str
     */
    @JvmStatic
    fun i(tag: String, str: Any?) {
        val name = funcName
        if (isDebugModel && logLevel <= Log.INFO) {
            Log.i(tag, "${name ?: ""} - ${str ?: "null"}")
        }

        saveDebugInfo(name, tag, str)
    }

    /**
     * The Log Level:d
     *
     * @param str
     */
    @JvmStatic
    fun d(str: Any?) {
        d(tag, str)
    }

    /**
     * The Log Level:d
     *
     * @param str
     */
    @JvmStatic
    fun d(tag: String, str: Any?) {
        val name = funcName
        if (isDebugModel && logLevel <= Log.DEBUG) {
            Log.d(tag, "${name ?: ""} - ${str ?: "null"}")
        }

        saveDebugInfo(name, tag, str)
    }

    /**
     * The Log Level:V
     *
     * @param str
     */
    fun v(str: Any?) {
        v(tag, str)
    }

    /**
     * The Log Level:V
     *
     * @param str
     */
    fun v(tag: String, str: Any?) {
        val name = funcName
        if (isDebugModel && logLevel <= Log.VERBOSE) {
            Log.v(tag, "${name ?: ""} - ${str ?: "null"}")
        }

        saveDebugInfo(name, tag, str)
    }

    /**
     * The Log Level:w
     *
     * @param str
     */
    fun w(str: Any?) {
        w(tag, str)
    }

    /**
     * The Log Level:w
     *
     * @param str
     */
    @JvmStatic
    fun w(tag: String, str: Any?) {
        val name = funcName
        if (isDebugModel && logLevel <= Log.WARN) {
            Log.w(tag, "${name ?: ""} - ${str ?: "null"}")
        }

        saveDebugInfo(name, tag, str)
    }

    /**
     * 调试日志，便于开发跟踪。
     *
     * @param str
     */
    @JvmStatic
    fun e(str: Any?) {
        e(tag, str)
    }

    /**
     * 调试日志，便于开发跟踪。
     *
     * @param str
     */
    @JvmStatic
    fun e(tag: String, str: Any?) {
        val name = funcName
        if (isDebugModel && logLevel <= Log.ERROR) {
            Log.e(tag, "${name ?: ""} - ${str ?: "null"}")
        }

        if (isSaveCrashInfo) {
            saveCrashInfo(name, tag, str)
        } else {
            saveDebugInfo(name, tag, str)
        }
    }

    /**
     * The Log Level:e
     *
     * @param ex
     */
    @JvmStatic
    fun e(ex: Exception) {
        e(tag, ex)
    }

    /**
     * The Log Level:e
     *
     * @param ex
     */
    @JvmStatic
    fun e(tag: String, ex: Exception) {
        val name = funcName
        if (isDebugModel && logLevel <= Log.ERROR) {
            Log.e(tag, "error", ex)
        }

        if (isSaveCrashInfo) {
            saveCrashInfo(name, tag, ex)
        } else {
            saveDebugInfo(name, tag, ex)
        }
    }

    /**
     * try catch 时使用，上线产品可上传反馈。
     *
     * @param tag
     * @param tr
     */
    fun e(tag: String, tr: Throwable) {
        e(tag, "", tr)
    }

    /**
     * Send a  log message and log the exception.
     *
     * @param tag Used to identify the source of a log message.  It usually identifies
     * the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     * @param tr  An exception to log
     */
    @JvmStatic
    fun e(tag: String, msg: String?, tr: Throwable) {
        var message = msg
        val name = funcName

        if (message.isNullOrEmpty()) {
            message = ""
        } else {
            message += "\n"
        }
        if (isDebugModel) {
            Log.e(tag, "{Thread:${Thread.currentThread().name}} [$name] $tag : $message", tr)
        }

        if (isSaveCrashInfo) {
            saveCrashInfo(name, tag, message + getStackTraceString(tr))
        } else {
            saveDebugInfo(name, tag, message + getStackTraceString(tr))
        }
    }

    /**
     * 保存debug信息到文本
     *
     * @param name
     * @param tag
     * @param str
     */
    private fun saveDebugInfo(name: String?, tag: String, str: Any?) {
        if (isSaveDebugInfo) {
            object : Thread() {
                override fun run() {
                    write("${time()} [$tag] ${name
                            ?: ""} --> ${str ?: "null"}\n")
                }
            }.start()
        }
    }

    /**
     * 保存异常信息到文本
     *
     * @param name
     * @param tag
     * @param str
     */
    private fun saveCrashInfo(name: String?, tag: String, str: Any?) {
        if (isSaveCrashInfo) {
            object : Thread() {
                override fun run() {
                    write("${time()} [$tag] ${name
                            ?: ""} [CRASH] --> ${str ?: "null"}\n")
                }
            }.start()
        }
    }

    /**
     * Handy function to get a loggable stack trace from a Throwable
     *
     * @param tr An exception to log
     */
    private fun getStackTraceString(tr: Throwable?): String {
        if (tr == null) {
            return ""
        }

        // This is to reduce the amount of log spew that apps do in the non-error
        // condition of the network being unavailable.
        var t = tr
        while (t != null) {
            if (t is UnknownHostException) {
                return ""
            }
            t = t.cause
        }

        val sw = StringWriter()
        val pw = PrintWriter(sw)
        tr.printStackTrace(pw)
        pw.flush()
        return sw.toString()
    }

    /**
     * 获取日志文件路径  以yyyy-MM-dd作为日志文件名称
     *
     * @return
     */
    private val file: String
        get() {
            val cacheDir = FileUtils.runLogCacheDir

            val filePath = File(cacheDir + File.separator + DateUtils.nowDate + ".txt")

            return filePath.toString()
        }

    /**
     * 标识每条日志产生的时间  yyyy-MM-dd HH:mm:ss
     *
     * @return
     */
    private fun time(): String {
        return ("[${DateUtils.nowTime}] ")
    }

    /**
     * 保存到日志文件
     *
     * @param content
     */
    @Synchronized
    private fun write(content: String) {
        try {
            val writer = FileWriter(file, true)
            writer.write(content)
            writer.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}
