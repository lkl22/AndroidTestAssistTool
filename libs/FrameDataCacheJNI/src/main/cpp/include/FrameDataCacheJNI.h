#include <jni.h>

#ifndef FRAME_DATA_CACHE_LIB_H
#define FRAME_DATA_CACHE_LIB_H
#ifdef __cplusplus
extern "C" {
#endif

#include "logger.h"
#include "FrameDataCache.h"

#define DATA_CACHE_UTILS_JAVA "com/lkl/framedatacachejni/FrameDataCacheUtils"

JNIEXPORT void JNICALL
initCache(JNIEnv *, jobject, jint, jboolean);

JNIEXPORT void JNICALL
addFrameData(JNIEnv *, jobject, jlong, jboolean, jbyteArray, jint);

JNIEXPORT jint
JNICALL getFirstFrameData(JNIEnv *, jobject, jlong, jlongArray, jbyteArray, jintArray);

JNIEXPORT jint
JNICALL getNextFrameData(JNIEnv *, jobject, jlong, jlongArray, jbyteArray, jintArray, jbooleanArray);

#ifdef __cplusplus
}
#endif

void throw_java_exception(JNIEnv *env, const char *msg);

#endif