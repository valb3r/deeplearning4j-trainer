package com.valb3r.deeeplearning4j_trainer.spring.flowable

import com.valb3r.deeeplearning4j_trainer.spring.flowable.dto.TrainingContext
import org.flowable.engine.delegate.BpmnError
import org.flowable.engine.delegate.DelegateExecution
import org.nd4j.autodiff.samediff.SameDiff
import java.io.*
import kotlin.random.Random

const val CONTEXT = "CONTEXT"

fun DelegateExecution.getContext(): TrainingContext {
    return this.getVariable(CONTEXT, TrainingContext::class.java)
}

fun DelegateExecution.setContext(ctx: TrainingContext) {
    return this.setVariable(CONTEXT, ctx)
}

fun DelegateExecution.updateContext(updater: (ctx: TrainingContext) -> TrainingContext) {
    this.setContext(updater(this.getContext()))
}

fun DelegateExecution.withChunkIterRandom(consumer: (rnd: Random) -> Unit) {
    val ctx = this.getContext()
    val random = ctx.chunkIteratorRandom?.let {
        ObjectInputStream(ByteArrayInputStream(it)).readObject() as Random
    } ?: Random(ctx.trainingSpec!!.seed)

    consumer(random)

    this.updateContext {
        val os = ByteArrayOutputStream()
        ObjectOutputStream(os).writeObject(random)
        it.copy(chunkIteratorRandom = os.toByteArray())
    }
}

fun DelegateExecution.loadSameDiff(): SameDiff {
    val sd = SameDiff.fromFlatFile(File(this.getContext().trainedModelPath))
    sd.variables.remove("one-var")
    sd.variables.remove("grad")
    val badVars = sd.variables.entries.filter { it.key.endsWith("-grad") }.map { it.key }
    badVars.forEach { sd.variables.remove(it) }
    return sd
}

fun DelegateExecution.storeSameDiff(sd: SameDiff) {
    sd.asFlatFile(File(this.getContext().trainedModelPath))
}

fun DelegateExecution.dataFiles(): List<File> {
    return File(this.getContext().inputDataPath).listFiles()!!
        .filter { it.absolutePath.endsWith(".csv") || it.absolutePath.endsWith(".csv.zip") }
}

fun DelegateExecution.updateWithErrorAndThrow(error: BpmnError) {
    this.setContext(this.getContext().copy(error = error.message))
    throw error
}
