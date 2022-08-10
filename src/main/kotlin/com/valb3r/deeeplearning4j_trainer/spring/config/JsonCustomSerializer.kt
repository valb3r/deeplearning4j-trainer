package com.valb3r.deeeplearning4j_trainer.spring.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.valb3r.deeeplearning4j_trainer.spring.flowable.dto.TrainingContext
import lombok.RequiredArgsConstructor
import lombok.SneakyThrows
import org.flowable.variable.api.types.ValueFields
import org.flowable.variable.api.types.VariableType

/**
 * JSON serializer for small classes (small resulting strings). Preserves the class name used, so deserialzation
 * returns the class that was used to serialize data.
 */
@RequiredArgsConstructor
class JsonCustomSerializer(private val mapper: ObjectMapper) : VariableType {

    override fun getTypeName(): String {
        return JSON
    }

    override fun isCachable(): Boolean {
        return true
    }

    @SneakyThrows
    override fun isAbleToStore(o: Any): Boolean {
        return o is TrainingContext
    }

    @SneakyThrows
    override fun setValue(o: Any?, valueFields: ValueFields) {
        if (o == null) {
            valueFields.bytes = null
            return
        }
        valueFields.bytes = mapper.writeValueAsBytes(o)
    }

    @SneakyThrows
    override fun getValue(valueFields: ValueFields): Any {
        return mapper.readValue(valueFields.bytes, TrainingContext::class.java)
    }

    companion object {
        const val JSON = "as_json"
    }
}