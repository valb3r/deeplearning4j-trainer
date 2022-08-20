package com.valb3r.deeplearning4j_trainer.listeners.samediff

import org.nd4j.autodiff.listeners.At
import org.nd4j.autodiff.listeners.BaseListener
import org.nd4j.autodiff.listeners.Loss
import org.nd4j.autodiff.listeners.Operation
import org.nd4j.autodiff.samediff.SameDiff
import org.nd4j.linalg.dataset.api.MultiDataSet
import java.io.File

class ModelSavingListener(
    private val dumpEachN: Int = 10_000,
    private val path: String = "/Users/valentynberezin/IdeaProjects/deeplearning4j_trainer/res/"
): BaseListener() {

    override fun isActive(operation: Operation?): Boolean {
        return true
    }

    override fun iterationDone(sd: SameDiff, at: At, dataSet: MultiDataSet, loss: Loss?) {
        if (0 != at.iteration() % dumpEachN) {
            return
        }

        val modelDir = File(path)
        if (!modelDir.exists()) {
            modelDir.mkdir()
        }

        val modelName = "mdl-sd-${sd.variables.size}-at-${at.iteration()}.fb"
        sd.save(modelDir.resolve(modelName), true)
    }
}