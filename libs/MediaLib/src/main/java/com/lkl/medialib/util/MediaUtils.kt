package com.lkl.medialib.util

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
}