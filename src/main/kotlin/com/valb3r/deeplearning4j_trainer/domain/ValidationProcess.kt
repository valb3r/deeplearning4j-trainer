package com.valb3r.deeplearning4j_trainer.domain

import com.valb3r.deeplearning4j_trainer.flowable.dto.ValidationContext
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

    @Lob var validationContext: String
    @Lob var validationResult: String? = null

    init {
        this.processId = processId
        this.trainedModelPath = trainedModelPath
        this.businessKey = businessKey
        this.processDefinitionName = processDefinitionName
        this.validationContext = domainCtxMapper.writeValueAsString(validationContext)
    }

    fun setCtx(ctx: ValidationContext) {
        validationContext = domainCtxMapper.writeValueAsString(ctx)
    }

    override fun getCtx(): ValidationContext? {
        return domainCtxMapper.readValue(validationContext, ValidationContext::class.java)
    }

    fun getRevertedStacktrace(): String? {
        return errorStacktrace?.lines()?.reversed()?.joinToString("\n")
    }
}