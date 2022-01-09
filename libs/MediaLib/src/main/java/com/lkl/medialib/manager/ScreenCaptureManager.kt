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
import com.lkl.medialib.core.CodecCallback
import com.lkl.medialib.core.ScreenCaptureThread
import com.lkl.medialib.core.VideoMuxerThread
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 手机屏幕录制管理类
 *
 * @author likunlun
 * @since 2022/01/05
 */
class ScreenCaptureManager {
    companion object {
        private const val TAG = "ScreenCaptureManager"

        val instance: ScreenCaptureManager by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
            ScreenCaptureManager()
        }
    }

    private val mProjectionManager: MediaProjectionManager = BaseApplication.context
        .getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

    private val mDisplayMetrics = DisplayUtils.getDisplayMetrics()

    private var mMediaFormat: MediaFormat? = null

    /**
     * 录屏环境是否已就绪
     */
    private val isEnvReady = AtomicBoolean(false)

    /**
     * 是否正在Muxer视频
     */
    private val isMuxer = AtomicBoolean(false)
    private var finishedMuxerTask = ConcurrentHashMap<Long, String>()

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
                mDisplayMetrics.widthPixels / 16 * 16, // 宽高要是16的整数倍
                mDisplayMetrics.heightPixels / 16 * 16,
                colorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
            ),
            mDisplayMetrics.densityDpi,
            mProjectionManager.getMediaProjection(resultCode, data),
            object : CodecCallback {
                override fun prepare() {
                    FrameDataCacheUtils.initCache(
                        cacheSize, BuildConfig.DEBUG
                    )
                    isEnvReady.set(true)
                }

                override fun formatChanged(mediaFormat: MediaFormat) {
                    mMediaFormat = mediaFormat
                }

                override fun putFrameData(frameData: FrameData) {
                    // 正在制作视频时暂停缓存视频frame数据
                    if (!isMuxer.get()) {
                        // 将编码好的H264数据存储到缓冲中
                        FrameDataCacheUtils.addFrameData(
                            frameData.timestamp,
                            frameData.isKeyFrame,
                            frameData.data,
                            frameData.length
                        )
                    }
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

    fun startMuxer(startTime: Long, endTime: Long, callback: Callback) {
        if (isMuxer.get()) {
            LogUtils.e(TAG, "正在制作视频。。。")
            return
        }

        if (mMediaFormat == null) {
            LogUtils.e(TAG, "")
            isMuxer.set(false)
            return
        }
        isMuxer.set(true)
        // 删除旧的Cache文件，只保留8个
        FileUtils.deleteOldFiles(FileUtils.videoDir, 8)
        mVideoMuxerThread =
            VideoMuxerThread(mMediaFormat!!, callback = object : VideoMuxerThread.Callback {
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

                override fun finished(filePath: String) {
                    finishedMuxerTask[endTime] = filePath
                    isMuxer.set(false)
                    callback.muxerFinished(filePath)
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
        return finishedMuxerTask.containsKey(taskId)
    }

    /**
     * Muxer视频任务是否结束
     *
     * @param taskId 任务ID，结束时间戳
     */
    fun getMuxerVideo(taskId: Long): String? {
        return finishedMuxerTask[taskId]
    }

    /**
     * 移除已完成的记录
     */
    fun removeFinishedMuxerTask(taskId: Long) {
        finishedMuxerTask.remove(taskId)
        LogUtils.d(TAG, "removeFinishedMuxerTask finishedMuxerTask: $finishedMuxerTask")
    }

    interface Callback {
        /**
         * 视频muxer完成
         *
         * @param filePath 文件路径
         */
        fun muxerFinished(filePath: String)
    }
}