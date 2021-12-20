package com.lkl.medialib.util

import android.media.projection.MediaProjection
import android.media.MediaCodec
import android.media.MediaMuxer
import android.hardware.display.VirtualDisplay
import android.hardware.display.DisplayManager
import android.media.MediaFormat
import kotlin.Throws
import android.media.MediaCodecInfo
import android.util.Log
import android.view.Surface
import com.lkl.commonlib.util.LogUtils
import com.lkl.framedatacachejni.FrameDataCacheUtils
import com.lkl.medialib.constant.VideoConfig
import java.io.IOException
import java.lang.RuntimeException
import java.util.concurrent.atomic.AtomicBoolean

class ScreenRecordService(
    private val width: Int,
    private val height: Int,
    private val dpi: Int,
    private val bitRate: Int = VideoConfig.BIT_RATE,
    private val mediaProjection: MediaProjection?
) : Thread(TAG) {
    companion object {
        private const val TAG = "ScreenRecordService"

        // parameters for the encoder
        private const val MIME_TYPE = "video/avc" // H.264 Advanced

        // Video Coding
        private val FRAME_RATE = VideoConfig.FPS // 20 fps
        private const val IFRAME_INTERVAL = VideoConfig.FRAME_INTERVAL // 1 seconds between

        // I-frames
        private const val TIMEOUT_US = 10000L

        var sMediaFormat: MediaFormat? = null
    }

    init {
        FrameDataCacheUtils.initCache(FRAME_RATE, width, height, 0)
    }

    private var mEncoder: MediaCodec? = null
    private var mSurface: Surface? = null
    private val mQuit = AtomicBoolean(false)
    private val mBufferInfo = MediaCodec.BufferInfo()
    private var mVirtualDisplay: VirtualDisplay? = null

    /**
     * stop task
     */
    fun quit() {
        mQuit.set(true)
    }

    override fun run() {
        try {
            prepareEncoder()
            mVirtualDisplay = mediaProjection!!.createVirtualDisplay(
                "$TAG-display", width, height, dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC, mSurface, null, null
            )
            Log.d(TAG, "created virtual display: $mVirtualDisplay")
            recordVirtualDisplay()
        } finally {
            release()
        }
    }

    private fun recordVirtualDisplay() {
        while (!mQuit.get()) {
            val index = mEncoder!!.dequeueOutputBuffer(mBufferInfo, TIMEOUT_US)
            //      Log.i(TAG, "dequeue output buffer index=" + index);
            if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // 后续输出格式变化
                sMediaFormat = mEncoder?.outputFormat
            } else if (index == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // 请求超时
//          Log.d(TAG, "retrieving buffers time out!");
                try {
                    // wait 10ms
                    sleep(10)
                } catch (e: InterruptedException) {
                }
            } else if (index >= 0) {
                // 有效输出
                encodeToVideoTrack(index)
                mEncoder?.releaseOutputBuffer(index, false)
            }
        }
    }

    private fun encodeToVideoTrack(index: Int) {
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
        } else {
//      Log.d(TAG, "got buffer, info: size=" + mBufferInfo.size + ", presentationTimeUs="
//          + mBufferInfo.presentationTimeUs + ", offset=" + mBufferInfo.offset);
        }
        if (encodedData != null) {
            // adjust the ByteBuffer values to match BufferInfo (not needed?)
            encodedData.position(mBufferInfo.offset)
            encodedData.limit(mBufferInfo.offset + mBufferInfo.size)

            // 取出编码好的H264数据
            val data = ByteArray(mBufferInfo.size)
            encodedData[data]

            // 将编码好的H264数据存储到缓冲中
            FrameDataCacheUtils.addFrameData(
                System.currentTimeMillis(),
                mBufferInfo.flags == MediaCodec.BUFFER_FLAG_KEY_FRAME,
                data, mBufferInfo.size
            )

            LogUtils.d(
                TAG, "sent " + mBufferInfo.size + " bytes to muxer, ts=" +
                        mBufferInfo.presentationTimeUs
            )
        }
    }

    @Throws(IOException::class)
    private fun prepareEncoder() {
        val format = MediaFormat.createVideoFormat(MIME_TYPE, width, height)
        format.setInteger(
            MediaFormat.KEY_COLOR_FORMAT,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
        )
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL)
        Log.d(TAG, "created video format: $format")
        mEncoder = MediaCodec.createEncoderByType(MIME_TYPE)
        mEncoder!!.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        mSurface = mEncoder!!.createInputSurface()
        Log.d(TAG, "created input surface: $mSurface")
        mEncoder!!.start()
    }

    private fun release() {
        if (mEncoder != null) {
            mEncoder!!.stop()
            mEncoder!!.release()
            mEncoder = null
        }
        if (mVirtualDisplay != null) {
            mVirtualDisplay!!.release()
        }
        mediaProjection?.stop()
    }
}