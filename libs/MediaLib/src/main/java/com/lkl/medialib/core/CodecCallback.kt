package com.lkl.medialib.core

import android.media.MediaFormat
import com.lkl.medialib.bean.FrameData

/**
 * 视频编解码回调
 *
 * @author likunlun
 * @since 2022/01/08
 */
interface CodecCallback {
    /**
     * 获取待编解码的数据帧
     */
    fun getFrameData(): FrameData?

    /**
     * MediaFormat Changed回调
     */
    fun formatChanged(mediaFormat: MediaFormat)

    /**
     * 将编解码出来的数据回调出去
     *
     * @param frameData 编解码后的视频帧数据
     */
    fun putFrameData(frameData: FrameData)

    /**
     * 数据编解码已经结束
     */
    fun finished()
}