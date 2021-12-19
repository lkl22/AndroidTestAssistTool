package com.lkl.medialib.util

import com.lkl.commonlib.util.FileUtils.videoDir
import com.lkl.commonlib.util.DateUtils.convertDateToString
import com.lkl.commonlib.util.LogUtils.d
import com.lkl.commonlib.util.LogUtils.w
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaCodec
import com.lkl.commonlib.util.LogUtils
import com.lkl.medialib.util.VideoMuxerCore
import com.lkl.framedatacachejni.FrameDataCacheUtils
import com.lkl.commonlib.util.BitmapUtils
import com.lkl.commonlib.util.DateUtils
import java.lang.Exception
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
    mediaFormat: MediaFormat?
) : Runnable {
    companion object {
        private const val TAG = "VideoMuxerCore"

        // 录制的MP4视频文件的时间总长度 单位 s
        private const val MP4_TOTAL_TIME = 20
        private var mTrackIndex = -1

        // 帧率
        private var mFrameRate = 25
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
        mOutputFileName = videoDir + convertDateToString(
            DateUtils.DATE_TIME,
            Date(timeStamp)
        ).replace(" ", "_") + BitmapUtils.VIDEO_FILE_EXT
        mMuxer = MediaMuxer(mOutputFileName, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        mFrameRate = fps
        mFramePeriod = 1000 / fps
        mTimeStamp = timeStamp
        mFirstTimeStamp = timeStamp - MP4_TOTAL_TIME / 2 * 1000
        // now that we have the Magic Goodies, start the muxer
        if (mediaFormat != null) {
            d(TAG, "Muxer init mediaFormat -> $mediaFormat")
            try {
                mTrackIndex = mMuxer.addTrack(mediaFormat)
                mMuxer.start()
                mMuxerStarted = true
                mFrameBuffer = ByteArray(2 * 1024 * 1024)
            } catch (e: Exception) {
                w(TAG, e.message)
            }
        }
    }

    override fun run() {
        d(TAG, "Muxer thread start")
        if (mMuxerStarted) {
//            LogUtils.d(TAG, "Muxer start " + DateUtils.convertDateToString(DateUtils.DATE_TIME, new Date(mFirstTimeSptamp)));
            var res = FrameDataCacheUtils.getFirstFrameData(
                mFirstTimeStamp,
                mNextTimeStamp,
                mFrameBuffer,
                mSize
            )
            if (res != 0) {
                d(TAG, "获取第一帧数据失败")
                return
            }
            var frameData = ByteBuffer.wrap(mFrameBuffer, 0, mSize[0])
            mFirstTimeStamp = mNextTimeStamp[0]
            setBufferInfo(MediaCodec.BUFFER_FLAG_KEY_FRAME, mFirstTimeStamp, mSize[0])
            //            LogUtils.d(TAG, "获取第一帧数据: size -> " + mSize[0] + "  数据时间戳 -> " + DateUtils.convertDateToString(DateUtils.DATE_TIME, new Date(mNextTimeSptamp[0])));
            mMuxer.writeSampleData(mTrackIndex, frameData, mBufferInfo)
            var curTime: Long
            for (frameIndex in 1 until 20 * mFrameRate) {
                d(TAG, "$frameIndex  frame start")
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
                        w(TAG, e.message)
                    }
                } while (res == 2)
                if (res == 1) {
                    d(TAG, "获取缓存数据出现问题")
                    return
                }

//                ByteBuffer tempBuf = ByteBuffer.allocateDirect(mSize[0]);
//                tempBuf.put(mFrameBuffer, 0, mSize[0]);
                frameData = ByteBuffer.wrap(mFrameBuffer, 0, mSize[0])
                if (frameIndex % mFrameRate == 0) {
                    // 关键帧数据
                    setBufferInfo(MediaCodec.BUFFER_FLAG_KEY_FRAME, mNextTimeStamp[0], mSize[0])

//                    LogUtils.d(TAG, "获取I帧数据: size -> " + mSize[0] + "  数据时间戳 -> " +
//                            DateUtils.convertDateToString(DateUtils.DATE_TIME, new Date(mNextTimeSptamp[0])));
                } else {
                    setBufferInfo(0, mNextTimeStamp[0], mSize[0])

//                    LogUtils.d(TAG, "获取P帧数据: size -> " + mSize[0] + "  数据时间戳 -> " +
//                            DateUtils.convertDateToString(DateUtils.DATE_TIME, new Date(mNextTimeSptamp[0])));
                }
                mMuxer.writeSampleData(mTrackIndex, frameData, mBufferInfo)
            }
            mMuxer.stop()
            mMuxer.release()
            d(TAG, "Muxer stop")
        }
    }

    private fun setBufferInfo(flags: Int, presentationTimeMs: Long, size: Int) {
        mBufferInfo.flags = flags
        mBufferInfo.offset = 0
        mBufferInfo.presentationTimeUs = presentationTimeMs * 1000
        mBufferInfo.size = size
    }
}