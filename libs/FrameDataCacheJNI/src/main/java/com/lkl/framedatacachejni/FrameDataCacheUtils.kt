package com.lkl.framedatacachejni

/**
 * 视频帧数据缓存工具类
 *
 * @author likunlun
 * @since 2021/12/19
 */
object FrameDataCacheUtils {
    /**
     * 初始化缓存
     *
     * @param cacheSize 缓存空间大小，单位 M
     * @param isDebug 是否debug模式
     */
    external fun initCache(cacheSize: Int, isDebug: Boolean)

    /**
     * 添加新的一帧数据到缓存
     *
     * @param timestamp 时间戳 ms
     * @param isKeyFrame 是否关键帧
     * @param frameData 帧数据
     * @param length 数据长度
     */
    external fun addFrameData(
        timestamp: Long,
        isKeyFrame: Boolean,
        frameData: ByteArray,
        length: Int
    )

    /**
     * 通过时间戳从缓存区中获取最近的一个关键帧数据
     *
     * @param timestamp 传入的时间戳 ms
     * @param nextTimestamp 下一帧的时间戳 ms
     * @param frameData 帧数据
     * @param length 数据长度
     * @return 0成功，非0失败
     */
    external fun getFirstFrameData(
        timestamp: Long,
        nextTimestamp: LongArray,
        frameData: ByteArray,
        length: IntArray
    ): Int

    /**
     * 通过时间戳从缓存区中获取下一帧数据
     *
     * @param curTimestamp 传入的时间戳 ms
     * @param nextTimestamp 下一帧的时间戳 ms
     * @param frameData 帧数据
     * @param length 数据长度
     * @param isKeyFrame 是否关键帧（I帧）true I帧
     * @return 0成功，非0失败
     */
    external fun getNextFrameData(
        curTimestamp: Long,
        nextTimestamp: LongArray,
        frameData: ByteArray,
        length: IntArray,
        isKeyFrame: BooleanArray
    ): Int

    init {
        System.loadLibrary("framedatacachejni")
    }
}