package com.lkl.commonlib.util

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import com.lkl.commonlib.BaseApplication
import com.lkl.commonlib.util.LogUtils.i

/**
 * Display工具类
 */
object DisplayUtils {
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    fun getDisplayMetrics(context: Context=BaseApplication.context): DisplayMetrics {
        val dm = context.resources.displayMetrics
        @SuppressLint("WrongConstant") val windowManager =
            context.getSystemService("window") as WindowManager
        windowManager.defaultDisplay.getRealMetrics(dm)
        return dm
    }

    fun printDisplayInfo(context: Context): DisplayMetrics {
        val dm = getDisplayMetrics(context)
        val sb = StringBuilder()
        sb.append("display info:  ")
        sb.append("\ndensity         :").append(dm.density)
        sb.append("\ndensityDpi      :").append(dm.densityDpi)
        sb.append("\nheightPixels    :").append(dm.heightPixels)
        sb.append("\nwidthPixels     :").append(dm.widthPixels)
        sb.append("\nscaledDensity   :").append(dm.scaledDensity)
        sb.append("\nxdpi            :").append(dm.xdpi)
        sb.append("\nydpi            :").append(dm.ydpi)
        i("ContentValues", sb.toString())
        return dm
    }

    fun dip2px(dpValue: Float, c: Context=BaseApplication.context): Int {
        val scale = getDisplayMetrics(c).density
        return (dpValue * scale + 0.5f).toInt()
    }

    fun px2dip(pxValue: Float, c: Context=BaseApplication.context): Int {
        val scale = getDisplayMetrics(c).density
        return (pxValue / scale + 0.5f).toInt()
    }

    fun px2sp(pxValue: Float, c: Context=BaseApplication.context): Int {
        val fontScale = getDisplayMetrics(c).scaledDensity
        return (pxValue / fontScale + 0.5f).toInt()
    }

    fun sp2px(spValue: Float, c: Context=BaseApplication.context): Int {
        val fontScale = getDisplayMetrics(c).scaledDensity
        return (spValue * fontScale + 0.5f).toInt()
    }

    fun getScreenWidthPixels(c: Context=BaseApplication.context): Int {
        return getDisplayMetrics(c).widthPixels
    }

    fun getScreenHeightPixels(c: Context=BaseApplication.context): Int {
        return getDisplayMetrics(c).heightPixels
    }

    fun getDensityDpi(c: Context=BaseApplication.context): Int {
        return getDisplayMetrics(c).densityDpi
    }

    @TargetApi(17)
    fun getScreenRealH(c: Context=BaseApplication.context): Int {
        @SuppressLint("WrongConstant") val winMgr =
            c.getSystemService("window") as WindowManager
        val display = winMgr.defaultDisplay
        val dm = DisplayMetrics()
        val h: Int
        h = if (Build.VERSION.SDK_INT >= 17) {
            display.getRealMetrics(dm)
            dm.heightPixels
        } else {
            try {
                val method = Class.forName("android.view.Display")
                    .getMethod("getRealMetrics", DisplayMetrics::class.java)
                method.invoke(display, dm)
                dm.heightPixels
            } catch (var6: Exception) {
                display.getMetrics(dm)
                dm.heightPixels
            }
        }
        return h
    }

    fun getStatusBarHeight(context: Context): Int {
        var statusBarHeight = 0
        try {
            val c = Class.forName("com.android.internal.R\$dimen")
            val obj = c.newInstance()
            val field = c.getField("status_bar_height")
            val x = field[obj].toString().toInt()
            statusBarHeight = context.resources.getDimensionPixelSize(x)
        } catch (var6: Exception) {
            var6.printStackTrace()
        }
        return statusBarHeight
    }

    fun getNavigationBarrHeight(c: Context): Int {
        val resources = c.resources
        val identifier = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return resources.getDimensionPixelOffset(identifier)
    }
}