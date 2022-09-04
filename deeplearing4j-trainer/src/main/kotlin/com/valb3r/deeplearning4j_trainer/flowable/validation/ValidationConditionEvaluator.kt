package com.valb3r.deeplearning4j_trainer.flowable.validation

import com.valb3r.deeplearning4j_trainer.flowable.CONTEXT
import com.valb3r.deeplearning4j_trainer.flowable.dto.ValidationContext
import org.flowable.engine.impl.persistence.entity.ExecutionEntity
import org.springframework.stereotype.Service

@Service("validationConditionEvaluator")
class ValidationConditionEvaluator {

    fun hasContext(exec: ExecutionEntity): Boolean {
        return null != exec.getVariable(CONTEXT)
    }
    fun hasMoreDataToValidate(ctx: ValidationContext): Boolean {
        return ctx.currentEpoch == 0L
    }
}