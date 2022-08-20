package com.valb3r.deeplearning4j_trainer.controller

import com.valb3r.deeplearning4j_trainer.domain.TrainingProcess
import com.valb3r.deeplearning4j_trainer.domain.ValidationProcess
import com.valb3r.deeplearning4j_trainer.flowable.calculator.ExpressionParser
import com.valb3r.deeplearning4j_trainer.flowable.uniqName
import com.valb3r.deeplearning4j_trainer.repository.ProcessRepository
import org.nd4j.autodiff.samediff.SameDiff
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.io.File

@RestController
class EvalApiController(private val processRepository: ProcessRepository) {

    @GetMapping("/api/{processId}/evaluate-expressions")
    fun evalSdExpressions(
        @PathVariable processId: String,
        @RequestParam("expressions") expressions: List<String>,
        @RequestParam("trainingEntryIdx") trainingEntryIdx: Long,
        @RequestParam("latest", defaultValue = "false") showLatest: Boolean
    ): Map<String, FloatArray> {
        val proc = processRepository.findByProcessId(processId)!!
        val sd = SameDiff.fromFlatFile(File(proc.modelPath(showLatest)))
        val iterator = if (proc is TrainingProcess) proc.getCtx()!!.trainingIterator() else (proc as ValidationProcess).getCtx()!!.validationIterator()
        for (i in (0 until trainingEntryIdx - 1)) {
            iterator.skipNext()
        }
        val dataSet = iterator.next(1)

        val parser = ExpressionParser(sd)
        val variables = mutableSetOf<String>()
        val renames = mutableMapOf<String, String>()
        for (it in expressions.filter { it.isNotBlank() }) {
            if (renames.contains(it)) {
                continue;
            }

            renames[it] = sd.uniqName(it)
            val value = sd.identity(parser.parse(it)).rename(renames[it])
            variables += value.name()
            variables += it
        }

        return sd.output(dataSet, *variables.toTypedArray()).map { it.key to it.value.toFloatVector() }.toMap()
    }
}