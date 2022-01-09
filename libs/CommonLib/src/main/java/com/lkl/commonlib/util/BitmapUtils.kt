package com.lkl.commonlib.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.lkl.commonlib.BaseApplication.Companion.context
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream


/**
 * 创建者     likunlun
 * 创建时间   2018/11/1 9:05
 * 描述	      图片处理工具类
 */
object BitmapUtils {
    private const val TAG = "BitmapUtils"

    // 录制视频文件的扩展名
    @JvmField
    val VIDEO_FILE_EXT = ".mp4"

    @JvmField
    val IMAGE_FILE_EXT_PNG = ".png"

    @JvmField
    val IMAGE_FILE_EXT_JPG = ".jpg"

    // 图片文件的扩展名
    @JvmField
    val IMAGE_FILE_EXT = IMAGE_FILE_EXT_JPG

    /**
     * 抽取视频的第一帧图片并保存到本地
     * @param uri 视频文件的全路径
     * @param timeStamp 要获取的图片时间戳 ms 特别注意：该时间戳是指相对于录制时（0），不是时刻表
     * @param fileExt 图片文件名扩展
     * @return 抽取的第一帧图片保存的全路径
     */
    @JvmStatic
    fun saveFrameBitmap(uri: String, timeStamp: Long, fileExt: String): String? {
        val bitmap = getFrameBitmap(uri, timeStamp) ?: return null
        val fileName = uri.substringAfterLast("/").substringBeforeLast(".")

        return saveBitmap(FileUtils.bitmapDir + fileName + fileExt, bitmap, fileExt)
    }

    /**
     * 获取网络/本地视频指定时间的帧图片
     * @param url 视频文件的url地址
     * @param timeStamp 要获取的图片时间戳 ms 特别注意：该时间戳是指相对于录制时（0），不是时刻表
     * @param isSd 是否是本地视频文件
     * @return 第一帧图片
     */
    fun getFrameBitmap(uri: String, timeStamp: Long, isSd: Boolean = true): Bitmap? {
        var bitmap: Bitmap? = null
        //MediaMetadataRetriever 是android中定义好的一个类，提供了统一的接口，用于从输入的媒体文件中取得帧和元数据
        val retriever = MediaMetadataRetriever()
        try {
            if (isSd) {
                //（）根据文件路径获取缩略图
                retriever.setDataSource(context, Uri.fromFile(File(uri)))
            } else {
                //根据网络路径获取缩略图
                retriever.setDataSource(uri, HashMap())
            }
            //获得指定时间戳的图片
            bitmap = retriever.getFrameAtTime(timeStamp * 1000)
        } catch (e: Exception) {
            LogUtils.w(TAG, e)
        } finally {
            retriever.release()
        }
        return bitmap
    }

    /**
     * 保存图片到本地
     * @param uri  要保存的全路径
     * @param bitmap 图片文件
     * @return null 保存失败 否则返回保存成功后的全路径
     */
    fun saveBitmap(
        uri: String?,
        bitmap: Bitmap,
        compressFormat: String = IMAGE_FILE_EXT_PNG
    ): String? {
        if (uri.isNullOrEmpty()) {
            return null
        }
        val f = File(uri)
        if (f.exists()) {
            f.delete()
        }
        return try {
            val out = FileOutputStream(f)
            val image = compressImage(bitmap, compressFormat)
            if (compressFormat == IMAGE_FILE_EXT_PNG) {
                image.compress(Bitmap.CompressFormat.PNG, 100, out)
            } else {
                image.compress(Bitmap.CompressFormat.JPEG, 100, out)
            }
            out.flush()
            out.close()
            uri
        } catch (e: Exception) {
            LogUtils.w(TAG, e)
            null
        }
    }

    /**
     * 图片尺寸压缩到 500kb
     * @param image 压缩前图片
     * @return 压缩后图片
     */
    fun compressImage(image: Bitmap, compressFormat: String = IMAGE_FILE_EXT_PNG): Bitmap {
        val baos = ByteArrayOutputStream()
        if (compressFormat == IMAGE_FILE_EXT_PNG) {
            image.compress(Bitmap.CompressFormat.PNG, 100, baos)
        } else {
            image.compress(Bitmap.CompressFormat.JPEG, 100, baos)
        }
        var options = 100
        while (baos.size() > 500 * 1024) {    //循环判断如果压缩后图片是否大于500kb,大于继续压缩
            baos.reset()//重置baos即清空baos
            options -= 10//每次都减少5
            if (compressFormat == IMAGE_FILE_EXT_PNG) {
                image.compress(
                    Bitmap.CompressFormat.PNG,
                    options,
                    baos
                )//这里压缩options%，把压缩后的数据存放到baos中
            } else {
                image.compress(Bitmap.CompressFormat.JPEG, options, baos)
            }
        }

        LogUtils.d(TAG, "image compress quality $options")

        val isBm = ByteArrayInputStream(baos.toByteArray())//把压缩后的数据baos存放到ByteArrayInputStream中
        return BitmapFactory.decodeStream(isBm)
    }

    fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int, reqHeight: Int
    ): Int {
        // 源图片的高度和宽度
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            // 计算出实际宽高和目标宽高的比率
            val heightRatio = Math.round(height.toFloat() / reqHeight.toFloat())
            val widthRatio = Math.round(width.toFloat() / reqWidth.toFloat())
            // 选择宽和高中最小的比率作为inSampleSize的值，这样可以保证最终图片的宽和高
            // 一定都会大于等于目标的宽和高。
            inSampleSize = if (heightRatio < widthRatio) heightRatio else widthRatio
        }
        return inSampleSize
    }

    /**
     * 从文件中解析图片，压缩处理，防止加载过大的图片导致OOM
     */
    fun decodeSampledBitmapFromFile(uri: String, reqWidth: Int, reqHeight: Int): Bitmap {
        // 第一次解析将inJustDecodeBounds设置为true，来获取图片大小
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        BitmapFactory.decodeFile(uri, options)
        // 调用上面定义的方法计算inSampleSize值
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
        // 使用获取到的inSampleSize值再次解析图片
        options.inJustDecodeBounds = false
        return BitmapFactory.decodeFile(uri, options)
    }

    fun textAsBitmap(text: String, textSize: Float = 30f, textColor: Int = Color.RED): Bitmap {
        val textPaint = TextPaint()
        // 特别注意：使用的libyuv中ARGB对应的是这边的ABGR，即 BLUE转化为RED，red、blue色彩做对换
        textPaint.color = textColor
        textPaint.isAntiAlias = true
        textPaint.textSize = textSize
        val length = textPaint.measureText(text).toInt()

        val layout = StaticLayout(
            text, textPaint, length, Layout.Alignment.ALIGN_NORMAL,
            0.1f, 0.0f, true
        )

        // 保证图片的width、height 为偶数，否则 ARGB -> NV21 数据可能会导致数据失真
        val bitmap = Bitmap.createBitmap(
            (layout.width + 1) / 2 * 2,
            (layout.height + 1) / 2 * 2, Bitmap.Config.ARGB_8888
        )

        val canvas = Canvas(bitmap)
        canvas.translate(0f, 0f)
        // 设置透明背景
        canvas.drawColor(Color.TRANSPARENT)
        layout.draw(canvas)
        return bitmap
    }
}