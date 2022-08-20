package com.valb3r.deeplearning4j_trainer.domain

import com.valb3r.deeplearning4j_trainer.flowable.dto.ValidationContext
import org.hibernate.annotations.Type
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files.lines
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Lob

@Entity
class ValidationProcess(
    processId: String,
    trainedModelPath: String,
    businessKey: String,
    processDefinitionName: String,
    validationContext: ValidationContext?
): Process<ValidationContext>() {

    @Lob
    var validationContext: ByteArray

    @Lob
    var validationResult: ByteArray? = null

    init {
        this.processId = processId
        this.trainedModelPath = trainedModelPath
        this.businessKey = businessKey
        this.processDefinitionName = processDefinitionName
        this.validationContext = domainCtxMapper.writeValueAsBytes(validationContext)
    }

    fun setCtx(ctx: ValidationContext) {
        validationContext = domainCtxMapper.writeValueAsBytes(ctx)
    }

    override fun getCtx(): ValidationContext? {
        return domainCtxMapper.readValue(validationContext, ValidationContext::class.java)
    }
}