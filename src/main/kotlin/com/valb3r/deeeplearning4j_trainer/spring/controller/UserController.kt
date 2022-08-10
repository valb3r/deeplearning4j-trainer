package com.valb3r.deeeplearning4j_trainer.spring.controller

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.valb3r.deeeplearning4j_trainer.spring.config.DirectoriesConfig
import com.valb3r.deeeplearning4j_trainer.spring.flowable.CONTEXT
import com.valb3r.deeeplearning4j_trainer.spring.flowable.FstSerDe
import com.valb3r.deeeplearning4j_trainer.spring.flowable.dto.TrainingContext
import com.valb3r.deeeplearning4j_trainer.spring.repository.TrainingProcessRepository
import com.valb3r.deeeplearning4j_trainer.spring.service.MermaidSchemaExtractor
import com.valb3r.deeeplearning4j_trainer.spring.service.poisonPill
import org.flowable.engine.RepositoryService
import org.flowable.engine.RuntimeService
import org.springframework.core.io.InputStreamResource
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.ui.Model
import org.springframework.util.FileSystemUtils
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.io.File
import java.lang.Thread.sleep
import java.time.LocalDateTime
import java.util.concurrent.ThreadLocalRandom
import javax.validation.Valid
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotEmpty
import kotlin.math.min


private val mapper: ObjectMapper = ObjectMapper(YAMLFactory().disable(YAMLGenerator.Feature.USE_NATIVE_TYPE_ID))
    .registerModule(KotlinModule.Builder().build())
    .setSerializationInclusion(JsonInclude.Include.NON_NULL)

@Validated
@Controller
class UserController(
    private val repositoryService: RepositoryService,
    private val trainingProcessRepository: TrainingProcessRepository,
    private val runtime: RuntimeService,
    private val directoriesConfig: DirectoriesConfig,
    private val mermaidExtractor: MermaidSchemaExtractor
) {

    companion object {
        const val indexPath = "/user/index.html"
    }

    @RequestMapping(indexPath)
    fun userIndex(@PageableDefault(page = 0, size = 10) page: Pageable, model: Model): String {
        val processes = runtime.createProcessInstanceQuery().orderByStartTime().desc().listPage(page.pageNumber, page.pageSize + 1)
        val variables = processes.map { runtime.getVariableInstance(it.id, CONTEXT) }.map { it?.value as TrainingContext? }
        val processesAndVariables = processes.zip(variables)
        model.addAttribute("processesAndVariables", processesAndVariables.subList(0, min(processes.size, page.pageSize)))
        model.addAttribute("pageNum", page.pageNumber)
        model.addAttribute("pageSize", page.pageSize)
        model.addAttribute("hasMorePages", processes.size > page.pageSize)
        return "user/index"
    }

    @RequestMapping("user/processes/index.html")
    fun listActiveProcesses(@PageableDefault(page = 0, size = 10) page: Pageable, model: Model): String {
        return "redirect:/user/index.html"
    }

    @RequestMapping("user/processes/completed.html")
    fun listCompletedProcesses(@PageableDefault(page = 0, size = 10) page: Pageable, model: Model): String {
        val processes = trainingProcessRepository.findAllByOrderByUpdatedAtDesc(Pageable.ofSize(page.pageSize).withPage(page.pageNumber))
        model.addAttribute("processes", processes)
        model.addAttribute("pageNum", page.pageNumber)
        model.addAttribute("pageSize", page.pageSize)
        model.addAttribute("hasMorePages",
            trainingProcessRepository.findAllByOrderByUpdatedAtDesc(
                Pageable.ofSize(page.pageSize).withPage(page.pageNumber + 1)
            ).isNotEmpty()
        )
        return "user/processes/completed"
    }

    @RequestMapping("user/processes/new-process.html")
    fun listNewProcessDefinitions(@PageableDefault(page = 0, size = 10) page: Pageable, model: Model): String {
        val definitions = repositoryService.createProcessDefinitionQuery().orderByProcessDefinitionVersion().desc().listPage(page.pageNumber * page.pageSize, page.pageSize + 1)
        model.addAttribute("definitions", definitions.subList(0, min(definitions.size, page.pageSize)))
        model.addAttribute("pageNum", page.pageNumber)
        model.addAttribute("pageSize", page.pageSize)
        model.addAttribute("hasMorePages", definitions.size > page.pageSize)
        return "user/processes/new-process"
    }

    @PostMapping("user/processes/definitions/{id}/start")
    fun startNewProcess(
        @PathVariable id: String,
        @Valid @NotBlank @RequestParam("business-key") businessKey: String,
        @Valid @NotEmpty @RequestParam("inputs") files: Array<MultipartFile>
    ): String {
        val definition = repositoryService.createProcessDefinitionQuery().processDefinitionId(id).singleResult()
            ?: return "redirect:/user/processes/new-process.html?error=missing-process-def"

        val name = LocalDateTime.now().toString()
        val input = File(directoriesConfig.input).resolve(name).absolutePath
        val output = File(directoriesConfig.output).resolve(name).absolutePath
        val logPath = File(output).resolve("${definition.name}.log").absolutePath
        val trainedModelPath = File(directoriesConfig.output).resolve(name).resolve("model.fb").absolutePath

        val inputDir = File(input)
        inputDir.mkdirs()
        val outputDir = File(output)
        outputDir.mkdirs()
        if (files.any { it.originalFilename?.isBlank() != false }) {
            throw IllegalArgumentException("Empty filename")
        }

        files.forEach { it.transferTo(inputDir.resolve(it.originalFilename!!)) }
        runtime.startProcessInstanceById(
            definition.id,
            businessKey,
            mapOf(CONTEXT to TrainingContext(input, output, logPath, trainedModelPath))
        )
        return "redirect:/"
    }

    @GetMapping("user/processes/{processId}/charts")
    fun drawCharts(@PathVariable processId: String, model: Model): String {
        val proc = trainingProcessRepository.findByProcessId(processId)!!
        model.addAttribute("processId", processId)
        model.addAttribute("dataSize", proc.getCtx().datasetSize!!)
        model.addAttribute("notes", proc.notes)
        model.addAttribute("businessKey", readBusinessKey(processId))
        return "user/processes/charts"
    }

    @GetMapping("user/processes/{processId}/network-structure")
    fun drawStructure(@PathVariable processId: String, model: Model): String {
        val proc = trainingProcessRepository.findByProcessId(processId)!!
        model.addAttribute("processId", processId)
        model.addAttribute("notes", proc.notes)
        model.addAttribute("businessKey", readBusinessKey(processId))
        model.addAttribute("mermaidSchema", mermaidExtractor.extract(processId))
        return "user/processes/network-structure"
    }

    @GetMapping("user/processes/{processId}/error-and-stacktrace.html")
    fun errorAndStacktrace(@PathVariable processId: String, model: Model): String {
        val proc = trainingProcessRepository.findByProcessId(processId)!!
        model.addAttribute("processId", processId)
        model.addAttribute("businessKey", readBusinessKey(processId))
        model.addAttribute("error", proc.errorMessage)
        model.addAttribute("stacktrace", proc.errorStacktrace)
        return "user/processes/error-and-stacktrace"
    }

    @GetMapping("user/processes/{processId}/notes.html")
    fun notes(@PathVariable processId: String, model: Model): String {
        val proc = trainingProcessRepository.findByProcessId(processId)!!
        model.addAttribute("processId", processId)
        model.addAttribute("businessKey", readBusinessKey(processId))
        model.addAttribute("notes", proc.notes)
        return "user/processes/notes"
    }

    @PostMapping("user/processes/{processId}/notes")
    fun updateNotes(
        @PathVariable processId: String,
        @Valid @NotBlank @RequestParam("notes") notes: String,
    ): String {
        val proc = trainingProcessRepository.findByProcessId(processId)!!
        proc.notes = notes
        trainingProcessRepository.save(proc)
        return "redirect:/user/processes/$processId/notes.html"
    }

    @ResponseBody
    @GetMapping("user/processes/{id}/download-model", produces = ["application/octet-stream"])
    fun downloadModel(@PathVariable("id") processId: String, @RequestParam("latest", defaultValue = "false") downloadLatest: Boolean): ResponseEntity<InputStreamResource> {
        val proc = trainingProcessRepository.findByProcessId(processId)!!

        return ResponseEntity.ok()
            .header("Content-Disposition", "attachment; filename=model-${readBusinessKey(processId)}-${if (downloadLatest) 'l' else 'b'}-${if (downloadLatest) proc.getCtx().currentEpoch else proc.bestPerformingEpoch!!}.fb")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(InputStreamResource(File(proc.modelPath(downloadLatest)).inputStream()));
    }

    @ResponseBody
    @GetMapping("user/processes/{id}/download-dataset", produces = ["application/octet-stream"])
    fun downloadDataset(@PathVariable("id") processId: String, @RequestParam("dataSetPos") dataSetPos: Int): ResponseEntity<InputStreamResource> {
        val proc = trainingProcessRepository.findByProcessId(processId)!!
        val file = File(proc.getCtx().inputFiles[dataSetPos])

        return ResponseEntity.ok()
            .header("Content-Disposition", "attachment; filename=model-${readBusinessKey(processId)}-${file.nameWithoutExtension}-#${dataSetPos}.bin")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(InputStreamResource(file.inputStream()));
    }

    @PostMapping("user/processes/{id}/abort")
    fun abortProcess(@PathVariable id: String): String {
        runtime.createExecutionQuery().processInstanceId(
            runtime.createExecutionQuery().executionId(id).singleResult().processInstanceId
        ).signalEventSubscriptionName("ABORT_SIGNAL").list().forEach {
            (0 until 10).forEach { _ ->
                try {
                    // FIXME H2 Only issue??? - Deadlocks without poison pill
                    poisonPill.add(it.processInstanceId)
                    runtime.signalEventReceived("ABORT_SIGNAL", it.id)
                } catch (ex: Throwable) {
                    sleep(ThreadLocalRandom.current().nextLong(100L, 1000L))
                    // NOP
                }
            }
        }
        return "redirect:/"
    }

    @Transactional
    @PostMapping("user/processes/{id}/delete")
    fun deleteProcess(@PathVariable id: String): String {
        val proc = trainingProcessRepository.findByProcessId(id)!!
        if (!proc.completed) {
            throw IllegalArgumentException("Process is active")
        }

        FileSystemUtils.deleteRecursively(File(proc.getCtx().inputDataPath))
        FileSystemUtils.deleteRecursively(File(proc.getCtx().outputDataPath))
        trainingProcessRepository.delete(proc)
        return "redirect:/"
    }

    @ResponseBody
    @GetMapping("user/processes/{id}/download-model-spec", produces = ["application/octet-stream"])
    fun downloadModelSpec(@PathVariable("id") processId: String): ResponseEntity<InputStreamResource> {
        val ctx = trainingProcessRepository.findByProcessId(processId)!!.getCtx()

        return ResponseEntity.ok()
            .header("Content-Disposition", "inline; filename=model-spec-${readBusinessKey(processId)}-$processId.fb")
            .contentType(MediaType.TEXT_PLAIN)
            .body(InputStreamResource(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(ctx.modelSpec!!).byteInputStream()))
    }


    @ResponseBody
    @GetMapping("user/processes/{id}/download-training-spec", produces = ["application/octet-stream"])
    fun downloadTrainingSpec(@PathVariable("id") processId: String): ResponseEntity<InputStreamResource> {
        val ctx = trainingProcessRepository.findByProcessId(processId)!!.getCtx()

        return ResponseEntity.ok()
            .header("Content-Disposition", "inline; filename=training-spec-${readBusinessKey(processId)}-$processId.fb")
            .contentType(MediaType.TEXT_PLAIN)
            .body(InputStreamResource(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(ctx.trainingSpec!!).byteInputStream()))
    }

    @ResponseBody
    @GetMapping("user/processes/{id}/download-dataset-heads", produces = ["application/octet-stream"])
    fun downloadDataSetHeads(@PathVariable("id") processId: String): ResponseEntity<InputStreamResource> {
        val ctx = trainingProcessRepository.findByProcessId(processId)!!.getCtx()

        var result = ""
        ctx.inputFiles.forEach { file ->
            result += "$file \n"
            val iter = FstSerDe.FstIterator(file)
            for (line in (0 until 10)) {
                if (!iter.hasNext()) {
                    break
                }
                val data = iter.next()
                if (0 == line) {
                    result += "${data.keys.joinToString(",")}\n"
                }
                data.values.forEach { vector ->
                    result += if (1 == vector.size) {
                        "${vector[0]},"
                    } else {
                        "${vector.joinToString(";")},"
                    }
                }
                result = result.trim(',')
            }
            if (iter.hasNext()) {
                result += "\n...\n"
            }
        }
        return ResponseEntity.ok()
            .header("Content-Disposition", "inline; filename=model-spec-${readBusinessKey(processId)}-$processId.fb")
            .contentType(MediaType.TEXT_PLAIN)
            .body(InputStreamResource(result.byteInputStream()))
    }

    private fun readBusinessKey(processId: String): String {
        return trainingProcessRepository.findByProcessId(processId)!!.businessKey
    }
}