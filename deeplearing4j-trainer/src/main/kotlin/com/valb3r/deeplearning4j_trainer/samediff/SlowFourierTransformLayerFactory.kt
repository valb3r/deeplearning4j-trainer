package com.valb3r.deeplearning4j_trainer.samediff

import com.valb3r.deeplearning4j_trainer.flowable.uniqName
import org.nd4j.autodiff.samediff.SDVariable
import org.nd4j.autodiff.samediff.SameDiff
import org.nd4j.linalg.api.buffer.DataType

// Based on: https://brilliant.org/wiki/discrete-fourier-transform/
// http://jakevdp.github.io/blog/2013/08/28/understanding-the-fft/
class SlowFourierTransformLayerFactory(private val sd: SameDiff, private val nIn: Int, private val dataType: DataType) {

    private constructor(builder: Builder) : this(builder.sd!!, builder.nIn!!, builder.dataType!!)

    // Returns real and imaginary parts
    // Amplitude = fftToTransform[0].zip(fftToTransform[1]).slice(0 .. samplingFreq / 2).map { sqrt(it.first.pow(2) + it.second.pow(2)) / samplingFreq }.toDoubleArray(),
    // Phase = fftToTransform[0].zip(fftToTransform[1]).slice(0 .. samplingFreq / 2).map { atan2(it.second, it.first) }.toDoubleArray(),
    fun create(name: String, input: SDVariable): Array<SDVariable> {
        if (0 != nIn % 2) {
            throw IllegalArgumentException("Size of input (nIn) must be a power of 2")
        }

        val n = sd.range(0.0, nIn.toDouble(), 1.0, dataType).rename(sd.uniqName("n"))
        val k = n.reshape(nIn, 1).rename(sd.uniqName("k"))
        // e^(2πikn/N) = cos(2πkn/N) + i * sin(2πkn/N)
        // e^(-2πikn/N) = cos(2πkn/N) - i * sin(2πkn/N)
        //  np.exp(-2j * np.pi * k * n / N)
        val pix2divN = sd.constant((2.0 * Math.PI / nIn).toFloat())
        val mReal = sd.math.cos(pix2divN.mul(k).mul(n)).rename(sd.uniqName("m_ft_real"))
        val mImag = sd.math.sin(pix2divN.mul(k).mul(n)).rename(sd.uniqName("m_ft_imag"))
        val input_T = sd.transpose(input)
        val ftReal = sd.mmul(mReal, input_T).rename("$name-ft_real")
        val ftImag = sd.mmul(mImag, input_T).neg().rename("$name-ft_imag")
        return arrayOf(sd.transpose(ftReal), sd.transpose(ftImag))
    }

    fun createAmplitudeView(name: String, input: SDVariable): SDVariable {
        val dft = create(name, input)
        return sd.math.sqrt(sd.math.square(dft[0]).add(sd.math.square(dft[1]))).div(nIn.toDouble()).rename("$name-ft_amplitude")
    }

    companion object {
        inline fun builder(block: Builder.() -> Unit) = Builder().apply(block)
        inline fun build(block: Builder.() -> Unit) = Builder().apply(block).build()
    }

    class Builder {
        var sd: SameDiff? = null
        var nIn: Int? = null
        var dataType: DataType? = null

        fun build() = SlowFourierTransformLayerFactory(this)

        inline fun update(block: Builder.() -> Unit) = this.apply(block)
    }
}