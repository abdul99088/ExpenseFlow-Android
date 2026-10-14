
package com.example.myapplication

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import data.ExpenseDatabase
import java.time.YearMonth
import java.util.Locale
import kotlinx.coroutines.flow.first

class BudgetAlertWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val TAG = "ExpenseFlowBudget"
        const val CHANNEL_ID = "expenseflow_budget_alerts"

        private const val PREFS_NAME = "expenseflow_budget_alert_status"
        private const val ALERT_80 = 80
        private const val ALERT_100 = 100
    }

    override suspend fun doWork(): Result {
        Log.i(
            TAG,
            "START: Budget check. workerId=$id, attempt=${runAttemptCount + 1}"
        )

        return try {
            // STAGE 1: Read the configured budget.
            val budget = BudgetPreferences.getMonthlyBudget(applicationContext)

            Log.i(TAG, "STAGE 1 OK: Monthly budget loaded: PKR $budget")

            if (!budget.isFinite() || budget <= 0.0) {
                Log.w(
                    TAG,
                    "SKIP: Budget is zero, negative, or invalid. " +
                            "Set a valid monthly budget before testing alerts."
                )
                return Result.success()
            }

            // STAGE 2: Identify the current month.
            val currentMonth = YearMonth.now().toString()
            Log.i(TAG, "STAGE 2 OK: Checking month=$currentMonth")

            // STAGE 3: Read the month's expenses from Room.
            val database = ExpenseDatabase.getDatabase(applicationContext)

            Log.d(TAG, "STAGE 3: Reading monthly total from Room")

            val monthlyTotal = database.expenseDao()
                .getMonthlyTotal(currentMonth)
                .first()

            if (!monthlyTotal.isFinite() || monthlyTotal < 0.0) {
                Log.e(
                    TAG,
                    "STAGE 3 ERROR: Invalid monthly total from Room: $monthlyTotal"
                )
                return Result.failure()
            }

            val percentage = (monthlyTotal / budget) * 100.0

            Log.i(
                TAG,
                "STAGE 3 OK: month=$currentMonth, " +
                        "spent=PKR ${formatAmount(monthlyTotal)}, " +
                        "budget=PKR ${formatAmount(budget)}, " +
                        "used=${String.format(Locale.US, "%.2f", percentage)}%"
            )

            // STAGE 4: Read alert history.
            val preferences = applicationContext.getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )

            val sent80 = wasAlertSent(currentMonth, ALERT_80, preferences)
            val sent100 = wasAlertSent(currentMonth, ALERT_100, preferences)

            Log.d(
                TAG,
                "STAGE 4 OK: Alert history: 80% sent=$sent80, 100% sent=$sent100"
            )

            // STAGE 5: Determine which threshold has been crossed.
            when {
                monthlyTotal >= budget -> {
                    Log.i(TAG, "STAGE 5: Spending reached or exceeded 100%")

                    if (sent100) {
                        Log.i(TAG, "SKIP: 100% alert already recorded for $currentMonth")

                        // Avoid a later duplicate 80% alert if spending decreases.
                        if (!sent80 && !markAlertsSent(
                                currentMonth,
                                listOf(ALERT_80),
                                preferences
                            )
                        ) {
                            Log.e(TAG, "ERROR: Could not save 80% alert history")
                            return Result.retry()
                        }

                        return Result.success()
                    }

                    val posted = showNotification(
                        notificationId = ALERT_100,
                        title = "Monthly budget reached!",
                        message = "You've spent PKR ${formatAmount(monthlyTotal)} " +
                                "of your PKR ${formatAmount(budget)} budget."
                    )

                    if (!posted) {
                        Log.e(
                            TAG,
                            "STAGE 5 ERROR: 100% notification was not posted. " +
                                    "Alert remains unrecorded so a later check can retry."
                        )
                        return Result.retry()
                    }

                    if (!markAlertsSent(
                            currentMonth,
                            listOf(ALERT_80, ALERT_100),
                            preferences
                        )
                    ) {
                        Log.e(
                            TAG,
                            "ERROR: Notification posted, but alert history could not " +
                                    "be saved. A retry may post the same notification again."
                        )
                        return Result.retry()
                    }

                    Log.i(TAG, "SUCCESS: 100% alert posted and history saved")
                }

                monthlyTotal >= budget * 0.80 -> {
                    Log.i(TAG, "STAGE 5: Spending reached 80%, but not 100%")

                    if (sent80) {
                        Log.i(TAG, "SKIP: 80% alert already recorded for $currentMonth")
                        return Result.success()
                    }

                    val posted = showNotification(
                        notificationId = ALERT_80,
                        title = "Monthly budget warning",
                        message = "You've used ${percentage.toInt()}% of your " +
                                "monthly budget (PKR ${formatAmount(monthlyTotal)} " +
                                "of PKR ${formatAmount(budget)})."
                    )

                    if (!posted) {
                        Log.e(
                            TAG,
                            "STAGE 5 ERROR: 80% notification was not posted. " +
                                    "Alert remains unrecorded."
                        )
                        return Result.retry()
                    }

                    if (!markAlertsSent(
                            currentMonth,
                            listOf(ALERT_80),
                            preferences
                        )
                    ) {
                        Log.e(TAG, "ERROR: Could not save 80% alert history")
                        return Result.retry()
                    }

                    Log.i(TAG, "SUCCESS: 80% alert posted and history saved")
                }

                else -> {
                    Log.i(
                        TAG,
                        "STAGE 5 OK: Spending is below 80%. No alert required."
                    )
                }
            }

            Log.i(TAG, "COMPLETE: Budget check finished successfully")
            Result.success()

        } catch (e: Exception) {
            Log.e(
                TAG,
                "FATAL: Budget check failed. " +
                        "workerId=$id, attempt=${runAttemptCount + 1}",
                e
            )

            Result.retry()
        }
    }

    private fun showNotification(
        notificationId: Int,
        title: String,
        message: String
    ): Boolean {
        return try {
            Log.d(TAG, "NOTIFICATION: Preparing id=$notificationId, title=$title")

            // STAGE 6: Check Android 13+ notification permission.
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.e(
                    TAG,
                    "NOTIFICATION ERROR: POST_NOTIFICATIONS permission is not granted. " +
                            "The worker cannot display the permission prompt."
                )
                return false
            }

            createNotificationChannel()

            // STAGE 7: Check whether app notifications are enabled.
            val notificationManager =
                NotificationManagerCompat.from(applicationContext)

            if (!notificationManager.areNotificationsEnabled()) {
                Log.e(
                    TAG,
                    "NOTIFICATION ERROR: Notifications are disabled for this app."
                )
                return false
            }

            // Check whether this particular channel has been disabled.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = applicationContext.getSystemService(
                    Context.NOTIFICATION_SERVICE
                ) as NotificationManager

                val channel = manager.getNotificationChannel(CHANNEL_ID)

                if (
                    channel == null ||
                    channel.importance == NotificationManager.IMPORTANCE_NONE
                ) {
                    Log.e(
                        TAG,
                        "NOTIFICATION ERROR: Budget notification channel is missing " +
                                "or disabled."
                    )
                    return false
                }
            }

            // STAGE 8: Build and submit the notification.
            val notification = NotificationCompat.Builder(
                applicationContext,
                CHANNEL_ID
            )
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(notificationId, notification)

            Log.i(
                TAG,
                "NOTIFICATION OK: Notification id=$notificationId submitted to Android."
            )

            true

        } catch (e: SecurityException) {
            Log.e(TAG, "NOTIFICATION ERROR: Security permission failure", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "NOTIFICATION ERROR: Failed to post notification", e)
            false
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Log.d(TAG, "CHANNEL: Android version does not require notification channels")
            return
        }

        val manager = applicationContext.getSystemService(
            Context.NOTIFICATION_SERVICE
        ) as NotificationManager

        val existing = manager.getNotificationChannel(CHANNEL_ID)

        if (existing != null) {
            Log.d(
                TAG,
                "CHANNEL OK: Existing channel found, importance=${existing.importance}"
            )
            return
        }

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Monthly Budget Alerts",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description =
                "Warnings when monthly spending approaches or reaches the budget"
        }

        manager.createNotificationChannel(channel)

        Log.i(TAG, "CHANNEL OK: Created notification channel $CHANNEL_ID")
    }

    private fun wasAlertSent(
        month: String,
        threshold: Int,
        preferences: SharedPreferences
    ): Boolean {
        return preferences.getBoolean("${month}_$threshold", false)
    }

    private fun markAlertsSent(
        month: String,
        thresholds: List<Int>,
        preferences: SharedPreferences
    ): Boolean {
        return try {
            val editor = preferences.edit()

            thresholds.forEach { threshold ->
                editor.putBoolean("${month}_$threshold", true)
            }

            val saved = editor.commit()

            if (saved) {
                Log.d(
                    TAG,
                    "HISTORY OK: Saved thresholds=$thresholds for month=$month"
                )
            } else {
                Log.e(TAG, "HISTORY ERROR: SharedPreferences commit returned false")
            }

            saved
        } catch (e: Exception) {
            Log.e(TAG, "HISTORY ERROR: Could not save alert history", e)
            false
        }
    }

    private fun formatAmount(amount: Double): String {
        return String.format(Locale.US, "%.2f", amount)
    }
}
