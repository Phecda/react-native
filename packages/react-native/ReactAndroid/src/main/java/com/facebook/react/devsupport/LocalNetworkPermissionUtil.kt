/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.react.devsupport

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import com.facebook.react.modules.core.PermissionAwareActivity
import com.facebook.react.modules.systeminfo.AndroidInfoHelpers
import com.facebook.react.util.AndroidVersion

/**
 * Debug-only helper to request the runtime `ACCESS_LOCAL_NETWORK` permission needed to reach Metro
 * on Android 17 (SDK 37) devices, which gate local-network addresses (the emulator's `10.0.2.2`
 * alias, a device's Wi-Fi/LAN IP) for any app that declares the permission, regardless of targetSdk.
 * Loopback via `adb reverse` (`localhost`) is exempt.
 */
internal object LocalNetworkPermissionUtil {
  private const val PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"
  private const val PERMISSION_REQUEST_CODE = 1

  /**
   * Requests the permission when needed and invokes [onResolved] once the user answers, returning
   * `true` so the caller can defer startup; returns `false` when no request is needed (proceed
   * immediately). Resolving before the first connection avoids racing the bundle load into a fatal
   * "Unable to load script" crash.
   */
  @JvmStatic
  fun requestLocalNetworkAccessIfNeeded(activity: Activity, onResolved: Runnable): Boolean {
    if (!AndroidVersion.isAtLeastSdk37()) return false // enforced by the device, not app targetSdk
    if (!isPermissionDeclared(activity)) return false // debug manifest only
    if (activity.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED) return false
    if (isExemptDevServerHost(activity)) return false // loopback needs no permission
    if (activity !is PermissionAwareActivity) return false // can't await the result

    activity.requestPermissions(arrayOf(PERMISSION), PERMISSION_REQUEST_CODE) { _, _, _ ->
      onResolved.run()
      true
    }
    return true
  }

  /**
   * True for loopback dev servers (`localhost` / `127.x` / `::1`, e.g. USB + `adb reverse`), which
   * are exempt. The emulator's `10.0.2.2` is not loopback here and does need the permission.
   */
  private fun isExemptDevServerHost(context: Context): Boolean {
    val host = AndroidInfoHelpers.getServerHost(context).substringBeforeLast(':').trim('[', ']')
    return host == "localhost" || host == "::1" || host.startsWith("127.")
  }

  private fun isPermissionDeclared(activity: Activity): Boolean =
      try {
        activity.packageManager
            .getPackageInfo(activity.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions
            ?.contains(PERMISSION) == true
      } catch (_: PackageManager.NameNotFoundException) {
        false
      }
}
