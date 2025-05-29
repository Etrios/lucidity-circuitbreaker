package com.ganeshl.luciditycircuitbreaker.repository

import com.ganeshl.luciditycircuitbreaker.model.User
import org.springframework.data.repository.CrudRepository

interface UserRepository: CrudRepository<User, String>