package com.ayer.backupmanager.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPSClient
import java.io.InputStream

class FtpsClient(
    private val ip: String,
    private val port: Int,
    private val username: String,
    private val password: String,
    private val remotePath: String
) : RemoteClient {

    private val ftpsClient = FTPSClient("TLS", false)

    override suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        try {
            ftpsClient.connect(ip, port)
            if (ftpsClient.login(username, password)) {
                ftpsClient.enterLocalPassiveMode()
                ftpsClient.setFileType(FTP.BINARY_FILE_TYPE)
                ftpsClient.execPBSZ(0)
                ftpsClient.execPROT("P")
                if (remotePath.isNotEmpty()) {
                    ftpsClient.changeWorkingDirectory(remotePath)
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
                ftpsClient.changeWorkingDirectory("/")
                ftpsClient.changeWorkingDirectory(remotePath)
            } else {
                ftpsClient.changeWorkingDirectory("/")
            }

            val folders = relativeRemotePath.split("/").filter { it.isNotEmpty() }
            for (folder in folders) {
                if (!ftpsClient.changeWorkingDirectory(folder)) {
                    if (ftpsClient.makeDirectory(folder)) {
                        ftpsClient.changeWorkingDirectory(folder)
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
            if (this@FtpsClient.remotePath.isNotEmpty()) {
                ftpsClient.changeWorkingDirectory("/")
                ftpsClient.changeWorkingDirectory(this@FtpsClient.remotePath)
            } else {
                ftpsClient.changeWorkingDirectory("/")
            }
            
            val names = ftpsClient.listNames(relativeRemoteFilePath)
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

            if (this@FtpsClient.remotePath.isNotEmpty()) {
                ftpsClient.changeWorkingDirectory("/")
                ftpsClient.changeWorkingDirectory(this@FtpsClient.remotePath)
            } else {
                ftpsClient.changeWorkingDirectory("/")
            }
            
            ftpsClient.storeFile(relativeRemoteFilePath, inputStream)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override suspend fun disconnect(): Unit = withContext(Dispatchers.IO) {
        try {
            if (ftpsClient.isConnected) {
                ftpsClient.logout()
                ftpsClient.disconnect()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override suspend fun testConnection(): ConnectionResult = withContext(Dispatchers.IO) {
        try {
            ftpsClient.connect(ip, port)
            if (ftpsClient.login(username, password)) {
                ftpsClient.execPBSZ(0)
                ftpsClient.execPROT("P")
                if (remotePath.isNotEmpty()) {
                    if (ftpsClient.changeWorkingDirectory(remotePath)) {
                        ConnectionResult.Success
                    } else {
                        ConnectionResult.Failure("Remote path not found: $remotePath")
                    }
                } else {
                    ConnectionResult.Success
                }
            } else {
                ConnectionResult.Failure("FTPS Login failed")
            }
        } catch (e: Exception) {
            ConnectionResult.Failure(e.localizedMessage ?: "Unknown FTPS error")
        } finally {
            disconnect()
        }
    }
}
