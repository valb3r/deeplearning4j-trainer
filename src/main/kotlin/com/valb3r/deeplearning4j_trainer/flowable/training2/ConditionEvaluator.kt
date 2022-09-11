package com.valb3r.deeplearning4j_trainer.flowable.training2

import org.springframework.stereotype.Service

@Service("conditionEvaluator")
class ConditionEvaluator {

    fun noMoreChunksAvailable(): Boolean {
        return true
    }
}