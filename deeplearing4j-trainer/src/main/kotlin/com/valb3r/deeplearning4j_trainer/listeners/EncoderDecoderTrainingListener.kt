package com.valb3r.deeplearning4j_trainer.listeners

import com.jogamp.opengl.util.texture.TextureData
import com.jogamp.opengl.util.texture.TextureIO
import org.deeplearning4j.nn.api.Model
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork
import org.deeplearning4j.optimize.api.BaseTrainingListener
import org.jzy3d.chart.AWTChart
import org.jzy3d.chart.factories.OffscreenChartFactory
import org.jzy3d.colors.Color
import org.jzy3d.plot2d.primitives.LineSerie2d
import org.jzy3d.plot3d.rendering.legends.overlay.Legend
import org.jzy3d.plot3d.rendering.legends.overlay.LineLegendLayout
import org.jzy3d.plot3d.rendering.legends.overlay.OverlayLegendRenderer
import org.nd4j.linalg.indexing.NDArrayIndex
import java.awt.Font
import java.io.File

class EncoderDecoderTrainingListener(
    private val numPoints: Int,
    private val freq: Int,
    private val dumpEachN: Int = 10_000
): BaseTrainingListener() {
    override fun iterationDone(model: Model?, iteration: Int, epoch: Int) {
        if (0 != iteration % dumpEachN) {
            return
        }
        val xVals = (0 until numPoints).map { it.toDouble() / freq }.toDoubleArray()
        val batchPointZero = model!!.input().get(NDArrayIndex.point(0))
        val inputLeft = batchPointZero.get(NDArrayIndex.interval(0, numPoints)).toDoubleVector()
        val inputRight = batchPointZero.get(NDArrayIndex.interval(numPoints, 2 * numPoints)).toDoubleVector()
        val output = (model as MultiLayerNetwork).output(batchPointZero.reshape(1, -1)).toDoubleVector()
        makeChart(
            mapOf(
                "Left" to Pair(xVals, inputLeft),
                "Right" to Pair(xVals, inputRight),
                "Output" to Pair(xVals, output)
            ),
            "encDecAt-${iteration}.png"
        )
    }
}

fun makeChart(
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