package com.lkl.commonlib.util

import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import com.lkl.commonlib.util.BitmapUtils.saveBitmap
import com.lkl.commonlib.util.DateUtils.convertDateToString
import com.lkl.commonlib.util.LogUtils.d
import com.lkl.yuvjni.YuvUtils
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.util.*

/**
 * 图片格式转换工具类
 *
 * @author likunlun
 * @since 2021/12/20
 */
object ImageFormatTransformUtils {
    private const val TAG = "ImageFormatTransformUtils"
    const val COLOR_FormatI420 = 1
    const val COLOR_FormatNV21 = 2

    /**
     * 判断Image格式是否支持
     *
     * @param image Image对象
     * @return true 支持的格式
     */
    fun isImageFormatSupported(image: Image): Boolean {
        val format = image.format
        when (format) {
            ImageFormat.YUV_420_888, ImageFormat.NV21, ImageFormat.YV12 -> return true
        }
        return false
    }

    /**
     * 将Image数据转化为指定格式的YUV数据(1280 * 720 -> 25ms  CPU -> 10%)
     *
     * @param image Image对象
     * @param colorFormat 转化格式
     * @return YUV数据
     */
    fun getDataFromImage(image: Image, colorFormat: Int): ByteArray {
        require(!(colorFormat != COLOR_FormatI420 && colorFormat != COLOR_FormatNV21)) { "only support COLOR_FormatI420 " + "and COLOR_FormatNV21" }
        if (!isImageFormatSupported(image)) {
            throw RuntimeException("can't convert Image to byte array, format " + image.format)
        }
        val crop = image.cropRect
        val format = image.format
        val width = crop.width()
        val height = crop.height()
        val planes = image.planes
        val data = ByteArray(width * height * ImageFormat.getBitsPerPixel(format) / 8)
        val rowData = ByteArray(planes[0].rowStride)
        var channelOffset = 0
        var outputStride = 1
        for (i in planes.indices) {
            when (i) {
                0 -> {
                    channelOffset = 0
                    outputStride = 1
                }
                1 -> if (colorFormat == COLOR_FormatI420) {
                    channelOffset = width * height
                    outputStride = 1
                } else if (colorFormat == COLOR_FormatNV21) {
                    channelOffset = width * height + 1
                    outputStride = 2
                }
                2 -> if (colorFormat == COLOR_FormatI420) {
                    channelOffset = (width * height * 1.25).toInt()
                    outputStride = 1
                } else if (colorFormat == COLOR_FormatNV21) {
                    channelOffset = width * height
                    outputStride = 2
                }
            }
            val buffer = planes[i].buffer
            val rowStride = planes[i].rowStride
            val pixelStride = planes[i].pixelStride
            val shift = if (i == 0) 0 else 1
            val w = width shr shift
            val h = height shr shift
            buffer.position(rowStride * (crop.top shr shift) + pixelStride * (crop.left shr shift))
            for (row in 0 until h) {
                var length: Int
                if (pixelStride == 1 && outputStride == 1) {
                    length = w
                    buffer[data, channelOffset, length]
                    channelOffset += length
                } else {
                    length = (w - 1) * pixelStride + 1
                    buffer[rowData, 0, length]
                    for (col in 0 until w) {
                        data[channelOffset] = rowData[col * pixelStride]
                        channelOffset += outputStride
                    }
                }
                if (row < h - 1) {
                    buffer.position(buffer.position() + rowStride - length)
                }
            }
        }
        return data
    }

    /**
     * 得到转换后的byte[]后就可以直接写入到文件了
     *
     * @param fileName 文件名
     * @param data 数据
     */
    fun dumpFile(fileName: String, data: ByteArray?) {
        val outStream: FileOutputStream
        outStream = try {
            FileOutputStream(fileName)
        } catch (ioe: IOException) {
            throw RuntimeException("Unable to create output file $fileName", ioe)
        }
        try {
            outStream.write(data)
            outStream.close()
        } catch (ioe: IOException) {
            throw RuntimeException("failed writing data to file $fileName", ioe)
        }
    }

    /**
     * NV12、NV21格式互相转换(1280 * 720 -> 15ms  CPU -> 10%)
     *
     * @param data 转换前后的YUV数据
     * @param width 图片的宽度
     * @param height 图片的高度
     */
    @JvmStatic
    fun nv12ConvertNV21(data: ByteArray, width: Int, height: Int) {
        var tmp: Byte
        for (index in width * height until data.size) {
            if ((index + 1) % 2 == 0) {
                tmp = data[index - 1]
                data[index - 1] = data[index]
                data[index] = tmp
            }
        }
    }

    /**
     * 将NV12数据保存为jpg图片(NV12 -> ARGB8888 -> bitmap -> jpg)
     *
     * @param data NV12数据
     * @param width 宽度
     * @param height 高度
     * @param timeSptamp 时间戳 us
     * @return jpg文件路径
     */
    fun saveNV12ToJpg(data: ByteArray?, width: Int, height: Int, timeSptamp: Long): String {
        d(TAG, "start NV12 to Argb")
        // 将NV12格式的数据转为ARGB_8888
        val argb = ByteArray(width * height * 4)
        YuvUtils.NV12ToArgb(data, argb, width, height)
        d(TAG, "end NV12 to Argb")
        // 将AGRB_8888转为Bitmap对象
        val stitchBmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        stitchBmp.copyPixelsFromBuffer(ByteBuffer.wrap(argb))
        val time = convertDateToString(DateUtils.DATE_TIME, Date(timeSptamp / 1000))
        val fileName = "/sdcard/" + time.replace(" ", "_") + ".jpg"
        saveBitmap(fileName, stitchBmp, BitmapUtils.IMAGE_FILE_EXT_JPG)
        d(TAG, "end NV12 to jpg")
        return fileName
    }

    /**
     * 将NV12数据保存为jpg图片(NV12 -> NV21 -> YuvImage -> jpg)
     *
     * @param data NV12数据
     * @param width 宽度
     * @param height 高度
     * @param timeSptamp 时间戳 us
     * @param swapUV 是否交换UV分量
     * @return jpg文件路径
     */
    @JvmStatic
    fun saveNV12ToJpg2(
        data: ByteArray,
        width: Int,
        height: Int,
        timeSptamp: Long,
        swapUV: Boolean
    ): String {
        d(TAG, "start NV12 to NV21")
        if (swapUV) {
            // 将NV12格式的数据转为NV21
            nv12ConvertNV21(data, width, height)
        }
        d(TAG, "end NV12 to NV21")
        // 将AGRB_8888转为Bitmap对象
//        String time = DateUtils.convertDateToString(DateUtils.DATE_TIME, new Date(timeSptamp / 1000));
//        String fileName = "/sdcard/iotaImage/" + time.replace(" ", "_") + ".jpg";
        val fileName = "/sdcard/iotaImage/$timeSptamp.jpg"
        val outStream: FileOutputStream
        outStream = try {
            FileOutputStream(fileName)
        } catch (ioe: IOException) {
            throw RuntimeException("Unable to create output file $fileName", ioe)
        }
        val rect = Rect(0, 0, width, height)
        val yuvImage = YuvImage(data, ImageFormat.NV21, width, height, null)
        yuvImage.compressToJpeg(rect, 100, outStream)
        d(TAG, "end NV12 to jpg")
        return fileName
    }

    /**
     * 将Image转化为jpg文件存储
     *
     * @param fileName 文件名
     * @param image Image对象
     */
    fun compressToJpeg(fileName: String, image: Image) {
        val outStream: FileOutputStream
        outStream = try {
            FileOutputStream(fileName)
        } catch (ioe: IOException) {
            throw RuntimeException("Unable to create output file $fileName", ioe)
        }
        val rect = image.cropRect
        val yuvImage = YuvImage(
            getDataFromImage(image, COLOR_FormatNV21),
            ImageFormat.NV21,
            rect.width(),
            rect.height(),
            null
        )
        yuvImage.compressToJpeg(rect, 100, outStream)
    }
}