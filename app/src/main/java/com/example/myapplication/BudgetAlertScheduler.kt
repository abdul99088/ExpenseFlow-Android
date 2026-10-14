
package com.example.myapplication

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object BudgetAlertScheduler {

    private const val TAG = BudgetAlertWorker.TAG

    private const val PERIODIC_WORK_NAME =
        "expenseflow_monthly_budget_check"

    private const val IMMEDIATE_WORK_NAME =
        "expenseflow_budget_check_on_open"

    fun schedule(context: Context) {
        val appContext = context.applicationContext

        try {
            Log.i(TAG, "SCHEDULER START: Registering budget checks")

            val workManager = WorkManager.getInstance(appContext)

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .build()

            val periodicRequest =
                PeriodicWorkRequestBuilder<BudgetAlertWorker>(
                    15,
                    TimeUnit.MINUTES
                )
                    .setConstraints(constraints)
                    .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        30,
                        TimeUnit.SECONDS
                    )
                    .build()

            workManager.enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                periodicRequest
            )

            Log.i(
                TAG,
                "SCHEDULER OK: Periodic work enqueue requested. " +
                        "id=${periodicRequest.id}"
            )

            val immediateRequest =
                OneTimeWorkRequestBuilder<BudgetAlertWorker>()
                    .setConstraints(constraints)
                    .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        30,
                        TimeUnit.SECONDS
                    )
                    .build()

            workManager.enqueueUniqueWork(
                IMMEDIATE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                immediateRequest
            )

            Log.i(
                TAG,
                "SCHEDULER OK: Immediate work enqueue requested. " +
                        "id=${immediateRequest.id}"
            )

        } catch (e: Exception) {
            Log.e(TAG, "SCHEDULER ERROR: Could not schedule budget checks", e)
        }
    }

    /**
     * Requests a fresh one-time check.
     * Useful after saving a budget or adding/editing an expense.
     */
    fun checkNow(context: Context) {
        try {
            Log.i(TAG, "CHECK NOW: Requesting an immediate budget check")

            val request = OneTimeWorkRequestBuilder<BudgetAlertWorker>()
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30,
                    TimeUnit.SECONDS
                )
                .build()

            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(
                    IMMEDIATE_WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    request
                )

            Log.i(
                TAG,
                "CHECK NOW OK: Enqueue requested. id=${request.id}"
            )

        } catch (e: Exception) {
            Log.e(TAG, "CHECK NOW ERROR: Could not request a budget check", e)
        }
    }
}
