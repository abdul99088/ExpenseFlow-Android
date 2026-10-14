package com.example.myapplication

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import data.Expense
import data.ExpenseDatabase
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

// The screens in the app.
enum class AppScreen {
    HOME,
    RECEIPT,
    MANUAL
}

class MainActivity : ComponentActivity() {

    private lateinit var database: ExpenseDatabase

    private var recognizedText by mutableStateOf("")

    private var parsedReceipt by mutableStateOf(
        ParsedReceipt(
            total = null,
            currency = "UNKNOWN",
            date = null,
            items = emptyList()
        )
    )

    private var ocrError by mutableStateOf("")

    // Opens the Android photo picker.
    // After the user selects an image, ML Kit OCR processes it.
    private val imagePicker =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                runTextRecognition(uri)
            }
        }

    // ML Kit on-device text recognizer.
    private val textRecognizer =
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    // Asks the user for notification permission (Android 13+).
    private val notificationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                Toast.makeText(
                    this,
                    "Budget notifications enabled",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    this,
                    "Enable notifications in Settings to receive budget alerts",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ask for notification permission on Android 13 and newer.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(
                Manifest.permission.POST_NOTIFICATIONS
            )
        }

        // Schedule the periodic budget check. Only one job is kept.
        BudgetAlertScheduler.schedule(this)

        enableEdgeToEdge()

        database = ExpenseDatabase.getDatabase(applicationContext)

        setContent {
            MyApplicationTheme {
                ExpenseApp(
                    database = database,
                    parsedReceipt = parsedReceipt,
                    recognizedText = recognizedText,
                    ocrError = ocrError,
                    onAddExpense = { expense ->
                        lifecycleScope.launch {
                            try {
                                database.expenseDao().insertExpense(expense)
                                BudgetAlertScheduler.checkNow(this@MainActivity)

                                Toast.makeText(
                                    this@MainActivity,
                                    "Expense saved successfully!",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } catch (exception: Exception) {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Save failed: ${exception.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    },
                    onUpdateExpense = { expense ->
                        lifecycleScope.launch {
                            try {
                                database.expenseDao().updateExpense(expense)
                                BudgetAlertScheduler.checkNow(this@MainActivity)

                                Toast.makeText(
                                    this@MainActivity,
                                    "Expense updated successfully!",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } catch (exception: Exception) {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Update failed: ${exception.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    },
                    onDeleteExpense = { expense ->
                        lifecycleScope.launch {
                            try {
                                database.expenseDao().deleteExpense(expense)
                                BudgetAlertScheduler.checkNow(this@MainActivity)

                                Toast.makeText(
                                    this@MainActivity,
                                    "Expense deleted successfully!",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } catch (exception: Exception) {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Delete failed: ${exception.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    },
                    onResetReceipt = { resetReceiptState() },
                    onPickReceiptPhoto = { imagePicker.launch("image/*") }
                )
            }
        }
    }

    // Clears the previous receipt results.
    private fun resetReceiptState() {
        recognizedText = ""
        parsedReceipt = ParsedReceipt(
            total = null,
            currency = "UNKNOWN",
            date = null,
            items = emptyList()
        )
        ocrError = ""
    }

    // Sends the selected image to ML Kit OCR.
    private fun runTextRecognition(uri: Uri) {
        try {
            val image = InputImage.fromFilePath(this, uri)

            textRecognizer.process(image)
                .addOnSuccessListener { visionText ->
                    recognizedText = visionText.text
                    parsedReceipt = ReceiptParser.parse(visionText.text)
                    ocrError = ""
                }
                .addOnFailureListener { exception ->
                    recognizedText = ""
                    ocrError = "OCR failed: ${exception.message}"
                }

        } catch (exception: Exception) {
            recognizedText = ""
            ocrError = "Could not open image: ${exception.message}"
        }
    }

    override fun onDestroy() {
        textRecognizer.close()
        super.onDestroy()
    }
}


// ============================================================
// APP ROOT: decides which screen is showing
// ============================================================

@Composable
fun ExpenseApp(
    database: ExpenseDatabase,
    parsedReceipt: ParsedReceipt,
    recognizedText: String,
    ocrError: String,
    onAddExpense: (Expense) -> Unit,
    onUpdateExpense: (Expense) -> Unit,
    onDeleteExpense: (Expense) -> Unit,
    onResetReceipt: () -> Unit,
    onPickReceiptPhoto: () -> Unit
) {
    var screen by rememberSaveable { mutableStateOf(AppScreen.HOME) }

    // The expense being edited, or null when adding a new one.
    var expenseToEdit by remember { mutableStateOf<Expense?>(null) }

    val expenses by database
        .expenseDao()
        .getAllExpenses()
        .collectAsState(initial = emptyList())

    val context = LocalContext.current
    val classifier = remember(context) {
        ExpenseClassifier(context)
    }

    val today = remember {
        LocalDate.now().toString()
    }

    // Called by the manual form. Updates the row if editing,
    // otherwise inserts a new expense, then returns to Home.
    val onSaveManualExpense: (Expense) -> Unit = { expense ->
        if (expenseToEdit == null) {
            onAddExpense(expense)
        } else {
            onUpdateExpense(expense)
        }
        expenseToEdit = null
        screen = AppScreen.HOME
    }

    // System back button: go to Home from any other screen.
    BackHandler(enabled = screen != AppScreen.HOME) {
        expenseToEdit = null
        screen = AppScreen.HOME
    }

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (screen) {

                AppScreen.HOME -> HomeScreen(
                    expenses = expenses,
                    onScanReceipt = {
                        onResetReceipt()
                        screen = AppScreen.RECEIPT
                    },
                    onAddManual = {
                        expenseToEdit = null
                        screen = AppScreen.MANUAL
                    },
                    onEditExpense = { expense ->
                        expenseToEdit = expense
                        screen = AppScreen.MANUAL
                    },
                    onDeleteExpense = onDeleteExpense
                )

                AppScreen.RECEIPT -> ReceiptScreen(
                    parsedReceipt = parsedReceipt,
                    recognizedText = recognizedText,
                    ocrError = ocrError,
                    classifier = classifier,
                    today = today,
                    onChoosePhoto = {
                        onResetReceipt()
                        onPickReceiptPhoto()
                    },
                    onSaveExpense = onAddExpense,
                    onBack = { screen = AppScreen.HOME }
                )

                AppScreen.MANUAL -> ManualExpenseScreen(
                    initialExpense = expenseToEdit,
                    today = today,
                    onSaveExpense = onSaveManualExpense,
                    onBack = {
                        expenseToEdit = null
                        screen = AppScreen.HOME
                    }
                )
            }
        }
    }
}


// ============================================================
// HOME SCREEN
// ============================================================

@Composable
fun HomeScreen(
    expenses: List<Expense>,
    onScanReceipt: () -> Unit,
    onAddManual: () -> Unit,
    onEditExpense: (Expense) -> Unit,
    onDeleteExpense: (Expense) -> Unit
) {
    val total = expenses.sumOf { it.amount }

    // Monthly summary, calculated from the expenses already loaded from Room.
    // expenseDate is stored as yyyy-MM-dd, so the first 7 characters are yyyy-MM.
    val currentMonth = LocalDate.now().toString().take(7)

    val monthlyExpenses = expenses.filter {
        it.expenseDate.startsWith(currentMonth)
    }

    val monthlyTotal = monthlyExpenses.sumOf { it.amount }

    val categoryTotals = monthlyExpenses
        .groupBy { it.category }
        .mapValues { (_, categoryExpenses) ->
            categoryExpenses.sumOf { it.amount }
        }
        .toList()
        .sortedByDescending { it.second }

    // The expense waiting for delete confirmation, or null if none.
    var expenseToDelete by remember { mutableStateOf<Expense?>(null) }

    // Confirmation dialog. Nothing is deleted until the user confirms.
    expenseToDelete?.let { expense ->
        AlertDialog(
            onDismissRequest = { expenseToDelete = null },
            title = { Text(text = "Delete expense?") },
            text = {
                Text(
                    text = "\"${expense.description}\" (${expense.currency} ${expense.amount}) will be removed."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteExpense(expense)
                        expenseToDelete = null
                    }
                ) {
                    Text(text = "Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { expenseToDelete = null }) {
                    Text(text = "Cancel")
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {

        item {
            Text(
                text = "ExpenseFlow",
                style = MaterialTheme.typography.headlineMedium
            )
        }

        item {
            Text(text = "Smart expense tracking")
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Total spent")
                    Text(
                        text = "PKR %.2f".format(total),
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "This Month",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "PKR %.2f".format(monthlyTotal),
                        style = MaterialTheme.typography.headlineSmall
                    )

                    Text(text = "${monthlyExpenses.size} expenses this month")

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Category Breakdown",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Pie chart of this month's spending by category.
                    ExpensePieChart(categoryTotals = categoryTotals)

                    Spacer(modifier = Modifier.height(8.dp))

                    if (categoryTotals.isEmpty()) {
                        Text(text = "No expenses recorded this month.")
                    } else {
                        categoryTotals.forEach { (category, categoryAmount) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = category)
                                Text(text = "PKR %.2f".format(categoryAmount))
                            }
                        }
                    }
                }
            }
        }

        item {
            MonthlyBudgetCard()
        }

        item {
            Button(
                onClick = onScanReceipt,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "ðŸ“· Scan Receipt")
            }
        }

        item {
            Button(
                onClick = onAddManual,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "âœï¸ Add Expense Manually")
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Recent Expenses",
                style = MaterialTheme.typography.titleMedium
            )
        }

        if (expenses.isEmpty()) {
            item {
                Text(text = "No expenses yet. Scan a receipt or add one manually.")
            }
        }

        items(
            items = expenses,
            key = { expense -> expense.id }
        ) { expense ->
            ExpenseItem(
                expense = expense,
                onEdit = { onEditExpense(expense) },
                onDelete = { expenseToDelete = expense }
            )
        }
    }
}


// ============================================================
// RECEIPT SCREEN
// ============================================================

@Composable
fun ReceiptScreen(
    parsedReceipt: ParsedReceipt,
    recognizedText: String,
    ocrError: String,
    classifier: ExpenseClassifier,
    today: String,
    onChoosePhoto: () -> Unit,
    onSaveExpense: (Expense) -> Unit,
    onBack: () -> Unit
) {
    val screenScrollState = rememberScrollState()
    val ocrScrollState = rememberScrollState()

    // Shows why a receipt was rejected. Resets when a new receipt is parsed.
    var currencyError by remember(parsedReceipt) { mutableStateOf("") }

    // Classify receipt items when available. If the parser did not
    // extract items, use the OCR text as a fallback.
    val classificationText = remember(parsedReceipt, recognizedText) {
        parsedReceipt.items
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { recognizedText.trim() }
    }

    val receiptPrediction = remember(classifier, classificationText) {
        if (classificationText.isBlank()) {
            null
        } else {
            classifier.predict(classificationText)
        }
    }

    val hasReceiptData =
        parsedReceipt.total != null ||
                parsedReceipt.date != null ||
                parsedReceipt.items.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(screenScrollState)
    ) {

        ScreenHeader(title = "Scan Receipt", onBack = onBack)

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onChoosePhoto,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "ðŸ“· Choose Receipt Photo")
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (ocrError.isNotBlank()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = ocrError,
                    modifier = Modifier.padding(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Validation message. Shown above the receipt card so it is visible
        // without scrolling past the Save button.
        if (currencyError.isNotBlank()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = currencyError,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (recognizedText.isNotBlank()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {

                    Text(text = "OCR Text âœ“")
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))

                    // Only the OCR text scrolls, so a long receipt
                    // doesn't push the rest of the screen away.
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .verticalScroll(ocrScrollState)
                    ) {
                        Text(text = recognizedText)
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (hasReceiptData) {
            ReceiptReviewCard(
                parsedReceipt = parsedReceipt,
                predictedCategory = receiptPrediction?.category ?: "Other",
                confidencePercent = receiptPrediction?.let {
                    it.confidence * 100.0
                },
                onSave = {
                    val total = parsedReceipt.total
                    val receiptDateText = parsedReceipt.date
                    val receiptDate = receiptDateText?.let {
                        runCatching { LocalDate.parse(it) }.getOrNull()
                    }

                    currencyError = when {
                        parsedReceipt.currency == "USD" ->
                            "Receipt rejected: USD receipts are not supported. Please use a PKR receipt."

                        parsedReceipt.currency == "EUR" ->
                            "Receipt rejected: EUR receipts are not supported. Please use a PKR receipt."

                        parsedReceipt.currency == "GBP" ->
                            "Receipt rejected: GBP receipts are not supported. Please use a PKR receipt."

                        parsedReceipt.currency != "PKR" ->
                            "Receipt rejected: Only PKR receipts are accepted. Detected currency: ${parsedReceipt.currency}. No currency conversion is performed."

                        total == null || total <= 0.0 ->
                            "Could not detect a valid receipt total."

                        receiptDateText != null && receiptDate == null ->
                            "Receipt date could not be read. Please check the receipt."

                        receiptDate != null && receiptDate.isAfter(LocalDate.now()) ->
                            "Receipt date is in the future. Please check the receipt."

                        else -> ""
                    }

                    if (currencyError.isEmpty() && total != null) {
                        val descriptionText = parsedReceipt.items
                            .filter { it.isNotBlank() }
                            .take(3)
                            .joinToString(", ")
                            .ifBlank { "Receipt expense" }

                        onSaveExpense(
                            Expense(
                                description = descriptionText,
                                amount = total,
                                currency = "PKR",
                                category = receiptPrediction?.category ?: "Other",
                                expenseDate = receiptDateText ?: today
                            )
                        )

                        // Return to Home after saving.
                        onBack()
                    }
                }
            )
        } else if (recognizedText.isBlank() && ocrError.isBlank()) {
            Text(text = "Choose a receipt photo to get started.")
        }
    }
}


// ============================================================
// MANUAL EXPENSE SCREEN (used for both adding and editing)
// ============================================================

@Composable
fun ManualExpenseScreen(
    initialExpense: Expense?,
    today: String,
    onSaveExpense: (Expense) -> Unit,
    onBack: () -> Unit
) {
    val isEditing = initialExpense != null
    val categories = listOf("Food", "Travel", "Bills", "Shopping", "Other")

    // Form state. Reloads when a different expense is opened for editing.
    var description by remember(initialExpense?.id) {
        mutableStateOf(initialExpense?.description ?: "")
    }
    var amount by remember(initialExpense?.id) {
        mutableStateOf(initialExpense?.amount?.toString() ?: "")
    }
    var category by remember(initialExpense?.id) {
        mutableStateOf(initialExpense?.category ?: "Other")
    }
    var expenseDate by remember(initialExpense?.id, today) {
        mutableStateOf(initialExpense?.expenseDate ?: today)
    }

    var categoryExpanded by remember { mutableStateOf(false) }
    var datePickerOpen by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    // The date picker starts on the saved date (or today for new expenses).
    // Future dates are disabled in the calendar.
    val datePickerState = key(initialExpense?.id, expenseDate) {
        rememberDatePickerState(
            initialSelectedDateMillis = runCatching {
                LocalDate.parse(expenseDate)
                    .atStartOfDay(ZoneOffset.UTC)
                    .toInstant()
                    .toEpochMilli()
            }.getOrNull(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val selectedDate = Instant.ofEpochMilli(utcTimeMillis)
                        .atZone(ZoneOffset.UTC)
                        .toLocalDate()

                    return !selectedDate.isAfter(LocalDate.now())
                }

                override fun isSelectableYear(year: Int): Boolean {
                    return year <= LocalDate.now().year
                }
            }
        )
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        ScreenHeader(
            title = if (isEditing) "Edit Expense" else "Add Expense",
            onBack = onBack
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Description") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it },
            label = { Text("Amount (PKR)") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Category dropdown. The transparent overlay makes the whole field tappable.
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = category,
                onValueChange = {},
                readOnly = true,
                label = { Text("Category") },
                modifier = Modifier.fillMaxWidth()
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clickable { categoryExpanded = true }
            )

            DropdownMenu(
                expanded = categoryExpanded,
                onDismissRequest = { categoryExpanded = false }
            ) {
                categories.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            category = option
                            categoryExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Date field. The transparent overlay opens the calendar on tap.
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = expenseDate,
                onValueChange = {},
                readOnly = true,
                label = { Text("Expense Date (YYYY-MM-DD)") },
                modifier = Modifier.fillMaxWidth()
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clickable { datePickerOpen = true }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (errorMessage.isNotBlank()) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Button(
            onClick = {
                val amountValue = amount.toDoubleOrNull()
                val parsedDate = runCatching {
                    LocalDate.parse(expenseDate)
                }.getOrNull()

                if (
                    description.isBlank() ||
                    amountValue == null ||
                    amountValue <= 0
                ) {
                    errorMessage =
                        "Enter a description and an amount greater than 0."
                } else if (parsedDate == null) {
                    errorMessage = "Enter a valid date (YYYY-MM-DD)."
                } else if (parsedDate.isAfter(LocalDate.now())) {
                    errorMessage = "Expense date cannot be in the future."
                } else {
                    errorMessage = ""

                    // When editing, copy the existing expense so its ID and
                    // currency are kept. When adding, create a new one.
                    val expense = if (initialExpense == null) {
                        Expense(
                            description = description.trim(),
                            amount = amountValue,
                            currency = "PKR",
                            category = category,
                            expenseDate = expenseDate
                        )
                    } else {
                        initialExpense.copy(
                            description = description.trim(),
                            amount = amountValue,
                            category = category,
                            expenseDate = expenseDate
                        )
                    }

                    onSaveExpense(expense)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = if (isEditing) "Update Expense" else "Save Expense")
        }
    }

    if (datePickerOpen) {
        DatePickerDialog(
            onDismissRequest = { datePickerOpen = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            expenseDate = Instant.ofEpochMilli(millis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                                .toString()
                        }
                        datePickerOpen = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { datePickerOpen = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}


// ============================================================
// SHARED HEADER WITH BACK BUTTON
// ============================================================

@Composable
fun ScreenHeader(
    title: String,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onBack) {
            Text(text = "â† Back")
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge
        )
    }
}


// ============================================================
// RECEIPT REVIEW CARD
// ============================================================

@Composable
fun ReceiptReviewCard(
    parsedReceipt: ParsedReceipt,
    predictedCategory: String,
    confidencePercent: Double?,
    onSave: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {

        Column(modifier = Modifier.padding(16.dp)) {

            Text(text = "Receipt Detected âœ“")
            Spacer(modifier = Modifier.height(6.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(6.dp))

            Text(text = "Currency")
            Text(text = parsedReceipt.currency)
            Spacer(modifier = Modifier.height(6.dp))

            Text(text = "Total")
            Text(
                text = if (parsedReceipt.total != null) {
                    "${parsedReceipt.currency} ${parsedReceipt.total}"
                } else {
                    "Not detected"
                }
            )
            Spacer(modifier = Modifier.height(6.dp))

            Text(text = "Date")
            Text(text = parsedReceipt.date ?: "Not detected")
            Spacer(modifier = Modifier.height(6.dp))

            Text(text = "Items")
            if (parsedReceipt.items.isEmpty()) {
                Text(text = "No items detected")
            } else {
                parsedReceipt.items.take(5).forEach { item ->
                    Text(text = "â€¢ $item")
                }
            }
            Spacer(modifier = Modifier.height(6.dp))

            Text(text = "Category")
            Text(text = predictedCategory)
            if (confidencePercent != null) {
                Text(text = "ML confidence: ${"%.1f".format(confidencePercent)}%")
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Always enabled so the validation in ReceiptScreen can
            // explain why a receipt cannot be saved.
            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Check Receipt")
            }
        }
    }
}


// ============================================================
// EXPENSE ITEM (description, category/date, amount, Edit/Delete)
// ============================================================

@Composable
fun ExpenseItem(
    expense: Expense,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            // Expense description
            Text(
                text = expense.description,
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Category and date
            Text(
                text = "${expense.category} â€¢ ${expense.expenseDate}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Amount and action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${expense.currency} ${
                        String.format("%.2f", expense.amount)
                    }",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(onClick = onEdit) {
                        Text("Edit")
                    }

                    Spacer(modifier = Modifier.padding(horizontal = 3.dp))

                    Button(onClick = onDelete) {
                        Text("Delete")
                    }
                }
            }
        }
    }
}