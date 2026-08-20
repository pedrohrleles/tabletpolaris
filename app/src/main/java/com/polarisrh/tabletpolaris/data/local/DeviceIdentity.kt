package com.polarisrh.tabletpolaris.data.local

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings

/** Small shared helper — avoids reading ANDROID_ID with slightly different code in each caller. */
object DeviceIdentity {
    @SuppressLint("HardwareIds")
    fun androidId(context: Context): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "desconhecido"
}
