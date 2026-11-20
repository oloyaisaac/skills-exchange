package com.example.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
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

    /**
     * R09-P2-03: hand a server-built export to the share sheet as a real file.
     *
     * The subject-access export can be large and contains personal data, so it is written to this
     * app's own cache (private storage) and shared through a [FileProvider] content URI with a
     * one-shot read grant. It is never written to shared/external storage and no bearer URL is
     * minted. The file is overwritten on each export and deleted by the OS with the cache.
     *
     * Fail closed: on any write or provider error nothing is shared and the caller is told.
     */
    fun exportAndShareFile(context: Context, jsonString: String, filePrefix: String): Boolean {
        if (jsonString.isBlank()) return false
        return try {
            val directory = File(context.cacheDir, EXPORT_DIR).apply { mkdirs() }
            val safePrefix = filePrefix.filter { it.isLetterOrDigit() || it == '_' || it == '-' }
                .ifBlank { "SkillsExchange_MyData" }
            val file = File(directory, "${safePrefix}_${System.currentTimeMillis()}.json")
            file.writeText(jsonString)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TITLE, file.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(sendIntent, "Save your Skills Exchange data")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(chooser)
            true
        } catch (e: Exception) {
            android.util.Log.e("DataBackupHelper", "export share failed: ${e.javaClass.simpleName}")
            false
        }
    }

    /** Cache sub-directory for exports; declared in `res/xml/file_paths.xml`. */
    private const val EXPORT_DIR = "exports"

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
