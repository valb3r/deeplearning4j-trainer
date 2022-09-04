package com.valb3r.deeplearning4j_trainer.flowable

import com.valb3r.deeplearning4j_trainer.storage.StorageService
import com.valb3r.deeplearning4j_trainer.storage.wrapToByteBuffer
import mu.KotlinLogging
import org.flowable.engine.delegate.DelegateExecution
import org.nd4j.autodiff.samediff.SameDiff
import org.springframework.stereotype.Service
import java.nio.channels.Channels

private val logger = KotlinLogging.logger {}
@Service("modelLoader")
class ModelLoader(private val storage: StorageService): WrappedJavaDelegate() {

    override fun doExecute(execution: DelegateExecution) {
        val trainingSpec = execution.getContext()!!.trainingSpec
        val sd = SameDiff.fromFlatBuffers(
            storage.read(execution.getContext()!!.modelPath!!).wrapToByteBuffer(),
            trainingSpec.loadUpdaterStateFromFbOnNew
        )

        if (!trainingSpec.loadUpdaterStateFromFbOnNew) {
            logger.info { "Not using updater state from SameDiff flat buffers" }
            makeTrainConfig(sd, trainingSpec)
        } else {
            logger.info { "Using updater state from SameDiff flat buffers" }
        }

        storage.write(execution.getContext()!!.trainedModelPath).use {
            Channels.newChannel(it).write(sd.asFlatBuffers(trainingSpec.loadUpdaterStateFromFbOnNew))
        }
    }
}