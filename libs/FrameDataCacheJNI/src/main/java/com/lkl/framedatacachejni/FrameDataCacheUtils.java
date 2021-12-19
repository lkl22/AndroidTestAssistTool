package com.lkl.framedatacachejni;

/**
 * 视频帧数据缓存工具类
 *
 * @author likunlun
 * @since 2021/12/19
 */
public class FrameDataCacheUtils {

    /**
     * 初始化缓存
     *
     * @param fps 帧率
     * @param width 视频宽度
     * @param height 视频高度
     * @param code 编码格式 0:H264 1:H265
     */
    public static native void initCache(int fps, int width, int height, int code);

    /**
     * 添加新的一帧数据到缓存
     *
     * @param timestamp 时间戳 ms
     * @param isKeyFrame 是否关键帧
     * @param frameData 帧数据
     * @param size 数据大小
     */
    public static native void addFrameData(long timestamp, boolean isKeyFrame, byte[] frameData, int size);

    /**
     * 通过时间戳从缓存区中获取最近的一个关键帧数据
     *
     * @param timestamp 传入的时间戳 ms
     * @param nextTimestamp 下一帧的时间戳 ms
     * @param frameData 帧数据
     * @param size 数据长度
     * @return 0成功，非0失败
     */
    public static native int getFirstFrameData(long timestamp, long[] nextTimestamp, byte[] frameData, int[] size);

    /**
     * 通过时间戳从缓存区中获取下一帧数据
     *
     * @param curTimestamp 传入的时间戳 ms
     * @param nextTimestamp 下一帧的时间戳 ms
     * @param frameData 帧数据
     * @param size 数据长度
     * @return 0成功，非0失败
     */
    public static native int getNextFrameData(long curTimestamp, long[] nextTimestamp, byte[] frameData, int[] size);

    static {
        System.loadLibrary("framedatacachejni");
    }
}
