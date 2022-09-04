package com.valb3r.deeplearning4j_trainer.flowable.training

import com.valb3r.deeplearning4j_trainer.flowable.CONTEXT
import com.valb3r.deeplearning4j_trainer.flowable.dto.TrainingContext
import org.flowable.engine.impl.persistence.entity.ExecutionEntity
import org.springframework.stereotype.Service

@Service("trainingConditionEvaluator")
class TrainingConditionEvaluator {

    fun hasContext(exec: ExecutionEntity): Boolean {
        return null != exec.getVariable(CONTEXT)
    }

    fun newModelNeeded(ctx: TrainingContext): Boolean {
        return null == ctx.modelPath
    }

    fun hasMoreDataToTrain(ctx: TrainingContext): Boolean {
        return ctx.currentEpoch <= ctx.trainingSpec.numEpochs
    }
}