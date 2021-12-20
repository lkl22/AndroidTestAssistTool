package com.lkl.medialib.util

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.text.TextUtils
import com.lkl.commonlib.util.BitmapUtils
import com.lkl.commonlib.util.DateUtils
import com.lkl.commonlib.util.FileUtils
import com.lkl.commonlib.util.LogUtils
import com.lkl.framedatacachejni.FrameDataCacheUtils
import java.nio.ByteBuffer
import java.util.*

/**
 * 视频合成
 *
 * @author likunlun
 * @since 2021/12/19
 */
class VideoMuxerCore(
    timeStamp: Long,
    width: Int,
    height: Int,
    fps: Int,
    mediaFormat: MediaFormat?,
    saveFilePath: String? = null
) : Runnable {
    companion object {
        private const val TAG = "VideoMuxerCore"

        // 录制的MP4视频文件的时间总长度 单位 s
        private const val MP4_TOTAL_TIME = 10
        private var mTrackIndex = -1

        // 帧率
        private var mFrameRate = 20
    }

    /**
     * 每帧数据时间间隔
     */
    private val mFramePeriod: Int
    private val mMuxer: MediaMuxer
    private val mBufferInfo = MediaCodec.BufferInfo()
    private var mMuxerStarted = false

    // 时间戳（ms），通过该时间抽帧
    private var mTimeStamp: Long = -1

    // video视频第一帧时间戳 (ms)，抽帧时作为起始点
    private var mFirstTimeStamp: Long = -1
    private var mFrameBuffer: ByteArray = ByteArray(0)
    private val mNextTimeStamp = LongArray(1)
    private val mSize = IntArray(1)
    private var mOutputFileName = ""

    init {
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
        mFrameRate = fps
        mFramePeriod = 1000 / fps
        mTimeStamp = timeStamp
        mFirstTimeStamp = timeStamp - MP4_TOTAL_TIME / 2 * 1000
        // now that we have the Magic Goodies, start the muxer
        if (mediaFormat != null) {
            LogUtils.d(TAG, "Muxer init mediaFormat -> $mediaFormat")
            try {
                mTrackIndex = mMuxer.addTrack(mediaFormat)
                mMuxer.start()
                mMuxerStarted = true
                mFrameBuffer = ByteArray(2 * 1024 * 1024)
            } catch (e: Exception) {
                LogUtils.w(TAG, e.message)
            }
        }
    }

    override fun run() {
        LogUtils.d(TAG, "Muxer thread start")
        if (mMuxerStarted) {
            LogUtils.d(
                TAG,
                "Muxer start " + DateUtils.convertDateToString(
                    DateUtils.DATE_TIME,
                    Date(mFirstTimeStamp)
                )
            )
            var res = FrameDataCacheUtils.getFirstFrameData(
                mFirstTimeStamp,
                mNextTimeStamp,
                mFrameBuffer,
                mSize
            )
            if (res != 0) {
                LogUtils.d(TAG, "get first IFrame data failed.")
                return
            }
            var frameData = ByteBuffer.wrap(mFrameBuffer, 0, mSize[0])
            mFirstTimeStamp = mNextTimeStamp[0]
            setBufferInfo(MediaCodec.BUFFER_FLAG_KEY_FRAME, mFirstTimeStamp, mSize[0])
            LogUtils.d(
                TAG, "get first IFrame data: size -> " + mSize[0] + " timestamp-> " + DateUtils.convertDateToString(
                    DateUtils.DATE_TIME,
                    Date(mNextTimeStamp[0])
                )
            );
            mMuxer.writeSampleData(mTrackIndex, frameData, mBufferInfo)
            var curTime: Long
            for (frameIndex in 1 until 20 * mFrameRate) {
                LogUtils.d(TAG, "$frameIndex  frame start")
                curTime = mNextTimeStamp[0]
                do {
                    res = FrameDataCacheUtils.getNextFrameData(
                        curTime,
                        mNextTimeStamp,
                        mFrameBuffer,
                        mSize
                    )
                    if (res == 0) {
                        // 返回0正常拿到数据
                        break
                    }
                    try {
                        // 2没有新数据，需循环等待
                        Thread.sleep(10)
                    } catch (e: InterruptedException) {
                        LogUtils.w(TAG, e.message)
                    }
                } while (res == 2)
                if (res == 1) {
                    LogUtils.d(TAG, "get cache data error.")
                    return
                }

//                ByteBuffer tempBuf = ByteBuffer.allocateDirect(mSize[0]);
//                tempBuf.put(mFrameBuffer, 0, mSize[0]);
                frameData = ByteBuffer.wrap(mFrameBuffer, 0, mSize[0])
                if (frameIndex % mFrameRate == 0) {
                    // 关键帧数据
                    setBufferInfo(MediaCodec.BUFFER_FLAG_KEY_FRAME, mNextTimeStamp[0], mSize[0])

                    LogUtils.d(
                        TAG, "get I frame data: size -> " + mSize[0] + "  timestamp -> " +
                                DateUtils.convertDateToString(
                                    DateUtils.DATE_TIME,
                                    Date(mNextTimeStamp[0])
                                )
                    )
                } else {
                    setBufferInfo(0, mNextTimeStamp[0], mSize[0])

                    LogUtils.d(
                        TAG, "get P frame data: size -> " + mSize[0] + "  timestamp -> " +
                                DateUtils.convertDateToString(
                                    DateUtils.DATE_TIME,
                                    Date(mNextTimeStamp[0])
                                )
                    )
                }
                mMuxer.writeSampleData(mTrackIndex, frameData, mBufferInfo)
            }
            mMuxer.stop()
            mMuxer.release()
            LogUtils.e(TAG, "Muxer stop")
        }
    }

    private fun setBufferInfo(flags: Int, presentationTimeMs: Long, size: Int) {
        mBufferInfo.flags = flags
        mBufferInfo.offset = 0
        mBufferInfo.presentationTimeUs = presentationTimeMs * 1000
        mBufferInfo.size = size
    }
}