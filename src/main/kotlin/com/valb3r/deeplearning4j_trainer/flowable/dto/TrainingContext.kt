package com.valb3r.deeplearning4j_trainer.flowable.dto

import com.valb3r.deeplearning4j_trainer.flowable.FilePoolFlatBufferDatasetIterator
import com.valb3r.deeplearning4j_trainer.storage.Storage

data class TrainingContext(
    val inputDataPath: String,
    val outputDataPath: String,
    val trainingLogPath: String,
    val trainedModelPath: String,
    val modelPath: String? = null,
    val modelSpec: ModelSpec? = null,
    override val inputFiles: List<String>,
    val trainingSpec: TrainingSpec,
    override val datasetSize: Long,
    override var currentEpoch: Long,
    val loss: Double? = null,
    val updaterName: String? = null,
    val updaterStep: String? = null
): Context {
    fun trainingIterator(storage: Storage): FilePoolFlatBufferDatasetIterator {
        return FilePoolFlatBufferDatasetIterator(
            storage,
            datasetSize,
            trainingSpec.batchSize,
            trainingSpec.featureVars,
            trainingSpec.labelVars,
            inputFiles
        )
    }

    companion object {
        val NULL = TrainingContext(
            "N/A",
            "N/A",
            "N/A",
            "N/A",
            inputFiles = emptyList(),
            datasetSize = -1L,
            currentEpoch = -1,
            trainingSpec = TrainingSpec(
                0,
                emptyList(),
                emptyList(),
                0,
                0,
                updater = Updater("N/A", null, null),
               regularization = null,
                loss = Loss("N/A", emptyList())
            )
        )
    }
}