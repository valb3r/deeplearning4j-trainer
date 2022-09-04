package com.valb3r.deeplearning4j_trainer.flowable.dto

data class ValidationSpec(
    val seed: Int,
    val featureVars: List<String>,
    val labelVars: List<String>,
    val batchSize: Int,
    val validations: List<ValidationAndScore>,
    val resultSequenceIdName: String? = null
)