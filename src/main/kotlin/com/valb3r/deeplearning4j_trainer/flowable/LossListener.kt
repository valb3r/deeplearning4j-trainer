package com.valb3r.deeplearning4j_trainer.flowable

import org.nd4j.autodiff.listeners.At
import org.nd4j.autodiff.listeners.BaseListener
import org.nd4j.autodiff.listeners.Loss
import org.nd4j.autodiff.listeners.Operation
import org.nd4j.autodiff.samediff.SameDiff
import org.nd4j.linalg.dataset.api.MultiDataSet

class LossListener(var loss: Double = 0.0, var epoch: Long = -1): BaseListener() {
    override fun isActive(operation: Operation?): Boolean {
        return true
    }

    override fun iterationDone(sd: SameDiff, at: At, dataSet: MultiDataSet, loss: Loss) {
        this.loss = loss.totalLoss()
        this.epoch = at.epoch().toLong()
    }
}