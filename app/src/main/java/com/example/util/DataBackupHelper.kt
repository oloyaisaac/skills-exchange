package com.example.util

import android.content.Context
import android.content.Intent
import com.example.data.db.JobPostEntity
import com.example.data.db.ServiceProviderEntity
import org.json.JSONArray
import org.json.JSONObject

object DataBackupHelper {

    fun generateBackupJson(
        providers: List<ServiceProviderEntity>,
        jobs: List<JobPostEntity>
    ): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("exportDate", System.currentTimeMillis())
        root.put("app", "SkillsExchange")

        val providersArray = JSONArray()
        providers.forEach { p ->
            val obj = JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("tagline", p.tagline)
                put("category", p.category)
                put("rating", p.rating)
                put("hourlyRate", p.hourlyRate)
                put("neighborhood", p.neighborhood)
                put("kycStatus", p.kycStatus)
                put("momoProvider", p.momoProvider)
                put("momoNumber", p.momoNumber)
                put("endorsementsCount", p.endorsementsCount)
            }
            providersArray.put(obj)
        }
        root.put("providers", providersArray)

        val jobsArray = JSONArray()
        jobs.forEach { j ->
            val obj = JSONObject().apply {
                put("id", j.id)
                put("title", j.title)
                put("description", j.description)
                put("category", j.category)
                put("district", j.district)
                put("budget", j.budget)
                put("status", j.status)
                put("escrowStatus", j.escrowStatus)
                put("paymentChannel", j.paymentChannel)
            }
            jobsArray.put(obj)
        }
        root.put("jobs", jobsArray)

        return root.toString(2)
    }

    fun exportAndShareBackup(context: Context, jsonString: String) {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, jsonString)
            putExtra(Intent.EXTRA_TITLE, "SkillsExchange_Data_Backup.json")
            type = "application/json"
        }

        val shareIntent = Intent.createChooser(sendIntent, "Export Backup JSON via")
        shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(shareIntent)
    }
}
