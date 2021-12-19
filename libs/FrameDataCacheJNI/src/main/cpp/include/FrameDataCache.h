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
 * @param fps 帧率
 * @param width 宽度
 * @param height 高度
 * @param code 编码格式 0:H264 1:H265
 */
void init(int fps, int width, int height, int code);

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
 * @param nextTimestamp 下一帧时间戳
 * @param data 帧数据
 * @param nLen 数据长度
 * @return 查找状态0:找到 1:无效 2:等待
 */
int getFirstFrame(int64 timestamp, int64 &nextTimestamp, unsigned char *&data, int &nLen);

/**
 * 返回当前curTimestamp的下一帧数据和index
 *
 * @param curTimestamp 当前帧时间戳
 * @param nextTimestamp 下一帧时间戳
 * @param data 帧数据
 * @param nLen 数据长度
 * @return 返回查找状态0:找到 1:无效 2:等待
 */
int getNextFrame(int64 curTimestamp, int64 &nextTimestamp, unsigned char *&data, int &nLen);

#ifdef __cplusplus
}
#endif
#endif
