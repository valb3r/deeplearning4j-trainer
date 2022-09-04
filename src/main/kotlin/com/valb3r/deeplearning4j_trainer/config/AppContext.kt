package com.valb3r.deeplearning4j_trainer.config

import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.context.annotation.Configuration

@Configuration
class AppContext: ApplicationContextAware {

    override fun setApplicationContext(applicationContext: ApplicationContext) {
        CONTEXT = applicationContext
    }

    companion object {
        lateinit var CONTEXT: ApplicationContext
    }
}