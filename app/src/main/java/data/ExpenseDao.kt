package data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {

    @Insert
    suspend fun insertExpense(expense: Expense)

    @Update
    suspend fun updateExpense(expense: Expense)

    @Delete
    suspend fun deleteExpense(expense: Expense)

    @Query("SELECT * FROM expenses ORDER BY expenseDate DESC, id DESC")
    fun getAllExpenses(): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE id = :id LIMIT 1")
    suspend fun getExpenseById(id: Int): Expense?

    @Query("""
        SELECT * FROM expenses
        WHERE expenseDate LIKE :monthPrefix || '%'
        ORDER BY expenseDate DESC, id DESC
    """)
    fun getExpensesByMonth(monthPrefix: String): Flow<List<Expense>>

    @Query("""
        SELECT category, SUM(amount) AS total
        FROM expenses
        WHERE expenseDate LIKE :monthPrefix || '%'
        GROUP BY category
        ORDER BY total DESC
    """)
    fun getCategoryTotalsByMonth(monthPrefix: String): Flow<List<CategoryTotal>>

    @Query("""
        SELECT COALESCE(SUM(amount), 0)
        FROM expenses
        WHERE expenseDate LIKE :monthPrefix || '%'
    """)
    fun getMonthlyTotal(monthPrefix: String): Flow<Double>
}

data class CategoryTotal(
    val category: String,
    val total: Double
)

