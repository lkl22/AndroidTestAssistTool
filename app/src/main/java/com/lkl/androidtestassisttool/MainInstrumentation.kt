package com.lkl.androidtestassisttool

import android.app.Instrumentation
import android.os.Bundle
import android.util.Log

class MainInstrumentation : Instrumentation() {
    companion object {
        const val TAG = "MainInstrumentation"
    }

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        arguments?.let {
            var params: String = it.getString("params", "")
            Log.e(TAG, params)
        }
        var resultBundle: Bundle = Bundle()
        resultBundle.putString("msg", "success.")
        finish(0, resultBundle)
    }
}