package com.lkl.commonlib.util

import android.content.Context
import android.widget.Toast
import com.lkl.commonlib.BaseApplication.Companion.context

/**
 * Toast统一管理类
 *
 * @author likunlun
 * @since 2021/12/19
 */
object ToastUtils {
    private var isShow = true
    private var mToast: Toast = Toast.makeText(context, "", Toast.LENGTH_SHORT)

    /**
     * 短时间显示Toast
     *
     * @param context
     * @param message
     */
    @JvmStatic
    fun showShort(context: Context, message: CharSequence) {
        showShort(message)
    }

    @JvmStatic
    fun showShort(message: CharSequence) {
        if (isShow) {
            mToast.setText(message)
            mToast.duration = Toast.LENGTH_SHORT
            mToast.show()
        }
    }

    /**
     * 短时间显示Toast
     *
     * @param context
     * @param message
     */
    @JvmStatic
    fun showShort(context: Context, message: Int) {
        if (isShow) {
            mToast.setText(message)
            mToast.duration = Toast.LENGTH_SHORT
            mToast.show()
        }
    }

    /**
     * 长时间显示Toast
     *
     * @param context
     * @param message
     */
    @JvmStatic
    fun showLong(context: Context, message: CharSequence) {
        showLong(message)
    }

    @JvmStatic
    fun showLong(message: CharSequence) {
        if (isShow) {
            mToast.setText(message)
            mToast.duration = Toast.LENGTH_LONG
            mToast.show()
        }
    }

    /**
     * 长时间显示Toast
     *
     * @param context
     * @param message
     */
    @JvmStatic
    fun showLong(context: Context, message: Int) {
        if (isShow) {
            mToast.setText(message)
            mToast.duration = Toast.LENGTH_LONG
            mToast.show()
        }
    }

    /**
     * 自定义显示Toast时间
     *
     * @param context
     * @param message
     * @param duration
     */
    @JvmStatic
    fun show(context: Context, message: CharSequence, duration: Int) {
        if (isShow) {
            mToast.setText(message)
            mToast.duration = duration
            mToast.show()
        }
    }

    /**
     * 自定义显示Toast时间
     *
     * @param context
     * @param message
     * @param duration
     */
    @JvmStatic
    fun show(context: Context, message: Int, duration: Int) {
        if (isShow) {
            mToast.setText(message)
            mToast.duration = duration
            mToast.show()
        }
    }
}

/**
 * Display the simple Toast message with the duration.
 *
 * @param message the message text.
 * @param duration
 */
fun Context.toast(message: CharSequence, duration: Int) =
    Toast.makeText(this, message, duration).show()

/**
 * Display the simple Toast message with the [Toast.LENGTH_SHORT] duration.
 *
 * @param message the message text resource.
 * @param duration
 */
fun Context.toast(message: Int, duration: Int) = Toast.makeText(this, message, duration).show()