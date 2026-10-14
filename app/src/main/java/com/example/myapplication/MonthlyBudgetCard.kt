
package com.example.myapplication

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
fun MonthlyBudgetCard() {
    val context = LocalContext.current

    var budgetText by remember {
        mutableStateOf(
            BudgetPreferences.getMonthlyBudget(context)
                .takeIf { it > 0.0 }
                ?.let { String.format(Locale.US, "%.2f", it) }
                ?: ""
        )
    }

    var message by remember { mutableStateOf("") }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Monthly Budget")

            Text("Set how much you want to spend this month.")

            OutlinedTextField(
                value = budgetText,
                onValueChange = { newValue ->
                    if (newValue.matches(Regex("^\\d{0,9}(\\.\\d{0,2})?$"))) {
                        budgetText = newValue
                        message = ""
                    }
                },
                label = { Text("Budget amount (PKR)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    val amount = budgetText.toDoubleOrNull()

                    if (amount == null || amount <= 0.0 || !amount.isFinite()) {
                        message = "Enter a valid budget greater than zero."
                    } else {
                        BudgetPreferences.saveMonthlyBudget(context, amount)
                        BudgetAlertScheduler.checkNow(context)
                        message = "Monthly budget saved successfully!"
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Budget")
            }

            if (message.isNotBlank()) {
                Text(message)
            }
        }
    }
}