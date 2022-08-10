package com.valb3r.deeeplearning4j_trainer.spring.flowable

import org.nd4j.linalg.dataset.api.MultiDataSet
import org.nd4j.linalg.dataset.api.MultiDataSetPreProcessor
import org.nd4j.linalg.dataset.api.iterator.MultiDataSetIterator
import org.nd4j.linalg.factory.Nd4j


class FilePoolFlatBufferDatasetIterator(
    private val dataSize: Long,
    private val batchSize: Int,
    private val featureNames: List<String>,
    private val labelNames: List<String>,
    dataFiles: List<String>,
    private var binIter: FstSerDe.FstIterator? = null,
    private var dataFilePool: MutableSet<String> = linkedSetOf(*dataFiles.toTypedArray()),
    private var fetchedSize: Long = 0,
): MultiDataSetIterator {

    override fun hasNext(): Boolean {
        return fetchedSize < dataSize
    }

    override fun next(num: Int): MultiDataSet {
        val features = mutableMapOf<String, MutableList<FloatArray>>()
        val labels = mutableMapOf<String, MutableList<FloatArray>>()
        for (ind in 0 until num) {
            if (null == binIter || !binIter!!.hasNext()) {
                if (dataFilePool.isEmpty()) { // No data left
                    break
                }
                val file = dataFilePool.first()
                dataFilePool.remove(file)
                binIter = FstSerDe.FstIterator(file)
            }
            val entry = binIter!!.next()
            featureNames.forEach { name ->
                entry[name]?.let { vals ->
                    features.computeIfAbsent(name) { mutableListOf() }.add(vals)
                }
            }
            labelNames.forEach { name ->
                entry[name]?.let { vals ->
                    labels.computeIfAbsent(name) { mutableListOf() }.add(vals)
                }
            }
        }

        if (features.isEmpty()) {
            throw IllegalArgumentException("No features were read")
        }

        if (labels.isEmpty()) {
            throw IllegalArgumentException("No labels were read")
        }

        fetchedSize += num

        return org.nd4j.linalg.dataset.MultiDataSet(
            featureNames.map { features[it]!! }.map { Nd4j.create(it.toTypedArray()) }.toTypedArray(),
            labelNames.map { labels[it]!! }.map { Nd4j.create(it.toTypedArray()) }.toTypedArray(),
        )
    }

    override fun next(): MultiDataSet {
        return next(batchSize)
    }

    fun skipNext() {
        if (null == binIter || !binIter!!.hasNext()) {
            if (dataFilePool.isEmpty()) { // No data left
                throw IllegalArgumentException("Empty")
            }
            val file = dataFilePool.first()
            dataFilePool.remove(file)
            binIter = FstSerDe.FstIterator(file)
        }

        binIter!!.skipNext()
    }

    override fun remove() {
        TODO("Not yet implemented")
    }

    override fun setPreProcessor(preProcessor: MultiDataSetPreProcessor?) {
        TODO("Not yet implemented")
    }

    override fun getPreProcessor(): MultiDataSetPreProcessor {
        TODO("Not yet implemented")
    }

    override fun resetSupported(): Boolean {
        return false
    }

    override fun asyncSupported(): Boolean {
        return false
    }

    override fun reset() {
        TODO("Not yet implemented")
    }
}