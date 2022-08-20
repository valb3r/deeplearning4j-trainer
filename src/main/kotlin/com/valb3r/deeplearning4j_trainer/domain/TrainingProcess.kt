package com.valb3r.deeplearning4j_trainer.domain

import com.valb3r.deeplearning4j_trainer.flowable.dto.TrainingContext
import org.nd4j.autodiff.samediff.SameDiff
import java.io.File
import javax.persistence.Entity
import javax.persistence.Lob

@Entity
class TrainingProcess(
    processId: String,
    trainedModelPath: String,
    businessKey: String,
    processDefinitionName: String,
    trainingContext: TrainingContext?
): Process<TrainingContext>() {

    var currentIter: Long? = null
    var bestLoss: Double? = null
    @Lob var trainingContext: String

    init {
        this.processId = processId
        this.trainedModelPath = trainedModelPath
        this.businessKey = businessKey
        this.processDefinitionName = processDefinitionName
        this.trainingContext = domainCtxMapper.writeValueAsString(trainingContext)
    }

    fun updatePerformance(sd: SameDiff, loss: Double, epoch: Long) {
        if (loss > (bestLoss ?: Double.MAX_VALUE)) {
            return
        }

        val ctx = getCtx()
        val output = File(ctx!!.outputDataPath).resolve("best-perf-model-${businessKey}.fb")
        sd.asFlatFile(output)
        bestPerformingEpoch = epoch
        bestLoss = loss
        bestPerformingTrainedModelPath = output.absolutePath
    }

    fun setCtx(ctx: TrainingContext) {
        trainingContext = domainCtxMapper.writeValueAsString(ctx)
    }

    override fun getCtx(): TrainingContext? {
        return domainCtxMapper.readValue(trainingContext, TrainingContext::class.java)
    }

    fun getRevertedStacktrace(): String? {
        return errorStacktrace?.lines()?.reversed()?.joinToString("\n")
    }
}