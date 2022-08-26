package com.valb3r.deeplearning4j_trainer.flowable

import com.valb3r.deeplearning4j_trainer.flowable.dto.InputContext
import com.valb3r.deeplearning4j_trainer.flowable.dto.TrainingContext
import com.valb3r.deeplearning4j_trainer.flowable.dto.ValidationContext
import com.valb3r.deeplearning4j_trainer.storage.Storage
import com.valb3r.deeplearning4j_trainer.storage.wrapToByteBuffer
import org.flowable.engine.delegate.DelegateExecution
import org.nd4j.autodiff.samediff.SameDiff
import java.nio.channels.Channels
import java.nio.channels.WritableByteChannel

const val INPUT = "INPUT"
const val CONTEXT = "CONTEXT"

fun DelegateExecution.getContext(): TrainingContext? {
    return this.getVariable(CONTEXT, TrainingContext::class.java)
}

fun DelegateExecution.getValidationContext(): ValidationContext? {
    return this.getVariable(CONTEXT, ValidationContext::class.java)
}

fun DelegateExecution.getInput(): InputContext {
    return this.getVariable(INPUT, InputContext::class.java)
}

fun DelegateExecution.setContext(ctx: Any) {
    return this.setVariable(CONTEXT, ctx)
}

fun DelegateExecution.updateContext(updater: (ctx: TrainingContext) -> TrainingContext) {
    this.setContext(updater(this.getContext()!!))
}

fun DelegateExecution.loadSameDiff(storage: Storage): SameDiff {
    val sd = SameDiff.fromFlatBuffers(storage.read(this.getContext()!!.trainedModelPath).wrapToByteBuffer())
    cleanup(sd)
    return sd
}

fun DelegateExecution.loadValidationSameDiff(storage: Storage): SameDiff {
    val sd = SameDiff.fromFlatBuffers(storage.read(this.getValidationContext()!!.trainedModelPath).wrapToByteBuffer())
    cleanup(sd)
    return sd
}

private fun cleanup(sd: SameDiff) {
    sd.variables.remove("one-var")
    sd.variables.remove("grad")
    val badVars = sd.variables.entries.filter { it.key.endsWith("-grad") }.map { it.key }
    badVars.forEach { sd.variables.remove(it) }
}

fun DelegateExecution.storeSameDiff(sd: SameDiff, storage: Storage) {
    val modelPath = this.getContext()!!.trainedModelPath
    val path = "$modelPath-update-candidate"
    storage.write(path).use {
        Channels.newChannel(it).write(sd.asFlatBuffers(true))
    }
    storage.move(path, modelPath)
}
