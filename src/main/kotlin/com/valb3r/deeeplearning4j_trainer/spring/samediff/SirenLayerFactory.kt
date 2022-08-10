package com.valb3r.deeeplearning4j_trainer.samediff

import org.nd4j.autodiff.samediff.SDVariable
import org.nd4j.autodiff.samediff.SameDiff
import org.nd4j.linalg.api.buffer.DataType
import org.nd4j.linalg.factory.Nd4j

class SirenLayerFactory(
    private val sd: SameDiff,
    private val nIn: Int,
    private val nRepeat: Int
) {

    private constructor(builder: Builder) : this(
        builder.sd!!,
        builder.nIn!!,
        builder.nRepeat!!
    )

    fun create(name: String, input: SDVariable): Res {
        val expanded = sd.tile(input.reshape(-1, 1), 1, 2 * nRepeat).rename("$name-embedded-input-expanded")

        val expandedPhases = sd.concat(
            0,
            sd.tile(sd.constant(Nd4j.create(floatArrayOf(0.0f))), nRepeat),
            sd.tile(sd.constant(Nd4j.create(floatArrayOf(Math.PI.toFloat() / 2.0f))), nRepeat)
        ).rename("$name-expanded-phases")

        val linSpace = sd.linspace(DataType.FLOAT, 0.0, (nRepeat - 1).toDouble(), nRepeat.toLong())
        val frequencyMult = sd.tile(sd.math.pow(sd.constant(2.0), linSpace), 2).mul(Math.PI).rename("$name-frequencies")
        val evalArgValue = expanded.mul(frequencyMult).rename("$name-scaled-inputs").add(expandedPhases).rename("$name-embedded-expanded-phases")
        val nOutStride = 2 * nIn * nRepeat
        // Produces i.e. for `variable`:
        // sin(Pi * x1) cos(Pi * x1) sin(2 * Pi * x1) cos(2 * Pi * x1) sin(Pi * y1) cos(Pi * y1) sin(2 * Pi * y1) cos(2 * Pi * y1) sin(Pi * z1) cos(Pi * z1) sin(2 * Pi * z1) cos(2 * Pi * z1)
        // sin(Pi * x2) cos(Pi * x2) sin(2 * Pi * x2) cos(2 * Pi * x2) sin(Pi * y2) cos(Pi * y2) sin(2 * Pi * y2) cos(2 * Pi * y2) sin(Pi * z2) cos(Pi * z2) sin(2 * Pi * z2) cos(2 * Pi * z2)
        return Res(
            sd.math().sin(evalArgValue).rename("$name-embedded-enc-value").reshape(-1, nOutStride)
                .rename("$name-embedded-enc-output"),
            nOutStride
        )
    }

    data class Res(val variable: SDVariable, val outSize: Int)

    companion object {
        inline fun builder(block: Builder.() -> Unit) = Builder().apply(block)
        inline fun build(block: Builder.() -> Unit) = Builder().apply(block).build()
    }

    class Builder {
        var sd: SameDiff? = null
        var nIn: Int? = null
        var nRepeat: Int? = null

        fun build() = SirenLayerFactory(this)

        inline fun update(block: Builder.() -> Unit) = this.apply(block)
    }
}