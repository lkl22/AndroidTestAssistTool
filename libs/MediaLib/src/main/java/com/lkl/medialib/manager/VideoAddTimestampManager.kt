package com.lkl.medialib.manager

import android.media.MediaFormat
import android.util.Size
import com.lkl.commonlib.util.DisplayUtils
import com.lkl.commonlib.util.LogUtils
import com.lkl.medialib.bean.FrameData
import com.lkl.medialib.bean.Position
import com.lkl.medialib.constant.VideoProperty
import com.lkl.medialib.core.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * video视频转换添加时间戳管理类
 *
 * @author likunlun
 * @since 2022/01/05
 */
class VideoAddTimestampManager {
    companion object {
        private const val TAG = "VideoAddTimestampManager"

        private const val QUEUE_MAX_CACHE_SIZE = 8
    }

    private var mMineType: String = MediaFormat.MIMETYPE_VIDEO_AVC
    private var mWidth = VideoProperty.WIDTH
    private var mHeight = VideoProperty.HEIGHT

    private val mExtractDataQueue: ConcurrentLinkedQueue<FrameData> =
        ConcurrentLinkedQueue<FrameData>()
    private val isExtractFinished = AtomicBoolean(false)

    private var mVideoDecoderThread: VideoDecoderThread? = null
    private val mDecodedDataQueue: ConcurrentLinkedQueue<FrameData> =
        ConcurrentLinkedQueue<FrameData>()
    private val isDecodedFinished = AtomicBoolean(false)

    private var mTimeWatermarkThread: TimeWatermarkThread? = null
    private val mWatermarkDataQueue: ConcurrentLinkedQueue<FrameData> =
        ConcurrentLinkedQueue<FrameData>()
    private val isWatermarkFinished = AtomicBoolean(false)

    private var mVideoEncoderThread: VideoEncoderThread? = null
    private val mEncodedDataQueue: ConcurrentLinkedQueue<FrameData> =
        ConcurrentLinkedQueue<FrameData>()
    private val isEncodedFinished = AtomicBoolean(false)

    private var mVideoMuxerThread: VideoMuxerThread? = null

    fun startTransform(videoFilePath: String) {
        startExtractVideo(videoFilePath)
    }

    private fun startExtractVideo(videoFilePath: String) {
        VideoExtractorThread(videoFilePath, object : VideoExtractorThread.Callback {
            override fun preExtract(mimeType: String, mediaFormat: MediaFormat) {
                mMineType = mimeType
                mWidth = mediaFormat.getInteger(MediaFormat.KEY_WIDTH)
                mHeight = mediaFormat.getInteger(MediaFormat.KEY_HEIGHT)
                LogUtils.d(
                    TAG, "startExtractVideo preExtract mMineType " +
                            "$mMineType mWidth $mWidth mHeight $mHeight"
                )
                startDecodeVideo(mimeType, mediaFormat)
            }

            override fun putExtractData(frameData: FrameData) {
                addCacheFrameData(mExtractDataQueue, frameData)
            }

            override fun finished() {
                isExtractFinished.set(true)
            }
        }).start()
    }

    private fun startDecodeVideo(mimeType: String, mediaFormat: MediaFormat) {
        mVideoDecoderThread =
            VideoDecoderThread(mimeType, mediaFormat, object : CodecCallback {
                override fun getFrameData(): FrameData? {
                    val frameData = mExtractDataQueue.poll()
                    if (frameData == null && isExtractFinished.get()) {
                        LogUtils.e(TAG, "startDecodeVideo decode finished.")
                        mVideoDecoderThread?.quit()
                    }
                    return frameData
                }

                override fun formatChanged(mediaFormat: MediaFormat) {
                    startAddWatermarkVideo(mediaFormat)
                }

                override fun putFrameData(frameData: FrameData) {
                    addCacheFrameData(mDecodedDataQueue, frameData)
                }

                override fun finished() {
                    isDecodedFinished.set(true)
                }
            })
        mVideoDecoderThread?.start()
    }

    private fun startAddWatermarkVideo(mediaFormat: MediaFormat) {
        val colorFormat = mediaFormat.getInteger(MediaFormat.KEY_COLOR_FORMAT)
        mTimeWatermarkThread =
            TimeWatermarkThread(
                System.currentTimeMillis(),
                Position(DisplayUtils.dip2px(8f), DisplayUtils.dip2px(48f)),
                Size(mWidth, mHeight),
                colorFormat,
                object : CodecCallback {
                    override fun prepare() {
                        startEncodeVideo(mMineType, mediaFormat)
                    }

                    override fun getFrameData(): FrameData? {
                        val frameData = mDecodedDataQueue.poll()
                        if (frameData == null && isDecodedFinished.get()) {
                            LogUtils.e(TAG, "startAddWatermarkVideo add watermark finished.")
                            mTimeWatermarkThread?.quit()
                        }
                        return frameData
                    }

                    override fun putFrameData(frameData: FrameData) {
                        addCacheFrameData(mWatermarkDataQueue, frameData)
                    }

                    override fun finished() {
                        isWatermarkFinished.set(true)
                    }
                })
        mTimeWatermarkThread?.start()
    }

    private fun startEncodeVideo(mimeType: String, mediaFormat: MediaFormat) {
        mVideoEncoderThread =
            VideoEncoderThread(mimeType, mediaFormat, object : CodecCallback {
                override fun getFrameData(): FrameData? {
                    val frameData = mWatermarkDataQueue.poll()
                    if (frameData == null && isWatermarkFinished.get()) {
                        LogUtils.e(TAG, "startEncodeVideo encode finished.")
                        mVideoEncoderThread?.quit()
                    }
                    return frameData
                }

                override fun formatChanged(mediaFormat: MediaFormat) {
                    startMuxerVideo(mediaFormat)
                }

                override fun putFrameData(frameData: FrameData) {
                    addCacheFrameData(mEncodedDataQueue, frameData)
                }

                override fun finished() {
                    isEncodedFinished.set(true)
                }
            })
        mVideoEncoderThread?.start()
    }

    private fun startMuxerVideo(mediaFormat: MediaFormat) {
        mVideoMuxerThread = VideoMuxerThread(mediaFormat, null, object : VideoMuxerThread.Callback {
            override fun getFirstIFrameData(): FrameData? {
                var frameData = mEncodedDataQueue.poll()
                while (frameData == null || !frameData.isKeyFrame) {
                    if (isEncodedFinished.get()) {
                        mVideoMuxerThread?.quit()
                        return null
                    }
                    Thread.sleep(10)
                    frameData = mEncodedDataQueue.poll()
                }
                return frameData
            }

            override fun getNextFrameData(): FrameData? {
                val frameData = mEncodedDataQueue.poll()
                if (frameData == null && isEncodedFinished.get()) {
                    LogUtils.e(TAG, "startMuxerVideo muxer finished.")
                    mVideoMuxerThread?.quit()
                }
                return frameData
            }

            override fun finished(filePath: String) {
                LogUtils.d(TAG, "startMuxerVideo finished fileName $filePath")
            }
        })
        mVideoMuxerThread?.start()
    }

    private fun addCacheFrameData(queue: ConcurrentLinkedQueue<FrameData>, frameData: FrameData) {
        while (queue.size >= QUEUE_MAX_CACHE_SIZE) {
            Thread.sleep(10)
        }
        queue.add(frameData)
    }
}