package com.lkl.medialib.util

import android.media.projection.MediaProjection
import com.lkl.medialib.util.ScreenRecordService
import android.media.MediaCodec
import android.media.MediaMuxer
import android.hardware.display.VirtualDisplay
import android.hardware.display.DisplayManager
import android.media.MediaFormat
import kotlin.Throws
import android.media.MediaCodecInfo
import android.util.Log
import android.view.Surface
import com.lkl.framedatacachejni.FrameDataCacheUtils
import java.io.IOException
import java.lang.RuntimeException
import java.util.concurrent.atomic.AtomicBoolean

class ScreenRecordService(
    private val mWidth: Int,
    private val mHeight: Int,
    private val mBitRate: Int,
    private val mDpi: Int,
    private val mMediaProjection: MediaProjection?,
    private val mDstPath: String
) : Thread(TAG) {
    companion object {
        private const val TAG = "ScreenRecordService"

        // parameters for the encoder
        private const val MIME_TYPE = "video/avc" // H.264 Advanced

        // Video Coding
        private const val FRAME_RATE = 20 // 20 fps
        private const val IFRAME_INTERVAL = 2 // 2 seconds between

        // I-frames
        private const val TIMEOUT_US = 10000
    }

    init {
        FrameDataCacheUtils.initCache(FRAME_RATE, mWidth, mHeight, 0)
    }

    private var mEncoder: MediaCodec? = null
    private var mSurface: Surface? = null
    private var mMuxer: MediaMuxer? = null
    private var mMuxerStarted = false
    private var mVideoTrackIndex = -1
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
            mMuxer = try {
                prepareEncoder()
                MediaMuxer(mDstPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            } catch (e: IOException) {
                throw RuntimeException(e)
            }
            mVirtualDisplay = mMediaProjection!!.createVirtualDisplay(
                TAG + "-display", mWidth, mHeight, mDpi,
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
            val index = mEncoder!!.dequeueOutputBuffer(mBufferInfo, TIMEOUT_US.toLong())
            //      Log.i(TAG, "dequeue output buffer index=" + index);
            if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // 后续输出格式变化
                resetOutputFormat()
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
                check(mMuxerStarted) { "MediaMuxer dose not call addTrack(format) " }
                encodeToVideoTrack(index)
                mEncoder!!.releaseOutputBuffer(index, false)
            }
        }
    }

    /**
     * 硬解码获取实时帧数据并写入mp4文件
     *
     * @param index
     */
    private fun encodeToVideoTrack(index: Int) {
        // 获取到的实时帧视频数据
        var encodedData = mEncoder!!.getOutputBuffer(index)
        if (mBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
            // The codec config data was pulled out and fed to the muxer
            // when we got
            // the INFO_OUTPUT_FORMAT_CHANGED status.
            // Ignore it.
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
            mMuxer!!.writeSampleData(mVideoTrackIndex, encodedData, mBufferInfo)
        }
    }

    private fun resetOutputFormat() {
        // should happen before receiving buffers, and should only happen
        // once
        check(!mMuxerStarted) { "output format already changed!" }
        val newFormat = mEncoder!!.outputFormat
        mVideoTrackIndex = mMuxer!!.addTrack(newFormat)
        mMuxer!!.start()
        mMuxerStarted = true
        Log.i(TAG, "started media muxer, videoIndex=$mVideoTrackIndex")
    }

    @Throws(IOException::class)
    private fun prepareEncoder() {
        val format = MediaFormat.createVideoFormat(MIME_TYPE, mWidth, mHeight)
        format.setInteger(
            MediaFormat.KEY_COLOR_FORMAT,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
        )
        format.setInteger(MediaFormat.KEY_BIT_RATE, mBitRate)
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
        mMediaProjection?.stop()
        if (mMuxer != null) {
            mMuxer!!.stop()
            mMuxer!!.release()
            mMuxer = null
        }
    }
}