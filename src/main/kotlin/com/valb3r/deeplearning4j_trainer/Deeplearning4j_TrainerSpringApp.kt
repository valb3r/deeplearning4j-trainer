package com.valb3r.deeplearning4j_trainer

import com.valb3r.deeplearning4j_trainer.config.DirectoriesConfig
import com.valb3r.deeplearning4j_trainer.config.FlowableConfig
import com.valb3r.deeplearning4j_trainer.config.S3Config
import com.valb3r.deeplearning4j_trainer.config.UsersConfig
import com.valb3r.deeplearning4j_trainer.s3_urlconnection_adapter.Handler
import com.valb3r.deeplearning4j_trainer.s3_urlconnection_adapter.S3URLConnection
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.session.jdbc.config.annotation.web.http.EnableJdbcHttpSession
import org.springframework.transaction.annotation.EnableTransactionManagement

@EnableScheduling
@EnableJdbcHttpSession
@EnableTransactionManagement
@EnableConfigurationProperties(DirectoriesConfig::class, UsersConfig::class, S3Config::class, FlowableConfig.FlowableExecutorPoolConfig::class)
@SpringBootApplication(scanBasePackageClasses = [Deeplearning4j_TrainerSpringApp::class])
class Deeplearning4j_TrainerSpringApp {

    companion object {

        @JvmStatic
        fun main(args: Array<String>) {
            Handler.register()
            SpringApplication.run(Deeplearning4j_TrainerSpringApp::class.java, *args)
        }
    }
}