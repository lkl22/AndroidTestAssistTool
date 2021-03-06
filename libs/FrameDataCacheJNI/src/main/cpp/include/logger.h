#ifndef LOGGER_H
#define LOGGER_H

#ifdef ANDROID

#include <android/log.h>

#define LOG_TAG    "FrameDataCache"
#define LOGD(format, ...)  __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, format, ##__VA_ARGS__)
#define LOGE(format, ...)  __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, format, ##__VA_ARGS__)
#define LOGI(format, ...)  __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, format, ##__VA_ARGS__)
#else
#define LOGE(format, ...)  printf(LOG_TAG format "\n", ##__VA_ARGS__)
#define LOGI(format, ...)  printf(LOG_TAG format "\n", ##__VA_ARGS__)
#endif

#endif //LOGGER_H
