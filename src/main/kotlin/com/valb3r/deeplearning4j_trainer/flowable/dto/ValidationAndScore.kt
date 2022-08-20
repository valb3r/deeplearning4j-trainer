package com.valb3r.deeplearning4j_trainer.flowable.dto

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.google.common.math.PairedStatsAccumulator
import com.google.common.math.Quantiles.percentiles
import com.google.common.math.Stats
import java.lang.Math.pow
import kotlin.math.abs

data class ValidationAndScore(
    val labelVar: String,
    val extractionExpr: String,
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
    @JsonSubTypes(
        JsonSubTypes.Type(value = RegressionValidation::class, name = "regression"),
        JsonSubTypes.Type(value = RegressionPercentileScoreValidation::class, name = "regression-percentile-score"),
    )
    val validation: Validation
)

interface MetricEval {
    fun eval(labelExpr: Array<FloatArray>, output: Array<FloatArray>, resultIds: FloatArray?): Map<String, Float> {
        if (null == resultIds) {
            return eval(labelExpr.flatMap { it.asIterable() }.toFloatArray(), output.flatMap { it.asIterable() }.toFloatArray())
        }

        val labels = ArrayList<FloatArray>()
        val outp = ArrayList<FloatArray>()
        var resultId: Float? = null
        var bestResult: Float? = null
        var bestResultId = 0
        for ((index, id) in resultIds.withIndex()) {
            if ((resultId != null && resultId != id) || index == resultIds.size) {
                labels.add(labelExpr[bestResultId])
                outp.add(output[bestResultId])
                resultId = id
                bestResultId = index
                bestResult = null
                continue
            }

            val value = sumAll(eval(labelExpr[index], output[index]))
            if (isBest(value, bestResult)) {
                bestResult = value
                bestResultId = index
            }
            resultId = id
        }

        return eval(labels.flatMap { it.asIterable() }.toFloatArray(), outp.flatMap { it.asIterable() }.toFloatArray())
    }

    fun eval(labelExpr: FloatArray, output: FloatArray): Map<String, Float>

    fun isBest(candidate: Float, current: Float?): Boolean {
        return null == current || candidate < current
    }

    fun sumAll(vals: Map<String, Float>): Float {
        return vals.values.map { it }.sum()
    }
}

interface Validation: MetricEval {
    fun name(): String
}



data class RegressionValidation(
    val metric: Metric
): Validation, MetricEval by metric {
    enum class Metric(private val eval: MetricEval): MetricEval by eval {
        MSE(MseEval()),
        MAE(MaeEval()),
        R2(R2Eval())
    }

    override fun name(): String {
        return "regression-${metric.name}"
    }

    private class MseEval: MetricEval {

        override fun eval(labelExpr: FloatArray, output: FloatArray): Map<String, Float> {
            val accum = Stats.of(output.zip(labelExpr).map { it.first - it.second }.map { it * it })
            return mapOf("MSE" to accum.mean().toFloat())
        }
    }

    private class MaeEval: MetricEval {

        override fun eval(labelExpr: FloatArray, output: FloatArray): Map<String, Float> {
            val accum = Stats.of(output.zip(labelExpr).map { it.first - it.second }.map { abs(it) })
            return mapOf("MAE" to accum.mean().toFloat())
        }
    }

    private class R2Eval: MetricEval {

        override fun eval(labelExpr: FloatArray, output: FloatArray): Map<String, Float> {
            return try {
                val accum = PairedStatsAccumulator()
                labelExpr.zip(output).forEach { accum.add(it.first.toDouble(), it.second.toDouble()) }
                return mapOf("R2" to pow(accum.pearsonsCorrelationCoefficient(), 2.0).toFloat())
            } catch (ex: Throwable) {
                emptyMap()
            }
        }

        override fun isBest(candidate: Float, current: Float?): Boolean {
            return null == current || candidate > current
        }
    }
}

data class RegressionPercentileScoreValidation(
    val metric: Metric,
    val percentiles: IntArray = intArrayOf(50, 75, 90, 95, 99)
): Validation {
    enum class Metric(private val eval: MetricWithArgs): MetricWithArgs by eval {
        ABS_ERR(AbsErrEval())
    }

    override fun name(): String {
        return "percentiles-regression-${metric.name}"
    }

    override fun eval(labelExpr: FloatArray, output: FloatArray): Map<String, Float> {
        return metric.eval(percentiles, labelExpr, output)
    }

    private class AbsErrEval: MetricWithArgs {

        override fun eval(args: Any, labelExpr: FloatArray, output: FloatArray): Map<String, Float> {
            return try {
                val quantiles = percentiles().indexes(*(args as IntArray)).compute(
                    output.zip(labelExpr).map { it.first - it.second }.map { abs(it) }
                )

                quantiles.map { it.key.toString() to it.value.toFloat() }.toMap()
            } catch (ex: Throwable) {
                emptyMap()
            }
        }
    }

    interface MetricWithArgs {
        fun eval(args: Any, labelExpr: FloatArray, output: FloatArray): Map<String, Float>
    }
}