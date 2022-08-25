package com.valb3r.deeplearning4j_trainer.flowable.training

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.csv.CsvMapper
import com.valb3r.deeplearning4j_trainer.domain.Dataset
import com.valb3r.deeplearning4j_trainer.domain.DatasetFile
import com.valb3r.deeplearning4j_trainer.domain.TrainingProcess
import com.valb3r.deeplearning4j_trainer.flowable.*
import com.valb3r.deeplearning4j_trainer.flowable.dto.InputContext
import com.valb3r.deeplearning4j_trainer.flowable.dto.ModelSpec
import com.valb3r.deeplearning4j_trainer.flowable.dto.TrainingContext
import com.valb3r.deeplearning4j_trainer.flowable.dto.TrainingSpec
import com.valb3r.deeplearning4j_trainer.repository.TrainingProcessRepository
import com.valb3r.deeplearning4j_trainer.storage.Storage
import com.valb3r.deeplearning4j_trainer.storage.StorageService
import com.valb3r.deeplearning4j_trainer.storage.resolve
import org.flowable.engine.delegate.BpmnError
import org.flowable.engine.delegate.DelegateExecution
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import java.io.File




/**
 * Input convention:
 * Data files: *.csv,*.csv.zip
 * Train spec files: *.train.yaml
 * Model files: *.fb
 * Model spec file: *.model.yaml
 */
@Service("trainingDataInputValidatorAndLoader")
class TrainingDataInputValidatorAndLoader(
    @Qualifier("yamlObjectMapper") private val yamlObjectMapper: ObjectMapper,
    private val trainingRepo: TrainingProcessRepository,
    private val storage: StorageService
): WrappedJavaDelegate() {

    override fun doExecute(execution: DelegateExecution) {
        var trainingProc = TrainingProcess(
            processId = execution.processInstanceId,
            trainedModelPath = "NOT-SET",
            businessKey = execution.processInstanceBusinessKey,
            trainingContext = null,
            processDefinitionName = execution.processDefinitionId
        )
        trainingProc = trainingRepo.save(trainingProc)

        val ctx = execution.getInput()
        val inputFolder = ctx.inputDataPath
        extractZipFilesAndDeleteArch(inputFolder, storage)
        val files = storage.list(inputFolder)
        val dataFiles = ctx.dataFilesPath ?: files.filter { it.endsWith(".csv") || it.endsWith(".csv.data.bin") }
        val trainSpecFiles = files.filter { it.endsWith(".train.yaml") }
        val modelFiles = files.filter { it.endsWith(".fb") }
        val modelSpecFiles = files.filter { it.endsWith(".model.yaml") }

        trainingProc = saveDatasetIfAvailable(ctx, trainingProc, dataFiles)

        if (modelFiles.size > 1) {
            throw BpmnError("INPUT_ERR", "There should be 0 or 1 model files (*.fb)")
        }

        if (modelSpecFiles.size > 1) {
            throw BpmnError("INPUT_ERR", "There should be 0 or 1 model spec files (*.model.yaml)")
        }

        if (modelSpecFiles.isEmpty() && modelFiles.isEmpty()) {
            throw BpmnError("INPUT_ERR", "No model or model spec available")
        }

        if (trainSpecFiles.isEmpty()) {
            throw BpmnError("INPUT_ERR", "No train spec files available")
        }

        if (trainSpecFiles.size > 1) {
            throw BpmnError("INPUT_ERR", "There are more than 1 training spec file")
        }

        if (dataFiles.isEmpty()) {
            throw BpmnError("INPUT_ERR", "No data files available")
        }

        val trainSpec = storage.read(inputFolder.resolve(trainSpecFiles.first())).use { yamlObjectMapper.readValue(it, TrainingSpec::class.java) }
        val modelSpec = modelSpecFiles.firstOrNull()?.let {
            storage.read(inputFolder.resolve(it)).use { yamlObjectMapper.readValue(it, ModelSpec::class.java) }
        }
        val filesAndDatasetSize = countRowsAndTranslateInputDataFilesToBinFormat(dataFiles)
        val logPath = ctx.outputDataPath.resolve("${execution.processInstanceId}.log")
        val trainedModelPath = ctx.outputDataPath.resolve("model.fb")

        val trainingCtx = TrainingContext(
            inputDataPath = ctx.inputDataPath,
            outputDataPath = ctx.outputDataPath,
            trainingLogPath = logPath,
            trainedModelPath = trainedModelPath,
            trainingSpec = trainSpec,
            inputFiles = filesAndDatasetSize.first,
            datasetSize = filesAndDatasetSize.second,
            modelPath = modelFiles.firstOrNull(),
            modelSpec = modelSpec,
            currentEpoch = 0L
        )
        execution.setContext(trainingCtx)
        trainingProc.trainedModelPath = trainingCtx.trainedModelPath
        trainingProc.setCtx(trainingCtx)
        trainingProc.completed = false
        trainingProc.businessKey = execution.processInstanceBusinessKey
        trainingRepo.save(trainingProc)
    }

    private fun saveDatasetIfAvailable(ctx: InputContext, trainingProc: TrainingProcess, dataFiles: List<String>): TrainingProcess {
        if (null != ctx.dataFilesPath) {
            return trainingProc
        }

        val dataset = Dataset()
        trainingProc.dataset = dataset
        dataset.name = trainingProc.businessKey
        dataset.process = trainingProc
        dataset.files = dataFiles.map { val file = DatasetFile(); file.path = it; file.dataset = dataset; return@map file }
        return trainingRepo.save(trainingProc)
    }

    private fun countRowsAndTranslateInputDataFilesToBinFormat(files: List<String>): Pair<List<String>, Long> {
        val mapper = CsvMapper()
        val result = mutableListOf<String>()
        var totalRows = 0L
        for (file in files) {
            if (file.endsWith(".csv.data.bin")) {
                var count = 0
                FstSerDe.FstIterator(file, storage).forEachRemaining { count++ }
                result += file
                totalRows += count
            } else {
                totalRows += csvToBinAndRemoveSrc(file, mapper, result, storage)
            }
        }

        return Pair(result, totalRows)
    }
}