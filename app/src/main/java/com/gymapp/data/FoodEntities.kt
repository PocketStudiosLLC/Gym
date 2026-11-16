package com.gymapp.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** A body-weight reading in pounds. */
@Entity(tableName = "body_weight", indices = [Index("date"), Index(value = ["uuid"], unique = true)])
data class BodyWeight(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    val date: LocalDate,
    val loggedAt: Long,
    val weightLb: Double,
)

@Entity(tableName = "meal", indices = [Index("date"), Index(value = ["uuid"], unique = true)])
data class Meal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    val date: LocalDate,
    val loggedAt: Long,
    val name: String,
    val photoPath: String?,
    val calories: Int,
    val fatG: Double,
    val proteinG: Double,
    val carbsG: Double,
)

/** Water (or another drink) in millilitres, with optional calories and creatine mixed in. */
@Entity(tableName = "water", indices = [Index("date"), Index(value = ["uuid"], unique = true)])
data class WaterLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    val date: LocalDate,
    val loggedAt: Long,
    val ml: Int,
    val calories: Int,
    @ColumnInfo(defaultValue = "0") val creatineG: Double = 0.0,
)

@Dao
interface FoodDao {
    @Query("SELECT * FROM body_weight ORDER BY loggedAt") fun observeWeights(): Flow<List<BodyWeight>>
    @Query("SELECT * FROM meal ORDER BY loggedAt") fun observeMeals(): Flow<List<Meal>>
    @Query("SELECT * FROM water ORDER BY loggedAt") fun observeWater(): Flow<List<WaterLog>>

    @Query("SELECT * FROM body_weight") suspend fun allWeights(): List<BodyWeight>
    @Query("SELECT * FROM meal") suspend fun allMeals(): List<Meal>
    @Query("SELECT * FROM water") suspend fun allWater(): List<WaterLog>

    @Insert suspend fun insertWeight(w: BodyWeight): Long
    @Insert suspend fun insertMeal(m: Meal): Long
    @Insert suspend fun insertWater(w: WaterLog): Long

    @Query("DELETE FROM body_weight WHERE id = :id") suspend fun deleteWeight(id: Long)
    @Query("DELETE FROM meal WHERE id = :id") suspend fun deleteMeal(id: Long)
    @Query("DELETE FROM water WHERE id = :id") suspend fun deleteWater(id: Long)

    @Query("DELETE FROM body_weight") suspend fun wipeWeights()
    @Query("DELETE FROM meal") suspend fun wipeMeals()
    @Query("DELETE FROM water") suspend fun wipeWater()
}
