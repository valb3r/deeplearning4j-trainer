package com.valb3r.deeeplearning4j_trainer.spring.flowable

import com.valb3r.deeeplearning4j_trainer.spring.flowable.dto.TrainingSpec
import com.valb3r.deeeplearning4j_trainer.spring.flowable.dto.Updater
import org.nd4j.autodiff.samediff.SameDiff
import org.nd4j.autodiff.samediff.TrainingConfig
import org.nd4j.linalg.learning.config.Adam
import org.nd4j.linalg.learning.config.IUpdater
import org.nd4j.linalg.learning.config.Nesterovs
import org.nd4j.linalg.schedule.ExponentialSchedule
import org.nd4j.linalg.schedule.ISchedule
import org.nd4j.linalg.schedule.MapSchedule

fun makeTrainConfig(sd: SameDiff, spec: TrainingSpec) {
    sd.trainingConfig = TrainingConfig.Builder()
        .updater(makeUpdater(spec.updater))
        .dataSetFeatureMapping(spec.featureVars)
        .dataSetLabelMapping(spec.labelVars)
        .build()
}

private fun makeUpdater(updater: Updater): IUpdater {
    val created = when(updater.type) {
        "nesterovs" -> Nesterovs(updater.params!![0], updater.params[1])
        "adam" -> Adam(updater.params!![0])
        else -> throw IllegalArgumentException("Unknown updater ${updater.type}")
    }
    makeSchedule(updater)?.apply { created.setLrAndSchedule(this.valueAt(0, 0), this) }
    return created
}

private fun makeSchedule(updater: Updater): ISchedule? {
    if (null == updater.schedule) {
        return null
    }

    val schedule = updater.schedule
    val created = when(schedule.type) {
        "exponential" -> ExponentialSchedule(schedule.mode, schedule.params!![0], schedule.params[1])
        "map" -> MapSchedule(schedule.mode, schedule.mapParams!!.map { it.key to it.value[0] }.toMap())
        else -> throw IllegalArgumentException("Unknown schedule ${schedule.type}")
    }

    return created
}