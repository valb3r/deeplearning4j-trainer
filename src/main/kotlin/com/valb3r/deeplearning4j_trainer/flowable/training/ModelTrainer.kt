package com.valb3r.deeplearning4j_trainer.flowable.training

import com.valb3r.deeplearning4j_trainer.flowable.*
import com.valb3r.deeplearning4j_trainer.repository.TrainingProcessRepository
import com.valb3r.deeplearning4j_trainer.storage.StorageService
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
class ModelTrainer(private val trainingRepo: TrainingProcessRepository, private val storage: StorageService): WrappedJavaDelegate() {

    override fun doExecute(execution: DelegateExecution) {
        val ctx = execution.getContext()
        val sd = execution.loadSameDiff(storage)

        val iter = ctx!!.trainingIterator(storage)
        val lossListener = LossListener()
        sd.fit(
            iter,
            1,
            ScoreListener(10),
            lossListener
        )

        execution.storeSameDiff(sd, storage)
        execution.updateContext { it.copy(
            loss = lossListener.loss,
            updaterName = sd.trainingConfig.updater.javaClass.simpleName,
            updaterStep = sd.trainingConfig.updater.getLearningRate(0, lossListener.epoch.toInt()).toString(),
            datasetSize = iter.computedDatasetSize
        ) }
        val process = trainingRepo.findByProcessId(execution.processInstanceId)!!
        process.setCtx(ctx)
        process.completed = false
        process.updatePerformance(sd, lossListener.loss, lossListener.epoch, storage)
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