package com.lkl.commonlib.util

import java.io.UnsupportedEncodingException
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

/**
 * md5数据加密
 *
 * @author likunlun
 * @since 2022/01/02
 */
object MD5Utils {
    private const val TAG = "MD5Util"

    /**
     * 获取MD5值
     *
     * @param s 待加密数据
     * @return 加密后数据
     */
    @JvmStatic
    fun MD5(s: String): String {
        try {
            // Create MD5 Hash
            val digest = MessageDigest.getInstance("MD5")

            digest.update(s.toByteArray(charset("UTF-8")))

            val messageDigest = digest.digest()

            // Create Hex String
            val hexString = StringBuffer()
            messageDigest.forEach {
                var h = Integer.toHexString((0xFF and it.toInt()))
                while (h.length < 2)
                    h = "0$h"
                hexString.append(h)
            }
            return hexString.toString()
        } catch (e: NoSuchAlgorithmException) {
            LogUtils.e(TAG, "error:${e.message}")
        } catch (e: UnsupportedEncodingException) {
            LogUtils.e(TAG, "error:${e.message}")
        }

        return ""
    }
}


