package com.valb3r.deeplearning4j_trainer.storage

import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.*
import mu.KotlinLogging
import org.springframework.util.unit.DataSize
import java.io.ByteArrayInputStream
import java.io.OutputStream
import java.util.concurrent.CompletionService
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.ExecutorService
import kotlin.math.min

// Amazon S3 has maximum chunks of up to 5GB, max chunk count is 10_000

private val logger = KotlinLogging.logger {}

class MultipartS3OutputStream(
    private val bucketName: String,
    private val objectName: String,
    private val amazonS3: AmazonS3,
    bufferSize: DataSize,
    executorService: ExecutorService
) : OutputStream() {

    private val MAX_CHUNKS = 10_000 + 1
    private val BUFFER_SIZE = bufferSize.toBytes().toInt()

    private val s3Wrapper = S3Wrapper(amazonS3)
    private val completionService: CompletionService<UploadPartResult>
    private var currentOutputStream: CustomizableByteArrayOutputStream? = newOutputStream()
    private var multiPartUploadResult: InitiateMultipartUploadResult? = null
    private var partCounter = 1
    private var bytesWritten = 0L

    init {
        completionService = ExecutorCompletionService(executorService)
    }

    @Synchronized
    override fun write(bytes: ByteArray, off: Int, len: Int) {
        var remainingSizeToWrite = len
        var inputPosition = off
        do {
            val availableCapacity = BUFFER_SIZE - currentOutputStream!!.size()
            val bytesToWrite = min(availableCapacity, remainingSizeToWrite)
            currentOutputStream!!.write(bytes, inputPosition, bytesToWrite)
            inputPosition += bytesToWrite
            remainingSizeToWrite -= bytesToWrite
            initiateMultipartRequestAndCommitPartIfNeeded()
        } while (remainingSizeToWrite > 0)
        bytesWritten += len
    }

    @Synchronized
    override fun write(b: Int) {
        currentOutputStream!!.write(b)
        initiateMultipartRequestAndCommitPartIfNeeded()
        bytesWritten += 1
    }

    @Synchronized
    override fun close() {
        if (currentOutputStream == null) {
            return
        }
        if (isMultiPartUpload) {
            finishMultiPartUpload()
        } else {
            finishSimpleUpload()
        }
    }

    private fun initiateMultipartRequestAndCommitPartIfNeeded() {
        if (currentOutputStream!!.size() != BUFFER_SIZE) {
            return
        }

        if (partCounter == MAX_CHUNKS) {
            throw IllegalArgumentException("Upload is too large - chunk size ${BUFFER_SIZE}b, written $bytesWritten, part $partCounter")
        }

        initiateMultiPartIfNeeded()
        val content = currentOutputStream!!.bufferOrCopy
        val size = currentOutputStream!!.size()

        currentOutputStream = newOutputStream()
        val counter = partCounter

        val request = UploadPartRequest()
            .withBucketName(bucketName)
            .withKey(objectName)
            .withUploadId(multiPartUploadResult!!.uploadId)
            .withInputStream(ByteArrayInputStream(content))
            .withPartNumber(counter)
            .withLastPart(false)
            .withPartSize(size.toLong())

        completionService.submit { s3Wrapper.uploadPart(request) }
        ++partCounter
    }

    private val isMultiPartUpload: Boolean
        get() = multiPartUploadResult != null

    private fun finishSimpleUpload() {
        val objectMetadata = ObjectMetadata()
        val size = currentOutputStream!!.size()
        objectMetadata.contentLength = size.toLong()
        val content = currentOutputStream!!.bufferOrCopy

        currentOutputStream = null
        amazonS3.putObject(
            bucketName,
            objectName,
            ByteArrayInputStream(content, 0, size),
            objectMetadata
        )
    }

    private fun finishMultiPartUpload() {
        sendLastChunkIfNeeded()
        try {
            logger.info { "Finishing multipart upload into ${multiPartUploadResult!!.key} with ${bytesWritten}b bytes" }
            val partETags = multiPartsUploads
            amazonS3.completeMultipartUpload(
                CompleteMultipartUploadRequest(
                    multiPartUploadResult!!.bucketName,
                    multiPartUploadResult!!.key,
                    multiPartUploadResult!!.uploadId,
                    partETags
                )
            )
            logger.info { "Finished multipart upload ${multiPartUploadResult!!.key}" }
        } catch (e: ExecutionException) {
            logger.error(e) { "Multipart upload failed ${multiPartUploadResult!!.key}" }
            abortMultiPartUpload()
        } catch (e: InterruptedException) {
            logger.error(e) { "Multipart upload aborted ${multiPartUploadResult!!.key}" }
            abortMultiPartUpload()
            Thread.currentThread().interrupt()
        } finally {
            currentOutputStream = null
        }
    }

    private fun sendLastChunkIfNeeded() {
        // empty file can be created only using simple upload:
        if (currentOutputStream!!.size() == 0) {
            partCounter--
            return
        }

        val content = currentOutputStream!!.bufferOrCopy
        val size = currentOutputStream!!.size()

        currentOutputStream = null
        val counter = partCounter

        val request = UploadPartRequest()
            .withBucketName(bucketName)
            .withKey(objectName)
            .withUploadId(multiPartUploadResult!!.uploadId)
            .withInputStream(ByteArrayInputStream(content))
            .withPartNumber(counter)
            .withLastPart(true)
            .withPartSize(size.toLong())

        completionService.submit { s3Wrapper.uploadPart(request) }
    }

    private fun initiateMultiPartIfNeeded() {
        if (multiPartUploadResult != null) {
            return
        }

        multiPartUploadResult = amazonS3.initiateMultipartUpload(InitiateMultipartUploadRequest(bucketName, objectName))
    }

    private fun abortMultiPartUpload() {
        if (!isMultiPartUpload) {
            return
        }

        amazonS3.abortMultipartUpload(
            AbortMultipartUploadRequest(
                multiPartUploadResult!!.bucketName,
                multiPartUploadResult!!.key,
                multiPartUploadResult!!.uploadId
            )
        )
    }

    private val multiPartsUploads: List<PartETag>
        get() {
            val result: MutableList<PartETag> = ArrayList(partCounter)
            for (i in 0 until partCounter) {
                val partResult = completionService.take().get()
                result.add(partResult.partETag)
            }
            return result
        }

    private fun newOutputStream(): CustomizableByteArrayOutputStream {
        return CustomizableByteArrayOutputStream(32, BUFFER_SIZE, 0.5)
    }

    class CustomizableByteArrayOutputStream(
        initialCapacity: Int,
        maxArraySize: Int,
        fillFactorToCopy: Double
    ) : OutputStream() {

        private val fillFactorToCopy: Double
        private val maxArraySize: Int
        private val initialCapacity: Int
        private var buffer: ByteArray
        private var count = 0

        init {
            require(initialCapacity > 0) { "Initial capacity must positive: $initialCapacity" }
            require(maxArraySize > 0) { "Max array size must be positive: $maxArraySize" }
            require(!(fillFactorToCopy < 0 || fillFactorToCopy > 1.0)) { "Fill factor must be in [0, 1]: $fillFactorToCopy" }
            this.initialCapacity = initialCapacity
            buffer = ByteArray(this.initialCapacity)
            this.maxArraySize = maxArraySize
            this.fillFactorToCopy = fillFactorToCopy
        }

        @Synchronized
        override fun write(byteToWrite: Int) {
            ensureCapacity(count + 1)
            buffer[count] = byteToWrite.toByte()
            count += 1
        }

        @Synchronized
        override fun write(buffer: ByteArray, off: Int, len: Int) {
            if (off < 0 || off > buffer.size || len < 0 || off + len - buffer.size > 0) {
                throw IndexOutOfBoundsException()
            }
            ensureCapacity(count + len)
            System.arraycopy(buffer, off, this.buffer, count, len)
            count += len
        }

        @get:Synchronized
        val bufferOrCopy: ByteArray
            get() {
                val fillFactor = count.toDouble() / buffer.size
                return if (fillFactor >= fillFactorToCopy) {
                    buffer
                } else buffer.copyOf(count)
            }

        @Synchronized
        fun size(): Int {
            return count
        }

        override fun close() {
            // NOP
        }

        private fun ensureCapacity(minCapacity: Int) {
            if (minCapacity <= buffer.size) {
                return
            }
            grow(minCapacity)
        }

        private fun grow(minCapacity: Int) {
            val oldCapacity = buffer.size
            var newCapacity = min(oldCapacity shl 1, maxArraySize)

            if (newCapacity < minCapacity) {
                newCapacity = minCapacity
            }

            if (newCapacity > maxArraySize) {
                throw OutOfMemoryError()
            }

            buffer = buffer.copyOf(newCapacity)
        }
    }
}

class S3Wrapper(private val s3: AmazonS3) {

    fun uploadPart(request: UploadPartRequest): UploadPartResult {
        return s3.uploadPart(request)
    }
}