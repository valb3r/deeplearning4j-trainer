package com.valb3r.deeeplearning4j_trainer.spring.flowable

import mu.KotlinLogging
import org.flowable.engine.delegate.DelegateExecution
import org.nd4j.autodiff.samediff.SameDiff
import org.springframework.stereotype.Service
import java.io.File

private val logger = KotlinLogging.logger {}
@Service("modelLoader")
class ModelLoader: WrappedJavaDelegate() {

    override fun doExecute(execution: DelegateExecution) {
        val trainingSpec = execution.getContext().trainingSpec!!
        val sd = SameDiff.fromFlatFile(File(execution.getContext().modelPath!!), trainingSpec.loadUpdaterStateFromFbOnNew)

        if (!trainingSpec.loadUpdaterStateFromFbOnNew) {
            logger.info { "Not using updater state from SameDiff flat buffers" }
            makeTrainConfig(sd, trainingSpec)
        } else {
            logger.info { "Using updater state from SameDiff flat buffers" }
        }

        sd.asFlatFile(File(execution.getContext().trainedModelPath), trainingSpec.loadUpdaterStateFromFbOnNew)
    }
}