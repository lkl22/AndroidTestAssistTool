package com.lkl.commonlib.util

import android.content.Intent

import java.io.Serializable

/**
 * Intent的包装类
 *
 * @author likunlun
 * @since 2022/12/02
 */
class SafeIntent(intent: Intent) : Intent(intent) {
    companion object {
        private const val TAG = "SafeIntent"
    }

    /**
     * 获取 boolean extra
     *
     * @param key extra key
     * @param defaultValue default value
     * @return boolean extra value
     */
    override fun getBooleanExtra(key: String, defaultValue: Boolean): Boolean {
        return try {
            super.getBooleanExtra(key, defaultValue)
        } catch (e: Exception) {
            LogUtils.e(TAG, "Error:" + e.message)
            defaultValue
        }
    }

    /**
     * 获取 string extra
     *
     * @param key extra key
     * @return string extra value
     */
    override fun getStringExtra(key: String): String? {
        return try {
            super.getStringExtra(key)
        } catch (e: Exception) {
            LogUtils.e(TAG, "Error:" + e.message)
            null
        }
    }

    /**
     * 获取 Serializable extra
     *
     * @param key extra key
     * @return Serializable extra value
     */
    override fun getSerializableExtra(key: String): Serializable? {
        return try {
            super.getSerializableExtra(key)
        } catch (e: Exception) {
            LogUtils.e(TAG, "Error:" + e.message)
            null
        }
    }
}
