package com.lkl.medialib.core

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import com.lkl.commonlib.util.LogUtils
import com.lkl.medialib.bean.FrameData
import com.lkl.medialib.constant.MediaConst
import com.lkl.medialib.constant.VideoProperty
import java.io.IOException
import java.nio.ByteBuffer

/**
 * 视频编码工作线程
 *
 * @author likunlun
 * @since 2022/01/06
 */
class VideoEncoderThread(
    private val mimeType: String,
    private val mediaFormat: MediaFormat,
    private val callback: CodecCallback,
    threadName: String = TAG
) : BaseMediaThread(threadName) {
    companion object {
        private const val TAG = "VideoEncoderCore"
        private const val TIMEOUT_USEC = 10000L
    }

    private var mEncoder: MediaCodec? = null
    private val mBufferInfo = MediaCodec.BufferInfo()

    override fun prepare() {
        LogUtils.d(TAG, "before mediaFormat: $mediaFormat")
        mediaFormat.setString(MediaFormat.KEY_MIME, mimeType)
        mediaFormat.setInteger(
            MediaFormat.KEY_COLOR_FORMAT,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
        )
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, VideoProperty.BIT_RATE)
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, VideoProperty.FPS)
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, VideoProperty.IFRAME_INTERVAL)
        LogUtils.d(TAG, "after mediaFormat: $mediaFormat")

        // Create a MediaCodec encoder, and configure it with our format.  Get a Surface
        // we can use for input and wrap it with a class that handles the EGL work.
        mEncoder = MediaCodec.createEncoderByType(mimeType)
        mEncoder?.apply {
            configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
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
     * 向编码器InputBuffer中填入数据
     *
     * @param data NV21数据
     * @param timestamp 时间戳 us
     * @throws IOException
     */
    @Throws(IOException::class)
    private fun putDataToInputBuffer(data: ByteArray, timestamp: Long) {
        mEncoder?.apply {
            val index = dequeueInputBuffer(-1)
            if (index >= 0) {
                val buffer = getInputBuffer(index)
                if (buffer == null) {
                    LogUtils.d(TAG, "InputBuffer is null point")
                    return
                }
                buffer.clear()
                buffer.put(data)
                queueInputBuffer(index, 0, data.size, timestamp, 0)
            }
            drainEncoder(this)
        }
    }

    /**
     * 读取编码后的H264数据并存入缓存
     */
    private fun drainEncoder(encoder: MediaCodec) {
        while (true) {
            val encoderStatus = encoder.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC)
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // no output available yet
                if (MediaConst.PRINT_DEBUG_LOG) {
                    LogUtils.d(TAG, "no output available, spinning to await EOS")
                }
                break
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // should happen before receiving buffers, and should only happen once
                val mediaFormat = encoder.outputFormat
                LogUtils.d(TAG, "encoder output format changed: $mediaFormat")
                callback.formatChanged(mediaFormat)
            } else if (encoderStatus < 0) {
                LogUtils.d(
                    TAG, "unexpected result from encoder.dequeueOutputBuffer: " +
                            encoderStatus
                )
            } else {
                val encodedData = encoder.getOutputBuffer(encoderStatus)
                if (encodedData == null) {
                    LogUtils.w(TAG, "encoderOutputBuffer $encoderStatus was null")
                    break
                }
                handleEncodeData(encodedData)
                encoder.releaseOutputBuffer(encoderStatus, false)
                if (mBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    LogUtils.d(TAG, "end of stream reached")
                    break // out of while
                }
            }
        }
    }

    private fun handleEncodeData(encodedData: ByteBuffer) {
        if (mBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
            // The codec config data was pulled out and fed to the muxer when we got
            // the INFO_OUTPUT_FORMAT_CHANGED status.  Ignore it.
            LogUtils.d(TAG, "ignoring BUFFER_FLAG_CODEC_CONFIG")
            mBufferInfo.size = 0
        }
        if (mBufferInfo.size != 0) {
            // adjust the ByteBuffer values to match BufferInfo (not needed?)
            encodedData.position(mBufferInfo.offset)
            encodedData.limit(mBufferInfo.offset + mBufferInfo.size)

            // 取出编码好的H264数据
            val data = ByteArray(mBufferInfo.size)
            encodedData[data]

            val frameData = FrameData(
                data,
                mBufferInfo.size,
                mBufferInfo.presentationTimeUs / 1000,
                mBufferInfo.flags == MediaCodec.BUFFER_FLAG_KEY_FRAME
            )
            callback.putFrameData(frameData)
            if (MediaConst.PRINT_DEBUG_LOG) {
                LogUtils.d(TAG, "encode data: $frameData")
            }
        }
    }

    /**
     * Releases encoder resources.
     */
    override fun release() {
        callback.finished()
        mEncoder?.release()
    }
}