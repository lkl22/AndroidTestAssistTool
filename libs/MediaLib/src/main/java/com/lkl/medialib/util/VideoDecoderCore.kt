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

import android.media.MediaCodec
import android.media.MediaCodecInfo.CodecCapabilities
import android.media.MediaFormat
import android.os.Build
import android.util.Log
import com.lkl.commonlib.util.ImageFormatTransformUtils.nv12ConvertNV21
import com.lkl.commonlib.util.ImageFormatTransformUtils.saveNV12ToJpg2
import com.lkl.commonlib.util.LogUtils.d
import com.lkl.commonlib.util.LogUtils.e
import com.lkl.commonlib.util.LogUtils.w
import com.lkl.medialib.bean.FrameData
import com.lkl.medialib.constant.VideoProperty
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentLinkedQueue

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
class VideoDecoderCore {
    companion object {
        private const val TAG = "VideoDecoderCore"
        var mMediaFormat: MediaFormat? = null
    }

    private var mFrameRate = 0
    private var mWidth = 0
    private var mHeight = 0

    /**
     * 每帧数据时间间隔
     */
    private var mFramePeriod: Long = 0
    private var mDecoder: MediaCodec? = null
    private val mBufferInfo = MediaCodec.BufferInfo()
    var mColorFormat = 0
    private var mDecoderThread: Thread? = null
    private var mDecoderStartFlag = false
    private val mFrameDataQueue: ConcurrentLinkedQueue<FrameData?> =
        ConcurrentLinkedQueue<FrameData?>()

    @Volatile
    private var isSavaBitmap = false
    private var mCaptureBitmapListener: CaptureBitmapListener? = null

    // 视频预览相关
    private var mPreviewThread: Thread? = null
    private var mPreviewStartFlag = false
    private var mPreviewListener: PreviewListener? = null
    private val mDecodedDataQueue: ConcurrentLinkedQueue<FrameData?> =
        ConcurrentLinkedQueue<FrameData?>()

    /**
     * 准备录制
     *
     * @param width  视频宽度
     * @param height 视频高度
     * @throws IOException
     */
    @Throws(IOException::class)
    fun prepare(
        width: Int,
        height: Int,
        fps: Int,
        codecType: Int,
        sps: ByteArray?,
        pps: ByteArray?
    ) {
        val mimeType = if (codecType == 0) "video/avc" else "video/hevc"
        val format = MediaFormat.createVideoFormat(mimeType, width, height)
        mFrameRate = fps
        mFramePeriod = (1000 / fps).toLong()
        mWidth = width
        mHeight = height
        if (sps != null) {
            format.setByteBuffer("csd-0", ByteBuffer.wrap(sps))
        }
        if (pps != null && codecType == 0) {
            format.setByteBuffer("csd-1", ByteBuffer.wrap(pps))
        }
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 0)
        format.setInteger(MediaFormat.KEY_PUSH_BLANK_BUFFERS_ON_STOP, 1)

        // Set some properties.  Failing to specify some of these can cause the MediaCodec
        // configure() call to throw an unhelpful exception.
//        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
//                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar);
        format.setInteger(
            MediaFormat.KEY_COLOR_FORMAT,
            CodecCapabilities.COLOR_FormatYUV420Flexible
        )
        format.setInteger(MediaFormat.KEY_BIT_RATE, VideoProperty.BIT_RATE)
        format.setInteger(MediaFormat.KEY_FRAME_RATE, mFrameRate)
        //        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);
        d(TAG, "format: $format")
        mMediaFormat = format
        // Create a MediaCodec encoder, and configure it with our format.  Get a Surface
        // we can use for input and wrap it with a class that handles the EGL work.
        val ci = MediaUtils.selectCodec(mimeType)
        if (ci == null) {
            d(TAG, "media decodec not support")
            return
        }
        mColorFormat = MediaUtils.selectColorFormat(ci, mimeType)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val capabilities = ci.getCapabilitiesForType(mimeType)
            val videoCapabilities = capabilities.videoCapabilities
            var supported = videoCapabilities.isSizeSupported(mWidth, mHeight)
            Log.i(
                TAG,
                "media codec " + ci.name + (if (supported) "support" else "not support") + mWidth + "*" + mHeight
            )
            if (!supported) {
                val b1 = videoCapabilities.supportedWidths.contains(mWidth + 0)
                val b2 = videoCapabilities.supportedHeights.contains(mHeight + 0)
                supported = supported or (b1 && b2)
                if (supported) {
                    Log.w(
                        TAG,
                        "......................................................................."
                    )
                } else {
                    throw IllegalStateException("media codec " + ci.name + (if (supported) "support" else "not support") + mWidth + "*" + mHeight)
                }
            }
        }
        Log.i(TAG, String.format("config codec:%s", format))
        val codec = MediaCodec.createByCodecName(ci.name)
        codec.configure(format, null, null, 0)
        //        codec.setVideoScalingMode(MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT);
        mDecoder = codec

//        mMediaFormat = mDecoder.getOutputFormat();
    }

    /**
     * 开始录制
     *
     * @throws InterruptedException
     */
    @Throws(InterruptedException::class)
    fun start() {
        if (mDecoderThread != null && mDecoderThread!!.isAlive) {
            mDecoderStartFlag = false
            mDecoderThread!!.join()
        }
        mDecoder!!.start()
        mDecoderStartFlag = true
        mDecoderThread = DecoderThread("VideoDecoderThread")
        mDecoderThread?.start()
    }

    /**
     * 停止解码
     */
    fun stop() {
        stopDecoder()
        closePreview()
    }

    /**
     * 停止解码线程及释放Decoder资源
     */
    private fun stopDecoder() {
        try {
            mDecoderStartFlag = false
            if (mDecoderThread != null) {
                mDecoderThread!!.join()
                mDecoderThread = null
            }
            d(TAG, "releasing encoder objects")
            if (mDecoder != null) {
                mDecoder!!.stop()
                mDecoder!!.release()
                mDecoder = null
            }
        } catch (e: Exception) {
            w(TAG, e.message)
        }
    }

    /**
     * 停止视频预览
     */
    fun closePreview() {
        if (mPreviewThread != null) {
            mPreviewListener = null
            mPreviewStartFlag = false
            try {
                mPreviewThread!!.join()
                mPreviewThread = null
            } catch (e: InterruptedException) {
                w(TAG, e.message)
            }
        }
    }

    /**
     * 由外部喂入一帧数据
     *
     * @param data       RGBA(NV21)数据
     * @param timeSptamp 时间戳 us
     */
    fun feedData(data: ByteArray?, timeSptamp: Long) {
//        LogUtils.d(TAG, "new frame data coming " + timeSptamp);
        mFrameDataQueue.add(FrameData(data!!, timestamp = timeSptamp))
    }

    /**
     * 向解码器InputBuffer中填入数据
     *
     * @param data       H264/H265数据
     * @param timeSptamp 时间戳 us
     */
    private fun putDataToInputBuffer(data: ByteArray, timeSptamp: Long) {
        val index = mDecoder!!.dequeueInputBuffer(-1)
        if (index >= 0) {
            val buffer = mDecoder!!.getInputBuffer(index)
            if (buffer == null) {
                d(TAG, "InputBuffer is null point")
                return
            }
            if (data.size > buffer.capacity()) {
                e(TAG, "data length:" + data.size + " > buffer capacity " + buffer.capacity())
            }
            buffer.clear()
            buffer.put(data)

//            LogUtils.d(TAG, "queueInputBuffer data length: " + data.length + "  timeSptamp: " + timeSptamp);
            mDecoder!!.queueInputBuffer(index, 0, data.size, timeSptamp, 0)
        }
        drainDecoder(false)
    }

    /**
     * 读取解码后的H264/H265数据
     *
     * @param endOfStream 标识是否结束
     */
    private fun drainDecoder(endOfStream: Boolean) {
        val TIMEOUT_USEC = 10000
        if (endOfStream) {
            d(TAG, "sending EOS to encoder")
            return
        }
        while (true) {
            val decoderStatus = mDecoder!!.dequeueOutputBuffer(mBufferInfo, 0)
            if (decoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // no output available yet  输出为空
                break // out of while
                //                if (!endOfStream) {
//                    break;      // out of while
//                } else {
//                    LogUtils.d(TAG, "no output available, spinning to await EOS");
//                }
            } else if (decoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // should happen before receiving buffers, and should only happen once
//                mMediaFormat = mDecoder.getOutputFormat();
//                LogUtils.d(TAG, "encoder output format changed: " + mMediaFormat);
            } else if (decoderStatus < 0) {
                d(
                    TAG, "unexpected result from encoder.dequeueOutputBuffer: " +
                            decoderStatus
                )
                // let's ignore it
            } else {
                val decodedOutputBuffer = mDecoder!!.getOutputBuffer(decoderStatus)
                if (decodedOutputBuffer == null) {
                    w(
                        TAG, "decoderOutputBuffer " + decoderStatus +
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
                if (mBufferInfo.size != 0 && isSavaBitmap) {
                    isSavaBitmap = false
                    // adjust the ByteBuffer values to match BufferInfo (not needed?)
                    decodedOutputBuffer.position(mBufferInfo.offset)
                    decodedOutputBuffer.limit(mBufferInfo.offset + mBufferInfo.size)

                    // 取出编码好的H264/H265数据
                    val data = ByteArray(mBufferInfo.size)
                    decodedOutputBuffer[data]
                    val fileName =
                        saveNV12ToJpg2(data, mWidth, mHeight, mBufferInfo.presentationTimeUs, true)
                    if (mCaptureBitmapListener != null) {
                        mCaptureBitmapListener!!.onCapture(fileName)
                    }
                    //                    writeToFile("/sdcard/ipc.yuv", data);
//                    LogUtils.d(TAG, "sent " + mBufferInfo.size + " bytes to muxer, ts=" +
//                            mBufferInfo.presentationTimeUs);
                }
                if (mBufferInfo.size != 0 && mPreviewListener != null) {
//                    LogUtils.d(TAG, "decoder status " + decoderStatus + " real timeSptamp " + mBufferInfo.presentationTimeUs);
                    decodedOutputBuffer.position(mBufferInfo.offset)
                    decodedOutputBuffer.limit(mBufferInfo.offset + mBufferInfo.size)

                    // 取出编码好的H264/H265数据
                    val decodedData = ByteArray(mBufferInfo.size)
                    decodedOutputBuffer[decodedData]
                    mDecodedDataQueue.clear()
                    mDecodedDataQueue.add(FrameData(decodedData, timestamp = mBufferInfo.presentationTimeUs))
                }
                mDecoder!!.releaseOutputBuffer(decoderStatus, false)
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

    private inner class DecoderThread internal constructor(name: String?) : Thread(name) {
        override fun run() {
            while (mDecoderStartFlag) {
//            LogUtils.d(TAG, "Encodec start");
                if (!mFrameDataQueue.isEmpty()) {
                    val data = mFrameDataQueue.poll()
                    putDataToInputBuffer(data!!.data, timeSptamp = data.timestamp)
                }
                try {
                    sleep(1)
                } catch (e: InterruptedException) {
                    w(TAG, e.message)
                }
            }
        }
    }

    private fun writeToFile(path: String, data: ByteArray) {
        try {
            val fos = FileOutputStream(path, true)
            fos.write(data)
            fos.close()
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    fun captureBitmap(listener: CaptureBitmapListener?) {
        isSavaBitmap = true
        mCaptureBitmapListener = listener
    }

    interface CaptureBitmapListener {
        fun onCapture(fileName: String?)
    }

    /**
     * 视频预览处理线程
     */
    private inner class PreviewThread(name: String?) : Thread(name) {
        override fun run() {
            while (mPreviewStartFlag) {
                var decodedData: FrameData? = null
                while (!mDecodedDataQueue.isEmpty()) {
                    decodedData = mDecodedDataQueue.poll()
                }
                if (decodedData != null) {
                    // 将NV12格式的数据转为NV21 6ms
                    nv12ConvertNV21(decodedData.data, mWidth, mHeight)
                    if (mPreviewListener != null) {
                        mPreviewListener!!.onPreview(decodedData)
                    }
                }
                try {
                    sleep(1)
                } catch (e: InterruptedException) {
                    w(TAG, e.message)
                }
            }
        }
    }

    fun setPreviewListener(listener: PreviewListener?) {
        if (listener != null) {
            closePreview()
            mPreviewListener = listener
            // 设置了预览监听，启动视频预览处理线程
            mPreviewThread = PreviewThread("PreviewThread")
            mPreviewStartFlag = true
            mPreviewThread?.start()
        }
    }

    interface PreviewListener {
        fun onPreview(frameData: FrameData?)
    }
}