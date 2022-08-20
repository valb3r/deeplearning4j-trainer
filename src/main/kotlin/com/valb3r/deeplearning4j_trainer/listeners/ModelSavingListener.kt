package com.valb3r.deeplearning4j_trainer.listeners

import org.deeplearning4j.nn.api.Model
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork
import org.deeplearning4j.optimize.api.BaseTrainingListener
import java.io.File

class ModelSavingListener(
    private val dumpEachN: Int = 10_000,
    private val path: String = "/Users/valentynberezin/IdeaProjects/deeplearning4j_trainer/res/"
): BaseTrainingListener() {
    override fun iterationDone(model: Model?, iteration: Int, epoch: Int) {
        if (0 != iteration % dumpEachN) {
            return
        }

        val modelDir = File(path)
        if (!modelDir.exists()) {
            modelDir.mkdir()
        }

        val mlModel = model as MultiLayerNetwork
        val modelName = "mdl-l${mlModel.layers.size}-at-${iteration}.fb"
        mlModel.save(modelDir.resolve(modelName), true)
    }
}