package com.conreo.couchytv.premium

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.conreo.couchytv.MainActivity

/** Best effort only: newer Fire OS can block background activity starts. Prefer Home on Fire. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED && context.getSharedPreferences("startup", 0).getBoolean("enabled", false)) {
            runCatching { context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }
}
