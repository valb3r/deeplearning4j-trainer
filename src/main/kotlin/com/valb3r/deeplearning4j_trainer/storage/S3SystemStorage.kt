package com.valb3r.deeplearning4j_trainer.storage

import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.amazonaws.services.s3.iterable.S3Objects
import com.google.common.cache.CacheBuilder
import com.valb3r.deeplearning4j_trainer.config.S3Config
import mu.KotlinLogging
import org.springframework.stereotype.Service
import java.io.InputStream
import java.io.OutputStream
import java.time.Duration
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors
import java.util.stream.StreamSupport

private const val S3_PROTO = "s3://"

private val logger = KotlinLogging.logger {}

@Service
class S3SystemStorage(private val conf: S3Config): StorageSystem {

    private val executorService = ThreadPoolExecutor(
        2,
        2,
        0L,
        TimeUnit.MILLISECONDS,
        LinkedBlockingQueue(1),
        ThreadPoolExecutor.CallerRunsPolicy()
    )

    private val s3clients = CacheBuilder.newBuilder()
        .expireAfterWrite(Duration.ofMinutes(1))
        .maximumSize(3)
        .build<String, AmazonS3>()

    override fun supports(path: String): Boolean {
        return path.startsWith(S3_PROTO)
    }

    override fun list(path: String, recursively: Boolean): List<String> {
        val s3ObjectSummaries = S3Objects.withPrefix(path.asS3(), path.bucket(), path.objKey())
        val objectStream = StreamSupport.stream(s3ObjectSummaries.spliterator(), false)
        return objectStream.filter { recursively || it.key.substring(path.objKey().length).trim('/').contains("/") }.map {
            "$S3_PROTO${path.hostAndPort()}/${it.bucketName}/${it.key}"
        }.collect(Collectors.toList())
    }

    override fun read(path: String): InputStream {
        return path.asS3().getObject(path.bucket(), path.objKey()).objectContent
    }

    override fun write(path: String): OutputStream {
        logger.info { "Write to $path" }
        return MultipartS3OutputStream(
            path.bucket(),
            path.objKey(),
            path.asS3(),
            executorService
        )
    }

    override fun exists(path: String): Boolean {
        return path.asS3().doesObjectExist(path.bucket(), path.objKey())
    }

    override fun remove(path: String): Boolean {
        logger.info { "Remove from $path" }
        path.asS3().deleteObject(path.bucket(), path.objKey())
        return true
    }

    private fun String.bucket(): String {
        return this.substring(S3_PROTO.length).split("/")[1]
    }

    private fun String.objKey(): String {
        return this.substring(S3_PROTO.length).split("/", limit = 3)[2]
    }

    private fun String.hostAndPort(): String {
        return this.substring(S3_PROTO.length).split("/", limit = 3)[0]
    }

    private fun String.asS3(): AmazonS3 {
        val hostAndPort = this.hostAndPort()
        return s3clients.get(hostAndPort) {
            AmazonS3ClientBuilder.standard()
                .withPathStyleAccessEnabled(true)
                .withEndpointConfiguration(AwsClientBuilder.EndpointConfiguration("${if (conf.isHttp) "http://" else "https://"}$hostAndPort", conf.region))
                .withCredentials(AWSStaticCredentialsProvider(BasicAWSCredentials(conf.accessKeyId, conf.secretKey)))
                .build()
        }
    }
}