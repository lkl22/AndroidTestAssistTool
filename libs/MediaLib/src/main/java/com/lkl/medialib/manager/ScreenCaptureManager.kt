package com.lkl.medialib.manager

import android.content.Context
import android.content.Intent
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjectionManager
import com.lkl.commonlib.BaseApplication
import com.lkl.commonlib.util.DisplayUtils
import com.lkl.commonlib.util.LogUtils
import com.lkl.commonlib.util.ThreadUtils
import com.lkl.commonlib.util.ToastUtils
import com.lkl.framedatacachejni.FrameDataCacheUtils
import com.lkl.framedatacachejni.constant.DataCacheCode
import com.lkl.medialib.BuildConfig
import com.lkl.medialib.bean.FrameData
import com.lkl.medialib.bean.MediaFormatParams
import com.lkl.medialib.core.ScreenCaptureThread
import com.lkl.medialib.core.VideoMuxerThread
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
    private var isEnvReady = AtomicBoolean(false)

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
        val mediaFormat = getMediaFormat()
        if (mediaFormat == null) {
            LogUtils.e(TAG, "")
            return
        }
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
                    ThreadUtils.runOnMainThread{
                        ToastUtils.showLong("视频录制完成。")
                    }
                }
            })

        mVideoMuxerThread?.start()
    }
}