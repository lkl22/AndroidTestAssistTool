package com.lkl.medialib.util

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.util.Log
import com.lkl.medialib.bean.MediaFormatParams

/**
 * media操作相关的工具方法
 *
 * @author likunlun
 * @since 2021/12/23
 */
object MediaUtils {
    private const val TAG = "MediaUtils"

    private val mMediaCodecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)

    /**
     * 根据 MediaFormatParams 创建视频的 MediaFormat
     *
     * @param params MediaFormatParams对象
     * @return 视频的MediaFormat
     */
    fun createVideoFormat(params: MediaFormatParams): MediaFormat {
        val format = MediaFormat.createVideoFormat(params.mimeType, params.width, params.height)
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, params.colorFormat)
        format.setInteger(MediaFormat.KEY_BIT_RATE, params.bitRate)
        format.setInteger(MediaFormat.KEY_FRAME_RATE, params.frameRate)
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, params.iFrameInterval)
        Log.d(TAG, "created video format: $format")
        return format
    }

    /**
     * 选择解码器
     *
     * @param mimeType mime类型
     * @return MediaCodecInfo对象
     */
    fun selectCodec(mimeType: String): MediaCodecInfo? {
        val codecInfos = mMediaCodecList.codecInfos
        for (codecInfo in codecInfos) {
            if (codecInfo.isEncoder) {
                continue
            }
            val types = codecInfo.supportedTypes
            for (j in types.indices) {
                if (types[j].equals(mimeType, ignoreCase = true)) {
                    return codecInfo
                }
            }
        }
        return null
    }

    /**
     * Returns a color format that is supported by the codec and by this test code.  If no
     * match is found, this throws a test failure -- the set of formats known to the test
     * should be expanded for new platforms.
     *
     * @param codecInfo MediaCodecInfo对象
     * @param mimeType mime类型
     * @return colorFormat
     */
    fun selectColorFormat(codecInfo: MediaCodecInfo, mimeType: String): Int {
        val capabilities = codecInfo.getCapabilitiesForType(mimeType)
        for (i in capabilities.colorFormats.indices) {
            val colorFormat = capabilities.colorFormats[i]
            if (isRecognizedFormat(colorFormat)) {
                return colorFormat
            }
        }
        return 0 // not reached
    }

    /**
     * Returns true if this is a color format that this test code understands (i.e. we know how
     * to read and generate frames in this format).
     *
     * @param colorFormat colorFormat
     * @return true RecognizedFormat
     */
    private fun isRecognizedFormat(colorFormat: Int): Boolean {
        return when (colorFormat) {
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar,
            MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar -> true
            else -> false
        }
    }
}