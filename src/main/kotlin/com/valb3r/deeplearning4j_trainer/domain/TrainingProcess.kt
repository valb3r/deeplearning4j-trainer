package com.valb3r.deeplearning4j_trainer.domain

import com.valb3r.deeplearning4j_trainer.flowable.dto.TrainingContext
import com.valb3r.deeplearning4j_trainer.storage.Storage
import com.valb3r.deeplearning4j_trainer.storage.resolve
import org.hibernate.annotations.Type
import org.nd4j.autodiff.samediff.SameDiff
import java.io.File
import java.nio.channels.Channels
import javax.persistence.Column
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

    @Lob
    var trainingContext: ByteArray

    init {
        this.processId = processId
        this.trainedModelPath = trainedModelPath
        this.businessKey = businessKey
        this.processDefinitionName = processDefinitionName
        this.trainingContext = domainCtxMapper.writeValueAsBytes(trainingContext)
    }

    fun updatePerformance(sd: SameDiff, loss: Double, epoch: Long, storage: Storage) {
        if (loss > (bestLoss ?: Double.MAX_VALUE)) {
            return
        }

        val ctx = getCtx()
        val output = ctx!!.outputDataPath.resolve("best-perf-model-${businessKey}.fb")
        storage.write(output).use { Channels.newChannel(it).write(sd.asFlatBuffers(true)) }
        bestPerformingEpoch = epoch
        bestLoss = loss
        bestPerformingTrainedModelPath = output
    }

    fun setCtx(ctx: TrainingContext) {
        trainingContext = domainCtxMapper.writeValueAsBytes(ctx)
    }

    override fun getCtx(): TrainingContext? {
        return domainCtxMapper.readValue(trainingContext, TrainingContext::class.java)
    }
}