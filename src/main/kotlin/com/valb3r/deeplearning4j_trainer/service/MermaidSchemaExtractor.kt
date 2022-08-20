package com.valb3r.deeplearning4j_trainer.service

import com.valb3r.deeplearning4j_trainer.repository.ProcessRepository
import org.nd4j.autodiff.samediff.SameDiff
import org.springframework.stereotype.Service
import java.io.File

@Service
class MermaidSchemaExtractor(private val processRepository: ProcessRepository) {

    fun extract(processId: String): String {
        val proc = processRepository.findByProcessId(processId)!!
        val sd = SameDiff.fromFlatFile(File(proc.modelPath(true)))
        val structure = StringBuilder()
        structure.append("\nflowchart TD\n")
        val inputVars = sd.inputs().toSet()
        val lossVars = sd.lossVariables.toSet()
        sd.ops.map {
            it.value.inputsToOp.forEach {inp ->
                it.value.outputsOfOp.forEach {outp ->
                    val inpId = makeId(inp)
                    val outpId = makeId(outp)
                    var inputBrackets = arrayOf("[", "]")
                    var outputBrackets = arrayOf("[", "]")
                    if (inputVars.contains(inp) || lossVars.contains(inp)) {
                        inputBrackets = arrayOf("[\\", "/]")
                    }
                    if (inputVars.contains(outp) || lossVars.contains(outp)) {
                        outputBrackets = arrayOf("[/", "\\]")
                    }
                    structure.append("$inpId${inputBrackets[0]}\"$inp${sd.shape(inp)}\"${inputBrackets[1]} " +
                            "-- \"${it.value.name}\" --> " +
                            "$outpId${outputBrackets[0]}\"$outp${sd.shape(inp)}\"${outputBrackets[1]}\n")
                }
            }
        }
        return structure.toString()
    }

    private fun SameDiff.shape(varName: String): String {
        val shape = this.getVariable(varName).shape ?: return ""
        return ", ${shape.joinToString(":")}"
    }

    private fun makeId(name: String): String {
        return name.replace("[-:\\[\\] *;]".toRegex(), "_")
    }
}