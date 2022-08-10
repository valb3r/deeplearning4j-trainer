package com.valb3r.deeeplearning4j_trainer.spring.flowable

import com.valb3r.deeeplearning4j_trainer.spring.repository.TrainingProcessRepository
import com.valb3r.deeeplearning4j_trainer.spring.service.poisonPill
import org.flowable.engine.delegate.DelegateExecution
import org.nd4j.autodiff.listeners.At
import org.nd4j.autodiff.listeners.BaseListener
import org.nd4j.autodiff.listeners.Loss
import org.nd4j.autodiff.listeners.Operation
import org.nd4j.autodiff.listeners.impl.ScoreListener
import org.nd4j.autodiff.samediff.SameDiff
import org.nd4j.linalg.dataset.api.MultiDataSet
import org.springframework.stereotype.Service

@Service("modelTrainer")
class ModelTrainer(private val trainingRepo: TrainingProcessRepository): WrappedJavaDelegate() {

    override fun doExecute(execution: DelegateExecution) {
        if (poisonPill.remove(execution.processInstanceId)) {
            throw IllegalArgumentException("Aborted")
        }

        val ctx = execution.getContext()
        val sd = execution.loadSameDiff()

        val lossListener = LossListener()
        sd.fit(
            ctx.trainingIterator(),
            1,
            ScoreListener(10),
            lossListener
        )

        execution.storeSameDiff(sd)
        execution.updateContext { it.copy(
            loss = lossListener.loss,
            updaterName = sd.trainingConfig.updater.javaClass.simpleName,
            updaterStep = sd.trainingConfig.updater.getLearningRate(0, lossListener.epoch.toInt()).toString()
        ) }
        val process = trainingRepo.findByProcessId(execution.processInstanceId)!!
        process.setCtx(ctx)
        process.updatePerformance(sd, lossListener.loss, lossListener.epoch)
        trainingRepo.save(process)
    }

    private class LossListener(var loss: Double = 0.0, var epoch: Long = -1): BaseListener() {
        override fun isActive(operation: Operation?): Boolean {
            return true
        }

        override fun iterationDone(sd: SameDiff, at: At, dataSet: MultiDataSet, loss: Loss) {
            this.loss = loss.totalLoss()
            this.epoch = at.epoch().toLong()
        }
    }
}