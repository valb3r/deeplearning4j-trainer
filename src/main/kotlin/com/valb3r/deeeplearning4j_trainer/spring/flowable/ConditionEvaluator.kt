package com.valb3r.deeeplearning4j_trainer.spring.flowable

import com.valb3r.deeeplearning4j_trainer.spring.flowable.dto.TrainingContext
import org.springframework.stereotype.Service

@Service("conditionEvaluator")
class ConditionEvaluator {

    fun newModelNeeded(ctx: TrainingContext): Boolean {
        return null == ctx.modelPath
    }

    fun hasMoreDataToTrain(ctx: TrainingContext): Boolean {
        return ctx.currentEpoch!! <= ctx.trainingSpec!!.numEpochs
    }
}