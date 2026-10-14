
package com.example.myapplication

import android.content.Context
import android.util.Log
import kotlin.math.roundToLong

object BudgetPreferences {

    private const val TAG = BudgetAlertWorker.TAG
    private const val PREFS_NAME = "expenseflow_budget"
    private const val KEY_MONTHLY_BUDGET = "monthly_budget_cents"

    fun saveMonthlyBudget(context: Context, amount: Double) {
        require(amount.isFinite() && amount >= 0.0) {
            "Budget must be a valid, non-negative amount"
        }

        require(amount <= Long.MAX_VALUE.toDouble() / 100.0) {
            "Budget amount is too large"
        }

        val cents = (amount * 100.0).roundToLong()

        try {
            Log.i(TAG, "BUDGET SAVE: Saving monthly budget PKR $amount")

            val saved = context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_MONTHLY_BUDGET, cents)
                .commit()

            if (!saved) {
                Log.e(TAG, "BUDGET SAVE ERROR: SharedPreferences commit failed")
                throw IllegalStateException("Could not save monthly budget")
            }

            Log.i(TAG, "BUDGET SAVE OK: Saved $cents paisa")

        } catch (e: Exception) {
            Log.e(TAG, "BUDGET SAVE ERROR: Failed to save monthly budget", e)
            throw e
        }
    }

    fun getMonthlyBudget(context: Context): Double {
        return try {
            val cents = context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getLong(KEY_MONTHLY_BUDGET, 0L)

            if (cents < 0L) {
                Log.e(TAG, "BUDGET READ ERROR: Stored budget is negative")
                return 0.0
            }

            val amount = cents / 100.0

            Log.d(TAG, "BUDGET READ OK: Monthly budget is PKR $amount")
            amount

        } catch (e: Exception) {
            Log.e(TAG, "BUDGET READ ERROR: Failed to read monthly budget", e)
            throw e
        }
    }
}
