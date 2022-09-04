package com.valb3r.deeplearning4j_trainer.config.serde

import com.fasterxml.jackson.databind.ObjectMapper
import com.valb3r.deeplearning4j_trainer.flowable.dto.InputContext

class InputSerde(mapper: ObjectMapper) : BaseJsonCustomSerializer<InputContext>(mapper, InputContext::class.java) {

    override fun getTypeName(): String {
        return "InputContext"
    }

    override fun isAbleToStore(value: Any?): Boolean {
        return value is InputContext
    }
}