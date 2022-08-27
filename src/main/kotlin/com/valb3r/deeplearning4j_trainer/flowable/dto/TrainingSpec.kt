package com.valb3r.deeplearning4j_trainer.flowable.dto

import org.nd4j.linalg.schedule.ScheduleType

data class TrainingSpec(
    val seed: Int,
    val featureVars: List<String>,
    val labelVars: List<String>,
    val numEpochs: Int,
    val batchSize: Int,
    val loadUpdaterStateFromFbOnNew: Boolean = true,
    val updater: Updater,
    val regularization: Regularization?,
    val loss: Loss
)

data class Updater(val type: String, val params: List<Double>?, val schedule: Schedule?)

data class Regularization(val type: String, val params: List<Double>?, val schedule: Schedule?)

data class Schedule(
    val type: String,
    val params: List<Double>?,
    val mapParams: Map<Int, List<Double>>?,
    val mode: ScheduleType = ScheduleType.ITERATION
)

data class Loss(val type: String, val entries: List<LossItem>)

data class LossItem(val varName: String, var labelName: String, val weight: Double? = 1.0)