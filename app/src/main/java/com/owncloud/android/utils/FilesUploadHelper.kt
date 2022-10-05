/*
 *
 * Nextcloud Android client application
 *
 * @author Tobias Kaminsky
 * Copyright (C) 2022 Tobias Kaminsky
 * Copyright (C) 2022 Nextcloud GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.owncloud.android.utils

import com.nextcloud.client.account.User
import com.nextcloud.client.jobs.BackgroundJobManager
import com.owncloud.android.MainApp
import com.owncloud.android.datamodel.OCFile
import com.owncloud.android.datamodel.UploadsStorageManager
import com.owncloud.android.db.OCUpload
import com.owncloud.android.files.services.NameCollisionPolicy
import com.owncloud.android.lib.common.utils.Log_OC
import javax.inject.Inject

class FilesUploadHelper {
    @Inject
    lateinit var backgroundJobManager: BackgroundJobManager

    @Inject
    lateinit var uploadsStorageManager: UploadsStorageManager

    init {
        MainApp.getAppComponent().inject(this)
    }

    fun uploadNewFiles(
        user: User,
        localPaths: Array<String>,
        remotePaths: Array<String>,
        mimeTypes: Array<String>?,
        behaviour: Int,
        createRemoteFolder: Boolean,
        createdBy: Int,
        requiresWifi: Boolean,
        requiresCharging: Boolean,
        nameCollisionPolicy: NameCollisionPolicy
    ) {
        for (i in localPaths.indices) {
            OCUpload(localPaths[i], remotePaths[i], user.accountName).apply {
                this.nameCollisionPolicy = nameCollisionPolicy
                isUseWifiOnly = requiresWifi
                isWhileChargingOnly = requiresCharging
                uploadStatus = UploadsStorageManager.UploadStatus.UPLOAD_IN_PROGRESS
                this.createdBy = createdBy
                isCreateRemoteFolder = createRemoteFolder

                uploadsStorageManager.storeUpload(this)
                backgroundJobManager.startFilesUploadJob(user)
            }
        }
    }

    fun uploadUpdatedFile(
        user: User,
        existingFiles: Array<OCFile>,
        behaviour: Int,
        nameCollisionPolicy: NameCollisionPolicy,
        disableRetries: Boolean
    ) {
        Log_OC.d(this, "upload updated file")

        for (file in existingFiles) {
            OCUpload(file, user).apply {
                fileSize = file.fileLength
                this.nameCollisionPolicy = nameCollisionPolicy
                isCreateRemoteFolder = true
                this.localAction = behaviour
                isUseWifiOnly = false
                isWhileChargingOnly = false
                uploadStatus = UploadsStorageManager.UploadStatus.UPLOAD_IN_PROGRESS

                uploadsStorageManager.storeUpload(this)
                backgroundJobManager.startFilesUploadJob(user)
            }
        }
    }

    fun retryUpload(upload: OCUpload, user: User) {
        Log_OC.d(this, "retry upload")

        upload.uploadStatus = UploadsStorageManager.UploadStatus.UPLOAD_IN_PROGRESS
        uploadsStorageManager.updateUpload(upload)

        backgroundJobManager.startFilesUploadJob(user)
    }
}
