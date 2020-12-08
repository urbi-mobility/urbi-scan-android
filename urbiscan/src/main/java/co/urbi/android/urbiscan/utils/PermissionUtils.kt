package co.urbi.android.urbiscan.utils

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.*

class PermissionUtils {
    companion object {
        const val PERMISSION_REQUESTS = 1

        fun check(activity: Activity): Boolean {
            if (!allPermissionsGranted(activity)) {
                getRuntimePermissions(activity)
                return false
            }
            return true
        }

        fun getRequiredPermissions(activity: Activity): Array<String?> {
            return try {
                val info = activity.packageManager
                    .getPackageInfo(activity.packageName, PackageManager.GET_PERMISSIONS)
                val ps = info.requestedPermissions
                if (ps != null && ps.isNotEmpty()) {
                    ps
                } else {
                    arrayOfNulls(0)
                }
            } catch (e: Exception) {
                arrayOfNulls(0)
            }
        }

        fun allPermissionsGranted(activity: Activity, permissions: Array<String>? = null): Boolean {
            for (permission in permissions ?: getRequiredPermissions(activity)) {
                permission?.let {
                    if (!isPermissionGranted(activity, it)) {
                        return false
                    }
                }
            }
            return true
        }

        fun getRuntimePermissions(activity: Activity, permissions: Array<String>? = null) {
            val allNeededPermissions = ArrayList<String>()
            for (permission in permissions ?: getRequiredPermissions(activity)) {
                permission?.let {
                    if (!isPermissionGranted(activity, it)) {
                        allNeededPermissions.add(permission)
                    }
                }
            }

            if (allNeededPermissions.isNotEmpty()) {
                ActivityCompat.requestPermissions(
                    activity,
                    allNeededPermissions.toTypedArray(),
                    PERMISSION_REQUESTS
                )
            }
        }

        fun isPermissionGranted(context: Context, permission: String): Boolean {
            if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
                return true
            }
            return false
        }
    }
}
