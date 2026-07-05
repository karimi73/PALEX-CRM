package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "customers")
data class Customer(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val phone: String,
    val email: String,
    val company: String, // Shop or workshop name
    val type: String, // "VIP", "Regular", "Lead", "Inactive"
    val registrationDate: Long = System.currentTimeMillis(),
    val totalSpent: Double = 0.0,
    val notes: String = ""
)
