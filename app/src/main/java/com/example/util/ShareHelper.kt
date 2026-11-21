package com.example.util

import android.content.Context
import android.content.Intent

object ShareHelper {

    fun shareJob(context: Context, jobTitle: String, district: String, budget: Double, description: String) {
        val shareText = """
            🛠️ Neighborhood Job Opportunity on Skills Exchange:
            📌 Title: $jobTitle
            📍 Location: $district District
            💰 Budget / Reward: $$budget
            📝 Details: $description
            
            📲 Open in Skills Exchange or reply with your verified trade ID to bid.
        """.trimIndent()

        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, shareText)
            putExtra(Intent.EXTRA_TITLE, "Job: $jobTitle")
            type = "text/plain"
        }

        val shareIntent = Intent.createChooser(sendIntent, "Share Job via")
        shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(shareIntent)
    }

    fun shareProviderProfile(context: Context, name: String, trade: String, rating: Double, endorsements: Int, district: String) {
        val shareText = """
            ⭐ Recommended Verified Craftsman on Skills Exchange:
            👤 Name: $name
            🔧 Trade / Skill: $trade
            🌟 Rating: $rating/5.0 ($endorsements Endorsements)
            📍 District: $district
            
            🛡️ Verified with National ID & Secure Mobile Money Escrow.
        """.trimIndent()

        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, shareText)
            putExtra(Intent.EXTRA_TITLE, "Recommend $name ($trade)")
            type = "text/plain"
        }

        val shareIntent = Intent.createChooser(sendIntent, "Recommend Craftsman via")
        shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(shareIntent)
    }

    fun shareApp(context: Context) {
        val shareText = """
            🏘️ Join Skills Exchange - The hyperlocal peer-to-peer trade network and services marketplace with Mobile Money escrow protection and verified local craftsmen.
            
            Download & connect with your neighbors today!
        """.trimIndent()

        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, shareText)
            putExtra(Intent.EXTRA_TITLE, "Invite to Skills Exchange")
            type = "text/plain"
        }

        val shareIntent = Intent.createChooser(sendIntent, "Share Skills Exchange via")
        shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(shareIntent)
    }
}
