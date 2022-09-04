package com.valb3r.deeplearning4j_trainer.listeners

import org.nd4j.autodiff.listeners.At
import org.nd4j.autodiff.listeners.ListenerEvaluations
import org.nd4j.autodiff.listeners.Loss
import org.nd4j.autodiff.listeners.impl.HistoryListener
import org.nd4j.autodiff.samediff.SameDiff
import org.nd4j.linalg.dataset.api.MultiDataSet

class TrainingPerfListener(evals: ListenerEvaluations, private val freq: Int = 1_000): HistoryListener(evals) {

    override fun iterationDone(sd: SameDiff, at: At, dataSet: MultiDataSet, loss: Loss) {
        super.iterationDone(sd, at, dataSet, loss)

        if (0 != at.iteration() % freq || report.trainingHistory.isEmpty()) {
            return
        }

        report.finalTrainingEvaluations().evaluations.forEach {
            println("Report at ${at.iteration()} for ${it.key} is: ")
            it.value.forEach { stat ->
                println("${stat.stats()}")
            }
        }

    }
}