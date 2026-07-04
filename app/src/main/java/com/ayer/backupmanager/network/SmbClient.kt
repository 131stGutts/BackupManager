package com.ayer.backupmanager.network

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.*

class SmbClient(
    private val ip: String,
    private val port: Int,
    private val username: String,
    private val password: String,
    private val remotePath: String
) : RemoteClient {

    private var client: SMBClient? = null
    private var connection: Connection? = null
    private var session: Session? = null
    private var share: DiskShare? = null

    override suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        try {
            client = SMBClient()
            connection = client?.connect(ip, port)
            val authContext = AuthenticationContext(username, password.toCharArray(), "")
            session = connection?.authenticate(authContext)

            val parts = remotePath.split("/", limit = 2)
            val shareName = parts[0]
            share = session?.connectShare(shareName) as? DiskShare
            share != null
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override suspend fun makeDirectory(relativeRemotePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val parts = remotePath.split("/", limit = 2)
            val baseSubPath = if (parts.size > 1) parts[1] else ""
            val fullPath = if (baseSubPath.isEmpty()) relativeRemotePath else "$baseSubPath/$relativeRemotePath"
            
            val folders = fullPath.split("/").filter { it.isNotEmpty() }
            var currentPath = ""
            for (folder in folders) {
                currentPath = if (currentPath.isEmpty()) folder else "$currentPath/$folder"
                if (share?.folderExists(currentPath) == false) {
                    share?.mkdir(currentPath)
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
            val parts = remotePath.split("/", limit = 2)
            val baseSubPath = if (parts.size > 1) parts[1] else ""
            val fullRemotePath = if (baseSubPath.isEmpty()) relativeRemoteFilePath else "$baseSubPath/$relativeRemoteFilePath"
            share?.fileExists(fullRemotePath) ?: false
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun uploadFile(inputStream: InputStream, relativeRemoteFilePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val parts = remotePath.split("/", limit = 2)
            val baseSubPath = if (parts.size > 1) parts[1] else ""
            val fullRemotePath = if (baseSubPath.isEmpty()) relativeRemoteFilePath else "$baseSubPath/$relativeRemoteFilePath"

            val file = share?.openFile(
                fullRemotePath,
                EnumSet.of(AccessMask.GENERIC_WRITE),
                null,
                SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_OVERWRITE_IF,
                null
            )

            file?.use { f ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var offset = 0L
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    f.write(buffer, offset, 0, bytesRead)
                    offset += bytesRead
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override suspend fun disconnect(): Unit = withContext(Dispatchers.IO) {
        try {
            share?.close()
            session?.close()
            connection?.close()
            client?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override suspend fun testConnection(): ConnectionResult = withContext(Dispatchers.IO) {
        try {
            client = SMBClient()
            connection = client?.connect(ip, port)
            val authContext = AuthenticationContext(username, password.toCharArray(), "")
            session = connection?.authenticate(authContext)
            
            val parts = remotePath.split("/", limit = 2)
            val shareName = parts[0]
            share = session?.connectShare(shareName) as? DiskShare
            
            if (share == null) {
                ConnectionResult.Failure("Cannot connect to share: $shareName")
            } else {
                if (parts.size > 1 && parts[1].isNotEmpty()) {
                    if (share?.folderExists(parts[1]) == true) {
                        ConnectionResult.Success
                    } else {
                        ConnectionResult.Failure("Folder does not exist: ${parts[1]}")
                    }
                } else {
                    ConnectionResult.Success
                }
            }
        } catch (e: Exception) {
            ConnectionResult.Failure(e.localizedMessage ?: "Unknown SMB error")
        } finally {
            disconnect()
        }
    }
}
