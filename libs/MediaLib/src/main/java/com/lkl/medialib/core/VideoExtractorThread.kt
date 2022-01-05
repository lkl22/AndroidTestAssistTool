package com.lkl.medialib.core

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.lkl.commonlib.util.LogUtils
import com.lkl.medialib.bean.FrameData
import com.lkl.medialib.constant.MediaConst
import java.nio.ByteBuffer

/**
 * video视频提取线程
 *
 * @author likunlun
 * @since 2022/01/05
 */
class VideoExtractorThread(
    private val videoFilePath: String,
    private val callback: Callback,
    threadName: String = TAG
) : BaseMediaThread(threadName) {
    companion object {
        private const val TAG = "VideoExtractorThread"
    }

    private val mExtractor = MediaExtractor()

    private val mByteBuffer = ByteBuffer.allocate(2 * 1024 * 1024)

    override fun prepare() {
        mExtractor.setDataSource(videoFilePath)
        var trackIndex = -1
        for (i in 0 until mExtractor.trackCount) {
            val format: MediaFormat = mExtractor.getTrackFormat(i)
            val mimeType = format.getString(MediaFormat.KEY_MIME)
            if (mimeType != null && mimeType.startsWith(MediaConst.MIMETYPE_VIDEO_PRE)) {
                trackIndex = i
                callback.preExtract(mimeType, format)
                LogUtils.d(TAG, "extract trackIndex $trackIndex, mediaFormat $format")
                break
            }
        }
        mExtractor.selectTrack(trackIndex)
    }

    override fun drain() {
        val size = mExtractor.readSampleData(mByteBuffer, 0)
        if (size < 0) {
            // 没有更多数据了，结束线程
            quit()
            return
        }
        val data = ByteArray(size)
        mByteBuffer[data]
        val frameData = FrameData(
            data,
            size,
            mExtractor.sampleTime,
            mExtractor.sampleFlags == MediaCodec.BUFFER_FLAG_KEY_FRAME
        )
        callback.putExtractData(frameData)
        if (MediaConst.PRINT_DEBUG_LOG) {
            LogUtils.d(TAG, "extract data: $frameData")
        }
        if (!mExtractor.advance()) {
            // 没有更多数据了，结束线程
            quit()
        }
    }

    override fun release() {
        mExtractor.release()
    }

    interface Callback {
        /**
         * 准备提取视频
         *
         * @param mimeType mime类型
         * @param mediaFormat media格式
         */
        fun preExtract(mimeType: String, mediaFormat: MediaFormat)

        /**
         * 将提取出来的数据回调出去
         *
         * @param frameData 视频帧数据
         */
        fun putExtractData(frameData: FrameData)
    }
}