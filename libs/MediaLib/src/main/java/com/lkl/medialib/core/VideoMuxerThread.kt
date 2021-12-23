package com.lkl.medialib.core

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.text.TextUtils
import com.lkl.commonlib.util.BitmapUtils
import com.lkl.commonlib.util.DateUtils
import com.lkl.commonlib.util.FileUtils
import com.lkl.commonlib.util.LogUtils
import com.lkl.medialib.bean.FrameData
import com.lkl.medialib.constant.ScreenCapture
import java.nio.ByteBuffer
import java.util.*

/**
 * 视频合成
 *
 * @author likunlun
 * @since 2021/12/19
 */
class VideoMuxerThread(
    private val mediaFormat: MediaFormat,
    private val saveFilePath: String? = null,
    private val callback: Callback,
    threadName: String = TAG
) : BaseMediaThread(threadName) {
    companion object {
        private const val TAG = "VideoMuxerCore"
    }

    private var mMuxer: MediaMuxer? = null
    private val mBufferInfo = MediaCodec.BufferInfo()
    private var mMuxerStarted = false

    // 时间戳（ms），通过该时间抽帧
    private var mTimeStamp: Long = -1

    // video视频第一帧时间戳 (ms)，抽帧时作为起始点
    private var mFirstTimeStamp: Long = -1
    private var mOutputFileName = ""
    private var mTrackIndex = -1

    override fun prepare() {
        // Create a MediaMuxer.  We can't add the video track and start() the muxer here,
        // because our MediaFormat doesn't have the Magic Goodies.  These can only be
        // obtained from the encoder after it has started processing data.
        //
        // We're not actually interested in multiplexing audio.  We just want to convert
        // the raw H.264 elementary stream we get from MediaCodec into a .mp4 file.
        mOutputFileName = if (TextUtils.isEmpty(saveFilePath)) {
            FileUtils.videoDir + DateUtils.nowTime.replace(" ", "_") + BitmapUtils.VIDEO_FILE_EXT
        } else {
            saveFilePath!!
        }
        mMuxer = MediaMuxer(mOutputFileName, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        // now that we have the Magic Goodies, start the muxer
        LogUtils.d(TAG, "Muxer init mediaFormat -> $mediaFormat")
        mMuxer?.apply {
            mTrackIndex = addTrack(mediaFormat)
            start()
            mMuxerStarted = true

            firstFrameHandler()
        }
    }

    private fun firstFrameHandler() {
        val frameData = callback.getFirstIFrameData()
        if (frameData == null) {
            LogUtils.e(TAG, "get first IFrame data failed.")
            quit()
            return
        }
        writeSampleData(frameData)
    }

    override fun drain() {
        val frameData = callback.getNextFrameData()
        if (frameData == null) {
            waitTime(10)
        } else {
            writeSampleData(frameData)
        }
    }

    private fun writeSampleData(frameData: FrameData) {
        mMuxer?.apply {
            val sampleData = ByteBuffer.wrap(frameData.data, 0, frameData.length)
            setBufferInfo(
                if (frameData.isKeyFrame) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0,
                frameData.timestamp,
                frameData.length
            )
            writeSampleData(mTrackIndex, sampleData, mBufferInfo)
            if (ScreenCapture.PRINT_DEBUG_LOG) {
                LogUtils.d(
                    TAG, "get frame data: size -> ${frameData.length}  timestamp -> " +
                            DateUtils.convertDateToString(
                                DateUtils.DATE_TIME,
                                Date(frameData.timestamp)
                            ) + " isKeyFrame -> ${frameData.isKeyFrame}"
                )
            }
        }
    }

    private fun setBufferInfo(flags: Int, presentationTimeMs: Long, size: Int) {
        mBufferInfo.flags = flags
        mBufferInfo.offset = 0
        mBufferInfo.presentationTimeUs = presentationTimeMs * 1000
        mBufferInfo.size = size
    }

    override fun release() {
        LogUtils.d(TAG, "release")
        callback.finished(mOutputFileName)
        mMuxer?.release()
    }

    interface Callback {

        fun getFirstIFrameData(): FrameData?

        fun getNextFrameData(): FrameData?

        fun finished(fileName: String)
    }
}