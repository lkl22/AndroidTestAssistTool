package com.lkl.medialib.manager

import android.media.MediaFormat
import com.lkl.commonlib.util.LogUtils
import com.lkl.medialib.bean.FrameData
import com.lkl.medialib.core.VideoDecoderThread
import com.lkl.medialib.core.VideoEncoderThread
import com.lkl.medialib.core.VideoExtractorThread
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
    }

    private val mExtractDataQueue: ConcurrentLinkedQueue<FrameData> =
        ConcurrentLinkedQueue<FrameData>()
    /**
     * 从视频文件中提取视频是否已经结束，true已经结束
     */
    private val isExtractFinished = AtomicBoolean(false)


    private var mVideoDecoderThread: VideoDecoderThread? = null
    private val mDecodedDataQueue: ConcurrentLinkedQueue<FrameData> =
        ConcurrentLinkedQueue<FrameData>()
    /**
     * 视频流是否已经解码结束，true已经结束
     */
    private val isDecodedFinished = AtomicBoolean(false)


    private var mVideoEncoderThread: VideoEncoderThread? = null
    private val mEncodedDataQueue: ConcurrentLinkedQueue<FrameData> =
        ConcurrentLinkedQueue<FrameData>()
    /**
     * 视频流是否已经编码结束，true已经结束
     */
    private val isEncodedFinished = AtomicBoolean(false)

    fun startTransform(videoFilePath: String) {
        startExtractVideo(videoFilePath)
    }

    private fun startExtractVideo(videoFilePath: String) {
        VideoExtractorThread(videoFilePath, object : VideoExtractorThread.Callback {
            override fun preExtract(mimeType: String, mediaFormat: MediaFormat) {
                startDecodeVideo(mimeType, mediaFormat)
            }

            override fun putExtractData(frameData: FrameData) {
                mExtractDataQueue.add(frameData)
            }

            override fun finished() {
                isExtractFinished.set(true)
            }
        }).start()
    }

    private fun startDecodeVideo(mimeType: String, mediaFormat: MediaFormat) {
        mVideoDecoderThread =
            VideoDecoderThread(mimeType, mediaFormat, object : VideoDecoderThread.Callback {
                override fun getEncodeData(): FrameData? {
                    val frameData = mExtractDataQueue.poll()
                    if (frameData == null && isExtractFinished.get()) {
                        LogUtils.e(TAG, "startDecodeVideo decode finished.")
                        mVideoDecoderThread?.quit()
                    }
                    return frameData
                }

                override fun putDecodeData(frameData: FrameData) {
                    mDecodedDataQueue.add(frameData)
                }

                override fun finished() {
                    isDecodedFinished.set(true)
                }
            })
        mVideoDecoderThread?.start()
    }

    private fun startEncodeVideo(mimeType: String, mediaFormat: MediaFormat) {
        mVideoEncoderThread = VideoEncoderThread(mimeType, mediaFormat, object :VideoEncoderThread.Callback{
            override fun getDecodeData(): FrameData? {
                val frameData = mDecodedDataQueue.poll()
                if (frameData == null && isDecodedFinished.get()) {
                    LogUtils.e(TAG, "startEncodeVideo encode finished.")
                    mVideoEncoderThread?.quit()
                }
                return frameData
            }

            override fun putEncodeData(frameData: FrameData) {
                mEncodedDataQueue.add(frameData)
            }

            override fun finished() {
                isEncodedFinished.set(true)
            }
        })
        mVideoEncoderThread?.start()
    }
}