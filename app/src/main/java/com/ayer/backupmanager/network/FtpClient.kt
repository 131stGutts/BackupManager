package com.ayer.backupmanager.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import java.io.InputStream

class FtpClient(
    private val ip: String,
    private val port: Int,
    private val username: String,
    private val password: String,
    private val remotePath: String
) : RemoteClient {

    private val ftpClient = FTPClient()

    override suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        try {
            ftpClient.connect(ip, port)
            if (ftpClient.login(username, password)) {
                ftpClient.enterLocalPassiveMode()
                ftpClient.setFileType(FTP.BINARY_FILE_TYPE)
                if (remotePath.isNotEmpty()) {
                    ftpClient.changeWorkingDirectory("/")
                    ftpClient.changeWorkingDirectory(remotePath)
                }
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override suspend fun makeDirectory(relativeRemotePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            if (remotePath.isNotEmpty()) {
                ftpClient.changeWorkingDirectory("/")
                ftpClient.changeWorkingDirectory(remotePath)
            } else {
                ftpClient.changeWorkingDirectory("/")
            }

            val folders = relativeRemotePath.split("/").filter { it.isNotEmpty() }
            for (folder in folders) {
                if (!ftpClient.changeWorkingDirectory(folder)) {
                    if (ftpClient.makeDirectory(folder)) {
                        ftpClient.changeWorkingDirectory(folder)
                    } else {
                        return@withContext false
                    }
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
            if (remotePath.isNotEmpty()) {
                ftpClient.changeWorkingDirectory("/")
                ftpClient.changeWorkingDirectory(remotePath)
            } else {
                ftpClient.changeWorkingDirectory("/")
            }
            
            // listNames returns null or empty array if file doesn't exist
            val names = ftpClient.listNames(relativeRemoteFilePath)
            !names.isNullOrEmpty()
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

            if (remotePath.isNotEmpty()) {
                ftpClient.changeWorkingDirectory("/")
                ftpClient.changeWorkingDirectory(remotePath)
            } else {
                ftpClient.changeWorkingDirectory("/")
            }
            
            ftpClient.storeFile(relativeRemoteFilePath, inputStream)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override suspend fun disconnect(): Unit = withContext(Dispatchers.IO) {
        try {
            if (ftpClient.isConnected) {
                ftpClient.logout()
                ftpClient.disconnect()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override suspend fun testConnection(): ConnectionResult = withContext(Dispatchers.IO) {
        try {
            ftpClient.connect(ip, port)
            if (ftpClient.login(username, password)) {
                if (remotePath.isNotEmpty()) {
                    if (ftpClient.changeWorkingDirectory(remotePath)) {
                        ConnectionResult.Success
                    } else {
                        ConnectionResult.Failure("Remote path not found: $remotePath")
                    }
                } else {
                    ConnectionResult.Success
                }
            } else {
                ConnectionResult.Failure("FTP Login failed")
            }
        } catch (e: Exception) {
            ConnectionResult.Failure(e.localizedMessage ?: "Unknown FTP error")
        } finally {
            disconnect()
        }
    }
}
