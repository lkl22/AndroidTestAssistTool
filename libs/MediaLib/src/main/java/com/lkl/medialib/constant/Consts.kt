package com.lkl.medialib.constant


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
    @JvmField
    val BIT_RATE = 400000

    /**
     * 关键帧时间间隔 s
     */
    @JvmField
    val FRAME_INTERVAL = 1
}