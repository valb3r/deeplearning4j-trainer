package com.valb3r.deeplearning4j_trainer.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import javax.validation.constraints.NotEmpty

@ConstructorBinding
@ConfigurationProperties(prefix = "access")
data class UsersConfig(
    @NotEmpty val users: List<UserDef>
)

data class UserDef(val username: String, val password: String, val roles: Set<String>)