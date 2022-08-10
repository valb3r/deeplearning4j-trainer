package com.valb3r.deeeplearning4j_trainer.spring.controller

import com.valb3r.deeeplearning4j_trainer.spring.flowable.CONTEXT
import com.valb3r.deeeplearning4j_trainer.spring.flowable.dto.TrainingContext
import org.flowable.engine.HistoryService

fun HistoryService.readTrainingContext(processId: String): TrainingContext {
    return createHistoricVariableInstanceQuery()
        .processInstanceId(processId)
        .list()
        .first { v -> CONTEXT == v.variableName }
        .value as TrainingContext
}