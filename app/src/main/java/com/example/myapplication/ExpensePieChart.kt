package com.example.myapplication

import android.graphics.Color
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.animation.Easing
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.PercentFormatter

private val chartColors = listOf(
    Color.rgb(66, 133, 244),   // blue
    Color.rgb(234, 67, 53),    // red
    Color.rgb(251, 188, 5),    // yellow
    Color.rgb(52, 168, 83),    // green
    Color.rgb(171, 71, 188),   // purple
    Color.rgb(0, 172, 193),    // teal
    Color.rgb(255, 112, 67)    // orange
)

/**
 * Pie chart of this month's spending by category.
 * categoryTotals: list of (category name, total amount).
 */
@Composable
fun ExpensePieChart(
    categoryTotals: List<Pair<String, Double>>
) {
    // Keep only categories with a positive, finite amount.
    val validTotals = categoryTotals.filter {
        it.second > 0.0 && it.second.isFinite()
    }

    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp),
        factory = { context ->
            PieChart(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                description.isEnabled = false
                setUsePercentValues(true)
                setDrawEntryLabels(false)   // labels shown in the legend instead
                isDrawHoleEnabled = true
                holeRadius = 45f
                transparentCircleRadius = 50f
                setHoleColor(Color.TRANSPARENT)
                setCenterTextSize(14f)
                setNoDataText("Add expenses to see your category chart")
                legend.isEnabled = true
                legend.textSize = 12f
            }
        },
        update = { chart ->
            if (validTotals.isEmpty()) {
                chart.clear()
                chart.centerText = ""
                chart.invalidate()
            } else {
                val entries = validTotals.map { (category, amount) ->
                    PieEntry(amount.toFloat(), category)
                }

                val dataSet = PieDataSet(entries, "").apply {
                    colors = chartColors
                    sliceSpace = 3f
                    selectionShift = 5f
                    valueTextSize = 12f
                    valueTextColor = Color.WHITE
                }

                chart.data = PieData(dataSet).apply {
                    setValueFormatter(PercentFormatter(chart))
                    setValueTextSize(12f)
                    setValueTextColor(Color.WHITE)
                }

                chart.centerText = "This Month"
                chart.highlightValues(null)
                chart.animateY(500, Easing.EaseInOutQuad)
                chart.invalidate()
            }
        }
    )
}