package com.valb3r.deeplearning4j_trainer.flowable

import com.valb3r.deeplearning4j_trainer.flowable.dto.TrainingSpec
import com.valb3r.deeplearning4j_trainer.flowable.dto.Updater
import org.nd4j.autodiff.samediff.SameDiff
import org.nd4j.autodiff.samediff.TrainingConfig
import org.nd4j.linalg.learning.config.Adam
import org.nd4j.linalg.learning.config.IUpdater
import org.nd4j.linalg.learning.config.Nesterovs
import org.nd4j.linalg.learning.regularization.L1Regularization
import org.nd4j.linalg.learning.regularization.L2Regularization
import org.nd4j.linalg.learning.regularization.Regularization
import org.nd4j.linalg.schedule.ExponentialSchedule
import org.nd4j.linalg.schedule.ISchedule
import org.nd4j.linalg.schedule.MapSchedule
import java.util.*

fun makeTrainConfig(sd: SameDiff, spec: TrainingSpec) {
    sd.trainingConfig = TrainingConfig.Builder()
        .updater(makeUpdater(spec.updater))
        .regularization(*makeRegularization(spec))
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

private fun makeRegularization(spec: TrainingSpec): Array<Regularization> {
    val regularization = spec.regularization ?: return arrayOf()
    val created = when(regularization.type.toUpperCase(Locale.ENGLISH)) {
        "L1" -> L1Regularization(regularization.params!![0])
        "L2" -> L2Regularization(regularization.params!![0])
        else -> throw IllegalArgumentException("Unknown regularization ${regularization.type}")
    }
    return arrayOf(created)
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