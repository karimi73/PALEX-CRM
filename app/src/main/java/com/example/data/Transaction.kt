package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val customerId: Int,
    val customerName: String,
    val title: String,
    val amount: Double, // total amount calculated as: (quantity * unitPrice) - discount + tax
    val type: String, // "Sale", "PreOrder"
    val status: String, // "Paid", "Pending", "Installment"
    val date: Long = System.currentTimeMillis(),
    val description: String = "",
    val quantity: Int = 1,
    val unitPrice: Double = 0.0,
    val discount: Double = 0.0,
    val tax: Double = 0.0
)
