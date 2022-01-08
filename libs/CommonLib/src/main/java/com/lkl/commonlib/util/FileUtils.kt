package com.lkl.commonlib.util

import android.content.Context
import android.os.Environment
import android.text.TextUtils
import com.lkl.commonlib.BaseApplication.Companion.context
import java.io.*

/**
 * @author 文件操作的工具类
 */
object FileUtils {
    private const val TAG = "FileUtils"

    private val ROOT_DIR = "Android/data/" + context.packageName
    private const val DOWNLOAD_DIR = "download"
    private const val CACHE_DIR = "cache"
    private const val BITMAP_DIR = "cache/bitmap"
    private const val VIDEO_DIR = "cache/video"
    private const val RUN_LOG_CACHE_DIR = "dPhoneLog"
    private const val ABNORMAL_LOG_CACHE_DIR = "/sdcard/Cache/"

    /**
     * 获取运行日志文件存储目录
     */
    @JvmStatic
    val runLogCacheDir: String?
        get() = getDiskCacheDir(context, RUN_LOG_CACHE_DIR)

    /**
     * 获取崩溃日志文件存储目录
     */
    @JvmStatic
    val abnormalLogCacheDir: String
        get() {
            createDirs(ABNORMAL_LOG_CACHE_DIR)
            return ABNORMAL_LOG_CACHE_DIR
        }

    /**
     * 判断SD卡是否挂载
     */
    @JvmStatic
    val isSDCardAvailable: Boolean
        get() = Environment.MEDIA_MOUNTED == Environment
                .getExternalStorageState()

    /**
     * 获取下载目录
     */
    @JvmStatic
    val downloadDir: String?
        get() = getDiskCacheDir(DOWNLOAD_DIR)

    /**
     * 获取缓存目录
     */
    val cacheDir: String?
        get() = getDiskCacheDir(CACHE_DIR)

    /**
     * 获取开门图片存储目录
     */
    @JvmStatic
    val bitmapDir: String?
        get() = getDiskCacheDir(BITMAP_DIR)

    /**
     * 获取开门录像存储目录
     */
    @JvmStatic
    val videoDir: String?
        get() = getDiskCacheDir(VIDEO_DIR)


    /**
     * 获取SD下的应用目录
     */
    private val externalStoragePath: String
        get() {
            val sb = StringBuilder()
            sb.append(Environment.getExternalStorageDirectory().absolutePath)
            sb.append(File.separator)
            sb.append(ROOT_DIR)
            sb.append(File.separator)
            return sb.toString()
        }

    /**
     * 获取应用的cache目录
     */
    private val cachePath: String?
        get() {
            val f = context.cacheDir
            return if (null == f) {
                null
            } else {
                f.absolutePath + "/"
            }
        }

    /**
     * 获取应用缓存目录，当SD卡存在时，获取SD卡上的缓存目录，当SD卡不存在时，获取应用的cache目录
     */
    private fun getDiskCacheDir(name: String): String? {
        val sb = StringBuilder()
        if (isSDCardAvailable) {
            sb.append(externalStoragePath)
        } else {
            sb.append(cachePath)
        }
        sb.append(name)
        sb.append(File.separator)
        val path = sb.toString()
        return if (createDirs(path)) {
            path
        } else {
            null
        }
    }

    /**
     * 获取diskCache的文件完整路径
     *
     * @param context    上下文
     * @param uniqueName 目录名
     * @return diskCache的文件完整路径
     */
    fun getDiskCacheDir(context: Context, uniqueName: String): String {
        var cachePath: String? = null
        if (Environment.MEDIA_MOUNTED == Environment.getExternalStorageState()
                || !Environment.isExternalStorageRemovable()) {
            val file = context.externalCacheDir
            if (null != file) {
                cachePath = file.path
            }
        }

        if (TextUtils.isEmpty(cachePath)) {
            cachePath = context.cacheDir.path
        }

        cachePath += File.separator + uniqueName + File.separator
        val dir = File(cachePath)
        // 判断文件夹是否存在，不存在则创建
        if (!dir.exists()) {
            dir.mkdir()
        }
        return cachePath ?: ""
    }

    /**
     * 创建文件夹
     */
    private fun createDirs(dirPath: String): Boolean {
        val file = File(dirPath)
        return if (!file.exists() || !file.isDirectory) {
            file.mkdirs()
        } else true
    }

    /**
     * 判断文件是否可写
     */
    fun isWriteable(path: String): Boolean {
        try {
            if (TextUtils.isEmpty(path)) {
                return false
            }
            val f = File(path)
            return f.exists() && f.canWrite()
        } catch (e: Exception) {
            LogUtils.e(e)
            return false
        }
    }

    /**
     * 修改文件的权限,例如"777"等
     */
    fun chmod(path: String, mode: String) {
        try {
            val command = "chmod $mode $path"
            val runtime = Runtime.getRuntime()
            runtime.exec(command)
        } catch (e: Exception) {
            LogUtils.e(e)
        }
    }

    /**
     * 删除指定文件
     * @param uri 文件的全路径
     * @return true 删除成功  false 删除失败
     */
    @JvmStatic
    fun deleteFile(uri: String?): Boolean {
        if (uri.isNullOrBlank()) {
            return false
        }
        return try {
            val file = File(uri)
            deleteFile(file)
        } catch (e: Exception) {
            LogUtils.e(TAG, e)
            false
        }
    }

    @JvmStatic
    fun deleteFile(file: File): Boolean {
        return try {
            file.delete()
        } catch (e: Exception) {
            LogUtils.e(TAG, e)
            false
        }
    }

    /**
     * 删除指定的文件夹及下面所有的文件
     */
    @JvmStatic
    fun deleteDir(dirPath: String?) {
        dirPath?.apply {
            val dirFile = File(dirPath)
            deleteDir(dirFile)
        }
    }

    fun deleteDir(dirFile: File) {
        if (dirFile.isDirectory) {
            dirFile.listFiles().forEach {
                if (it.isDirectory) {
                    deleteDir(it)
                } else {
                    deleteFile(it)
                }
            }
            // 不删除目录，只删文件
//            deleteFile(dirFile)
        }
    }

    /**
     * 获取指定目录下的所有文件名
     * @param dirUri 目录全路径
     * @return 文件名列表（不带扩展名）
     */
    @JvmStatic
    fun getDirFiles(dirUri: String?): List<String> {
        if (dirUri.isNullOrEmpty()) {
            return emptyList()
        }
        val dir = File(dirUri)
        if (dir.isDirectory) {
            return dir.listFiles().filter {
                //过滤出文件
                it.isFile
            }.map {
                //去掉扩展名
                it.name
            }.sorted()
        }

        return emptyList()
    }

    /**
     * 获取指定目录下的所有文件
     * @param dirUri 目录全路径
     * @return 文件列表
     */
    @JvmStatic
    fun getDirFile(dirUri: String?): Array<File> {
        if (dirUri.isNullOrEmpty()) {
            return emptyArray()
        }
        val dir = File(dirUri)
        if (dir.isDirectory) {
            return dir.listFiles()
        }

        return emptyArray()
    }

    /**
     * 读取文件内容
     * @param filePath 文件路径
     * @return 字节数组
     */
    @JvmStatic
    fun readFile(filePath: String): ByteArray? {
        val file = File(filePath)
        if (file.isFile) {
            // 以字节流方法读取文件

            var fis: FileInputStream? = null
            try {
                fis = FileInputStream(file)
                // 设置一个，每次 装载信息的容器
                val buffer = ByteArray(1024)
                val outputStream = ByteArrayOutputStream()
                // 开始读取数据
                var len = fis.read(buffer)// 每次读取到的数据的长度
                while (len != -1) {// len值为-1时，表示没有数据了
                    // append方法往sb对象里面添加数据
                    outputStream.write(buffer, 0, len)
                    len = fis.read(buffer)
                }
                // 输出字符串
                return outputStream.toByteArray()
            } catch (e: IOException) {
                LogUtils.e(TAG, e)
            } finally {
                fis?.apply {
                    close()
                    fis = null
                }
            }
        } else {
            LogUtils.d(TAG, "文件不存在！")
        }
        return null
    }

    /**
     * 删除指定文件夹中旧的数据（保留指定数量的文件）
     *
     * @param dirPath 目标目录
     * @param reserveCount 需要保留的文件数量
     */
    fun deleteOldFiles(dirPath: String?, reserveCount: Int) {
        val fileList = getDirFiles(dirPath)
        if (fileList.size > reserveCount) {
            val deleteFiles = fileList.subList(0, fileList.size - reserveCount)
            deleteFiles.forEach {
                deleteFile(dirPath + it)
            }
        }
    }

    @Throws(java.io.IOException::class)
    @JvmStatic
    fun loadFileAsString(filePath: String): String {
        val fileData = StringBuffer(1024)
        val reader = BufferedReader(FileReader(filePath))
        val buf = CharArray(1024)
        var numRead = reader.read(buf)
        while (numRead != -1) {
            val readData = String(buf, 0, numRead)
            fileData.append(readData)
            numRead = reader.read(buf)
        }
        reader.close()
        return fileData.toString()
    }

    /**
     * 把数据存入指定的文件
     *
     * @param path 文件路径
     * @param data 数据
     */
    fun writeToFile(path: String, data: ByteArray) {
        try {
            val fos = FileOutputStream(path, false)
            fos.write(data)
            fos.close()
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }
}
