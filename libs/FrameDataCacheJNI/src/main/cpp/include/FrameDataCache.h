#ifndef DATA_CACHE_H
#define DATA_CACHE_H

typedef long long int64;

#include "logger.h"

#ifdef __cplusplus
extern "C" {
#endif

/**
 * 初始化缓存大小
 *
 * @param cacheSize 缓存空间大小，单位 M
 * @param isDebug 是否debug模式
 */
void init(int cacheSize, bool isDebug);

/**
 * 资源释放
 */
void UnInit();

/**
 * 添加帧数据
 *
 * @param timestamp 时间戳
 * @param bKeyFrame 是否关键帧
 * @param puf
 * @param nLen 长度
 */
void addFrame(int64 timestamp, bool isKeyFrame, unsigned char *puf, int nLen);

//遍历帧数据
/**
 * 获取第一帧数据
 *
 * @param timestamp 时间戳
 * @param curTimestamp 当前帧时间戳
 * @param data 帧数据
 * @param nLen 数据长度
 * @return 查找状态0:找到 1:无效 2:等待
 */
int getFirstFrame(int64 timestamp, int64 &curTimestamp, unsigned char *&data, int &nLen);

/**
 * 返回当前curTimestamp的下一帧数据和index
 *
 * @param preTimestamp 前一帧时间戳
 * @param curTimestamp 当前帧时间戳
 * @param data 帧数据
 * @param len 数据长度
 * @param isKeyFrame 是否关键帧（I帧）
 * @return 返回查找状态0:找到 1:无效 2:等待
 */
int getNextFrame(int64 preTimestamp, int64 &curTimestamp, unsigned char *&data, int &len, bool &isKeyFrame);

#ifdef __cplusplus
}
#endif
#endif
