package dev.andes.app

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.os.StatFs

object DeviceDiagnostics {
    fun describe(context: Context): String {
        val manager = context.getSystemService(ActivityManager::class.java)
        val memory = ActivityManager.MemoryInfo().also { manager.getMemoryInfo(it) }
        val power = context.getSystemService(PowerManager::class.java)
        val storage = StatFs(context.filesDir.path)
        fun mib(value: Long) = value / (1024 * 1024)
        return "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n" +
            "ABI: ${Build.SUPPORTED_ABIS.joinToString()} • ${Runtime.getRuntime().availableProcessors()} CPU cores\n" +
            "App memory class: ${manager.memoryClass} MiB\n" +
            "Available RAM: ${mib(memory.availMem)} MiB • Storage: ${mib(storage.availableBytes)} MiB\n" +
            "Vulkan feature advertised: ${context.packageManager.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL)}\n" +
            "Battery saver: ${power.isPowerSaveMode} • Thermal status: ${power.currentThermalStatus}\n" +
            "Vulkan rendering and game execution are not implemented. Diagnostics stay on this device."
    }
}
