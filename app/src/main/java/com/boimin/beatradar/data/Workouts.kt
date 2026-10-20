package com.boimin.beatradar.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Left from workout plans (removed): the table was created by database v7 and stays, empty, so no new migration is
 * needed. Nothing reads or writes it.
 */
@Entity(tableName = "workout_plan")
data class WorkoutPlan(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val steps: String,
    val createdAt: Long,
)
