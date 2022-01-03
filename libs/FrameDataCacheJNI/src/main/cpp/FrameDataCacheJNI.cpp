#include <cstring>
#include "FrameDataCacheJNI.h"

/**
 * 动态注册
 */
JNINativeMethod methods[] = {
        {"initCache",         "(IZ)V",        (void *) initCache},
        {"addFrameData",      "(JZ[BI)V",     (void *) addFrameData},
        {"getFirstFrameData", "(J[J[B[I)I",   (jint *) getFirstFrameData},
        {"getNextFrameData",  "(J[J[B[I[Z)I", (jint *) getNextFrameData}
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

void initCache(JNIEnv *env, jobject obj, jint cacheSize, jboolean isDebug) {
    init(cacheSize, isDebug);
}

void addFrameData(JNIEnv *env, jobject obj, jlong timeSptamp, jboolean bKeyFrame, jbyteArray buf,
                  jint len) {
    jbyte *frameBuffer = env->GetByteArrayElements(buf, 0);

    addFrame(timeSptamp, bKeyFrame, (unsigned char *) frameBuffer, len);

    env->ReleaseByteArrayElements(buf, frameBuffer, 0);

    throw_java_exception(env, "Add frame Exception");
}

jint getFirstFrameData(JNIEnv *env, jobject obj, jlong timeSptamp_, jlongArray curTimestamp_,
                       jbyteArray buf_, jintArray len_) {
    jlong *curTimestamp = env->GetLongArrayElements(curTimestamp_, 0);
    jbyte *frameBuffer = env->GetByteArrayElements(buf_, 0);
    jint *len = env->GetIntArrayElements(len_, 0);

    int64 cCurTimestamp;
    unsigned char *frameData;
    int cLen;
    jint res = getFirstFrame(timeSptamp_, cCurTimestamp, frameData, cLen);
    if (res != 0) {
        LOGE("getFirstFrame res failed %d", res);
        return res;
    }
    LOGI("getFirstFrame find success: cCurTimestamp -> %lld , size -> %d", cCurTimestamp, cLen);
    memcpy(frameBuffer, frameData, cLen);
    curTimestamp[0] = cCurTimestamp;
    len[0] = cLen;

    env->ReleaseLongArrayElements(curTimestamp_, curTimestamp, 0);
    env->ReleaseByteArrayElements(buf_, frameBuffer, 0);
    env->ReleaseIntArrayElements(len_, len, 0);

    throw_java_exception(env, "get first frame Exception");
    return res;
}

jint getNextFrameData(JNIEnv *env, jobject obj, jlong preTimestamp_, jlongArray curTimestamp_,
                      jbyteArray buf_, jintArray len_, jbooleanArray isKeyFrame_) {
    jlong *curTimestamp = env->GetLongArrayElements(curTimestamp_, 0);
    jbyte *frameBuffer = env->GetByteArrayElements(buf_, 0);
    jint *len = env->GetIntArrayElements(len_, 0);
    jboolean *isKeyFrame = env->GetBooleanArrayElements(isKeyFrame_, 0);

    int64 cCurTimestamp;
    unsigned char *frameData;
    int cLen;
    bool cIsKeyFrame;
    jint res = getNextFrame(preTimestamp_, cCurTimestamp, frameData, cLen, cIsKeyFrame);

    memcpy(frameBuffer, frameData, cLen);
    curTimestamp[0] = cCurTimestamp;
    len[0] = cLen;
    isKeyFrame[0] = cIsKeyFrame;

    env->ReleaseLongArrayElements(curTimestamp_, curTimestamp, 0);
    env->ReleaseByteArrayElements(buf_, frameBuffer, 0);
    env->ReleaseIntArrayElements(len_, len, 0);
    env->ReleaseBooleanArrayElements(isKeyFrame_, isKeyFrame, 0);

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