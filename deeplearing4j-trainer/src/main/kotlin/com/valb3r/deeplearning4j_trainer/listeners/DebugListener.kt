package com.valb3r.deeplearning4j_trainer.listeners

import lombok.RequiredArgsConstructor
import org.nd4j.autodiff.listeners.At
import org.nd4j.autodiff.listeners.BaseListener
import org.nd4j.autodiff.listeners.Operation
import org.nd4j.autodiff.samediff.SameDiff
import org.nd4j.autodiff.samediff.internal.SameDiffOp
import org.nd4j.linalg.api.ndarray.INDArray
import org.nd4j.linalg.api.ops.OpContext
import org.nd4j.linalg.dataset.api.MultiDataSet
import java.util.*

@RequiredArgsConstructor
class DebugListener(
    private val dumpEachNIter: Int = 1000,
    private val dumpInputs: Boolean = true,
    private val dumpOutputs: Boolean = true
) : BaseListener() {
    override fun isActive(operation: Operation): Boolean {
        return true
    }

    override fun opExecution(
        sd: SameDiff,
        at: At,
        batch: MultiDataSet?,
        op: SameDiffOp,
        opContext: OpContext,
        outputs: Array<INDArray>
    ) {
        if (0 != at.iteration() % dumpEachNIter) {
            return
        }
        if (opContext.inputArrays.size == 1) {
            System.out.printf(
                "%s with %s %s to %s %n",
                op.name,
                op.inputsToOp,
                Arrays.toString(opContext.getInputArray(0).shape()),
                op.outputsOfOp
            )
        } else if (opContext.inputArrays.size == 2) {
            System.out.printf(
                "%s with %s %s %s to %s %n",
                op.name,
                op.inputsToOp,
                Arrays.toString(opContext.getInputArray(0).shape()),
                Arrays.toString(opContext.getInputArray(1).shape()),
                op.outputsOfOp
            )
        }
        if (dumpInputs) {
            for (i in opContext.inputArrays.indices) {
                System.out.printf(
                    "Input of %s with %s: %n %s %n",
                    op.name,
                    op.inputsToOp[i],
                    opContext.getInputArray(i)
                )
            }
        }
        if (dumpOutputs) {
            for (i in opContext.outputArrays.indices) {
                System.out.printf(
                    "Output of %s with %s: %n %s %n",
                    op.name,
                    op.outputsOfOp[i],
                    opContext.getOutputArray(i)
                )
            }
        }
    }
}