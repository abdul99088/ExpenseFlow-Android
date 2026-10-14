package com.example.myapplication



import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.exp
import kotlin.math.sqrt

class ExpenseClassifier(context: Context) {

    private val vocabulary: Map<String, Int>
    private val idf: DoubleArray
    private val classes: Array<String>
    private val coefficients: Array<DoubleArray>
    private val intercepts: DoubleArray

    init {
        val jsonText = context.assets
            .open("expense_model.json")
            .bufferedReader()
            .use { it.readText() }

        val root = JSONObject(jsonText)

        val tfidf = root.getJSONObject("tfidf")
        val classifier = root.getJSONObject("classifier")

        vocabulary = buildVocabulary(
            tfidf.getJSONObject("vocabulary")
        )

        idf = jsonArrayToDoubleArray(
            tfidf.getJSONArray("idf")
        )

        classes = jsonArrayToStringArray(
            classifier.getJSONArray("classes")
        )

        coefficients = jsonMatrixToDoubleArray(
            classifier.getJSONArray("coefficients")
        )

        intercepts = jsonArrayToDoubleArray(
            classifier.getJSONArray("intercepts")
        )
    }

    fun predict(text: String): Prediction {

        val features = buildTfIdfVector(text)

        val scores = DoubleArray(classes.size)

        for (classIndex in classes.indices) {

            var score = intercepts[classIndex]

            for (featureIndex in features.indices) {
                score += coefficients[classIndex][featureIndex] *
                        features[featureIndex]
            }

            scores[classIndex] = score
        }

        val probabilities = softmax(scores)

        var bestIndex = 0

        for (i in probabilities.indices) {
            if (probabilities[i] > probabilities[bestIndex]) {
                bestIndex = i
            }
        }

        return Prediction(
            category = classes[bestIndex],
            confidence = probabilities[bestIndex]
        )
    }

    private fun buildTfIdfVector(text: String): DoubleArray {

        val vector = DoubleArray(idf.size)

        val tokens = tokenize(text)

        if (tokens.isEmpty()) {
            return vector
        }

        val termCounts = mutableMapOf<String, Int>()

        for (token in tokens) {
            termCounts[token] =
                (termCounts[token] ?: 0) + 1
        }

        for (i in 0 until tokens.size - 1) {

            val bigram =
                "${tokens[i]} ${tokens[i + 1]}"

            termCounts[bigram] =
                (termCounts[bigram] ?: 0) + 1
        }

        for ((term, count) in termCounts) {

            val index = vocabulary[term] ?: continue

            vector[index] =
                count.toDouble() * idf[index]
        }

        // L2 normalization.
        var sumSquares = 0.0

        for (value in vector) {
            sumSquares += value * value
        }

        val norm = sqrt(sumSquares)

        if (norm > 0.0) {
            for (i in vector.indices) {
                vector[i] /= norm
            }
        }

        return vector
    }

    private fun tokenize(text: String): List<String> {

        return Regex("[a-zA-Z0-9]+")
            .findAll(text.lowercase())
            .map { it.value }
            .toList()
    }

    private fun softmax(scores: DoubleArray): DoubleArray {

        val maxScore = scores.maxOrNull() ?: 0.0

        val exponentials = DoubleArray(scores.size)

        var total = 0.0

        for (i in scores.indices) {

            val value =
                exp(scores[i] - maxScore)

            exponentials[i] = value
            total += value
        }

        for (i in exponentials.indices) {
            exponentials[i] /= total
        }

        return exponentials
    }

    private fun buildVocabulary(
        json: JSONObject
    ): Map<String, Int> {

        val result = mutableMapOf<String, Int>()

        val keys = json.keys()

        while (keys.hasNext()) {

            val key = keys.next()

            result[key] = json.getInt(key)
        }

        return result
    }

    private fun jsonArrayToDoubleArray(
        array: JSONArray
    ): DoubleArray {

        return DoubleArray(array.length()) { index ->
            array.getDouble(index)
        }
    }

    private fun jsonArrayToStringArray(
        array: JSONArray
    ): Array<String> {

        return Array(array.length()) { index ->
            array.getString(index)
        }
    }

    private fun jsonMatrixToDoubleArray(
        matrix: JSONArray
    ): Array<DoubleArray> {

        return Array(matrix.length()) { rowIndex ->

            val row = matrix.getJSONArray(rowIndex)

            DoubleArray(row.length()) { columnIndex ->
                row.getDouble(columnIndex)
            }
        }
    }
}

data class Prediction(
    val category: String,
    val confidence: Double
)
