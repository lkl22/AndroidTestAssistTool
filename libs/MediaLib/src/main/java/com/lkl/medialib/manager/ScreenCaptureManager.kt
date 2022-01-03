package com.lkl.medialib.manager

import android.content.Context
import android.content.Intent
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjectionManager
import com.lkl.commonlib.BaseApplication
import com.lkl.commonlib.util.*
import com.lkl.framedatacachejni.FrameDataCacheUtils
import com.lkl.framedatacachejni.constant.DataCacheCode
import com.lkl.medialib.BuildConfig
import com.lkl.medialib.bean.FrameData
import com.lkl.medialib.bean.MediaFormatParams
import com.lkl.medialib.core.ScreenCaptureThread
import com.lkl.medialib.core.VideoMuxerThread
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

class ScreenCaptureManager {
    companion object {
        private const val TAG = "ScreenCaptureManager"

        val instance: ScreenCaptureManager by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
            ScreenCaptureManager()
        }
    }

    private val mProjectionManager: MediaProjectionManager = BaseApplication.context
        .getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

    private val mDisplayMetrics = DisplayUtils.getDisplayMetrics(BaseApplication.context)

    /**
     * 录屏环境是否已就绪
     */
    private val isEnvReady = AtomicBoolean(false)

    /**
     * 是否正在Muxer视频
     */
    private val isMuxer = AtomicBoolean(false)
    private var finishedMuxerTask = CopyOnWriteArrayList<Long>()

    private var mScreenCaptureThread: ScreenCaptureThread? = null

    private var mVideoMuxerThread: VideoMuxerThread? = null

    private var mFrameBuffer: ByteArray = ByteArray(2 * 1024 * 1024)
    private val mCurTimeStamp = LongArray(1)
    private val mLength = IntArray(1)
    private val mIsKeyFrame = BooleanArray(1)

    fun createScreenCaptureIntent(): Intent {
        return mProjectionManager.createScreenCaptureIntent()
    }

    fun startRecord(resultCode: Int, data: Intent, cacheSize: Int) {
        mScreenCaptureThread = ScreenCaptureThread(
            MediaFormatParams(
                mDisplayMetrics.widthPixels,
                mDisplayMetrics.heightPixels,
                colorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
            ),
            mDisplayMetrics.densityDpi,
            mProjectionManager.getMediaProjection(resultCode, data),
            object : ScreenCaptureThread.Callback {
                override fun prePrepare(mediaFormatParams: MediaFormatParams) {
                    FrameDataCacheUtils.initCache(
                        cacheSize, BuildConfig.DEBUG
                    )
                    isEnvReady.set(true)
                }

                override fun putFrameData(frameData: FrameData) {
                    // 将编码好的H264数据存储到缓冲中
                    FrameDataCacheUtils.addFrameData(
                        frameData.timestamp,
                        frameData.isKeyFrame,
                        frameData.data,
                        frameData.length
                    )
                }
            }
        )
        mScreenCaptureThread?.start()
    }

    /**
     * 录屏环境是否已就绪
     *
     * @return true 环境已就绪
     */
    fun isEnvReady(): Boolean {
        return isEnvReady.get()
    }

    fun getMediaFormat(): MediaFormat? {
        return mScreenCaptureThread?.getMediaFormat()
    }

    fun startMuxer(fileName: String, startTime: Long, endTime: Long) {
        if (isMuxer.get()) {
            LogUtils.e(TAG, "正在制作视频。。。")
            return
        }
        val mediaFormat = getMediaFormat()
        if (mediaFormat == null) {
            LogUtils.e(TAG, "")
            isMuxer.set(false)
            return
        }
        isMuxer.set(true)
        // 删除旧的Cache文件，只保留8个
        FileUtils.deleteOldFiles(FileUtils.videoDir, 8)
        mVideoMuxerThread =
            VideoMuxerThread(mediaFormat!!, fileName, object : VideoMuxerThread.Callback {
                override fun getFirstIFrameData(): FrameData? {
                    val res = FrameDataCacheUtils.getFirstFrameData(
                        startTime,
                        mCurTimeStamp,
                        mFrameBuffer,
                        mLength
                    )
                    if (res == DataCacheCode.RES_SUCCESS) {
                        return FrameData(mFrameBuffer, mLength[0], mCurTimeStamp[0], true)
                    }
                    return null
                }

                override fun getNextFrameData(): FrameData? {
                    val res = FrameDataCacheUtils.getNextFrameData(
                        mCurTimeStamp[0],
                        mCurTimeStamp,
                        mFrameBuffer,
                        mLength,
                        mIsKeyFrame
                    )
                    if (res == DataCacheCode.RES_SUCCESS) {
                        if (mCurTimeStamp[0] > endTime) {
                            mVideoMuxerThread?.quit()
                        }
                        return FrameData(mFrameBuffer, mLength[0], mCurTimeStamp[0], mIsKeyFrame[0])
                    } else if (res == DataCacheCode.RES_FAILED) {
                        mVideoMuxerThread?.quit()
                    }
                    return null
                }

                override fun finished(fileName: String) {
                    finishedMuxerTask.add(endTime)
                    isMuxer.set(false)
                    ThreadUtils.runOnMainThread {
                        ToastUtils.showLong("视频录制完成。")
                    }
                }
            })

        mVideoMuxerThread?.start()
    }

    /**
     * Muxer视频任务是否结束
     *
     * @param taskId 任务ID，结束时间戳
     */
    fun isFinishedMuxerTask(taskId: Long): Boolean {
        return finishedMuxerTask.contains(taskId)
    }

    /**
     * 移除已完成的记录
     */
    fun removeFinishedMuxerTask(taskId: Long) {
        finishedMuxerTask.remove(taskId)
        LogUtils.d(TAG, "removeFinishedMuxerTask finishedMuxerTask: $finishedMuxerTask")
    }
}