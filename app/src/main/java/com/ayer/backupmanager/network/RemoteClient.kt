package com.ayer.backupmanager.network

import java.io.InputStream

interface RemoteClient {
    suspend fun connect(): Boolean
    suspend fun uploadFile(inputStream: InputStream, remoteFilePath: String): Boolean
    suspend fun disconnect()
    suspend fun testConnection(): ConnectionResult
    suspend fun makeDirectory(remotePath: String): Boolean
    suspend fun fileExists(remoteFilePath: String): Boolean
}

sealed class ConnectionResult {
    object Success : ConnectionResult()
    data class Failure(val message: String) : ConnectionResult()
}
