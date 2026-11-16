package com.gymapp.data

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.util.UUID

class FoodRepository(db: GymDatabase, private val photos: PhotoStore) {
    private val dao = db.foodDao()

    val weights: Flow<List<BodyWeight>> = dao.observeWeights()
    val meals: Flow<List<Meal>> = dao.observeMeals()
    val water: Flow<List<WaterLog>> = dao.observeWater()

    private fun uuid() = UUID.randomUUID().toString()
    private fun now() = System.currentTimeMillis()

    suspend fun addWeight(weightLb: Double) =
        dao.insertWeight(BodyWeight(uuid = uuid(), date = LocalDate.now(), loggedAt = now(), weightLb = weightLb))

    suspend fun addMeal(name: String, photoPath: String?, calories: Int, fatG: Double, proteinG: Double, carbsG: Double) =
        dao.insertMeal(
            Meal(
                uuid = uuid(), date = LocalDate.now(), loggedAt = now(), name = name.trim(), photoPath = photoPath,
                calories = calories, fatG = fatG, proteinG = proteinG, carbsG = carbsG,
            ),
        )

    suspend fun addWater(ml: Int, calories: Int, creatineG: Double) =
        dao.insertWater(WaterLog(uuid = uuid(), date = LocalDate.now(), loggedAt = now(), ml = ml, calories = calories, creatineG = creatineG))


    suspend fun deleteWeight(w: BodyWeight) = dao.deleteWeight(w.id)
    suspend fun deleteWater(w: WaterLog) = dao.deleteWater(w.id)
    suspend fun deleteMeal(m: Meal) {
        dao.deleteMeal(m.id)
        photos.delete(m.photoPath)
    }
}
