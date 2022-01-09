package com.lkl.medialib.core

import com.lkl.commonlib.util.BitmapUtils
import com.lkl.commonlib.util.DateUtils
import com.lkl.commonlib.util.LogUtils
import com.lkl.medialib.bean.FrameData
import com.lkl.yuvjni.YuvUtils
import java.nio.ByteBuffer
import java.util.*

/**
 * 时间水印工作线程
 *
 * @author likunlun
 * @since 2022/01/09
 */
class TimeWatermarkThread(
    private val width: Int,
    private val height: Int,
    private val colorFormat: Int,
    private val callback: CodecCallback,
    threadName: String = TAG
) : BaseMediaThread(threadName) {
    companion object {
        private const val TAG = "TimeWatermarkThread"
    }

    private var lastTimeWaterMark = ""
    private var lastTimeWaterMarkWidth = 0
    private var lastTimeWaterMarkHeight = 0
    private lateinit var lastTimeWaterMarkYuv: ByteArray

    override fun prepare() {
        LogUtils.e(TAG, "size: $width * $height colorFormat: $colorFormat")
        callback.prepare()
    }

    override fun drain() {
        val frameData = callback.getFrameData()
        frameData?.apply {
            nv21ToYuv420p(this)
        }
    }

    /**
     * nv21数据添加时间水印并转为YUV420P
     *
     * @param frameData 视频帧数据
     */
    private fun nv21ToYuv420p(frameData: FrameData) {
        generateTimeWaterMarkBitmap(frameData.timestamp)

        // NV21数据添加时间水印
        YuvUtils.NV21AddWaterMark(
            width - lastTimeWaterMarkWidth - lastTimeWaterMarkHeight,
            height - 2 * lastTimeWaterMarkHeight,
            lastTimeWaterMarkYuv,
            lastTimeWaterMarkWidth,
            lastTimeWaterMarkHeight,
            frameData.data,
            width,
            height
        )

        // 将NV21数据转为YUV420P（I420）
//        YuvUtils.NV21ToI420(frameData.data, yuv, width, height, false)
        callback.putFrameData(frameData)
    }

    private fun generateTimeWaterMarkBitmap(timestamp: Long) {
        val time = DateUtils.convertDateToString(DateUtils.DATE_TIME_MS, Date(timestamp))
        if (lastTimeWaterMark != time) {
            lastTimeWaterMark = time
            // 时间戳变动了，才需要重新生成新的水印数据
            // 生成时间水印图片
            val bitmap = BitmapUtils.textAsBitmap(time, 30f)
            lastTimeWaterMarkWidth = bitmap.width
            lastTimeWaterMarkHeight = bitmap.height
            // 获取argb byte 数组
            val timeBuffer =
                ByteBuffer.allocate(lastTimeWaterMarkWidth * lastTimeWaterMarkHeight * 4)
            bitmap.copyPixelsToBuffer(timeBuffer)
            // 图片数据释放
            bitmap.recycle()
            lastTimeWaterMarkYuv =
                ByteArray(lastTimeWaterMarkWidth * lastTimeWaterMarkHeight * 3 / 2)
            // 将argb数据转为 NV21
//            YuvUtils.RgbaToI420(Key.ARGB_TO_I420, timeBuffer.array(), lastTimeWaterMarkYuv, lastTimeWaterMarkWidth, lastTimeWaterMarkHeight);
            YuvUtils.ArgbToNV21(
                timeBuffer.array(),
                lastTimeWaterMarkYuv,
                lastTimeWaterMarkWidth,
                lastTimeWaterMarkHeight
            )
        }
    }

    override fun release() {
        callback.finished()
    }
}