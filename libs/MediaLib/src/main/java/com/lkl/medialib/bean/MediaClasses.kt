package com.lkl.medialib.bean

import android.media.MediaCodecInfo
import android.media.MediaFormat
import com.lkl.medialib.constant.VideoConfig

/**
 * 媒体操作相关的实体类
 *
 * @author likunlun
 * @since 2021/12/19
 *
 * @param data 帧数据
 * @param timeStamp 时间戳
 */
data class FrameData(var data: ByteArray, var timeStamp: Long) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as FrameData
        if (!data.contentEquals(other.data)) return false
        if (timeStamp != other.timeStamp) return false
        return true
    }

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + timeStamp.hashCode()
        return result
    }
}

/**
 * 裁剪数据实体类
 *
 * @author likunlun
 * @since 2021/12/19
 *
 * @param frameData 视频帧数据
 * @param centerX  裁剪数据的中心点X坐标
 * @param centerY 裁剪数据的中心点Y坐标
 */
data class CutData(var frameData: FrameData, var centerX: Int, var centerY: Int)

data class MediaFormatData(
    var width: Int,
    var height: Int,
    var mimeType: String = MediaFormat.MIMETYPE_VIDEO_AVC, // H264
    var bitRate: Int = VideoConfig.BIT_RATE,
    var frameRate: Int = VideoConfig.FPS,
    var iFrameInterval: Int = VideoConfig.IFRAME_INTERVAL,
    var colorFormat: Int = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar
)

