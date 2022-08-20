package com.valb3r.deeplearning4j_trainer.security

import com.valb3r.deeplearning4j_trainer.config.UsersConfig
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.security.web.SecurityFilterChain


@Configuration
@EnableGlobalMethodSecurity(prePostEnabled = true)
@EnableWebSecurity
class SpringSecurityConfig(private val usersDef: UsersConfig) {

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        return http
            .formLogin()
            .loginPage("/login.html")
            .failureUrl("/login-error.html")
            .and()
            .logout()
            .logoutSuccessUrl("/index.html")
            .and()
            .authorizeRequests()
            .antMatchers("/admin/**").hasRole("ADMIN")
            .antMatchers("/user/**").hasRole("USER")
            .antMatchers("/api/**").hasRole("USER")
            .antMatchers("/shared/**").hasAnyRole("USER", "ADMIN")
            .and()
            .exceptionHandling()
            .accessDeniedPage("/403.html")
            .and()
            .build()
    }

    @Bean
    fun userDetailsManager(): InMemoryUserDetailsManager? {
        val users = mutableListOf<UserDetails>()
        usersDef.users.forEach { userDef ->
            val builder: User.UserBuilder = User.builder()
                .passwordEncoder { rawPassword: CharSequence? ->
                    PasswordEncoderFactories.createDelegatingPasswordEncoder().encode(rawPassword)
                }

            val user: UserDetails = builder
                .username(userDef.username)
                .password(userDef.password)
                .roles(*userDef.roles.toTypedArray())
                .build()
            users += user
        }

        return InMemoryUserDetailsManager(*users.toTypedArray())
    }
}