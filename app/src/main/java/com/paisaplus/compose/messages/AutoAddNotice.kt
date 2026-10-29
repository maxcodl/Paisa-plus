package com.paisaplus.compose.messages

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.paisaplus.compose.MainActivity
import com.paisaplus.compose.R
import com.paisaplus.compose.data.Money
import com.paisaplus.compose.data.TransactionType
import com.paisaplus.compose.str

/** "Added ₹250 · Swiggy" after an alert was posted without review, so nothing is added silently. */
object AutoAddNotice {
    const val CHANNEL = "transactions"

    fun show(ctx: Context, name: String, amount: Double, type: TransactionType, account: String) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, str(R.string.auto_added_transactions), NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE)
        val title = str(if (type == TransactionType.INCOME) R.string.auto_added_income_x else R.string.auto_added_expense_x, Money.format(amount), name)
        nm.notify((System.nanoTime() % Int.MAX_VALUE).toInt(), NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_paisa).setContentTitle(title).setContentText(account)
            .setContentIntent(open).setAutoCancel(true).build())
    }
}
