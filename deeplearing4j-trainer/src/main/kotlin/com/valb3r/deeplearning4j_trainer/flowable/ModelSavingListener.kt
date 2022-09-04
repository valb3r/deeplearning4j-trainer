package com.valb3r.deeplearning4j_trainer.flowable

import com.valb3r.deeplearning4j_trainer.storage.interfaces.Storage
import org.flowable.engine.delegate.DelegateExecution
import org.nd4j.autodiff.listeners.At
import org.nd4j.autodiff.listeners.BaseListener
import org.nd4j.autodiff.listeners.Loss
import org.nd4j.autodiff.listeners.Operation
import org.nd4j.autodiff.samediff.SameDiff
import org.nd4j.linalg.dataset.api.MultiDataSet

class ModelSavingListener(
    private val sameDiff: SameDiff,
    private val frequency: Int,
    private val execution: DelegateExecution,
    private val storage: Storage
): BaseListener()  {

    override fun isActive(operation: Operation?): Boolean {
        return true
    }

    override fun iterationDone(sd: SameDiff, at: At, dataSet: MultiDataSet, loss: Loss) {
        if (0 != at.iteration() % frequency) {
            return
        }

        // Do not use 'sd' for storage
        execution.storeSameDiff(sameDiff, storage)
    }
}