package com.valb3r.deeplearning4j_trainer.flowable.training

import com.valb3r.deeplearning4j_trainer.flowable.dto.TrainingContext
import org.springframework.stereotype.Service

@Service("trainingConditionEvaluator")
class TrainingConditionEvaluator {

    fun newModelNeeded(ctx: TrainingContext): Boolean {
        return null == ctx.modelPath
    }

    fun hasMoreDataToTrain(ctx: TrainingContext): Boolean {
        return ctx.currentEpoch <= ctx.trainingSpec.numEpochs
    }
}