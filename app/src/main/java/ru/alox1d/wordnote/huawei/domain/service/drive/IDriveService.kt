package ru.alox1d.wordnote.huawei.domain.service.drive

import android.content.Intent
import com.huawei.cloud.base.auth.DriveCredential
import java.io.File

interface IDriveService {

    fun login(): Intent
    fun uploadFile(file: File)
    fun downloadFile(file: com.huawei.cloud.services.drive.model.File?): File?
    fun queryFiles(searchFileName: String): com.huawei.cloud.services.drive.model.File?
    fun updateFile(updatedFile: File, oldFile: com.huawei.cloud.services.drive.model.File?)
    fun init(unionID: String?, at: String?, refreshAT: DriveCredential.AccessMethod?): Int
    val refreshAT: DriveCredential.AccessMethod

}