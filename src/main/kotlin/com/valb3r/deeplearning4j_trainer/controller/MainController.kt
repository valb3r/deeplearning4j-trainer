package com.valb3r.deeplearning4j_trainer.controller

import com.valb3r.deeplearning4j_trainer.controller.UserController.Companion.indexPath
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.RequestMapping
import java.util.*
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@Controller
class MainController {
    @RequestMapping("/")
    fun root(locale: Locale?): String {
        return "redirect:/index.html"
    }

    @RequestMapping("/index.html")
    fun index(): String {
        val auth: Authentication = SecurityContextHolder.getContext().authentication
        if (auth is UsernamePasswordAuthenticationToken) {
            return "redirect:${indexPath}"
        }

        return "login"
    }

    @RequestMapping("/logout")
    fun logoutPage(request: HttpServletRequest?, response: HttpServletResponse?): String? {
        val auth = SecurityContextHolder.getContext().authentication
        if (auth != null) {
            SecurityContextLogoutHandler().logout(request, response, auth)
        }
        return "login"
    }

    @RequestMapping("/login.html")
    fun login(): String {
        return "login"
    }

    @RequestMapping("/login-error.html")
    fun loginError(model: Model): String {
        model.addAttribute("loginError", true)
        return "login"
    }

    @RequestMapping("/error.html")
    fun error(request: HttpServletRequest, model: Model): String {
        model.addAttribute("errorCode", "Error " + request.getAttribute("javax.servlet.error.status_code"))
        return "error"
    }

    @RequestMapping("/403.html")
    fun forbidden(): String {
        return "403"
    }
}