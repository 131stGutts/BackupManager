package com.ayer.backupmanager.network

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.jcraft.jsch.SftpException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.*

class SftpClient(
    private val ip: String,
    private val port: Int,
    private val username: String,
    private val password: String,
    private val remotePath: String
) : RemoteClient {

    private var jschSession: Session? = null
    private var channelSftp: ChannelSftp? = null

    override suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        try {
            val jsch = JSch()
            jschSession = jsch.getSession(username, ip, port)
            jschSession?.setPassword(password)
            
            val config = Properties()
            config["StrictHostKeyChecking"] = "no"
            jschSession?.setConfig(config)
            
            jschSession?.connect()
            
            val channel = jschSession?.openChannel("sftp")
            channel?.connect()
            channelSftp = channel as? ChannelSftp
            
            if (remotePath.isNotEmpty()) {
                try {
                    channelSftp?.cd(remotePath)
                } catch (e: SftpException) {
                    // Base remote path doesn't exist
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override suspend fun makeDirectory(relativeRemotePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            if (remotePath.isNotEmpty()) {
                channelSftp?.cd(remotePath)
            } else {
                channelSftp?.cd("/")
            }

            val folders = relativeRemotePath.split("/").filter { it.isNotEmpty() }
            for (folder in folders) {
                try {
                    channelSftp?.cd(folder)
                } catch (e: SftpException) {
                    channelSftp?.mkdir(folder)
                    channelSftp?.cd(folder)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override suspend fun fileExists(relativeRemoteFilePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            if (this@SftpClient.remotePath.isNotEmpty()) {
                channelSftp?.cd(this@SftpClient.remotePath)
            } else {
                channelSftp?.cd("/")
            }
            
            try {
                channelSftp?.stat(relativeRemoteFilePath)
                true
            } catch (e: SftpException) {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun uploadFile(inputStream: InputStream, relativeRemoteFilePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val lastSlash = relativeRemoteFilePath.lastIndexOf('/')
            if (lastSlash != -1) {
                val dirPath = relativeRemoteFilePath.substring(0, lastSlash)
                makeDirectory(dirPath)
            }

            if (this@SftpClient.remotePath.isNotEmpty()) {
                channelSftp?.cd(this@SftpClient.remotePath)
            } else {
                channelSftp?.cd("/")
            }
            
            channelSftp?.put(inputStream, relativeRemoteFilePath)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override suspend fun disconnect(): Unit = withContext(Dispatchers.IO) {
        try {
            channelSftp?.disconnect()
            jschSession?.disconnect()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override suspend fun testConnection(): ConnectionResult = withContext(Dispatchers.IO) {
        try {
            val jsch = JSch()
            jschSession = jsch.getSession(username, ip, port)
            jschSession?.setPassword(password)
            
            val config = Properties()
            config["StrictHostKeyChecking"] = "no"
            jschSession?.setConfig(config)
            
            jschSession?.connect(5000)
            
            val channel = jschSession?.openChannel("sftp")
            channel?.connect(5000)
            val testChannel = channel as? ChannelSftp
            
            if (remotePath.isNotEmpty()) {
                try {
                    testChannel?.cd(remotePath)
                    ConnectionResult.Success
                } catch (e: Exception) {
                    ConnectionResult.Failure("Remote path not found: $remotePath")
                }
            } else {
                ConnectionResult.Success
            }
        } catch (e: Exception) {
            ConnectionResult.Failure(e.localizedMessage ?: "Unknown SFTP error")
        } finally {
            disconnect()
        }
    }
}
