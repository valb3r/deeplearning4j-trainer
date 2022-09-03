package com.valb3r.deeplearning4j_trainer.flowable.validation

import com.fasterxml.jackson.databind.ObjectMapper
import com.valb3r.deeplearning4j_trainer.flowable.*
import com.valb3r.deeplearning4j_trainer.flowable.calculator.ExpressionParser
import com.valb3r.deeplearning4j_trainer.flowable.dto.TrainingContext
import com.valb3r.deeplearning4j_trainer.flowable.dto.ValidationContext
import com.valb3r.deeplearning4j_trainer.flowable.training.ModelTrainer
import com.valb3r.deeplearning4j_trainer.repository.ValidationProcessRepository
import com.valb3r.deeplearning4j_trainer.storage.StorageService
import org.flowable.engine.delegate.DelegateExecution
import org.nd4j.autodiff.samediff.SameDiff
import org.nd4j.linalg.api.ndarray.INDArray
import org.springframework.stereotype.Service

@Service("modelValidator")
class ModelValidator(
    private val validationRepo: ValidationProcessRepository,
    private val mapper: ObjectMapper,
    private val storage: StorageService
): WrappedJavaDelegate() {

    override fun doExecute(execution: DelegateExecution) {
        val ctx = execution.getValidationContext()!!
        val sd = execution.loadValidationSameDiff(storage)
        val parser = ExpressionParser(sd)
        val variables = mutableSetOf<String>()
        val renames = mutableMapOf<String, String>()
        for (it in ctx.validations) {
            if (renames.contains(it.extractionExpr)) {
                continue;
            }

            renames[it.extractionExpr] = sd.uniqName(it.extractionExpr)
            renames[it.labelVar] = it.labelVar
            val value = sd.identity(parser.parse(it.extractionExpr)).rename(renames[it.extractionExpr])
            variables += value.name()
            variables += it.labelVar
        }

        val iter = ctx.validationIterator(storage)
        val result = sd.output(iter, *variables.toTypedArray())

        val metrics = mutableMapOf<String, Map<String, Float>>()
        ctx.validations.forEach {
            metrics["${it.extractionExpr} vs ${it.labelVar} as ${it.validation.name()}"] = it.validation.eval(
                extract(result[renames[it.labelVar]]!!),
                extract(result[renames[it.extractionExpr]]!!),
                iter.resultSetIds?.toFloatArray()
            )
        }

        execution.updateContext {
            it.copy(
                datasetSize = iter.computedDatasetSize
            )
        }

        updateProcess(sd, execution, ctx, metrics)
    }

    private fun updateProcess(sd: SameDiff, execution: DelegateExecution, ctx: ValidationContext, metrics: Map<String, Map<String, Float>>) {
        txOper!!.execute {
            val process = validationRepo.findByProcessId(execution.processInstanceId)!!
            process.setCtx(ctx)
            process.validationResult = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(metrics)
            validationRepo.save(process)
        }
    }

    fun extract(array: INDArray): Array<FloatArray> {
        if (array.rank() < 2) {
            return array.toFloatVector().map { floatArrayOf(it) }.toTypedArray()
        }

        return array.toFloatMatrix()
    }
}