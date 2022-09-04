package com.valb3r.deeplearning4j_trainer.flowable.validation

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.csv.CsvMapper
import com.valb3r.deeplearning4j_trainer.domain.ValidationProcess
import com.valb3r.deeplearning4j_trainer.flowable.*
import com.valb3r.deeplearning4j_trainer.flowable.dto.ValidationContext
import com.valb3r.deeplearning4j_trainer.flowable.dto.ValidationSpec
import com.valb3r.deeplearning4j_trainer.repository.ValidationProcessRepository
import com.valb3r.deeplearning4j_trainer.storage.StorageService
import com.valb3r.deeplearning4j_trainer.storage.resolve
import org.flowable.engine.delegate.BpmnError
import org.flowable.engine.delegate.DelegateExecution
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service

@Service("validationDataInputValidatorAndLoader")
class ValidationDataInputValidatorAndLoader(
    @Qualifier("yamlObjectMapper") private val yamlObjectMapper: ObjectMapper,
    private val validationRepo: ValidationProcessRepository,
    private val storage: StorageService
): WrappedJavaDelegate() {

    override fun doExecute(execution: DelegateExecution) {
        var validationProc = ValidationProcess(
            processId = execution.processInstanceId,
            trainedModelPath = "NOT-SET",
            businessKey = execution.processInstanceBusinessKey,
            validationContext = null,
            processDefinitionName = execution.processDefinitionId
        )
        validationProc = validationRepo.save(validationProc)

        val inputCtx = execution.getInput()
        val validationFolder = inputCtx.inputDataPath
        extractZipFilesAndDeleteArch(validationFolder, storage)
        val files = storage.list(validationFolder)
        val validationSpecFiles = files.filter { it.endsWith(".validation.yaml") }
        val dataFiles = inputCtx.dataFilesPath ?: files.filter { it.isCsvDataFile() || it.isBinDataFile() }
        val trainedModelPath = inputCtx.modelPath ?: files.first { it.endsWith(".fb") }

        if (validationSpecFiles.isEmpty()) {
            throw BpmnError("INPUT_ERR", "No validation spec files available")
        }

        if (validationSpecFiles.size > 1) {
            throw BpmnError("INPUT_ERR", "There are more than 1 validation spec file")
        }

        if (dataFiles.isEmpty()) {
            throw BpmnError("INPUT_ERR", "No validation data files available")
        }

        val validationSpec = storage.read(validationFolder.resolve(validationSpecFiles.first())).use { yamlObjectMapper.readValue(it, ValidationSpec::class.java) }
        val filesAndDatasetSize = countRowsAndTranslateInputDataFilesToBinFormat(dataFiles)
        val ctx = ValidationContext(
            validationSpec = validationSpec,
            validations = validationSpec.validations,
            inputFiles = filesAndDatasetSize,
            currentEpoch = 0L,
            validationDataPath = validationFolder,
            trainedModelPath = trainedModelPath
        )

        execution.setContext(ctx)
        validationProc.trainedModelPath = ctx.trainedModelPath
        validationProc.bestPerformingTrainedModelPath = ctx.trainedModelPath
        validationProc.setCtx(ctx)
        validationProc.completed = false
        validationProc.businessKey = execution.processInstanceBusinessKey
        validationRepo.save(validationProc)
    }

    private fun countRowsAndTranslateInputDataFilesToBinFormat(files: List<String>): List<String> {
        val mapper = CsvMapper()
        val result = mutableListOf<String>()
        for (file in files) {
            if (file.isBinDataFile()) {
                result += file
            } else {
                csvToBinAndRemoveSrc(file, mapper, result, storage)
            }
        }

        return result
    }
}