package com.example.storagevolumes

import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.system.Os.statvfs
import android.system.StructStatVfs
import android.view.Gravity
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.util.*
import kotlin.collections.HashSet
import kotlin.math.roundToLong


class MainActivity : AppCompatActivity() {
    private lateinit var mStorageManager: StorageManager
    private lateinit var mStorageVolumes: List<StorageVolume>
    private val mStorageVolumesByPath = HashMap<String, VolumeStats>()
    private val mStorageVolumePathsWeHaveAccessTo = HashSet<Uri>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mStorageManager = getSystemService(Context.STORAGE_SERVICE) as StorageManager
        mStorageVolumes = mStorageManager.storageVolumes
        for (storageVolume in mStorageVolumes) {
            val path = storageVolume.getStorageVolumePath()
            mStorageVolumesByPath[path] =
                VolumeStats(
                    mStorageVolume = storageVolume
                )
        }
        getVolumeStats()
        showVolumeStats()

        // Convenience button to release all access permissions for testing.
        releaseAccessButton.setOnClickListener {
            getAccessibleVolumes()

            val takeFlags =
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            mStorageVolumePathsWeHaveAccessTo.forEach {
                contentResolver.releasePersistableUriPermission(it, takeFlags)
            }
            val toast = Toast.makeText(
                this,
                "All volume permissions were released.",
                Toast.LENGTH_LONG
            )
            toast.setGravity(Gravity.BOTTOM, 0, releaseAccessButton.height)
            toast.show()
            getVolumeStats()
            showVolumeStats()
        }
    }

    private fun getAccessibleVolumes() {
        val persistedUriPermissions = contentResolver.persistedUriPermissions
        mStorageVolumePathsWeHaveAccessTo.clear()
        persistedUriPermissions.forEach {
            mStorageVolumePathsWeHaveAccessTo.add(it.uri)
        }
    }

    private fun getVolumeStats() {

        mStorageVolumesByPath.forEach {
            if (it.value.mStorageVolume.isPrimary) {
                val uuid = StorageManager.UUID_DEFAULT
                val storageStatsManager =
                    getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
                val totalPrimarySpace = storageStatsManager.getTotalBytes(uuid)
                val primaryUsedSpace = totalPrimarySpace - storageStatsManager.getFreeBytes(uuid)
                it.value.mTotalSpace = totalPrimarySpace
                it.value.mUsedSpace = primaryUsedSpace
            } else {
                val stats = getStatsFromPath(it.value.mStorageVolume.getStorageVolumePath())
                val blockSize = stats.f_bsize
                val totalSpace = stats.f_blocks * blockSize
                val usedSpace = totalSpace - stats.f_bavail * blockSize
                it.value.mTotalSpace = totalSpace
                it.value.mUsedSpace = usedSpace
            }
        }
    }

    private fun showVolumeStats() {
        val sb = StringBuilder()
        mStorageVolumesByPath.forEach {
            val usedShiftUnits = getShiftUnits(it.value.mUsedSpace)
            val totalShiftUnits = getShiftUnits(it.value.mTotalSpace)

            val usedSpace = (100f * it.value.mUsedSpace / usedShiftUnits.first).roundToLong() / 100f
            val totalSpace =
                (100f * it.value.mTotalSpace / totalShiftUnits.first).roundToLong() / 100f
            val desc = if (it.value.mStorageVolume.isPrimary) {
                "Primary Storage"
            } else {
                it.value.mStorageVolume.getDescription(this)
            }
            sb.appendln("$desc(${it.key})")
            sb.appendln(" Used space: ${usedSpace.nice()} ${usedShiftUnits.second}")
            sb.appendln("Total space: ${totalSpace.nice()} ${totalShiftUnits.second}")
            sb.appendln("----------------")
        }
        volumeStats.text = sb.toString()
        if (mStorageVolumePathsWeHaveAccessTo.size > 0) {
            releaseAccessButton.visibility = View.VISIBLE
        }
    }

    private fun getShiftUnits(x: Long): Pair<Float, String> {
        var usedSpaceUnits: String? = null
        val shift =
            when {
                x < KB -> {
                    usedSpaceUnits = "Bytes"
                    1f
                }
                x < MB -> {
                    usedSpaceUnits = "KB"
                    KB
                }
                x < GB -> {
                    usedSpaceUnits = "MB"
                    MB
                }
                else -> {
                    usedSpaceUnits = "GB"
                    GB
                }
            }
        return Pair(shift, usedSpaceUnits)
    }

    private fun getStatsFromPath(path: String): StructStatVfs {
        return statvfs(path)
    }

    companion object {
        fun Float.nice(fieldLength: Int = 6): String =
            java.lang.String.format(Locale.US, "%$fieldLength.2f", this)

        // StorageVolume should probably have a "getPath()" method that will do the following
        // so we don't have to resort to reflection.
        fun StorageVolume.getStorageVolumePath(): String {
            val field = StorageVolume::class.java.getDeclaredField("mPath")
            field.isAccessible = true
            val f = field.get(this) as File
            return f.toString()
        }

        // These seem to be the values that work...
        const val KB = 1_000f
        const val MB = 1_000_000f
        const val GB = 1_000_000_000f
        @Suppress("unused")
        const val TAG = "AppLog"
    }

    data class VolumeStats(
        var mTotalSpace: Long = 0,
        var mUsedSpace: Long = 0,
        val mStorageVolume: StorageVolume
    )
}