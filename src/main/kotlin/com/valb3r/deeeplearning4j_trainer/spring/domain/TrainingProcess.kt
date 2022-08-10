package com.valb3r.deeeplearning4j_trainer.spring.domain

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.valb3r.deeeplearning4j_trainer.spring.config.ObjectMapperConfig.Configurer.configure
import com.valb3r.deeeplearning4j_trainer.spring.flowable.dto.TrainingContext
import org.hibernate.annotations.UpdateTimestamp
import org.nd4j.autodiff.samediff.SameDiff
import java.io.File
import java.time.Instant
import javax.persistence.*

private val ctxMapper = configure(ObjectMapper().registerModule(KotlinModule.Builder().build()))

@Entity
class TrainingProcess(
    processId: String,
    trainedModelPath: String,
    businessKey: String,
    processDefinitionName: String,
    trainingContext: TrainingContext
) {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null
    var trainedModelPath: String
    val businessKey: String
    val processDefinitionName: String
    @Column(unique = true) val processId: String
    @Lob var trainingContext: String
    var currentIter: Long? = null
    var bestPerformingEpoch: Long? = null
    var bestPerformingTrainedModelPath: String? = null
    var bestLoss: Double? = null
    @Lob var errorStacktrace: String? = null
    var errorMessage: String? = null
    var notes: String? = null
    var completed = false
    @UpdateTimestamp var updatedAt: Instant = Instant.EPOCH

    init {
        this.processId = processId
        this.trainedModelPath = trainedModelPath
        this.businessKey = businessKey
        this.processDefinitionName = processDefinitionName
        this.trainingContext = ctxMapper.writeValueAsString(trainingContext)
    }

    fun updatePerformance(sd: SameDiff, loss: Double, epoch: Long) {
        if (loss > (bestLoss ?: Double.MAX_VALUE)) {
            return
        }

        val ctx = getCtx()
        val output = File(ctx.outputDataPath).resolve("best-perf-model-${businessKey}.fb")
        sd.asFlatFile(output)
        bestPerformingEpoch = epoch
        bestLoss = loss
        bestPerformingTrainedModelPath = output.absolutePath
    }

    fun setCtx(ctx: TrainingContext) {
        trainingContext = ctxMapper.writeValueAsString(ctx)
    }

    fun getCtx(): TrainingContext {
        return ctxMapper.readValue(trainingContext, TrainingContext::class.java)
    }

    fun modelPath(latest: Boolean): String {
        if (latest) {
            return trainedModelPath
        }

        return bestPerformingTrainedModelPath!!
    }

    fun getRevertedStacktrace(): String? {
        return errorStacktrace?.lines()?.reversed()?.joinToString("\n")
    }
}