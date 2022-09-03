package com.valb3r.deeplearning4j_trainer.flowable.training

import com.valb3r.deeplearning4j_trainer.flowable.*
import com.valb3r.deeplearning4j_trainer.flowable.dto.TrainingContext
import com.valb3r.deeplearning4j_trainer.repository.TrainingProcessRepository
import com.valb3r.deeplearning4j_trainer.storage.StorageService
import org.flowable.engine.delegate.DelegateExecution
import org.nd4j.autodiff.listeners.impl.ScoreListener
import org.nd4j.autodiff.samediff.SameDiff
import org.springframework.stereotype.Service

@Service("modelTrainer")
class ModelTrainer(private val trainingRepo: TrainingProcessRepository, private val storage: StorageService): WrappedJavaDelegate() {

    override fun doExecute(execution: DelegateExecution) {
        val ctx = execution.getContext()
        val sd = execution.loadSameDiff(storage)

        val iter = ctx!!.trainingIterator(storage)
        val lossListener = LossListener()
        val modelSavingListener = ModelSavingListener(1000, execution, sd, storage)

        sd.fit(
            iter,
            1,
            ScoreListener(10),
            lossListener,
            modelSavingListener
        )

        execution.storeSameDiff(sd, storage)
        execution.updateContext { it.copy(
            loss = lossListener.loss,
            updaterName = sd.trainingConfig.updater.javaClass.simpleName,
            updaterStep = sd.trainingConfig.updater.getLearningRate(0, lossListener.epoch.toInt()).toString(),
            datasetSize = iter.computedDatasetSize
        ) }

        updateProcess(sd, execution, ctx, lossListener)
    }

    private fun updateProcess(sd: SameDiff, execution: DelegateExecution, ctx: TrainingContext, lossListener: LossListener) {
        txOper.execute {
            val process = trainingRepo.findByProcessId(execution.processInstanceId)!!
            process.setCtx(ctx)
            process.completed = false
            process.updatePerformance(sd, lossListener.loss, lossListener.epoch, storage)
            trainingRepo.save(process)
        }
    }
}