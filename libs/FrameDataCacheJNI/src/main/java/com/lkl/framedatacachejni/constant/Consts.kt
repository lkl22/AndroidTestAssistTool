package com.lkl.framedatacachejni.constant

object DataCacheCode {
    /**
     * jni接口请求结果 - 成功
     */
    const val RES_SUCCESS = 0
    /**
     * jni接口请求结果 - 失败
     */
    const val RES_FAILED = 1
    /**
     * jni接口请求结果 - 等待，没有更多缓存数据了，需等待新数据
     */
    const val RES_WAITING = 2
}