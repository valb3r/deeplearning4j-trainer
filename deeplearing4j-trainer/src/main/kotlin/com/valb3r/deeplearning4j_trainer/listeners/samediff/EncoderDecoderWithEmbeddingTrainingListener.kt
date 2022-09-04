package com.valb3r.deeplearning4j_trainer.listeners.samediff

import com.jogamp.opengl.util.texture.TextureData
import com.jogamp.opengl.util.texture.TextureIO
import org.jzy3d.chart.AWTChart
import org.jzy3d.chart.factories.OffscreenChartFactory
import org.jzy3d.colors.Color
import org.jzy3d.plot2d.primitives.LineSerie2d
import org.jzy3d.plot3d.rendering.legends.overlay.Legend
import org.jzy3d.plot3d.rendering.legends.overlay.LineLegendLayout
import org.jzy3d.plot3d.rendering.legends.overlay.OverlayLegendRenderer
import org.nd4j.autodiff.listeners.At
import org.nd4j.autodiff.listeners.BaseListener
import org.nd4j.autodiff.listeners.Loss
import org.nd4j.autodiff.listeners.Operation
import org.nd4j.autodiff.samediff.SameDiff
import org.nd4j.evaluation.BaseEvaluation
import org.nd4j.evaluation.IMetric
import org.nd4j.linalg.api.ndarray.INDArray
import org.nd4j.linalg.dataset.api.MultiDataSet
import org.nd4j.linalg.indexing.NDArrayIndex
import java.awt.Font
import java.io.File
import java.io.Serializable

class EncoderDecoderWithEmbeddingTrainingListener(
    private val chartVarNames: List<String>,
    private val singleValVars: Map<String, List<Int>>,
    private val numPoints: Int,
    private val freq: Int,
    private val dumpEachN: Int = 10_000
): BaseListener() {

    override fun isActive(operation: Operation?): Boolean {
        return true
    }

    override fun iterationDone(sd: SameDiff, at: At, dataSet: MultiDataSet, loss: Loss?) {
        if (0 != at.iteration() % dumpEachN) {
            return
        }

        val xVals = (0 until numPoints).map { it.toDouble() / freq }.toDoubleArray()
        val inputLeft = dataSet.features[0].get(NDArrayIndex.point(0)).toDoubleVector()
        val inputRight = dataSet.features[1].get(NDArrayIndex.point(0)).toDoubleVector()

        val outputs = sd.output(dataSet, *chartVarNames.toTypedArray(), *singleValVars.keys.toTypedArray())
        singleValVars.forEach {
            println(
                "Value of ${it.key} at ${at.iteration()}: " +
                        "expected=${it.value.map { pos -> dataSet.labels[pos][NDArrayIndex.point(0)] }} " +
                        "actual=${outputs[it.key]!![NDArrayIndex.point(0)]}"
            )
        }

        makeChart(
            mapOf(
                "Left" to Pair(xVals, inputLeft),
                "Right" to Pair(xVals, inputRight),
                *chartVarNames.map { it to Pair(xVals, outputs[it]!!.get(NDArrayIndex.point(0))!!.toDoubleVector()) }.toTypedArray()
            ),
            "encDecAt-${at.iteration()}-${chartVarNames.joinToString()}.png"
        )
    }

    class Eval: BaseEvaluation<Eval>() {

        override fun eval(
            labels: INDArray?,
            networkPredictions: INDArray?,
            maskArray: INDArray?,
            recordMetaData: MutableList<out Serializable>?
        ) {
            TODO("Not yet implemented")
        }

        override fun merge(other: Eval?) {
            TODO("Not yet implemented")
        }

        override fun reset() {
            TODO("Not yet implemented")
        }

        override fun stats(): String {
            TODO("Not yet implemented")
        }

        override fun getValue(metric: IMetric?): Double {
            TODO("Not yet implemented")
        }

        override fun newInstance(): Eval {
            TODO("Not yet implemented")
        }
    }
}

private fun makeChart(
    samplesNameXY: Map<String, Pair<DoubleArray, DoubleArray>>,
    imageName: String,
    path: String = "/Users/valentynberezin/IdeaProjects/deeplearning4j_trainer/res/"
) {
    val colors = arrayOf(Color.RED, Color.BLUE, Color.GREEN, Color.CYAN, Color.MAGENTA, Color.ORANGE)
    val f = OffscreenChartFactory(1024, 1024)
    val chart = f.newChart() as AWTChart


    val charts = samplesNameXY.toSortedMap().entries.mapIndexed { ind, it ->
        val serie = LineSerie2d(it.key)
        serie.color = colors[ind % colors.size]
        val serieData = it.value
        serieData.first.zip(serieData.second).forEach { serie.add(it.first, it.second) }
        serie
    }
    chart.add(charts)

    // Legend
    val infos: MutableList<Legend> = ArrayList()
    charts.forEach { infos.add(Legend(it.name, it.color)) }
    val legend = OverlayLegendRenderer(infos)
    val layout: LineLegendLayout = legend.layout
    layout.backgroundColor = Color.WHITE
    layout.font = Font("Helvetica", Font.PLAIN, 12)
    chart.addRenderer(legend)
    chart.axisLayout.font = org.jzy3d.painters.Font("Helvetica", 30)

    // Open as 2D chart
    chart.view2d()
    val imagesDir = File(path)
    if (!imagesDir.exists()) {
        imagesDir.mkdir()
    }
    TextureIO.write(chart.screenshot() as TextureData, imagesDir.resolve(imageName))
}