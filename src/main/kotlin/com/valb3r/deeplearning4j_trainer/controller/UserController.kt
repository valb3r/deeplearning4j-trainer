package com.valb3r.deeplearning4j_trainer.controller

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.valb3r.deeplearning4j_trainer.config.DirectoriesConfig
import com.valb3r.deeplearning4j_trainer.domain.TrainingProcess
import com.valb3r.deeplearning4j_trainer.domain.ValidationProcess
import com.valb3r.deeplearning4j_trainer.flowable.CONTEXT
import com.valb3r.deeplearning4j_trainer.flowable.FstSerDe
import com.valb3r.deeplearning4j_trainer.flowable.INPUT
import com.valb3r.deeplearning4j_trainer.flowable.dto.InputContext
import com.valb3r.deeplearning4j_trainer.repository.ProcessRepository
import com.valb3r.deeplearning4j_trainer.repository.TrainingProcessRepository
import com.valb3r.deeplearning4j_trainer.service.MermaidSchemaExtractor
import com.valb3r.deeplearning4j_trainer.service.poisonPill
import com.valb3r.deeplearning4j_trainer.storage.StorageService
import com.valb3r.deeplearning4j_trainer.storage.resolve
import org.apache.commons.io.IOUtils
import org.apache.tomcat.util.http.fileupload.FileItemIterator
import org.apache.tomcat.util.http.fileupload.FileItemStream
import org.apache.tomcat.util.http.fileupload.servlet.ServletFileUpload
import org.flowable.engine.HistoryService
import org.flowable.engine.RepositoryService
import org.flowable.engine.RuntimeService
import org.springframework.core.io.InputStreamResource
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.ui.Model
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import java.io.File
import java.lang.Thread.sleep
import java.nio.charset.StandardCharsets.UTF_8
import java.time.LocalDateTime
import java.util.concurrent.ThreadLocalRandom
import javax.servlet.http.HttpServletRequest
import javax.validation.Valid
import javax.validation.constraints.NotBlank
import kotlin.math.min


private val mapper: ObjectMapper = ObjectMapper(YAMLFactory().disable(YAMLGenerator.Feature.USE_NATIVE_TYPE_ID))
    .registerModule(KotlinModule.Builder().build())
    .setSerializationInclusion(JsonInclude.Include.NON_NULL)

@Validated
@Controller
class UserController(
    private val repositoryService: RepositoryService,
    private val trainingProcessRepository: TrainingProcessRepository,
    private val processRepository: ProcessRepository,
    private val runtime: RuntimeService,
    private val history: HistoryService,
    private val directoriesConfig: DirectoriesConfig,
    private val mermaidExtractor: MermaidSchemaExtractor,
    private val storage: StorageService
) {

    companion object {
        const val indexPath = "/user/index.html"
    }

    @RequestMapping(indexPath)
    fun userIndex(@PageableDefault(page = 0, size = 10) page: Pageable, model: Model): String {
        val processes = runtime.createProcessInstanceQuery().orderByStartTime().desc().listPage(page.pageNumber, page.pageSize + 1)
        val variables = processes.map { runtime.getVariableInstance(it.id, CONTEXT) }.map { it?.value }
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
        val processes = processRepository.findAllByOrderByUpdatedAtDesc(Pageable.ofSize(page.pageSize).withPage(page.pageNumber))
        model.addAttribute("processes", processes)
        model.addAttribute("pageNum", page.pageNumber)
        model.addAttribute("pageSize", page.pageSize)
        model.addAttribute("hasMorePages",
            processRepository.findAllByOrderByUpdatedAtDesc(
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
    fun startNewProcess(@PathVariable id: String, request: HttpServletRequest): String {
        val definition = repositoryService.createProcessDefinitionQuery().processDefinitionId(id).singleResult()
            ?: return "redirect:/user/processes/new-process.html?error=missing-process-def"

        val name = LocalDateTime.now().toString()
        val inputDir = directoriesConfig.input.resolve(name)
        val outputDir = directoriesConfig.output.resolve(name)

        val businessKey = processUploadedFilesAndSendThemToStorage(request, inputDir)

        runtime.startProcessInstanceById(
            definition.id,
            businessKey,
            mapOf(INPUT to InputContext(inputDir, outputDir))
        )
        return "redirect:/"
    }

    @GetMapping("user/processes/{trainingProcessId}/start-validation")
    fun startNewTrainingValidationProcessView(@PathVariable trainingProcessId: String, model: Model): String {
        val proc = trainingProcessRepository.findByProcessId(trainingProcessId)!!
        model.addAttribute("id", trainingProcessId)
        model.addAttribute("businessKey", proc.businessKey)
        return "user/processes/start-validation"
    }

    @PostMapping("user/processes/{trainingProcessId}/start-validation")
    fun startNewTrainingValidationProcess(
        @PathVariable trainingProcessId: String,
        @Valid @RequestParam("use-training-data", defaultValue = "false") useTrainingData: Boolean,
        request: HttpServletRequest
    ): String {
        val definition = repositoryService.createProcessDefinitionQuery().processDefinitionName("model-validation-process").latestVersion().singleResult()
            ?: return "redirect:/user/processes/new-process.html?error=missing-process-def"
        val ctx = trainingProcessRepository.findByProcessId(trainingProcessId)!!.getCtx()!!

        val name = LocalDateTime.now().toString()
        val inputDir = directoriesConfig.input.resolve(name)
        val outputDir = directoriesConfig.output.resolve(name)

        val businessKey = processUploadedFilesAndSendThemToStorage(request, inputDir)

        runtime.startProcessInstanceById(
            definition.id,
            businessKey,
            mapOf(INPUT to InputContext(inputDir, outputDir,  ctx.trainedModelPath, if (useTrainingData) ctx.inputFiles else null ))
        )
        return "redirect:/"
    }

    @Transactional
    @PostMapping("user/processes/{trainingProcessId}/continue-process-in-new")
    fun continueProcessInNewProcess(
        @PathVariable trainingProcessId: String
    ): String {
        val proc = processRepository.findByProcessId(trainingProcessId)!!
        val definition = repositoryService.createProcessDefinitionQuery().processDefinitionId(proc.processDefinitionName).orderByProcessDefinitionVersion().desc().list().firstOrNull()
            ?: return "redirect:/user/processes/new-process.html?error=missing-process-def"
        val inputValue = history.createHistoricVariableInstanceQuery().processInstanceId(trainingProcessId).variableName(INPUT).singleResult()

        val values = mutableMapOf<String, Any?>(CONTEXT to proc.getCtx())
        if (null != inputValue) {
            values[INPUT] = inputValue.value
        }
        if (null != proc.inputCtx()) {
            values[INPUT] = proc.inputCtx()
        }

        val continuation = runtime.startProcessInstanceById(
            definition.id,
            proc.businessKey,
            values
        )
        // Might cause issue if continuation process tries to find proc entity before it is saved here, Transactional should help
        proc.processId = continuation.id
        proc.completed = false
        processRepository.save(proc)

        return "redirect:/"
    }

    @GetMapping("user/processes/{trainingProcessId}/start-inherited-dataset-training")
    fun startNewTrainingWithInheritedDatasetView(@PathVariable trainingProcessId: String, model: Model): String {
        val proc = trainingProcessRepository.findByProcessId(trainingProcessId)!!
        model.addAttribute("id", trainingProcessId)
        model.addAttribute("businessKey", proc.businessKey)
        return "user/processes/start-inherited-dataset-training"
    }

    @PostMapping("user/processes/{trainingProcessId}/start-inherited-dataset-training")
    fun startNewTrainingWithInheritedDatasetProcess(
        @PathVariable trainingProcessId: String,
        @Valid @RequestParam("use-training-data", defaultValue = "false") useTrainingData: Boolean,
        request: HttpServletRequest
    ): String {
        val definition = repositoryService.createProcessDefinitionQuery().processDefinitionName("model-training-process").latestVersion().singleResult()
            ?: return "redirect:/user/processes/new-process.html?error=missing-process-def"
        val ctx = trainingProcessRepository.findByProcessId(trainingProcessId)!!.getCtx()!!

        val name = LocalDateTime.now().toString()
        val inputDir = directoriesConfig.input.resolve(name)
        val outputDir = directoriesConfig.output.resolve(name)

        val businessKey = processUploadedFilesAndSendThemToStorage(request, inputDir)

        runtime.startProcessInstanceById(
            definition.id,
            businessKey,
            mapOf(INPUT to InputContext(inputDir, outputDir, dataFilesPath = ctx.inputFiles ))
        )
        return "redirect:/"
    }

    @GetMapping("user/processes/{processId}/charts")
    fun drawCharts(@PathVariable processId: String, model: Model): String {
        val proc = processRepository.findByProcessId(processId)!!
        model.addAttribute("processId", processId)
        model.addAttribute("dataSize", proc.getCtx()!!.datasetSize)
        model.addAttribute("notes", proc.notes)
        model.addAttribute("businessKey", readBusinessKey(processId))
        return "user/processes/charts"
    }

    @GetMapping("user/processes/{processId}/evaluate-expression")
    fun evaluateExpression(@PathVariable processId: String, model: Model): String {
        val proc = processRepository.findByProcessId(processId)!!
        model.addAttribute("processId", processId)
        model.addAttribute("dataSize", proc.getCtx()!!.datasetSize)
        model.addAttribute("notes", proc.notes)
        model.addAttribute("businessKey", readBusinessKey(processId))
        return "user/processes/evaluate-expression"
    }

    @GetMapping("user/processes/{processId}/network-structure")
    fun drawStructure(@PathVariable processId: String, model: Model): String {
        val proc = processRepository.findByProcessId(processId)!!
        model.addAttribute("processId", processId)
        model.addAttribute("notes", proc.notes)
        model.addAttribute("businessKey", readBusinessKey(processId))
        model.addAttribute("mermaidSchema", mermaidExtractor.extract(processId))
        return "user/processes/network-structure"
    }

    @GetMapping("user/processes/{processId}/error-and-stacktrace.html")
    fun errorAndStacktrace(@PathVariable processId: String, model: Model): String {
        val proc = processRepository.findByProcessId(processId)!!
        model.addAttribute("processId", processId)
        model.addAttribute("businessKey", readBusinessKey(processId))
        model.addAttribute("error", proc.errorMessage)
        model.addAttribute("stacktrace", proc.errorStacktrace)
        return "user/processes/error-and-stacktrace"
    }

    @GetMapping("user/processes/{processId}/notes.html")
    fun notes(@PathVariable processId: String, model: Model): String {
        val proc = processRepository.findByProcessId(processId)!!
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
        val proc = processRepository.findByProcessId(processId)!!
        proc.notes = notes
        processRepository.save(proc)
        return "redirect:/user/processes/$processId/notes.html"
    }

    @GetMapping("user/processes/{processId}/validation-results")
    fun validationResults(@PathVariable processId: String, model: Model): String {
        val proc = processRepository.findByProcessId(processId)!! as ValidationProcess
        model.addAttribute("processId", processId)
        model.addAttribute("businessKey", readBusinessKey(processId))
        model.addAttribute("validationResults", proc.validationResult?.let { String(it, UTF_8) })
        return "user/processes/validation-results"
    }

    @ResponseBody
    @GetMapping("user/processes/{id}/download-model", produces = ["application/octet-stream"])
    fun downloadModel(@PathVariable("id") processId: String, @RequestParam("latest", defaultValue = "false") downloadLatest: Boolean): ResponseEntity<InputStreamResource> {
        val proc = processRepository.findByProcessId(processId)!!

        return ResponseEntity.ok()
            .header("Content-Disposition", "attachment; filename=model-${readBusinessKey(processId)}-${if (downloadLatest) 'l' else 'b'}-${if (downloadLatest) proc.currentEpoch else proc.bestPerformingEpoch}.fb")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(InputStreamResource(storage.read(proc.modelPath(downloadLatest))))
    }

    @ResponseBody
    @GetMapping("user/processes/{id}/download-dataset", produces = ["application/octet-stream"])
    fun downloadDataset(@PathVariable("id") processId: String, @RequestParam("dataSetPos") dataSetPos: Int): ResponseEntity<InputStreamResource> {
        val proc = processRepository.findByProcessId(processId)!!
        val file = proc.getCtx()!!.inputFiles[dataSetPos]

        return ResponseEntity.ok()
            .header("Content-Disposition", "attachment; filename=model-${readBusinessKey(processId)}-${File(file).nameWithoutExtension}-#${dataSetPos}.bin")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(InputStreamResource(storage.read(file)))
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
    fun deleteProcess(@PathVariable id: String, @RequestHeader(HttpHeaders.REFERER) referer: String): String {
        val proc = processRepository.findByProcessId(id)!!
        if (!proc.completed) {
            throw IllegalArgumentException("Process is active")
        }

        when (proc) {
            is TrainingProcess -> {
                storage.list(proc.getCtx()!!.inputDataPath, true).forEach { storage.remove(it) }
                storage.list(proc.getCtx()!!.outputDataPath, true).forEach { storage.remove(it) }
            }
            is ValidationProcess -> {
                storage.list(proc.getCtx()!!.validationDataPath, true).forEach { storage.remove(it) }
            }
        }
        processRepository.delete(proc)
        return "redirect:${referer}"
    }

    @ResponseBody
    @GetMapping("user/processes/{id}/download-model-spec", produces = ["application/octet-stream"])
    fun downloadModelSpec(@PathVariable("id") processId: String): ResponseEntity<InputStreamResource> {
        val ctx = trainingProcessRepository.findByProcessId(processId)!!.getCtx()

        return ResponseEntity.ok()
            .header("Content-Disposition", "inline; filename=model-spec-${readBusinessKey(processId)}-$processId.fb")
            .contentType(MediaType.TEXT_PLAIN)
            .body(InputStreamResource(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(ctx!!.modelSpec).byteInputStream()))
    }


    @ResponseBody
    @GetMapping("user/processes/{id}/download-training-spec", produces = ["application/octet-stream"])
    fun downloadTrainingSpec(@PathVariable("id") processId: String): ResponseEntity<InputStreamResource> {
        val ctx = trainingProcessRepository.findByProcessId(processId)!!.getCtx()

        return ResponseEntity.ok()
            .header("Content-Disposition", "inline; filename=training-spec-${readBusinessKey(processId)}-$processId.fb")
            .contentType(MediaType.TEXT_PLAIN)
            .body(InputStreamResource(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(ctx!!.trainingSpec).byteInputStream()))
    }

    @ResponseBody
    @GetMapping("user/processes/{id}/download-dataset-heads", produces = ["application/octet-stream"])
    fun downloadDataSetHeads(@PathVariable("id") processId: String): ResponseEntity<InputStreamResource> {
        val ctx = processRepository.findByProcessId(processId)!!.getCtx()

        var result = ""
        ctx!!.inputFiles.forEach { file ->
            result += "$file \n"
            val iter = FstSerDe.FstIterator(file, storage)
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


    // Very special implementation to send file directly to i.e. S3 and not to temp directory first and copy after (Spring default behaviour)
    private fun processUploadedFilesAndSendThemToStorage(request: HttpServletRequest, inputDir: String): String {
        val upload = ServletFileUpload()
        val iterator: FileItemIterator = upload.getItemIterator(request)
        var businessKeyExtract: String? = null
        while (iterator.hasNext()) {
            val item: FileItemStream = iterator.next()
            if (!item.isFormField && "inputs" == item.fieldName) {
                if (item.name.isBlank()) {
                    throw IllegalArgumentException("Empty filename")
                }
                // Must be done while iterating, otherwise stream will be closed
                storage.write(inputDir.resolve(item.name!!))
                    .use { os -> item.openStream().use { inp -> IOUtils.copy(inp, os) } }
            } else {
                if (item.fieldName == "business-key") {
                    businessKeyExtract = item.openStream().use { String(it.readBytes()) }
                }
            }
        }

        val businessKey = businessKeyExtract!!
        return businessKey
    }

    private fun readBusinessKey(processId: String): String {
        return processRepository.findByProcessId(processId)!!.businessKey
    }
}