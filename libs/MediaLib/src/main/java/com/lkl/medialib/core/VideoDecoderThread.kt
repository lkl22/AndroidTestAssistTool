package com.lkl.medialib.core

import android.media.MediaCodec
import android.media.MediaCodecInfo.CodecCapabilities
import android.media.MediaFormat
import com.lkl.commonlib.util.FileUtils
import com.lkl.commonlib.util.LogUtils
import com.lkl.medialib.bean.FrameData
import com.lkl.medialib.constant.MediaConst
import com.lkl.medialib.constant.VideoProperty
import java.io.FileOutputStream
import java.nio.ByteBuffer

/**
 * 视频解码工作线程
 *
 * @author likunlun
 * @since 2022/01/06
 */
class VideoDecoderThread(
    private val mimeType: String,
    private val mediaFormat: MediaFormat,
    private val callback: CodecCallback,
    threadName: String = TAG
) : BaseMediaThread(threadName) {
    companion object {
        private const val TAG = "VideoDecoderThread"
    }

    private var mDecoder: MediaCodec? = null
    private val mBufferInfo = MediaCodec.BufferInfo()

    /**
     * 保存一张yuv图片，方便分析解码数据
     */
    private var hasSavaBitmap = false

    override fun prepare() {
        LogUtils.i(TAG, "before mediaFormat: $mediaFormat")
        mediaFormat.setInteger(
            MediaFormat.KEY_COLOR_FORMAT,
            CodecCapabilities.COLOR_FormatYUV420Flexible
        )
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, VideoProperty.BIT_RATE)
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, VideoProperty.FPS)
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, VideoProperty.IFRAME_INTERVAL)
        LogUtils.i(TAG, "after mediaFormat: $mediaFormat")

        mDecoder = MediaCodec.createDecoderByType(mimeType)
        mDecoder?.apply {
            configure(mediaFormat, null, null, 0)
            start()
        }
    }

    override fun drain() {
        val frameData = callback.getFrameData()
        frameData?.apply {
            putDataToInputBuffer(data, timestamp * 1000)
        }
        waitTime(10)
    }

    /**
     * 向解码器InputBuffer中填入数据
     *
     * @param data H264/H265数据
     * @param timestamp 时间戳 us
     */
    private fun putDataToInputBuffer(data: ByteArray, timestamp: Long) {
        mDecoder?.apply {
            val index = dequeueInputBuffer(-1)
            if (index >= 0) {
                val buffer = getInputBuffer(index)
                if (buffer == null) {
                    LogUtils.d(TAG, "InputBuffer is null point")
                    return
                }
                if (data.size > buffer.capacity()) {
                    LogUtils.e(
                        TAG,
                        "data length:${data.size} > buffer capacity ${buffer.capacity()}"
                    )
                }
                buffer.clear()
                buffer.put(data)

                LogUtils.d(TAG, "queueInputBuffer length: ${data.size} timestamp: " + timestamp)
                queueInputBuffer(index, 0, data.size, timestamp, 0)
            }
            drainDecoder(this)
        }
    }

    /**
     * 读取解码后的H264/H265数据
     *
     * @param decoder 解码器
     */
    private fun drainDecoder(decoder: MediaCodec) {
        while (true) {
            val decoderStatus = decoder.dequeueOutputBuffer(mBufferInfo, 0)
            if (decoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // no output available yet  输出为空
                if (MediaConst.PRINT_DEBUG_LOG) {
                    LogUtils.e(TAG, "no output available yet")
                }
                break
            } else if (decoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // should happen before receiving buffers, and should only happen once
                val mediaFormat = decoder.outputFormat
                LogUtils.d(TAG, "decoder output format changed: $mediaFormat")
                callback.formatChanged(mediaFormat)
            } else if (decoderStatus > 0) {
                val decodedOutputBuffer = decoder.getOutputBuffer(decoderStatus)
                if (decodedOutputBuffer == null) {
                    LogUtils.e(TAG, "decoderOutputBuffer $decoderStatus was null")
                    break
                }

                handleDecodeData(decodedOutputBuffer)
                decoder.releaseOutputBuffer(decoderStatus, false)
                if (mBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    LogUtils.e(TAG, "end of stream reached")
                    break // out of while
                }
            }
        }
    }

    private fun handleDecodeData(decodedOutputBuffer: ByteBuffer) {
        if (mBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
            // The codec config data was pulled out and fed to the muxer when we got
            // the INFO_OUTPUT_FORMAT_CHANGED status.  Ignore it.
            LogUtils.d(TAG, "ignoring BUFFER_FLAG_CODEC_CONFIG")
            mBufferInfo.size = 0
        }
        if (mBufferInfo.size != 0) {
            // adjust the ByteBuffer values to match BufferInfo (not needed?)
            decodedOutputBuffer.position(mBufferInfo.offset)
            decodedOutputBuffer.limit(mBufferInfo.offset + mBufferInfo.size)
            // 取出编码好的H264/H265数据
            val data = ByteArray(mBufferInfo.size)
            decodedOutputBuffer[data]
            if (!hasSavaBitmap) {
                hasSavaBitmap = true
                writeToFile(FileUtils.videoDir + "ipc.yuv", data)
            }

            val frameData = FrameData(
                data,
                mBufferInfo.size,
                mBufferInfo.presentationTimeUs / 1000,
                mBufferInfo.flags == MediaCodec.BUFFER_FLAG_KEY_FRAME
            )
            callback.putFrameData(frameData)
            if (MediaConst.PRINT_DEBUG_LOG) {
                LogUtils.d(TAG, "decode offset ${mBufferInfo.offset} data: $frameData")
            }
        }
    }

    private fun writeToFile(path: String, data: ByteArray) {
        try {
            val fos = FileOutputStream(path, false)
            fos.write(data)
            fos.close()
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    override fun release() {
        callback.finished()
        mDecoder?.release()
    }
}