# Backup Manager 🛡️

**Backup Manager** is a native Android application designed to automate the backup of your local folders to remote servers. Secure your photos, documents, and messaging data (like WhatsApp) by automatically syncing them to your NAS or private server via **SMB, FTP, FTPS, or SFTP**.

![App Version](https://img.shields.io/badge/version-1.0.0-blue.svg)
![Platform](https://img.shields.io/badge/platform-Android-green.svg)
![Min SDK](https://img.shields.io/badge/minSdk-26-orange.svg)

## ✨ Key Features

- **Multi-Protocol Support**: Connect to any remote storage using SMB, FTP, FTPS (Explicit TLS), or SFTP (SSH).
- **Advanced Scheduling**: Plan your backups daily, weekly, or monthly at specific times.
- **Smart Sync (Delta)**: Only transfers new or modified files since the last successful backup.
- **Recursive Backup**: Automatically recreates your local folder structure on the remote server.
- **Folder Exclusion**: Select specific subfolders to ignore using a visual browser.
- **Real-time Progress**: Track your transfers with a visual progress bar and file counter.
- **Network Awareness**: Limit backups to Wi-Fi only and manage data roaming preferences.
- **Rule Management**: Enable/Disable rules with a simple Play/Stop toggle.
- **Import/Export**: Easily backup or transfer your sync rules via JSON files.
- **Secure**: Passwords are encrypted (Base64) in export files and masked in the UI.

## 🚀 Getting Started

### Prerequisites
- Android 8.0 (API 26) or higher.
- A remote server (NAS, PC, or VPS) with one of the supported protocols enabled.

### Installation
1. Download the latest `BackupManager.apk` from the [Releases](../../releases) section.
2. Enable "Install from unknown sources" on your Android device.
3. Open the APK and install.

## 🛠️ Technical Stack

- **UI**: Jetpack Compose (Material 3)
- **Database**: Room (with Migrations)
- **Background Tasks**: WorkManager
- **Networking**: 
  - [smbj](https://github.com/hierynomus/smbj) for SMB
  - [commons-net](https://commons.apache.org/proper/commons-net/) for FTP/FTPS
  - [JSch](http://www.jcraft.com/jsch/) for SFTP
- **JSON**: GSON

## 📝 Usage Tips (Xiaomi Users)
On MIUI/HyperOS, ensure to disable battery optimization for Backup Manager to allow background tasks to run reliably:
`Settings > Apps > Manage Apps > Backup Manager > Battery Saver > No Restrictions`.

## 📄 License
This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
