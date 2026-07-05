package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "inventory_items")
data class InventoryItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val category: String,     // "مواد اولیه" (Raw Materials) or "محصول نهایی" (Finished Goods)
    val sku: String,          // Product Code
    val stockQuantity: Int,
    val minStockLimit: Int,   // Threshold for low stock warning
    val unitPrice: Double,
    val location: String = "", // Shelf location
    val description: String = ""
)
