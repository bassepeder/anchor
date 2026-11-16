package app.anchor.protection

import android.app.admin.DeviceAdminReceiver

class AdminReceiver : DeviceAdminReceiver()

const val DEVICE_OWNER_COMMAND = "adb shell dpm set-device-owner app.anchor/.protection.AdminReceiver"
