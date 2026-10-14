package data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val description: String,

    val amount: Double,

    val currency: String = "PKR",

    val category: String,

    val expenseDate: String,

    val createdAt: Long = System.currentTimeMillis()
)

