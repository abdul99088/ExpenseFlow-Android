# ExpenseFlow — Smart Android Expense Tracker

An Android expense-tracking application that combines receipt text recognition, on-device machine learning, monthly spending analytics, and automated budget alerts.

**Built with Kotlin, Jetpack Compose, Room, Google ML Kit, and a custom TF-IDF + Logistic Regression classifier.**

## ✨ Features

- **Receipt Scanning:** Extract expense information from receipt images using Google ML Kit Text Recognition.
- **Automatic Categorization:** Classify expenses into Food, Travel, Bills, Shopping, and Other using an on-device machine learning model.
- **Manual Expense Management:** Add, edit, and delete expenses.
- **Monthly Analytics:** View monthly spending summaries and category-wise visualizations.
- **Budget Management:** Set a monthly budget and monitor spending against it.
- **Budget Alerts:** Receive notifications when spending reaches configured budget thresholds.
- **PKR Support:** Track expenses in Pakistani Rupees.
- **Local Data Storage:** Store expense records using Room.

## 🧠 Machine Learning

ExpenseFlow uses a lightweight text-classification pipeline designed to run locally on the device.

**Pipeline:**

1. Extract text from receipt images using OCR.
2. Parse relevant receipt information, including totals and dates.
3. Convert transaction text into TF-IDF features using unigrams and bigrams.
4. Apply a Logistic Regression classifier to predict an expense category.
5. Display the predicted category in the application.

The exported model uses a vocabulary of 761 terms across five categories: Bills, Food, Other, Shopping, and Travel.

The training workflow reported approximately **91.9% validation accuracy**. This is a validation result, not a guarantee of real-world accuracy; performance can vary with receipt quality and transaction text.

## 🛠️ Tech Stack

| Technology | Purpose |
|---|---|
| Kotlin | Application development |
| Jetpack Compose | UI development |
| Google ML Kit Text Recognition | Receipt OCR |
| TF-IDF + Logistic Regression | Expense categorization |
| Room | Local database |
| WorkManager | Scheduled budget checks |
| MPAndroidChart | Expense visualization |
| Gradle | Build automation |

## 🏗️ Architecture Overview

- **Presentation:** Compose screens for expense management, monthly summaries, and budgets.
- **Receipt Processing:** OCR and parsing logic to extract transaction details.
- **ML Classification:** An exported model performs local text-based expense categorization.
- **Data Layer:** Room persists expense records on the device.
- **Background Work:** WorkManager checks spending against monthly budget thresholds and triggers notifications.

## 🚀 Getting Started

### Requirements

- Android Studio with a compatible Android SDK.
- JDK compatible with the project's Gradle configuration.
- Android emulator or compatible Android device.

### Build the application

Clone the repository:

```bash
git clone https://github.com/abdul99088/ExpenseFlow-Android.git
cd ExpenseFlow-Android
```

Open the project in Android Studio and allow Gradle synchronization to finish.

On Windows PowerShell, build the debug APK with:

```powershell
.\gradlew.bat assembleDebug
```

The generated APK is normally located at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 🧪 Testing

The application has been manually smoke-tested for:

- Adding, editing, and deleting expenses.
- Receipt scanning and information extraction.
- Automatic expense categorization.
- Monthly summaries and category charts.
- PKR amounts and future-date restrictions.
- Budget-alert worker execution and notification submission.

The 100% budget-alert worker was confirmed to submit a notification request to Android during testing. Further device-level verification may be needed for notification display and the 80% threshold.

## 🔒 Privacy

Expense records are stored locally using Room. Receipt text recognition and categorization are designed to run on-device. Review the application's permissions and implementation before relying on it for sensitive financial information.

## 🔮 Potential Improvements

- Camera-based receipt capture.
- Expanded receipt-format and currency validation.
- More comprehensive automated tests.
- Improved model evaluation on diverse real-world receipts.
- Exportable reports and additional analytics.

## 👨‍💻 Author

**Abdul Vassay Khaliq**

Computer Engineering student interested in AI/ML, applied machine learning, and practical software engineering.

- GitHub: [@
