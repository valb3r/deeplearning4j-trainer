package com.valb3r.deeeplearning4j_trainer.spring.flowable

import com.valb3r.deeeplearning4j_trainer.samediff.SdDenseLayerFactory
import com.valb3r.deeeplearning4j_trainer.samediff.SirenLayerFactory
import com.valb3r.deeeplearning4j_trainer.samediff.WeightShape
import com.valb3r.deeeplearning4j_trainer.spring.flowable.dto.*
import org.flowable.engine.delegate.DelegateExecution
import org.nd4j.autodiff.samediff.SDIndex
import org.nd4j.autodiff.samediff.SDVariable
import org.nd4j.autodiff.samediff.SameDiff
import org.nd4j.linalg.api.buffer.DataType
import org.nd4j.linalg.api.rng.distribution.impl.NormalDistribution
import org.nd4j.linalg.factory.Nd4j
import org.nd4j.weightinit.WeightInitScheme
import org.nd4j.weightinit.impl.DistributionInitScheme
import org.nd4j.weightinit.impl.XavierInitScheme
import org.springframework.stereotype.Service

@Service("newModelGenerator")
class NewModelGenerator: WrappedJavaDelegate() {

    override fun doExecute(execution: DelegateExecution) {
        val createdReferencableLayersByName = mutableMapOf<String, SdDenseLayerFactory>()
        val modelSpec = execution.getContext().modelSpec!!
        val trainingSpec = execution.getContext().trainingSpec!!
        val sd = SameDiff.create()

        trainingSpec.featureVars.forEach { sd.placeHolder(it, DataType.FLOAT, -1, 1) }
        trainingSpec.labelVars.forEach { sd.placeHolder(it, DataType.FLOAT, -1, 1) }
        var prevLayer: SDVariable? = null
        for (layer in modelSpec.layers) {
            prevLayer = when (layer) {
                is SirenLayerDef -> makeSirenLayer(sd, layer, prevLayer)
                is DiagonalLayerDef -> makeDenseLayer(sd, layer, prevLayer, createdReferencableLayersByName, weightShape = WeightShape.DIAGONAL)
                is DenseLayerDef -> makeDenseLayer(sd, layer, prevLayer, createdReferencableLayersByName)
                is ConcatLayerDef -> makeConcatLayer(sd, layer)
                is ReferenceLayerDef -> makeReferenceLayer(sd, layer, prevLayer, createdReferencableLayersByName)
                is PadLayerDef -> makePadLayer(sd, layer)
                is SumLayerDef -> makeAddSumLayer(sd, layer)
                else -> throw IllegalArgumentException("Unknown layer type $layer")
            }
        }
        makeLoss(sd, trainingSpec.loss)
        makeTrainConfig(sd, trainingSpec)
        execution.storeSameDiff(sd)
    }

    private fun makeConcatLayer(sd: SameDiff, layerDef: ConcatLayerDef): SDVariable {
        return sd.concat(layerDef.concatDim, *layerDef.inputVariables!!.map { extractVarExpr(sd, it) }.toTypedArray())
            .rename(sd.uniqName(layerDef.name))
    }

    private fun makeSirenLayer(sd: SameDiff, layerDef: SirenLayerDef, prevLayer: SDVariable?): SDVariable {
        val builder = SirenLayerFactory.builder {
            this.sd = sd
            this.nIn = layerDef.`in`
            this.nRepeat = layerDef.repeatFactor
        }

        val input: SDVariable = deduceInput(sd, layerDef, prevLayer)
        val built = builder.build()
        val name = sd.uniqName(arrayOf(layerDef.outputVarName, layerDef.name).filterNotNull().first())
        return built.create(name, input).variable.rename(name)
    }

    private fun makeDenseLayer(
        sd: SameDiff,
        layerDef: DenseLayerDef,
        prevLayer: SDVariable?,
        createdReferencableLayersByName: MutableMap<String, SdDenseLayerFactory>,
        weightShape: WeightShape = WeightShape.STANDARD
    ): SDVariable {
        val denseBuilder = SdDenseLayerFactory.builder {
            this.sd = sd
            this.dataType = DataType.FLOAT
            this.activation = makeActivation(sd, layerDef.activation)
            this.initScheme = makeInitScheme(layerDef.initScheme, layerDef.`in`, layerDef.`out`)
            this.weightShape = weightShape
            this.nIn = layerDef.`in`
            this.nOut = layerDef.`out`
        }
        val input: SDVariable = deduceInput(sd, layerDef, prevLayer)
        val builder = denseBuilder.build()
        createdReferencableLayersByName[layerDef.name] = builder
        val built = builder.create(layerDef.name, input)
        built.rename(sd.uniqName(arrayOf(layerDef.outputVarName, layerDef.name).filterNotNull().first()))
        return built
    }

    private fun makeReferenceLayer(sd: SameDiff, layerDef: ReferenceLayerDef, prevLayer: SDVariable?, createdReferencableLayersByName: MutableMap<String, SdDenseLayerFactory>): SDVariable {
        val refBuilder = createdReferencableLayersByName[layerDef.reference] ?: throw IllegalArgumentException("Layer ${layerDef.reference} does not exist (yet?)")
        val input: SDVariable = deduceInput(sd, layerDef, prevLayer)
        return refBuilder.create(layerDef.reference, input, true).rename(uniqName(sd, layerDef))
    }

    private fun uniqName(
        sd: SameDiff,
        layerDef: LayerDef
    ) = sd.uniqName(arrayOf(layerDef.outputVarName, layerDef.name).filterNotNull().first())

    private fun makePadLayer(sd: SameDiff, layerDef: PadLayerDef): SDVariable {
        return sd.nn().pad(extractVarExpr(sd, layerDef.inputVariables!![0]), sd.constant(Nd4j.create(layerDef.dimensions)), layerDef.value)
            .rename(uniqName(sd, layerDef))
    }

    private fun makeAddSumLayer(sd: SameDiff, layerDef: SumLayerDef): SDVariable {
        var target: SDVariable = sd.constant(0.0f)
        layerDef.inputVariables!!.forEach {
            val value = extractVarExpr(sd, it)
            target = target.add(value)
        }
        layerDef.value?.let { target = target.add(it) }
        return target.rename(uniqName(sd, layerDef))
    }

    private fun deduceInput(
        sd: SameDiff,
        layerDef: LayerDef,
        prevLayer: SDVariable?
    ): SDVariable {
        return layerDef.inputVariables?.get(0)?.let { sd.getVariable(it) } ?: prevLayer
        ?: throw IllegalArgumentException("Layer ${layerDef.name} should have its input defined")
    }

    private fun makeActivation(sd: SameDiff, activation: Activation?): (SDVariable) -> SDVariable {
        return when (activation?.type) {
            "relu" -> { it -> sd.nn.relu(it, activation.params?.get(0) ?: 0.0) }
            "leakyRelu" -> { it -> sd.nn.leakyRelu(it, activation.params?.get(0) ?: 0.0) }
            "tanh" -> { it -> sd.math.tanh(it) }
            "identity" -> { it -> sd.identity(it) }
            else -> { it -> sd.math.tanh(it) }
        }
    }

    private fun makeInitScheme(initScheme: InitScheme?, nIn: Int, nOut: Int): WeightInitScheme {
        return when (initScheme?.type) {
            "xavier" -> XavierInitScheme('c', initScheme.params?.get(0) ?: nIn.toDouble(), initScheme.params?.get(0) ?: nOut.toDouble())
            "normal" -> DistributionInitScheme('c', NormalDistribution(initScheme.params?.get(0) ?: 0.0, initScheme.params?.get(0) ?: 0.001))
            else -> XavierInitScheme('c', nIn.toDouble(), nOut.toDouble())
        }
    }

    private fun makeLoss(sd: SameDiff, loss: Loss) {
        when (loss.type) {
            "mse" -> makeMseLoss(sd, loss.entries)
            else -> throw IllegalArgumentException("Unknown loss type ${loss.type}")
        }
    }

    private fun makeMseLoss(sd: SameDiff, entries: List<LossItem>) {
        if (entries.isEmpty()) {
            throw IllegalArgumentException("Empty loss entries")
        }

        var lossValue = sd.constant(0.0)
        for (entry in entries) {
            lossValue = lossValue.add(
                sd.mean(
                    sd.math.squaredDifference(extractVarExpr(sd, entry.varName), sd.getVariable(entry.labelName))
                        .rename(sd.uniqName("squared-diff-${entry.varName}-${entry.labelName}"))
                ).mul(entry.weight ?: 1.0).rename(sd.uniqName("mse-${entry.varName}-${entry.labelName}"))
            )
        }
        lossValue.markAsLoss()
    }

    private fun extractVarExpr(sd: SameDiff, varName: String): SDVariable {
        val negate = varName.startsWith("-")
        val applyNegate: (SDVariable) -> SDVariable = { sdVar: SDVariable ->
            if (negate) sdVar.neg().rename("-${sdVar.name()}") else sdVar
        }

        var computedName = varName
        if (negate) {
            computedName = varName.substringAfter("-")
        }

        val shapeDesc = computedName.substringAfter("@", "")
        computedName = computedName.substringBefore("@")

        val mulFactor = computedName.substringAfter("*", "")
        computedName = computedName.substringBefore("*")

        if (!computedName.contains("[")) {
            return applyNegate(sd.getVariable(computedName) ?: throw IllegalArgumentException("Unknown variable $computedName"))
        }

        val index = mutableListOf<SDIndex>()
        val range = computedName.substringAfter("[").substringBefore("]")
        for (pointer in range.split(":")) {
            when {
                pointer.isEmpty() -> index.add(SDIndex.all())
                pointer.contains("-") -> index.add(SDIndex.interval(pointer.substringBefore("-").toInt(), pointer.substringAfter("-").toInt()))
                else -> index.add(SDIndex.point(pointer.toLong()))
            }
        }

        var value = applyNegate(sd.getVariable(computedName.substringBefore("[")).get(*index.toTypedArray()))
        if ("" != shapeDesc) {
            value = value.reshape(*shapeDesc.split(";").map { it.toInt() }.toIntArray()).rename("${value.name()} shape ${shapeDesc}")
        }
        if ("" != mulFactor) {
            value = value.mul(mulFactor.toDouble()).rename("${value.name()} * ${mulFactor}")
        }
        return value
    }

}