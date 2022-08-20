package com.valb3r.deeplearning4j_trainer.flowable.validation

import com.valb3r.deeplearning4j_trainer.flowable.dto.ValidationContext
import org.springframework.stereotype.Service

@Service("validationConditionEvaluator")
class ValidationConditionEvaluator {

    fun hasMoreDataToValidate(ctx: ValidationContext): Boolean {
        return ctx.currentEpoch == 0L
    }
}