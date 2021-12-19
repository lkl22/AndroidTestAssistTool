package com.lkl.yuvjni;

public class NativeLib {

    // Used to load the 'yuvjni' library on application startup.
    static {
        System.loadLibrary("yuvjni");
    }

    /**
     * A native method that is implemented by the 'yuvjni' native library,
     * which is packaged with this application.
     */
    public native String stringFromJNI();
}