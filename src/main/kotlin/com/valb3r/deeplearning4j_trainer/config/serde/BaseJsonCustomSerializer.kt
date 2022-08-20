package com.valb3r.deeplearning4j_trainer.config.serde

import com.fasterxml.jackson.databind.ObjectMapper
import lombok.RequiredArgsConstructor
import lombok.SneakyThrows
import org.flowable.variable.api.types.ValueFields
import org.flowable.variable.api.types.VariableType

/**
 * JSON serializer for small classes (small resulting strings).
 */
@RequiredArgsConstructor
abstract class BaseJsonCustomSerializer<T: Any>(private val mapper: ObjectMapper, private val clazz: Class<T>) : VariableType {

    override fun isCachable(): Boolean {
        return true
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
        return mapper.readValue(valueFields.bytes, clazz)
    }
}