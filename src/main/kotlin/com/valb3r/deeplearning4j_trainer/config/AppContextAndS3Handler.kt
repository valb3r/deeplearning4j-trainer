package com.valb3r.deeplearning4j_trainer.config

import com.valb3r.deeplearning4j_trainer.s3_urlconnection_adapter.s3.Handler
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.context.annotation.Configuration

@Configuration
class AppContextAndS3Handler: ApplicationContextAware {

    override fun setApplicationContext(applicationContext: ApplicationContext) {
        Handler.registerV2()
        CONTEXT = applicationContext
    }

    companion object {
        lateinit var CONTEXT: ApplicationContext
    }
}