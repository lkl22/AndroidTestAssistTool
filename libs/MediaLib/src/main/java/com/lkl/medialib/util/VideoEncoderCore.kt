/*
 * Copyright 2014 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.lkl.medialib.util

import com.lkl.commonlib.util.LogUtils.d
import com.lkl.commonlib.util.LogUtils.w
import com.lkl.commonlib.util.DateUtils.convertDateToString
import com.lkl.commonlib.util.BitmapUtils.textAsBitmap
import android.media.MediaCodec
import kotlin.Throws
import android.media.MediaFormat
import com.lkl.medialib.util.VideoEncoderCore
import android.media.MediaCodecInfo
import com.lkl.medialib.constant.VideoConfig
import com.lkl.commonlib.util.LogUtils
import com.lkl.framedatacachejni.FrameDataCacheUtils
import android.graphics.Bitmap
import com.lkl.commonlib.util.BitmapUtils
import com.lkl.commonlib.util.DateUtils
import com.lkl.yuvjni.YuvUtils
import java.io.IOException
import java.lang.Exception
import java.nio.ByteBuffer
import java.util.*

/**
 * This class wraps up the core components used for surface-input video encoding.
 *
 *
 * Once created, frames are fed to the input surface.  Remember to provide the presentation
 * time stamp, and always call drainEncoder() before swapBuffers() to ensure that the
 * producer side doesn't get backed up.
 *
 *
 * This class is not thread-safe, with one exception: it is valid to use the input surface
 * on one thread, and drain the output on a different thread.
 */
class VideoEncoderCore(private val mFrameRate: Int) : Runnable {
    private var mWidth = 0
    private var mHeight = 0

    /**
     * 每帧数据时间间隔
     */
    private val mFramePeriod: Long
    private var mEncoder: MediaCodec? = null
    private val mBufferInfo = MediaCodec.BufferInfo()
    private var mThread: Thread? = null
    private var mStartFlag = false
    private var mNowFeedData: ByteArray? = null

    // 当前帧的时间戳 ms
    private var mNowTimeSptamp: Long = 0
    private val mFrameDataSemaphore = Object()

    /**
     * 准备录制
     *
     * @param width 视频宽度
     * @param height 视频高度
     * @throws IOException
     */
    @Throws(IOException::class)
    fun prepare(width: Int, height: Int) {
        val format = MediaFormat.createVideoFormat(MIME_TYPE, width, height)
        mWidth = width
        mHeight = height

        // Set some properties.  Failing to specify some of these can cause the MediaCodec
        // configure() call to throw an unhelpful exception.
        format.setInteger(
            MediaFormat.KEY_COLOR_FORMAT,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar
        )
        format.setInteger(MediaFormat.KEY_BIT_RATE, VideoConfig.BIT_RATE)
        format.setInteger(MediaFormat.KEY_FRAME_RATE, mFrameRate)
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL)
        d(TAG, "format: $format")

        // Create a MediaCodec encoder, and configure it with our format.  Get a Surface
        // we can use for input and wrap it with a class that handles the EGL work.
        mEncoder = MediaCodec.createEncoderByType(MIME_TYPE)
        mEncoder!!.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)

        // 初始化H264数据缓冲区
        FrameDataCacheUtils.initCache(mFrameRate, width, height, 0)
        mMediaFormat = mEncoder!!.outputFormat
    }

    /**
     * 开始录制
     *
     * @throws InterruptedException
     */
    @Throws(InterruptedException::class)
    fun start() {
        if (mThread != null && mThread!!.isAlive) {
            mStartFlag = false
            mThread!!.join()
        }
        mEncoder!!.start()
        mStartFlag = true
        mThread = Thread(this, "VideoEncoderThread")
        mThread!!.start()
    }

    /**
     * 停止录制
     */
    fun stop() {
        try {
            mStartFlag = false
            synchronized(mFrameDataSemaphore) { mFrameDataSemaphore.notifyAll() }
            mThread!!.join()
            release()
        } catch (e: Exception) {
            w(TAG, e.message)
        }
    }

    /**
     * Releases encoder resources.
     */
    private fun release() {
        d(TAG, "releasing encoder objects")
        if (mEncoder != null) {
            mEncoder!!.stop()
            mEncoder!!.release()
            mEncoder = null
        }
    }

    /**
     * 由外部喂入一帧数据
     *
     * @param data RGBA(NV21)数据
     * @param timeSptamp camera附带时间戳
     */
    fun feedData(data: ByteArray?, timeSptamp: Long) {
//        LogUtils.d(TAG, "new frame data coming " + timeSptamp);
        mNowFeedData = data
        mNowTimeSptamp = timeSptamp
        synchronized(mFrameDataSemaphore) { mFrameDataSemaphore.notifyAll() }
    }

    private var yuv: ByteArray? = null

    //    private byte[] rgbData;
    private var lastTimeWaterMark = ""
    private var lastTimeWaterMarkWidth = 0
    private var lastTimeWaterMarkHeight = 0
    private lateinit var lastTimeWaterMarkYuv: ByteArray

    /**
     * nv21数据添加时间水印并转为YUV420P
     *
     * @param data 摄像机原始数据 NV21
     * @param timeSpam 数据的时间戳
     */
    private fun nv21ToYuv420p(data: ByteArray, timeSpam: Long) {
        val time = convertDateToString(DateUtils.DATE_TIME, Date(timeSpam))
        if (lastTimeWaterMark != time) {
            lastTimeWaterMark = time
            // 时间戳变动了，才需要重新生成新的水印数据
            // 生成时间水印图片
            val bitmap = textAsBitmap(time, 30f)
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

        // NV21数据添加时间水印
        YuvUtils.NV21AddWaterMark(
            mWidth - lastTimeWaterMarkWidth - lastTimeWaterMarkHeight,
            mHeight - 2 * lastTimeWaterMarkHeight,
            lastTimeWaterMarkYuv,
            lastTimeWaterMarkWidth,
            lastTimeWaterMarkHeight,
            data,
            mWidth,
            mHeight
        )

        // 将NV21数据转为YUV420P（I420）
        YuvUtils.NV21ToI420(data, yuv, mWidth, mHeight, false)
    }

    /**
     * 向编码器InputBuffer中填入数据
     *
     * @param data NV21数据
     * @param timestamp 时间戳 ms
     * @throws IOException
     */
    @Throws(IOException::class)
    private fun putDataToInputBuffer(data: ByteArray, timestamp: Long) {
        val index = mEncoder!!.dequeueInputBuffer(-1)
        if (index >= 0) {
            val buffer = mEncoder!!.getInputBuffer(index)
            if (buffer == null) {
                d(TAG, "InputBuffer is null point")
                return
            }
            if (yuv == null) {
                yuv = ByteArray(mWidth * mHeight * 3 / 2)
                //                rgbData = new byte[mWidth * mHeight * 3];
            }
            // YUV图片叠加的方式，会导致时间戳部分文字显示不自然
            nv21ToYuv420p(data, timestamp)

//            YuvUtils.NV21ToI420(data, yuv, mWidth, mHeight, false);
//            LogUtils.i(TAG, "start nv21 to rgb24");
//            YuvUtils.NV21ToRgb24(data,rgbData,mWidth,mHeight);
//            LogUtils.i(TAG, "end nv21 to rgb24");

//            LogUtils.i(TAG,"start add text");
//            OpenCVUtils.yuv420spAddText(data, yuv, 100, 100, mWidth, mHeight,
//                    DateUtils.convertDateToString(DateUtils.DATE_TIME, new Date(timeSptamp)));
//            LogUtils.i(TAG,"end add text");
            buffer.clear()
            buffer.put(yuv)
            mEncoder!!.queueInputBuffer(index, 0, data.size, timestamp * 1000, 0)
        }
        drainEncoder(false)
    }

    /**
     * 读取编码后的H264数据并存入缓存
     *
     * @param endOfStream 标识是否结束
     */
    fun drainEncoder(endOfStream: Boolean) {
        val TIMEOUT_USEC = 10000
        //        LogUtils.d(TAG, "drainEncoder(" + endOfStream + ")");
        if (endOfStream) {
            d(TAG, "sending EOS to encoder")
            return
        }
        while (true) {
            val encoderStatus = mEncoder!!.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC.toLong())
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // no output available yet
                if (!endOfStream) {
                    break // out of while
                } else {
                    d(TAG, "no output available, spinning to await EOS")
                }
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // should happen before receiving buffers, and should only happen once
                mMediaFormat = mEncoder!!.outputFormat
                d(TAG, "encoder output format changed: " + mMediaFormat)
            } else if (encoderStatus < 0) {
                d(
                    TAG, "unexpected result from encoder.dequeueOutputBuffer: " +
                            encoderStatus
                )
            } else {
                val encodedData = mEncoder!!.getOutputBuffer(encoderStatus)
                if (encodedData == null) {
                    w(
                        TAG, "encoderOutputBuffer " + encoderStatus +
                                " was null"
                    )
                    break
                }
                if (mBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                    // The codec config data was pulled out and fed to the muxer when we got
                    // the INFO_OUTPUT_FORMAT_CHANGED status.  Ignore it.
                    d(TAG, "ignoring BUFFER_FLAG_CODEC_CONFIG")
                    mBufferInfo.size = 0
                }
                if (mBufferInfo.size != 0) {
                    // adjust the ByteBuffer values to match BufferInfo (not needed?)
                    encodedData.position(mBufferInfo.offset)
                    encodedData.limit(mBufferInfo.offset + mBufferInfo.size)

                    // 取出编码好的H264数据
                    val data = ByteArray(mBufferInfo.size)
                    encodedData[data]

                    // 将编码好的H264数据存储到缓冲中
                    FrameDataCacheUtils.addFrameData(
                        mBufferInfo.presentationTimeUs / 1000,
                        mBufferInfo.flags == MediaCodec.BUFFER_FLAG_KEY_FRAME,
                        data, mBufferInfo.size
                    )

//                    LogUtils.d(TAG, "sent " + mBufferInfo.size + " bytes to muxer, ts=" +
//                            mBufferInfo.presentationTimeUs);
                }
                mEncoder!!.releaseOutputBuffer(encoderStatus, false)
                if (mBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    if (!endOfStream) {
                        d(TAG, "reached end of stream unexpectedly")
                    } else {
                        d(TAG, "end of stream reached")
                    }
                    break // out of while
                }
            }
        }
    }

    override fun run() {
        while (mStartFlag) {
//            LogUtils.d(TAG, "Encodec start");
//            LogUtils.d(TAG, "new frame data coming " + mNowTimeSptamp);
            if (mNowFeedData != null) {
                synchronized(mFrameDataSemaphore) {
                    try {
                        putDataToInputBuffer(mNowFeedData!!, mNowTimeSptamp)
                    } catch (e: IOException) {
                        w(TAG, e.message)
                    }

//                    LogUtils.d(TAG, "Encodec stop");
                    try {
                        mFrameDataSemaphore.wait()
                    } catch (e: InterruptedException) {
                        w(TAG, e.message)
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "VideoEncoderCore"

        // TODO: these ought to be configurable as well
        private val MIME_TYPE = VideoConfig.MIME // H.264 Advanced Video Coding
        private val IFRAME_INTERVAL = VideoConfig.FRAME_INTERVAL // 1 seconds between I-frames
        var mMediaFormat: MediaFormat? = null
    }

    /**
     * Configures encoder and muxer state, and prepares the input Surface.
     */
    init {
        mFramePeriod = (1000 / mFrameRate).toLong()
    }
}