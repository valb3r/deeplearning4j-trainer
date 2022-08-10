package com.valb3r.deeeplearning4j_trainer.spring.flowable.dto

import com.valb3r.deeeplearning4j_trainer.spring.flowable.FilePoolFlatBufferDatasetIterator

data class TrainingContext(
    val inputDataPath: String,
    val outputDataPath: String,
    val trainingLogPath: String,
    val trainedModelPath: String,
    val modelPath: String? = null,
    val inputFiles: List<String> = emptyList(),
    val modelSpec: ModelSpec? = null,
    val trainingSpec: TrainingSpec? = null,
    val chunkIteratorRandom: ByteArray? = null,
    val datasetSize: Long? = null,
    val currentEpoch: Long? = null,
    val error: String? = null,
    val loss: Double? = null,
    val updaterName: String? = null,
    val updaterStep: String? = null
) {
    fun trainingIterator(): FilePoolFlatBufferDatasetIterator {
        return FilePoolFlatBufferDatasetIterator(
            datasetSize!!,
            trainingSpec!!.batchSize,
            trainingSpec.featureVars,
            trainingSpec.labelVars,
            inputFiles
        )
    }
}