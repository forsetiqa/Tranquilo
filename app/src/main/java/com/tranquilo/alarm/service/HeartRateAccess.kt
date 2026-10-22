package com.tranquilo.alarm.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Доступ к пульсу. Датчик включается ночью по будильнику, то есть в фоне,
 * поэтому на Android 13+ нужно ещё и разрешение «в фоне».
 */
object HeartRateAccess {

    const val BACKGROUND = "android.permission.BODY_SENSORS_BACKGROUND"

    fun foreground(context: Context) = granted(context, Manifest.permission.BODY_SENSORS)

    fun background(context: Context) =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || granted(context, BACKGROUND)

    fun full(context: Context) = foreground(context) && background(context)

    private fun granted(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
