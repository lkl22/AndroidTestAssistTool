package com.lkl.yuvjni;

/**
 * YUV 图片格式转换工具
 *
 * @author likunlun
 * @since 2021/12/19
 */
public class YuvUtils {

    public static native int RgbaToI420(int type, byte[] rgba, int stride, byte[] yuv,
                                        int y_stride, int u_stride, int v_stride, int width, int height);

    public static native int RgbaToI420(int type, byte[] rgba, byte[] yuv, int width, int height);

    public static native int ArgbToNV21(byte[] rgba, byte[] yuv, int width, int height);

    /* 1280 * 720 -> 30ms 不可取*/
    public static native void NV12ToNV21(byte[] yuv, int width, int height);

    public static native int NV12ToArgb(byte[] yuv, byte[] argb, int width, int height);

    public static native int NV21ToArgb(byte[] yuv, byte[] argb, int width, int height);

    // 1280 * 720 耗时 103 ms 左右
    public static native int NV21ToRgb24(byte[] yuv, byte[] rgb24, int width, int height);

    public static native void NV21AddWaterMark(int startX, int startY, byte[] waterMarkData, int waterMarkW, int waterMarkH, byte[] yuvData, int yuvW, int yuvH);

    public static native int I420ToRgba(int type, byte[] yuv, int y_stride, int u_stride, int v_stride,
                                        byte[] rgba, int stride, int width, int height);

    public static native int I420ToRgba(int type, byte[] yuv, byte[] rgba, int width, int height);

    public static native int I420ToNV21(byte[] yuv420p, byte[] yuv420sp, int width, int height, boolean swapUV);

    public static native int NV21ToI420(byte[] yuv420sp, byte[] yuv420p, int width, int height, boolean swapUV);

    public static native void NV21Scale(byte[] src_data, int width, int height, byte[] out,
                                        int dst_width, int dst_height, int type);

    public static native void I420Scale(byte[] src_data, int width, int height, byte[] out,
                                        int dst_width, int dst_height, int type, boolean swapUV);

    public static native void RgbaScale(byte[] src_data, int width, int height, byte[] out,
                                        int dst_width, int dst_height, int type);

    public static native void NV21ToI420Rotate(byte[] src, int width, int height, byte[] dst, int de, boolean swapUV);

    public static native void NV21CutData(byte[] tarYuv, byte[] srcYuv, int startW, int startH, int cutW, int cutH, int srcW, int srcH);

    static {
        System.loadLibrary("yuv-jni");
    }

}
