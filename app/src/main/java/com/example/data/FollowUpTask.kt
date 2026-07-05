package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "follow_up_tasks")
data class FollowUpTask(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val customerId: Int,
    val customerName: String,
    val title: String,
    val dueDate: Long,
    val isCompleted: Boolean = false,
    val priority: String = "Medium" // "High", "Medium", "Low"
)
