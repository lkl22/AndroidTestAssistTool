package com.lkl.medialib.core

import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.util.Log
import android.view.Surface
import com.lkl.commonlib.util.LogUtils
import com.lkl.medialib.bean.FrameData
import com.lkl.medialib.bean.MediaFormatParams
import com.lkl.medialib.constant.MediaConst
import com.lkl.medialib.constant.ScreenCapture
import com.lkl.medialib.util.MediaUtils
import java.io.IOException

/**
 * 获取屏幕录屏的线程处理类
 *
 * @author likunlun
 * @since 2021/12/23
 */
class ScreenCaptureThread(
    private val mediaFormatParams: MediaFormatParams,
    private val dpi: Int,
    private val mediaProjection: MediaProjection,
    private val callback: Callback,
    threadName: String = TAG
) : BaseMediaThread(threadName) {
    companion object {
        private const val TAG = "ScreenRecordService"

        private const val TIMEOUT_US = 10000L
    }

    private var mEncoder: MediaCodec? = null
    private var mSurface: Surface? = null
    private val mBufferInfo = MediaCodec.BufferInfo()
    private var mVirtualDisplay: VirtualDisplay? = null

    private var mMediaFormat: MediaFormat? = null

    @Throws(IOException::class)
    override fun prepare() {
        callback.prePrepare(mediaFormatParams)

        val format = MediaUtils.createVideoFormat(mediaFormatParams)
        Log.d(TAG, "created video format: $format")
        mEncoder = MediaCodec.createEncoderByType(mediaFormatParams.mimeType)
        mEncoder?.apply {
            configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            mSurface = createInputSurface()
            Log.d(TAG, "created input surface: $mSurface")
            start()

            mVirtualDisplay = mediaProjection.createVirtualDisplay(
                "$TAG-display", mediaFormatParams.width, mediaFormatParams.height, dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC, mSurface, null, null
            )
            Log.d(TAG, "created virtual display: $mVirtualDisplay")
        }

    }

    override fun drain() {
        val index = mEncoder!!.dequeueOutputBuffer(mBufferInfo, TIMEOUT_US)
        when {
            index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                // 后续输出格式变化
                mMediaFormat = mEncoder?.outputFormat
            }
            index == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                // 请求超时
                waitTime(10)
            }
            index >= 0 -> {
                // 有效输出
                encodeDataToCallback(index)
                mEncoder?.releaseOutputBuffer(index, false)
            }
        }
    }

    private fun encodeDataToCallback(index: Int) {
        // 获取到的实时帧视频数据
        var encodedData = mEncoder!!.getOutputBuffer(index)
        if (mBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
            // The codec config data was pulled out and fed to the muxer
            // when we got the INFO_OUTPUT_FORMAT_CHANGED status. Ignore it.
            Log.d(TAG, "ignoring BUFFER_FLAG_CODEC_CONFIG")
            mBufferInfo.size = 0
        }
        if (mBufferInfo.size == 0) {
            Log.d(TAG, "info.size == 0, drop it.")
            encodedData = null
        }

        if (encodedData != null) {
            // adjust the ByteBuffer values to match BufferInfo (not needed?)
            encodedData.position(mBufferInfo.offset)
            encodedData.limit(mBufferInfo.offset + mBufferInfo.size)

            // 取出编码好的H264数据
            val data = ByteArray(mBufferInfo.size)
            encodedData[data]

            callback.putFrameData(
                FrameData(
                    data, mBufferInfo.size, System.currentTimeMillis(),
                    mBufferInfo.flags == MediaCodec.BUFFER_FLAG_KEY_FRAME
                )
            )

            if (MediaConst.PRINT_DEBUG_LOG) {
                LogUtils.d(
                    TAG,
                    "sent ${mBufferInfo.size} bytes callback, ts=${mBufferInfo.presentationTimeUs}"
                )
            }
        }
    }

    fun getMediaFormat(): MediaFormat? {
        return mMediaFormat
    }

    override fun release() {
        LogUtils.d(TAG, "release")
        mEncoder?.apply {
            stop()
            release()
            mEncoder = null
        }
        mVirtualDisplay?.apply {
            release()
            mVirtualDisplay = null
        }
        mediaProjection.stop()
    }

    interface Callback {
        /**
         * prepare方法执行前回调
         *
         * @param mediaFormatParams MediaFormatParams对象
         */
        fun prePrepare(mediaFormatParams: MediaFormatParams)

        /**
         * 传输新的屏幕编码数据
         *
         * @param frameData 视频帧数据
         */
        fun putFrameData(frameData: FrameData)
    }
}