package com.bearphone.app.call

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telecom.TelecomManager
import android.widget.Toast
import androidx.core.content.ContextCompat

/** Исходящие звонки и роль "Звонилка по умолчанию". */
object Dialer {

    @SuppressLint("MissingPermission")
    /**
     * @param sim конкретная SIM; null — пусть решает система (если у неё есть SIM по умолчанию,
     *            иначе Telecom спросит — экран звонка покажет выбор SIM)
     */
    fun call(ctx: Context, number: String, sim: SimAccount? = null) {
        val clean = number.trim()
        if (clean.isEmpty()) return
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(ctx, "Нет разрешения на звонки", Toast.LENGTH_SHORT).show()
            return
        }
        val tm = ctx.getSystemService(TelecomManager::class.java)
        // '#' в tel: нужно кодировать, иначе USSD вида *100# обрежется
        val uri = Uri.fromParts("tel", clean, null)
        try {
            val extras = android.os.Bundle()
            sim?.let { extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, it.handle) }
            tm.placeCall(uri, extras)
        } catch (e: SecurityException) {
            Toast.makeText(ctx, "Не удалось позвонить: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun isDefault(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ctx.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_DIALER)
        } else {
            ctx.getSystemService(TelecomManager::class.java).defaultDialerPackage == ctx.packageName
        }

    /** Intent системного диалога "Сделать BearPhone звонилкой по умолчанию?". */
    fun requestDefaultIntent(activity: Activity): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            activity.getSystemService(RoleManager::class.java)
                .createRequestRoleIntent(RoleManager.ROLE_DIALER)
        } else {
            @Suppress("DEPRECATION")
            Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
                .putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, activity.packageName)
        }
}
