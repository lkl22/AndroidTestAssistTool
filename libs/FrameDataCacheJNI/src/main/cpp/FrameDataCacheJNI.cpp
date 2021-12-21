#include <cstring>
#include "FrameDataCacheJNI.h"

/**
 * 动态注册
 */
JNINativeMethod methods[] = {
        {"initCache",         "(III)V",     (void *) initCache},
        {"addFrameData",      "(JZ[BI)V",   (void *) addFrameData},
        {"getFirstFrameData", "(J[J[B[I)I", (jint *) getFirstFrameData},
        {"getNextFrameData",  "(J[J[B[I)I", (jint *) getNextFrameData}
};

/**
 * 动态注册
 * @param env
 * @return
 */
jint registerNativeMethod(JNIEnv *env) {
    jclass cl = env->FindClass(DATA_CACHE_UTILS_JAVA);
    if ((env->RegisterNatives(cl, methods, sizeof(methods) / sizeof(methods[0]))) < 0) {
        return JNI_ERR;
    }
    return JNI_OK;
}

/**
 * 加载默认回调
 * @param vm
 * @param reserved
 * @return
 */
jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env = nullptr;
    if (vm->GetEnv((void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }
    //注册方法
    if (registerNativeMethod(env) != JNI_OK) {
        return JNI_ERR;
    }
    return JNI_VERSION_1_6;
}

void JNI_OnUnload(JavaVM *jvm, void *reserved) {
    JNIEnv *env = nullptr;

    if (jvm->GetEnv((void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        return;
    }
    jclass clazz = env->FindClass(DATA_CACHE_UTILS_JAVA);
    env->UnregisterNatives(clazz);
}

void initCache(JNIEnv *env, jobject obj, jint fps, jint width, jint height) {
    init(fps, width, height);
}

void addFrameData(JNIEnv *env, jobject obj, jlong timeSptamp, jboolean bKeyFrame, jbyteArray buf,
                  jint nLen) {
    jbyte *frameBuffer = env->GetByteArrayElements(buf, 0);

    addFrame(timeSptamp, bKeyFrame, (unsigned char *) frameBuffer, nLen);

    env->ReleaseByteArrayElements(buf, frameBuffer, 0);

    throw_java_exception(env, "Add frame Exception");
}

jint getFirstFrameData(JNIEnv *env, jobject obj, jlong timeSptamp_, jlongArray nextTimestamp_,
                       jbyteArray buf_, jintArray nLen_) {
    jlong *nextTimestamp = env->GetLongArrayElements(nextTimestamp_, 0);
    jbyte *frameBuffer = env->GetByteArrayElements(buf_, 0);
    jint *nLen = env->GetIntArrayElements(nLen_, 0);

    int64 cNextTimestamp;
    unsigned char *frameAddress;
    int cLen;
    jint res = getFirstFrame(timeSptamp_, cNextTimestamp, frameAddress, cLen);
    if (res != 0) {
        LOGE("getFirstFrame res failed %d", res);
        return res;
    }
    LOGI("getFirstFrame find success: cNextTimestamp -> %lld , size -> %d", cNextTimestamp, cLen);
    memcpy(frameBuffer, frameAddress, cLen);
    nextTimestamp[0] = cNextTimestamp;
    nLen[0] = cLen;

    env->ReleaseLongArrayElements(nextTimestamp_, nextTimestamp, 0);
    env->ReleaseByteArrayElements(buf_, frameBuffer, 0);
    env->ReleaseIntArrayElements(nLen_, nLen, 0);

    throw_java_exception(env, "get first frame Exception");
    return res;
}

jint getNextFrameData(JNIEnv *env, jobject obj, jlong curTimestamp_, jlongArray nextTimestamp_,
                      jbyteArray buf_, jintArray nLen_) {
    jlong *nextTimestamp = env->GetLongArrayElements(nextTimestamp_, 0);
    jbyte *frameBuffer = env->GetByteArrayElements(buf_, 0);
    jint *nLen = env->GetIntArrayElements(nLen_, 0);

    int64 cNextTimestamp;
    unsigned char *frameAddress;
    int cLen;
    jint res = getNextFrame(curTimestamp_, cNextTimestamp, frameAddress, cLen);

    if (2 * 1024 * 1024 < cLen) {
        LOGI("alloc size < src length ");
    }

    memcpy(frameBuffer, frameAddress, cLen);
    nextTimestamp[0] = cNextTimestamp;
    nLen[0] = cLen;

    env->ReleaseLongArrayElements(nextTimestamp_, nextTimestamp, 0);
    env->ReleaseByteArrayElements(buf_, frameBuffer, 0);
    env->ReleaseIntArrayElements(nLen_, nLen, 0);

    throw_java_exception(env, "get next frame Exception");
    return res;
}

void throw_java_exception(JNIEnv *env, const char *msg) {
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        jclass cException = env->FindClass("java/lang/Exception");
        env->ThrowNew(cException, msg);
    }
}