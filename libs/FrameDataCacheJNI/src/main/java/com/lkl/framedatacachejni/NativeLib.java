package com.lkl.framedatacachejni;

public class NativeLib {

    // Used to load the 'framedatacachejni' library on application startup.
    static {
        System.loadLibrary("framedatacachejni");
    }

    /**
     * A native method that is implemented by the 'framedatacachejni' native library,
     * which is packaged with this application.
     */
    public native String stringFromJNI();
}