#include <assert.h>
#include <cstring>
#include "libyuv.h"
#include "jni.h"

#ifdef ANDROID

#define LOG_TAG    "Native_YUV"
#define LOGD(format, ...)  __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, format, ##__VA_ARGS__)
#define LOGE(format, ...)  __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, format, ##__VA_ARGS__)
#define LOGI(format, ...)  __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, format, ##__VA_ARGS__)
#else
#define LOGE(format, ...)  printf(LOG_TAG format "\n", ##__VA_ARGS__)
#define LOGI(format, ...)  printf(LOG_TAG format "\n", ##__VA_ARGS__)
#endif

#define YUV_UTILS_JAVA "com/lkl/yuvjni/YuvUtils"

#ifdef __cplusplus
extern "C" {
#endif

// debug 输出 YUV 文件
//#define SAVE_RET

static int
(*rgbaToI420Func[])(const uint8_t *, int, uint8_t *, int, uint8_t *, int, uint8_t *, int, int,
                    int) ={
        libyuv::ABGRToI420, libyuv::RGBAToI420, libyuv::ARGBToI420, libyuv::BGRAToI420,
        libyuv::RGB24ToI420, libyuv::RGB565ToI420
};

static int
(*i420ToRgbaFunc[])(const uint8_t *, int, const uint8_t *, int, const uint8_t *, int, uint8_t *,
                    int, int, int) ={
        libyuv::I420ToABGR, libyuv::I420ToRGBA, libyuv::I420ToARGB, libyuv::I420ToBGRA,
        libyuv::I420ToRGB24, libyuv::I420ToRGB565
};

static void (*rotatePlaneFunc[])(const uint8_t *src, int src_stride, uint8_t *dst, int dst_stride,
                                 int width, int height) ={
        libyuv::RotatePlane90, libyuv::RotatePlane180, libyuv::RotatePlane270,
};

static void (*rotateUVFunc[])(const uint8_t *src, int src_stride, uint8_t *dst_a, int dst_stride_a,
                              uint8_t *dst_b, int dst_stride_b, int width, int height) ={
        libyuv::RotateUV90, libyuv::RotateUV180, libyuv::RotateUV270,
};

/**
 * 裁剪NV21、NV12数据
 * @param tarYuv 裁剪后的数据
 * @param srcYuv 要裁剪的源数据
 * @param startW 开始裁剪的Width位置
 * @param startH 开始裁剪的Height位置
 * @param cutW 裁剪的Width
 * @param cutH 裁剪的Height
 * @param srcW 源数据的Width
 * @param srcH 源数据的Height
 */
static void cutYuv(unsigned char *tarYuv, unsigned char *srcYuv, int startW,
                   int startH, int cutW, int cutH, int srcW, int srcH) {
    int i;
    int j = 0;
    int k = 0;
    //分配一段内存，用于存储裁剪后的Y分量
    unsigned char *tmpY = tarYuv;
    //分配一段内存，用于存储裁剪后的UV分量
    unsigned char *tmpUV = tarYuv + cutW * cutH;

    for (i = startH; i < cutH + startH; i++) {
        // 逐行拷贝Y分量，共拷贝cutW*cutH
        memcpy(tmpY + j * cutW, srcYuv + startW + i * srcW, cutW);
        j++;
    }
    for (i = startH / 2; i < (cutH + startH) / 2; i++) {
        //逐行拷贝UV分量，共拷贝cutW*cutH/2
        memcpy(tmpUV + k * cutW, srcYuv + startW + srcW * srcH + i * srcW, cutW);
        k++;
    }

#ifdef SAVE_RET
    FILE *outPutFp = fopen("/sdcard/Pictures/cutSrcYuv.yuv", "w+");
    fwrite(tarYuv, 1, cutW * cutH * 3 / 2, outPutFp);
    fclose(outPutFp);
#endif
}

JNIEXPORT void JNICALL
Jni_NV21CutData(JNIEnv *env, jclass clazz, jbyteArray tar, jbyteArray src, jint startW,
            jint startH, jint cutW, jint cutH, jint srcW, jint srcH) {
    jbyte *srcData = env->GetByteArrayElements(src, JNI_FALSE);
    jbyte *tarData = env->GetByteArrayElements(tar, JNI_FALSE);

    cutYuv((unsigned char *)tarData, (unsigned char *)srcData, startW, startH, cutW, cutH, srcW, srcH);

    env->ReleaseByteArrayElements(src, srcData, JNI_OK);
    env->ReleaseByteArrayElements(tar, tarData, JNI_OK);
}

static int
fCutWaterMark(unsigned char *waterMarkSrc, unsigned char *srcYuv, int waterMarkW, int waterMarkH) {
    int i;
    size_t data_length = (size_t) waterMarkW * waterMarkH * 3 / 2;
    unsigned char *tmpData = (unsigned char *) malloc(data_length);;
    unsigned char *tmpWaterMark = (unsigned char *) malloc(data_length);
    memcpy(tmpData, srcYuv, data_length);
    memcpy(tmpWaterMark, waterMarkSrc, data_length);

    for (i = 0; i < data_length; i++) {
        if (tmpWaterMark[i] != 0x10 && tmpWaterMark[i] != 0x80 && tmpWaterMark[i] != 0xeb) {
            tmpData[i] = tmpWaterMark[i];
            // printf("0x%X\n", tmpData[i]);
        }
    }

    memcpy(waterMarkSrc, tmpData, data_length);

#ifdef SAVE_RET
    FILE *outPutFp = fopen("/sdcard/Pictures/afterCutWaterMark.yuv", "w+");
    fwrite(waterMarkSrc, 1, data_length, outPutFp);
    fclose(outPutFp);
#endif

    free(tmpData);
    free(tmpWaterMark);
#if 0
    FILE *tarFp = fopen("afterCutWaterMark.yuv", "w+");
    fwrite(waterMarkSrc, 1, waterMarkW*waterMarkH*3/2, tarFp);
    fclose(tarFp);
#endif
    return 0;
}


JNIEXPORT jint JNICALL
rgbaToI420(JNIEnv *env, jclass clazz, jbyteArray rgba, jint rgba_stride,
           jbyteArray yuv, jint y_stride, jint u_stride, jint v_stride,
           jint width, jint height,
           int (*func)(const uint8_t *, int, uint8_t *, int, uint8_t *, int, uint8_t *, int,
                       int,
                       int)) {
    size_t ySize = (size_t) (y_stride * height);
    size_t uSize = (size_t) (u_stride * height >> 1);
    jbyte *rgbaData = env->GetByteArrayElements(rgba, JNI_FALSE);
    jbyte *yuvData = env->GetByteArrayElements(yuv, JNI_FALSE);
    int ret = func((const uint8_t *) rgbaData, rgba_stride, (uint8_t *) yuvData, y_stride,
                   (uint8_t *) (yuvData) + ySize, u_stride, (uint8_t *) (yuvData) + ySize + uSize,
                   v_stride, width, height);
    env->ReleaseByteArrayElements(rgba, rgbaData, JNI_OK);
    env->ReleaseByteArrayElements(yuv, yuvData, JNI_OK);
    return ret;
}

JNIEXPORT jint JNICALL
i420ToRgba(JNIEnv *env, jclass clazz, jbyteArray yuv, jint y_stride, jint u_stride, jint v_stride,
           jbyteArray rgba, jint rgba_stride, jint width, jint height,
           int (*func)(const uint8_t *, int, const uint8_t *, int, const uint8_t *, int, uint8_t *,
                       int, int, int)) {
    size_t ySize = (size_t) (y_stride * height);
    size_t uSize = (size_t) (u_stride * height >> 1);
    jbyte *rgbaData = env->GetByteArrayElements(rgba, JNI_FALSE);
    jbyte *yuvData = env->GetByteArrayElements(yuv, JNI_FALSE);
    int ret = func((const uint8_t *) yuvData, y_stride, (uint8_t *) yuvData + ySize, u_stride,
                   (uint8_t *) (yuvData) + ySize + uSize, v_stride, (uint8_t *) (rgbaData),
                   rgba_stride, width, height);
    env->ReleaseByteArrayElements(rgba, rgbaData, JNI_OK);
    env->ReleaseByteArrayElements(yuv, yuvData, JNI_OK);
    return ret;
}

JNIEXPORT jint JNICALL
Jni_I420ToNV21(JNIEnv *env, jclass clazz, jbyteArray yuv420p, jbyteArray yuv420sp, jint width,
               jint height, jboolean swapUV) {
    size_t ySize = (size_t) (width * height);
    size_t uSize = (size_t) (width * height >> 2);
    size_t stride[] = {0, uSize};
    jbyte *yuv420pData = env->GetByteArrayElements(yuv420p, JNI_FALSE);
    jbyte *yuv420spData = env->GetByteArrayElements(yuv420sp, JNI_FALSE);
    int ret = libyuv::I420ToNV21((const uint8_t *) yuv420pData, width,
                                 (const uint8_t *) (yuv420pData + ySize + stride[swapUV]),
                                 width >> 1,
                                 (const uint8_t *) (yuv420pData + ySize + stride[1 - swapUV]),
                                 width >> 1,
                                 (uint8_t *) yuv420spData, width,
                                 (uint8_t *) (yuv420spData + ySize),
                                 width, width, height);
    env->ReleaseByteArrayElements(yuv420p, yuv420pData, JNI_OK);
    env->ReleaseByteArrayElements(yuv420sp, yuv420spData, JNI_OK);
    return ret;
}

JNIEXPORT jint JNICALL
Jni_NV21ToI420(JNIEnv *env, jclass clazz, jbyteArray yuv420sp, jbyteArray yuv420p, jint width,
               jint height, jboolean swapUV) {
    size_t ySize = (size_t) (width * height);
    size_t uSize = (size_t) (width * height >> 2);
    size_t stride[] = {0, uSize};
    jbyte *yuv420pData = env->GetByteArrayElements(yuv420p, JNI_FALSE);
    jbyte *yuv420spData = env->GetByteArrayElements(yuv420sp, JNI_FALSE);
    int ret = libyuv::NV21ToI420((const uint8_t *) yuv420spData, width,
                                 (const uint8_t *) (yuv420spData + ySize), width,
                                 (uint8_t *) yuv420pData, width,
                                 (uint8_t *) (yuv420pData + ySize + stride[swapUV]), width >> 1,
                                 (uint8_t *) (yuv420pData + ySize + stride[1 - swapUV]), width >> 1,
                                 width, height);
    env->ReleaseByteArrayElements(yuv420p, yuv420pData, JNI_OK);
    env->ReleaseByteArrayElements(yuv420sp, yuv420spData, JNI_OK);
    return ret;
}


JNIEXPORT jint JNICALL
Jni_ArgbToNV21(JNIEnv *env, jclass clazz, jbyteArray argb, jbyteArray nv21, jint width,
               jint height) {
    jbyte *argbData = env->GetByteArrayElements(argb, JNI_FALSE);
    jbyte *nv21Data = env->GetByteArrayElements(nv21, JNI_FALSE);

    size_t ySize = (size_t) (width * height);

    int res = libyuv::ARGBToNV21((const uint8_t *) argbData, width * 4, (uint8_t *) nv21Data, width,
                                 (uint8_t *) (nv21Data + ySize), width, width, height);

    env->ReleaseByteArrayElements(argb, argbData, JNI_OK);
    env->ReleaseByteArrayElements(nv21, nv21Data, JNI_OK);
    return res;
}
JNIEXPORT void JNICALL
Jni_NV12ToNV21(JNIEnv *env, jclass clazz, jbyteArray yuv, jint width,
               jint height) {
    jbyte *nv12Data = env->GetByteArrayElements(yuv, JNI_FALSE);

    int ySize = width * height;

    jbyte tmp;
    for (int index = ySize; index < ySize * 1.5; index++) {
        if ((index + 1) % 2 == 0) {
            tmp = nv12Data[index - 1];
            nv12Data[index - 1] = nv12Data[index];
            nv12Data[index] = tmp;
        }
    }

    env->ReleaseByteArrayElements(yuv, nv12Data, JNI_OK);
}

JNIEXPORT jint JNICALL
Jni_NV12ToArgb(JNIEnv *env, jclass clazz, jbyteArray nv12, jbyteArray argb, jint width,
               jint height) {
    jbyte *nv12Data = env->GetByteArrayElements(nv12, JNI_FALSE);
    jbyte *argbData = env->GetByteArrayElements(argb, JNI_FALSE);

    size_t ySize = (size_t) (width * height);

    int res = libyuv::NV12ToABGR((const uint8_t *) nv12Data, width,
                                 (const uint8_t *) (nv12Data + ySize), width, (uint8_t *) argbData,
                                 width * 4, width, height);

    env->ReleaseByteArrayElements(argb, argbData, JNI_OK);
    env->ReleaseByteArrayElements(nv12, nv12Data, JNI_OK);
    return res;
}

JNIEXPORT jint JNICALL
Jni_NV21ToArgb(JNIEnv *env, jclass clazz, jbyteArray nv21, jbyteArray argb, jint width,
               jint height) {
    jbyte *nv21Data = env->GetByteArrayElements(nv21, JNI_FALSE);
    jbyte *argbData = env->GetByteArrayElements(argb, JNI_FALSE);

    size_t ySize = (size_t) (width * height);

    int res = libyuv::NV21ToABGR((const uint8_t *) nv21Data, width,
                                 (const uint8_t *) (nv21Data + ySize), width, (uint8_t *) argbData,
                                 width * 4, width, height);

    env->ReleaseByteArrayElements(argb, argbData, JNI_OK);
    env->ReleaseByteArrayElements(nv21, nv21Data, JNI_OK);
    return res;
}

JNIEXPORT jint JNICALL
Jni_NV21ToRGB24(JNIEnv *env, jclass clazz, jbyteArray nv21, jbyteArray rgb24, jint width,
                jint height) {
    jbyte *nv21Data = env->GetByteArrayElements(nv21, JNI_FALSE);
    jbyte *rgbData = env->GetByteArrayElements(rgb24, JNI_FALSE);

    size_t ySize = (size_t) (width * height);

    int res = libyuv::NV21ToRGB24((const uint8_t *) nv21Data, width,
                                  (const uint8_t *) (nv21Data + ySize), width, (uint8_t *) rgbData,
                                  width * 3, width, height);

    env->ReleaseByteArrayElements(rgb24, rgbData, JNI_OK);
    env->ReleaseByteArrayElements(nv21, nv21Data, JNI_OK);
    return res;
}

JNIEXPORT void JNICALL
Jni_NV21Scale(JNIEnv *env, jclass clazz, jbyteArray src, jint width, jint height, jbyteArray dst,
              jint dst_width, jint dst_height, int mode) {
    size_t ySize = (size_t) (width * height);
    size_t dstYSize = (size_t) (dst_width * dst_height);
    jbyte *srcData = env->GetByteArrayElements(src, JNI_FALSE);
    jbyte *dstData = env->GetByteArrayElements(dst, JNI_FALSE);
    libyuv::ScalePlane((const uint8_t *) srcData, width, width, height,
                       (uint8_t *) dstData, dst_width, dst_width, dst_height,
                       (libyuv::FilterMode) mode);
    libyuv::ScalePlane((const uint8_t *) (srcData + ySize), width, width, height >> 1,
                       (uint8_t *) (dstData + dstYSize), dst_width, dst_width, dst_height >> 1,
                       (libyuv::FilterMode) mode);
    env->ReleaseByteArrayElements(src, srcData, JNI_OK);
    env->ReleaseByteArrayElements(dst, dstData, JNI_OK);
}

JNIEXPORT void JNICALL
Jni_I420Scale(JNIEnv *env, jclass clazz, jbyteArray src, jint width, jint height, jbyteArray dst,
              jint dst_width, jint dst_height, int mode, jboolean swapUV) {
    int ySize = width * height;
    int swap[] = {0, ySize >> 2};
    size_t dstYSize = (size_t) (dst_width * dst_height);
    jbyte *srcData = env->GetByteArrayElements(src, JNI_FALSE);
    jbyte *dstData = env->GetByteArrayElements(dst, JNI_FALSE);
    libyuv::I420Scale((const uint8_t *) srcData, width, (const uint8_t *) (srcData + ySize),
                      width >> 1,
                      (uint8_t *) (srcData + ySize + (ySize >> 2)), width >> 1, width, height,
                      (uint8_t *) dstData, dst_width,
                      (uint8_t *) (dstData + dstYSize + swap[swapUV]),
                      dst_width >> 1,
                      (uint8_t *) (dstData + dstYSize + swap[1 - swapUV]), dst_width >> 1,
                      dst_width, dst_height, (libyuv::FilterMode) mode);
    env->ReleaseByteArrayElements(src, srcData, JNI_OK);
    env->ReleaseByteArrayElements(dst, dstData, JNI_OK);
}

JNIEXPORT void JNICALL
Jni_RgbaScale(JNIEnv *env, jclass clazz, jint type, jbyteArray src, jint src_width, jint src_height,
              jbyteArray dst, jint dst_width, jint dst_height, jint mode) {
    int bytes = (type & 0x0F000000) >> 24;
    jbyte *srcData = env->GetByteArrayElements(src, JNI_FALSE);
    jbyte *dstData = env->GetByteArrayElements(dst, JNI_FALSE);
    libyuv::ARGBScale((const uint8_t *) srcData, bytes * src_width, src_width, src_height,
                      (uint8_t *) dstData, bytes * dst_width, dst_width, dst_height,
                      (libyuv::FilterMode) mode);
    env->ReleaseByteArrayElements(src, srcData, JNI_OK);
    env->ReleaseByteArrayElements(dst, dstData, JNI_OK);
}

JNIEXPORT void JNICALL
Jni_NV21ToI420Rotate(JNIEnv *env, jclass clazz, jbyteArray src, jint width, jint height,
                     jbyteArray dst, jint de, jboolean swapUV) {
    int dst_stride[] = {height, width, height};
    size_t ySize = (size_t) (width * height);
    size_t swap[] = {0, ySize >> 2};
    jbyte *srcData = env->GetByteArrayElements(src, JNI_FALSE);
    jbyte *dstData = env->GetByteArrayElements(dst, JNI_FALSE);
    rotatePlaneFunc[de]((const uint8_t *) srcData, width, (uint8_t *) dstData, dst_stride[de],
                        width,
                        height);
    rotateUVFunc[de]((const uint8_t *) (srcData + ySize), width,
                     (uint8_t *) (dstData + ySize + swap[swapUV]), dst_stride[de] >> 1,
                     (uint8_t *) (dstData + ySize + swap[1 - swapUV]), dst_stride[de] >> 1,
                     width >> 1, height >> 1);
    env->ReleaseByteArrayElements(src, srcData, JNI_OK);
    env->ReleaseByteArrayElements(dst, dstData, JNI_OK);
}

JNIEXPORT jint JNICALL
Jni_RgbaToI420WithStride(JNIEnv *env, jclass clazz, jint type, jbyteArray rgba, jint rgba_stride,
                         jbyteArray yuv, jint y_stride, jint u_stride, jint v_stride,
                         jint width, jint height) {
    uint8_t cType = (uint8_t) (type & 0x0F);
    return rgbaToI420(env, clazz, rgba, rgba_stride, yuv, y_stride, u_stride, v_stride, width,
                      height, rgbaToI420Func[cType]);
}

JNIEXPORT jint JNICALL
Jni_RgbaToI420(JNIEnv *env, jclass clazz, jint type, jbyteArray rgba, jbyteArray yuv, jint width,
               jint height) {
    uint8_t cType = (uint8_t) (type & 0x0F);
    int rgba_stride = ((type & 0xF0) >> 4) * width;
    int y_stride = width;
    int u_stride = width >> 1;
    int v_stride = u_stride;
    return rgbaToI420(env, clazz, rgba, rgba_stride, yuv, y_stride, u_stride, v_stride, width,
                      height, rgbaToI420Func[cType]);
}

JNIEXPORT jint JNICALL
Jni_I420ToRgbaWithStride(JNIEnv *env, jclass clazz, jint type, jbyteArray yuv, jint y_stride,
                         jint u_stride, jint v_stride,
                         jbyteArray rgba, jint rgba_stride,
                         jint width, jint height) {
    uint8_t cType = (uint8_t) (type & 0x0F);
    return i420ToRgba(env, clazz, yuv, y_stride, u_stride, v_stride, rgba, rgba_stride, width,
                      height, i420ToRgbaFunc[cType]);
}

JNIEXPORT jint JNICALL
Jni_I420ToRgba(JNIEnv *env, jclass clazz, jint type, jbyteArray yuv, jbyteArray rgba, jint width,
               jint height) {
    uint8_t cType = (uint8_t) (type & 0x0F);
    int rgba_stride = ((type & 0xF0) >> 4) * width;
    int y_stride = width;
    int u_stride = width >> 1;
    int v_stride = u_stride;
    return i420ToRgba(env, clazz, yuv, y_stride, u_stride, v_stride, rgba, rgba_stride, width,
                      height, i420ToRgbaFunc[cType]);
}

JNIEXPORT void JNICALL
Jni_NV21AddWaterMark(JNIEnv *env, jclass clazz, jint startX, jint startY, jbyteArray waterMarkData,
                     jint waterMarkW, jint waterMarkH, jbyteArray yuvData, jint yuvW, jint yuvH) {
    int i = 0;
    int j = 0;
    int k = 0;

    jbyte *srcData = env->GetByteArrayElements(yuvData, JNI_FALSE);
    jbyte *dstData = env->GetByteArrayElements(waterMarkData, JNI_FALSE);

#if 1
    // 方法一：直接在原始数据上计算，一步到位
    // 注意：该处必须使用 unsigned char 不能使用 jbyte（signed char），否则判断会出错
    unsigned char temp_char;
    for (i = startY, k = 0; i < waterMarkH + startY; i++) {
        for (j = 0; j < waterMarkW; j++) {
            temp_char = dstData[k * waterMarkW + j];
            // 透明背景的 Y分量是0x10
            if (temp_char != 0x10) {
                srcData[startX + i * yuvW + j] = temp_char;
            }
        }
        k++;
    }

    for (i = startY / 2, k = 0; i < (waterMarkH + startY) / 2; i++) {
        for (j = 0; j < waterMarkW; j++) {
            temp_char = dstData[waterMarkW * waterMarkH + k * waterMarkW + j];
            // 透明背景的 UV分量是0x80
            if (temp_char != 0x80 && temp_char != 0xeb) {
                srcData[startX + yuvW * yuvH + i * yuvW + j] = temp_char;
            }
        }
        k++;
    }
#else
    // 方法二：裁剪 -> 合成水印 -> 合成完整图片 缺点：大量的内存频繁开辟释放
    unsigned char *tmpY = (unsigned char *) malloc(waterMarkW * waterMarkH * 3 / 2);
    cutYuv(tmpY, (unsigned char *) srcData, startX, startY, waterMarkW, waterMarkH, yuvW, yuvH);
    fCutWaterMark((unsigned char *) dstData, tmpY, waterMarkW, waterMarkH);

    for (i = startY, j = 0; i < waterMarkH + startY; i++) {
        memcpy(srcData + startX + i * yuvW, dstData + j * waterMarkW,
               (size_t) waterMarkW);
        j++;
    }
    for (i = startY / 2, k = 0; i < (waterMarkH + startY) / 2; i++) {
        memcpy(srcData + startX + yuvW * yuvH + i * yuvW,
               dstData + waterMarkW * waterMarkH + k * waterMarkW, (size_t) waterMarkW);
        k++;
    }
#endif

#ifdef SAVE_RET
    FILE *outPutFp = fopen("/sdcard/Pictures/Final.yuv", "w+");
//    fwrite(dstData, 1, waterMarkW * waterMarkH * 3 / 2, outPutFp);
    fwrite(srcData, 1, yuvW * yuvH * 3 / 2, outPutFp);
    fclose(outPutFp);
#endif

    env->ReleaseByteArrayElements(yuvData, srcData, JNI_OK);
    env->ReleaseByteArrayElements(waterMarkData, dstData, JNI_OK);
}


//libyuv中，rgba表示abgrabgrabgr这样的顺序写入文件，java使用的时候习惯rgba表示rgbargbargba写入文件
static JNINativeMethod g_methods[] = {
        {"RgbaToI420",       "(I[BI[BIIIII)I", (jint *) Jni_RgbaToI420WithStride},
        {"RgbaToI420",       "(I[B[BII)I",     (jint *) Jni_RgbaToI420},
        {"ArgbToNV21",       "([B[BII)I",      (jint *) Jni_ArgbToNV21},
        {"NV12ToNV21",       "([BII)V",        (jint *) Jni_NV12ToNV21},
        {"NV12ToArgb",       "([B[BII)I",      (jint *) Jni_NV12ToArgb},
        {"NV21ToArgb",       "([B[BII)I",      (jint *) Jni_NV21ToArgb},
        {"NV21ToRgb24",      "([B[BII)I",      (jint *) Jni_NV21ToRGB24},
        {"NV21AddWaterMark", "(II[BII[BII)V",  (void *) Jni_NV21AddWaterMark},

        {"I420ToRgba",       "(I[BIII[BIII)I", (jint *) Jni_I420ToRgbaWithStride},
        {"I420ToRgba",       "(I[B[BII)I",     (jint *) Jni_I420ToRgba},

        {"I420ToNV21",       "([B[BIIZ)I",     (jint *) Jni_I420ToNV21},
        {"NV21ToI420",       "([B[BIIZ)I",     (jint *) Jni_NV21ToI420},

        {"NV21Scale",        "([BII[BIII)V",   (void *) Jni_NV21Scale},
        {"I420Scale",        "([BII[BIIIZ)V",  (void *) Jni_I420Scale},
        {"RgbaScale",        "([BII[BIII)V",   (void *) Jni_RgbaScale},
        {"NV21ToI420Rotate", "([BII[BIZ)V",    (void *) Jni_NV21ToI420Rotate},

        {"NV21CutData",      "([B[BIIIIII)V",  (void *) Jni_NV21CutData},
};

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env = nullptr;

    if (vm->GetEnv((void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }
    assert(env != nullptr);
    jclass clazz = env->FindClass(YUV_UTILS_JAVA);
    env->RegisterNatives(clazz, g_methods, (int) (sizeof(g_methods) / sizeof((g_methods)[0])));

    return JNI_VERSION_1_6;
}

JNIEXPORT void JNI_OnUnload(JavaVM *jvm, void *reserved) {
    JNIEnv *env = nullptr;

    if (jvm->GetEnv((void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        return;
    }
    jclass clazz = env->FindClass(YUV_UTILS_JAVA);
    env->UnregisterNatives(clazz);
}

#ifdef __cplusplus
}
#endif