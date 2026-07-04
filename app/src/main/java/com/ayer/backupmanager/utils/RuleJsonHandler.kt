package com.ayer.backupmanager.utils

import android.util.Base64
import android.util.Log
import com.ayer.backupmanager.data.SyncRule
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import java.io.InputStream
import java.io.OutputStream

object RuleJsonHandler {
    private val gson = Gson()

    fun exportRules(rules: List<SyncRule>, outputStream: OutputStream) {
        try {
            // Encode passwords to Base64 before export
            val encodedRules = rules.map { rule ->
                rule.copy(password = Base64.encodeToString(rule.password.toByteArray(), Base64.NO_WRAP))
            }
            val json = gson.toJson(encodedRules)
            outputStream.write(json.toByteArray())
        } catch (e: Exception) {
            Log.e("BackupManager", "Export failed", e)
        } finally {
            outputStream.close()
        }
    }

    fun importRules(inputStream: InputStream): List<SyncRule> {
        return try {
            val json = inputStream.bufferedReader().use { it.readText() }
            Log.d("BackupManager", "Importing JSON: $json")
            
            val type = object : TypeToken<List<SyncRule>>() {}.type
            val importedRules: List<SyncRule> = gson.fromJson(json, type)
            
            importedRules.map { rule ->
                // Decode password if it looks like Base64, otherwise keep as is
                val decodedPassword = try {
                    val decodedBytes = Base64.decode(rule.password, Base64.DEFAULT)
                    String(decodedBytes)
                } catch (e: Exception) {
                    rule.password
                }
                
                // Ensure default values for fields that might be missing in older JSON versions
                rule.copy(
                    id = 0, // Reset ID for import
                    password = decodedPassword,
                    excludedFolders = rule.excludedFolders ?: "" // Fix for missing field in JSON
                )
            }
        } catch (e: JsonSyntaxException) {
            Log.e("BackupManager", "JSON Syntax Error during import", e)
            emptyList()
        } catch (e: Exception) {
            Log.e("BackupManager", "General Error during import", e)
            emptyList()
        }
    }
}
