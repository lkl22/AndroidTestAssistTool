package com.lkl.commonlib.util

import android.os.Debug
import com.google.gson.Gson
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

/**
 * Json数据解析工具类
 *
 * @author likunlun
 * @since 2022/01/02
 */
object JsonUtils {
    private const val TAG = "JsonUtils"

    /**
     * json字符串通过Gson框架生成对象
     *
     * @param jsonData 原始json数据
     * @param clazz 要解析成的数据实体类
     * @return 解析后的实体对象
     */
    @JvmStatic
    fun <T> parseJson2Obj(jsonData: String?, clazz: Class<T>): T? {
        try {
            if (null == jsonData) {
                return null
            }
            val gson = Gson()
            return gson.fromJson(jsonData, clazz)
        } catch (e: Exception) {
            LogUtils.e(TAG, e.message)
            return null
        }
    }

    /**
     * json字符串通过Gson框架生成对象
     *
     * @param jsonData 原始json数据
     * @param clazz 要解析成的数据实体类
     * @return 解析后的实体对象
     */
    @JvmStatic
    fun <T> parseJson2List(jsonData: String?, clazz: Class<T>): ArrayList<T>? {
        try {
            if (null == jsonData) {
                return null
            }
            val gson = Gson()
            val type = type(ArrayList::class.java, clazz)
            return gson.fromJson<ArrayList<T>>(jsonData, type)
        } catch (e: Exception) {
            LogUtils.e(TAG, e.message)
            return null
        }
    }

    /**
     * 将java对象转换成json字符串
     *
     * @param obj java对象
     * @return json字符串
     */
    @JvmStatic
    fun parseObj2Json(obj: Any?): String {
        var objstr = ""
        if (null == obj) {
            return ""
        }
        try {
            val gson = Gson()
            objstr = gson.toJson(obj)
            if (Debug.isDebuggerConnected()) {
                LogUtils.i("parseObj2Json", objstr)
            }
            return objstr
        } catch (e: Exception) {
            LogUtils.e(TAG, e.message)
            return ""
        }
    }

    internal fun type(raw: Class<*>, vararg args: Type): ParameterizedType {
        return object : ParameterizedType {
            override fun getRawType(): Type {
                return raw
            }

            override fun getActualTypeArguments(): Array<out Type> {
                return args
            }

            override fun getOwnerType(): Type? {
                return null
            }
        }
    }
}
