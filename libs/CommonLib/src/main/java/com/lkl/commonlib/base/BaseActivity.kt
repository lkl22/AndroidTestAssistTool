package com.lkl.commonlib.base

import androidx.appcompat.app.AppCompatActivity
import com.lkl.commonlib.util.LogUtils
import pub.devrel.easypermissions.EasyPermissions
import pub.devrel.easypermissions.EasyPermissions.PermissionCallbacks
import pub.devrel.easypermissions.EasyPermissions.RationaleCallbacks

abstract class BaseActivity : AppCompatActivity(), PermissionCallbacks, RationaleCallbacks {
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // Forward results to EasyPermissions
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
    }

    fun hasPermissions(vararg perms: String?): Boolean {
        return EasyPermissions.hasPermissions(this, *perms)
    }

    override fun onPermissionsGranted(requestCode: Int, perms: List<String>) {
        LogUtils.d(TAG, "onPermissionsGranted:" + requestCode + ":" + perms.size)
    }

    override fun onPermissionsDenied(requestCode: Int, perms: List<String>) {
        LogUtils.d(TAG, "onPermissionsDenied:" + requestCode + ":" + perms.size)
    }

    override fun onRationaleAccepted(requestCode: Int) {
        LogUtils.d(TAG, "onRationaleAccepted:$requestCode")
    }

    override fun onRationaleDenied(requestCode: Int) {
        LogUtils.d(TAG, "onRationaleDenied:$requestCode")
    }

    companion object {
        private const val TAG = "BaseActivity"
    }
}