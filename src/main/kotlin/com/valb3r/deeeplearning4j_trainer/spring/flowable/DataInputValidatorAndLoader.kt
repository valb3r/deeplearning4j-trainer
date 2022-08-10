package com.valb3r.deeeplearning4j_trainer.spring.flowable

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.csv.CsvMapper
import com.valb3r.deeeplearning4j_trainer.spring.domain.TrainingProcess
import com.valb3r.deeeplearning4j_trainer.spring.flowable.dto.ModelSpec
import com.valb3r.deeeplearning4j_trainer.spring.flowable.dto.TrainingContext
import com.valb3r.deeeplearning4j_trainer.spring.flowable.dto.TrainingSpec
import com.valb3r.deeeplearning4j_trainer.spring.repository.TrainingProcessRepository
import org.flowable.engine.delegate.BpmnError
import org.flowable.engine.delegate.DelegateExecution
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream




/**
 * Input convention:
 * Data files: *.csv,*.csv.zip
 * Train spec files: *.train.yaml
 * Model files: *.fb
 * Model spec file: *.model.yaml
 */
@Service("dataInputValidatorAndLoader")
class DataInputValidatorAndLoader(
    @Qualifier("yamlObjectMapper") private val yamlObjectMapper: ObjectMapper,
    private val trainingRepo: TrainingProcessRepository
): WrappedJavaDelegate() {

    override fun doExecute(execution: DelegateExecution) {
        val inputFolder = File(execution.getContext().inputDataPath)
        extractZipFiles(inputFolder)
        val files = inputFolder.listFiles()!!.map { it.absolutePath }
        val dataFiles = files.filter { it.endsWith(".csv") || it.endsWith(".csv.data.bin") }
        val trainSpecFiles = files.filter { it.endsWith(".train.yaml") }
        val modelFiles = files.filter { it.endsWith(".fb") }
        val modelSpecFiles = files.filter { it.endsWith(".model.yaml") }

        var trainingProc = TrainingProcess(
            processId = execution.processInstanceId,
            trainedModelPath = "NOT-SET",
            businessKey = execution.processInstanceBusinessKey,
            trainingContext = TrainingContext("DUMMY", "DUMMY", "DUMMY", "DUMMY"),
            processDefinitionName = execution.processDefinitionId
        )
        trainingProc = trainingRepo.save(trainingProc)

        if (modelFiles.size > 1) {
            execution.updateWithErrorAndThrow(BpmnError("INPUT_ERR", "There should be 0 or 1 model files (*.fb)"))
        }

        if (modelSpecFiles.size > 1) {
            execution.updateWithErrorAndThrow(BpmnError("INPUT_ERR", "There should be 0 or 1 model spec files (*.model.yaml)"))
        }

        if (modelSpecFiles.isEmpty() && modelFiles.isEmpty()) {
            execution.updateWithErrorAndThrow(BpmnError("INPUT_ERR", "No model or model spec available"))
        }

        if (trainSpecFiles.isEmpty()) {
            execution.updateWithErrorAndThrow(BpmnError("INPUT_ERR", "No train spec files available"))
        }

        if (trainSpecFiles.size > 1) {
            execution.updateWithErrorAndThrow(BpmnError("INPUT_ERR", "There are more than 1 training spec file"))
        }

        if (dataFiles.isEmpty()) {
            execution.updateWithErrorAndThrow(BpmnError("INPUT_ERR", "No data files available"))
        }

        val trainSpec = yamlObjectMapper.readValue(inputFolder.resolve(trainSpecFiles.first()), TrainingSpec::class.java)
        val modelSpec = modelSpecFiles.firstOrNull()?.let { yamlObjectMapper.readValue(inputFolder.resolve(it), ModelSpec::class.java) }
        val filesAndDatasetSize = countRowsAndTranslateInputDataFilesToBinFormat(dataFiles)
        val ctx = execution.getContext().copy(
            trainingSpec = trainSpec,
            inputFiles = filesAndDatasetSize.first,
            datasetSize = filesAndDatasetSize.second,
            modelPath = modelFiles.firstOrNull(),
            modelSpec = modelSpec
        )
        execution.setContext(ctx)
        trainingProc.trainedModelPath = ctx.trainedModelPath
        trainingProc.setCtx(ctx)
        trainingRepo.save(trainingProc)
    }

    private fun extractZipFiles(inputFolder: File) {
        val zipFiles = inputFolder.listFiles()!!.map { it.absolutePath }.filter { it.endsWith(".zip") }
        for (zipFile in zipFiles) {
            ZipInputStream(FileInputStream(zipFile)).use { zis ->
                while (true) {
                    val zipEntry: ZipEntry = zis.nextEntry ?: break
                    if (zipEntry.isDirectory) {
                        throw IllegalArgumentException("Directories are not allowed in ZIP files, found ${zipEntry.name} in $zipFile")
                    }

                    val buffer = ByteArray(1024)
                    FileOutputStream(inputFolder.resolve(zipEntry.name)).use { fos ->
                        while (true) {
                            val len = zis.read(buffer)
                            if (len <= 0) break
                            fos.write(buffer, 0, len)
                        }
                    }
                    zis.closeEntry()
                }
            }

            File(zipFile).delete()
        }
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