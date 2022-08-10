package com.valb3r.deeeplearning4j_trainer.spring.controller

import com.google.common.cache.CacheBuilder
import com.valb3r.deeeplearning4j_trainer.spring.repository.TrainingProcessRepository
import org.nd4j.autodiff.samediff.SameDiff
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.io.File
import java.time.Duration

@RestController
class ChartApiController(private val trainingProcessRepository: TrainingProcessRepository) {

    private val sdCacheBySdFileName = CacheBuilder.newBuilder()
        .expireAfterWrite(Duration.ofMinutes(1))
        .maximumSize(3)
        .build<String, SameDiff>()

    @GetMapping("/api/{processId}/sd-variables")
    fun listSdVariables(@PathVariable processId: String, @RequestParam("latest", defaultValue = "false") showLatest: Boolean): List<String> {
        val proc = trainingProcessRepository.findByProcessId(processId)!!
        val sd = loadSameDiff(proc.modelPath(showLatest))
        return sd.variables().map { it.name() }
    }

    @GetMapping("/api/{processId}/sd-variables/output/training")
    fun evalSdVariables(
        @PathVariable processId: String,
        @RequestParam("variables") variables: List<String>,
        @RequestParam("trainingEntryIdx") trainingEntryIdx: Long,
        @RequestParam("latest", defaultValue = "false") showLatest: Boolean
    ): Map<String, FloatArray> {
        val proc = trainingProcessRepository.findByProcessId(processId)!!
        val sd = SameDiff.fromFlatFile(File(proc.modelPath(showLatest)))
        val iterator = proc.getCtx().trainingIterator()
        for (i in (0 until trainingEntryIdx - 1)) {
            iterator.skipNext()
        }
        val dataSet = iterator.next(1)

        return sd.output(dataSet, *variables.toTypedArray()).map { it.key to it.value.toFloatVector() }.toMap()
    }

    private fun loadSameDiff(filePath: String): SameDiff {
        return sdCacheBySdFileName.get(filePath) { SameDiff.fromFlatFile(File(filePath)) }
    }
}