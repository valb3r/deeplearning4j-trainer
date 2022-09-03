package com.valb3r.deeplearning4j_trainer.flowable.dto

import com.valb3r.deeplearning4j_trainer.flowable.FilePoolFlatBufferDatasetIterator
import com.valb3r.deeplearning4j_trainer.storage.Storage

data class ValidationContext(
    val validationDataPath: String,
    val trainedModelPath: String,
    override val inputFiles: List<String> = emptyList(),
    val validationSpec: ValidationSpec,
    val validations: List<ValidationAndScore>,
    override val datasetSize: Long? = null,
    override var currentEpoch: Long,
    val loss: Double? = null
): Context {
    fun validationIterator(storage: Storage): FilePoolFlatBufferDatasetIterator {
        return FilePoolFlatBufferDatasetIterator(
            storage,
            validationSpec.batchSize,
            validationSpec.featureVars,
            validationSpec.labelVars,
            inputFiles,
            resultSetIdName = validationSpec.resultSequenceIdName
        )
    }

    companion object {
        val NULL = ValidationContext(
            "N/A",
            "N/A",
            emptyList(),
            ValidationSpec(0, emptyList(), emptyList(), 0, emptyList()),
            datasetSize = null,
            currentEpoch = -1,
            validations = emptyList()
        )
    }
}