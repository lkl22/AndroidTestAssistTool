package com.lkl.commonlib.util

import android.os.Handler
import android.os.Looper
import android.os.Message

import java.lang.ref.WeakReference
import java.lang.reflect.Modifier
import kotlin.properties.Delegates

/**
 * 封装的弱引用的 Handler
 *
 * @author likunlun
 * @since 2017/6/1
 */
abstract class WeakReferenceHandler<T> : Handler {
    constructor(reference: T) : super() {
        initReference(reference)
    }

    constructor(reference: T, looper: Looper) : super(looper) {
        initReference(reference)
    }

    private var mReference: WeakReference<T> by Delegates.notNull()

    fun initReference(reference: T) {
        val clazz = javaClass
        if (Modifier.STATIC and clazz.modifiers == 0) {
            //子类未声明为static抛出异常信息
            throw RuntimeException("Subclass must be static...")
        }
        mReference = WeakReference(reference)
    }

    override fun handleMessage(msg: Message) {
        if (null == mReference.get()) {
            return
        }
        handleMessage(mReference.get() as T, msg)
    }

    protected abstract fun handleMessage(reference: T, msg: Message)

    fun invalidateHandler() {
        mReference.clear()
    }
}
