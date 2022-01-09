package com.lkl.medialib.bean

import android.media.MediaCodecInfo
import android.media.MediaFormat
import com.lkl.medialib.constant.VideoProperty

/**
 * 媒体操作相关的实体类
 *
 * @author likunlun
 * @since 2021/12/19
 *
 * @param data 帧数据
 * @param length 帧数据长度
 * @param timestamp 时间戳 ms
 * @param isKeyFrame 是否关键帧（I帧）true I帧
 */
data class FrameData(
    var data: ByteArray,
    var length: Int = -1,
    var timestamp: Long,
    var isKeyFrame: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FrameData

        if (!data.contentEquals(other.data)) return false
        if (length != other.length) return false
        if (timestamp != other.timestamp) return false
        if (isKeyFrame != other.isKeyFrame) return false

        return true
    }

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + length
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + isKeyFrame.hashCode()
        return result
    }

    override fun toString(): String {
        return "FrameData(length=$length, timestamp=$timestamp, isKeyFrame=$isKeyFrame)"
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

data class MediaFormatParams(
    var width: Int,
    var height: Int,
    var mimeType: String = MediaFormat.MIMETYPE_VIDEO_AVC, // H264
    var bitRate: Int = VideoProperty.BIT_RATE,
    var frameRate: Int = VideoProperty.FPS,
    var iFrameInterval: Int = VideoProperty.IFRAME_INTERVAL,
    var colorFormat: Int = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar
)

/**
 * 坐标信息
 *
 * @param x x坐标
 * @param y y坐标
 */
data class Position(val x:Int, val y:Int) {
    override fun toString(): String {
        return "($x, $y)"
    }
}

