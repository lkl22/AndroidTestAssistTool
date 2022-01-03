package com.lkl.medialib.constant

import com.lkl.medialib.BuildConfig

/**
 * 屏幕录制常量
 */
object ScreenCapture {
    /**
     * createScreenCaptureIntent 时回调的 resultCode
     */
    const val KEY_RESULT_CODE = "resultCode"
    /**
     * createScreenCaptureIntent 时回调的 data
     */
    const val KEY_DATA = "data"
    /**
     * 缓存大小
     */
    const val KEY_CACHE_SIZE = "cacheSize"

    /**
     * 默认缓存大小
     */
    const val DEFAULT_CACHE_SIZE = 30

    /**
     * 是否打印视频处理的debug log
     */
    val PRINT_DEBUG_LOG = BuildConfig.DEBUG
}

/**
 * 视频录制配置参数
 */
object VideoConfig {
    @JvmField
    val MIME = "video/avc" //HEVC -> H265 AVC -> H264

    @JvmField
    var FPS = 20

    @JvmField
    var WIDTH = 1280

    @JvmField
    var HEIGHT = 720

    /**
     * 录制视频时的比特率
     */
    const val BIT_RATE = 6000000

    /**
     * 关键帧时间间隔 s
     */
    const val IFRAME_INTERVAL = 1
}