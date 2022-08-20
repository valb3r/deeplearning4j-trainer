package com.valb3r.deeplearning4j_trainer.config.serde

import com.fasterxml.jackson.databind.ObjectMapper
import com.valb3r.deeplearning4j_trainer.flowable.dto.TrainingContext

@Deprecated("old version compat")
class OldTrainingSerde(mapper: ObjectMapper) : BaseJsonCustomSerializer<TrainingContext>(mapper, TrainingContext::class.java) {

    override fun getTypeName(): String {
        return "as_json"
    }

    override fun isAbleToStore(value: Any?): Boolean {
        return value is TrainingContext
    }
}