package com.valb3r.deeplearning4j_trainer.flowable.validation

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.csv.CsvMapper
import com.valb3r.deeplearning4j_trainer.domain.ValidationProcess
import com.valb3r.deeplearning4j_trainer.flowable.*
import com.valb3r.deeplearning4j_trainer.flowable.dto.ValidationContext
import com.valb3r.deeplearning4j_trainer.flowable.dto.ValidationSpec
import com.valb3r.deeplearning4j_trainer.repository.ValidationProcessRepository
import org.flowable.engine.delegate.BpmnError
import org.flowable.engine.delegate.DelegateExecution
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import java.io.File

@Service("validationDataInputValidatorAndLoader")
class ValidationDataInputValidatorAndLoader(
    @Qualifier("yamlObjectMapper") private val yamlObjectMapper: ObjectMapper,
    private val validationRepo: ValidationProcessRepository
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
        val validationFolder = File(inputCtx.inputDataPath)
        extractZipFiles(validationFolder)
        val files = validationFolder.listFiles()!!.map { it.absolutePath }
        val validationSpecFiles = files.filter { it.endsWith(".validation.yaml") }
        val dataFiles = inputCtx.dataFilesPath ?: files.filter { it.endsWith(".csv") || it.endsWith(".csv.data.bin") }
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

        val validationSpec = yamlObjectMapper.readValue(validationFolder.resolve(validationSpecFiles.first()), ValidationSpec::class.java)
        val filesAndDatasetSize = countRowsAndTranslateInputDataFilesToBinFormat(dataFiles)
        val ctx = ValidationContext(
            validationSpec = validationSpec,
            validations = validationSpec.validations,
            inputFiles = filesAndDatasetSize.first,
            datasetSize = filesAndDatasetSize.second,
            currentEpoch = 0L,
            validationDataPath = validationFolder.absolutePath,
            trainedModelPath = trainedModelPath
        )

        execution.setContext(ctx)
        validationProc.trainedModelPath = ctx.trainedModelPath
        validationProc.bestPerformingTrainedModelPath = ctx.trainedModelPath
        validationProc.setCtx(ctx)
        validationRepo.save(validationProc)
    }

    private fun countRowsAndTranslateInputDataFilesToBinFormat(files: List<String>): Pair<List<String>, Long> {
        val mapper = CsvMapper()
        val result = mutableListOf<String>()
        var totalRows = 0L
        for (file in files) {
            if (file.endsWith(".csv.data.bin")) {
                var count = 0
                FstSerDe.FstIterator(file).forEachRemaining { count++ }
                result += file
                totalRows += count
            } else {
                totalRows += csvToBin(file, mapper, result)
            }
        }

        return Pair(result, totalRows)
    }

    private fun csvToBin(
        file: String,
        mapper: CsvMapper,
        result: MutableList<String>
    ): Long {
        val parsed = FstSerDe().csvToBin(file, ".data.bin", mapper)
        result += parsed.fstFileName
        File(file).delete()
        return parsed.numRows.toLong()
    }
}