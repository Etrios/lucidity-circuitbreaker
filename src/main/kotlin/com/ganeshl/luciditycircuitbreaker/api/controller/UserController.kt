package com.ganeshl.luciditycircuitbreaker.api.controller

import com.ganeshl.luciditycircuitbreaker.model.User
import com.ganeshl.luciditycircuitbreaker.service.UserService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.net.URI

@RestController
@RequestMapping("/v1/users")
class UserController(private val userService: UserService) {

    @GetMapping()
    fun listUsers() = ResponseEntity.ok(userService.getAllUsers())

    @PostMapping()
    fun post(@RequestBody user: User): ResponseEntity<User> {
        val createdUser = userService.saveUser(user)
        return ResponseEntity.created(URI("/${createdUser.id}")).body(createdUser)
    }

    @GetMapping("/{id}")
    fun getUser(
        @PathVariable id: String,
        @RequestParam throwError: Boolean = false,
        @RequestParam delay: Long?
    ) = userService.findUser(id, throwError, delay).toResponseEntity()

    private fun User?.toResponseEntity(): ResponseEntity<User> =
        // If the message is null (not found), set response code to 404
        this?.let { ResponseEntity.ok(it) } ?: ResponseEntity.notFound().build()
}