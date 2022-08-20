package com.valb3r.deeplearning4j_trainer.config.serde

import com.fasterxml.jackson.databind.ObjectMapper
import com.valb3r.deeplearning4j_trainer.flowable.dto.ValidationContext

class ValidationSerde(mapper: ObjectMapper) : BaseJsonCustomSerializer<ValidationContext>(mapper, ValidationContext::class.java) {

    override fun getTypeName(): String {
        return "ValidationContext"
    }

    override fun isAbleToStore(value: Any?): Boolean {
        return value is ValidationContext
    }
}