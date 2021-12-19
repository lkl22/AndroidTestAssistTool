package com.lkl.commonlib

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import com.lkl.commonlib.util.CrashHandler
import com.lkl.commonlib.util.LogUtils
import kotlin.properties.Delegates

/**
 * 创建者     likunlun
 * 创建时间   2018/11/5 17:16
 * 描述	      desc
 */
open class BaseApplication : Application() {
    companion object {
        const val TAG = "BaseApplication"

        @JvmStatic
        var context: Context by Delegates.notNull()
            private set

        @JvmStatic
        var instance: BaseApplication by Delegates.notNull()
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // context初始化之前不能打印log
        context = applicationContext

        //初始化异常处理类
        CrashHandler.instance.init(context)

        registerActivityLifecycleCallbacks(mActivityLifecycleCallbacks)
    }

    private val mActivityLifecycleCallbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            LogUtils.d(TAG, "onCreated: " + activity.componentName.className)
        }

        override fun onActivityStarted(activity: Activity) {
            LogUtils.d(TAG, "onStart: " + activity.componentName.className)
        }

        override fun onActivityResumed(activity: Activity) {

        }

        override fun onActivityPaused(activity: Activity) {

        }

        override fun onActivityStopped(activity: Activity) {

        }

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {

        }

        override fun onActivityDestroyed(activity: Activity) {
            LogUtils.d(TAG, "onDestroy: " + activity.componentName.className)
        }
    }
}
