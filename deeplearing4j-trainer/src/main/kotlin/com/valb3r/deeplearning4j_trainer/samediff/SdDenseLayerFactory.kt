package com.valb3r.deeplearning4j_trainer.samediff

import com.valb3r.deeplearning4j_trainer.flowable.uniqName
import org.nd4j.autodiff.samediff.SDVariable
import org.nd4j.autodiff.samediff.SameDiff
import org.nd4j.autodiff.samediff.VariableType
import org.nd4j.linalg.api.buffer.DataType
import org.nd4j.linalg.api.rng.DefaultRandom
import org.nd4j.linalg.api.rng.Random
import org.nd4j.weightinit.WeightInitScheme
import org.nd4j.weightinit.impl.XavierInitScheme
import org.nd4j.weightinit.impl.ZeroInitScheme
import java.util.function.Function

class SdDenseLayerFactory (
    private val random: Random = DefaultRandom(123456),
    private val sd: SameDiff,
    private val nIn: Int,
    private val nOut: Int,
    private val dataType: DataType,
    private val weightShape: WeightShape,
    private val activation: Function<SDVariable, SDVariable>,
    private val initScheme: WeightInitScheme?
) {

    private constructor(builder: Builder) : this(
        builder.random,
        builder.sd!!,
        builder.nIn!!,
        builder.nOut!!,
        builder.dataType!!,
        builder.weightShape,
        builder.activation!!,
        builder.initScheme
    )

    fun create(name: String, input: SDVariable, forReuse: Boolean = false): SDVariable {
        var initWith = initScheme
        if (null == initWith) {
            // For images
            initWith = XavierInitScheme('c', nIn.toDouble(), nOut.toDouble())
        }

        val w = when (weightShape) {
            WeightShape.STANDARD -> sd.v("$name-w", initWith, dataType, forReuse, nIn.toLong(), nOut.toLong())
            WeightShape.DIAGONAL -> makeDiagonal(name, initWith, forReuse)
        }
        val b = sd.v("$name-b", ZeroInitScheme(), dataType, forReuse, 1, nOut.toLong())
        val z = input.mmul(w).add(b).rename(sd.uniqName("$name-weighted-input"))
        return activation.apply(z).rename(sd.uniqName("$name-out"))
    }

    private fun makeDiagonal(name: String, initWith: WeightInitScheme, forReuse: Boolean = true): SDVariable {
        if (nIn != nOut) {
            throw IllegalArgumentException("Diagonal weights must have same number of inputs and outputs, got nIn=$nIn,nOut=$nOut")
        }

        return sd.math.diag(sd.v("$name-w-diag", initWith, dataType, forReuse, nIn.toLong()))
    }

    fun SameDiff.v(name: String, weightInitScheme: WeightInitScheme, dataType: DataType, forReuse: Boolean = true, vararg shape: Long): SDVariable {
        val existing = getVariable(name)
        if (null != existing && !forReuse) {
            throw IllegalArgumentException("Variable $name already exists")
        }

        if (null == existing && forReuse) {
            throw IllegalArgumentException("Variable $name does not exist yet")
        }

        if (null != existing) {
            return existing
        }

        return this.`var`(name, VariableType.VARIABLE, weightInitScheme, dataType, *shape)
    }

    companion object {
        inline fun builder(block: Builder.() -> Unit) = Builder().apply(block)
        inline fun build(block: Builder.() -> Unit) = Builder().apply(block).build()
    }

    class Builder {
        var random: Random = DefaultRandom(123456)
        var sd: SameDiff? = null
        var nIn: Int? = null
        var nOut: Int? = null
        var dataType: DataType? = null
        var weightShape: WeightShape = WeightShape.STANDARD
        var activation: ((SDVariable) -> SDVariable)? = null
        var initScheme: WeightInitScheme? = null

        fun build() = SdDenseLayerFactory(this)

        inline fun update(block: Builder.() -> Unit) = this.apply(block)
    }
}

enum class WeightShape {
    STANDARD,
    DIAGONAL
}