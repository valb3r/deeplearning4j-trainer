package com.valb3r.deeplearning4j_trainer.s3_urlconnection_adapter.s3

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.valb3r.deeplearning4j_trainer.storage.s3.S3Config
import com.valb3r.deeplearning4j_trainer.storage.s3.S3Storage
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.net.URLConnection
import java.net.URLStreamHandler
import java.util.*

// Name MUST BE Handler
open class Handler : URLStreamHandler() {

    @Throws(IOException::class)
    override fun openConnection(url: URL): URLConnection {
        return S3URLConnection(url)
    }

    class S3URLConnection(url: URL?) : URLConnection(url) {

        @Throws(IOException::class)
        override fun connect() {
            connected = true
        }

        override fun getInputStream(): InputStream {
            val url = url.toURI().toASCIIString()
            return S3Storage(url.decodeS3CredentialsFromUrl()).read(url.decodeS3Url())
        }
    }

    companion object {

        fun register() {
            val packageName = Handler::class.qualifiedName!!.replace("\\.\\w+\\.\\w+$".toRegex(), "")
            var registered: String? = System.getProperty("java.protocol.handler.pkgs")
            registered = if (true == registered?.isNotBlank()) "|$registered" else ""
            System.setProperty("java.protocol.handler.pkgs", "$packageName$registered")
        }

        fun String.encodeS3CredentialsToUrl(config: S3Config): String {
            return "$this?c=${Base64.getUrlEncoder().encodeToString(ObjectMapper().registerModule(KotlinModule.Builder().build()).writeValueAsBytes(config))}"
        }

        fun String.decodeS3CredentialsFromUrl(): S3Config {
            val base64 = this.split("?c=")[1]
            return ObjectMapper().registerModule(KotlinModule.Builder().build()).readValue(Base64.getUrlDecoder().decode(base64), S3Config::class.java)
        }

        fun String.decodeS3Url(): String {
            return this.split("?c=")[0]
        }
    }
}