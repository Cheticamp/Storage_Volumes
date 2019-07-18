package com.example.storagevolumes

// https://stackoverflow.com/questions/56663624/how-to-get-free-and-total-size-of-each-storagevolume

import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.system.Os.statvfs
import android.view.Gravity
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*
import java.util.*
import kotlin.collections.HashSet
import kotlin.math.roundToLong

class MainActivity : AppCompatActivity() {
    private lateinit var mStorageManager: StorageManager
    private lateinit var mStorageVolumes: List<StorageVolume>
    private val mStorageVolumesByPath = HashMap<String, VolumeStats>()
    private val mStorageVolumeWeHaveAccessTo = HashSet<Uri>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mStorageManager = getSystemService(Context.STORAGE_SERVICE) as StorageManager

        getAccessibleVolumes()
        getVolumeStats()
        showVolumeStats()

        // Convenience button to release all access permissions for testing.
        releaseAccessButton.setOnClickListener {

            val takeFlags =
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            mStorageVolumeWeHaveAccessTo.forEach { uri ->
                contentResolver.releasePersistableUriPermission(uri, takeFlags)
            }
            val toast = Toast.makeText(
                this,
                "All volume permissions were released.",
                Toast.LENGTH_LONG
            )
            toast.setGravity(Gravity.BOTTOM, 0, releaseAccessButton.height)
            toast.show()

            getAccessibleVolumes()
            getVolumeStats()
            showVolumeStats()
        }

        swipeToRefresh.setOnRefreshListener {
            getAccessibleVolumes()
            getVolumeStats()
            showVolumeStats()
            swipeToRefresh.isRefreshing = false
        }
    }

    private fun getAccessibleVolumes() {
        val persistedUriPermissions = contentResolver.persistedUriPermissions
        mStorageVolumeWeHaveAccessTo.clear()
        persistedUriPermissions.forEach {
            mStorageVolumeWeHaveAccessTo.add(it.uri)
        }
    }

    private fun getVolumeStats() {
        // Get our volumes
        mStorageVolumes = mStorageManager.storageVolumes
        mStorageVolumesByPath.clear()

        var totalSpace: Long
        var usedSpace: Long
        mStorageVolumes.forEach { storageVolume ->
            val path: String = storageVolume.getStorageVolumePath()
            if (storageVolume.isPrimary) {
                // Special processing for primary volume. "Total" should equal size advertised
                // on retail packaging and we get that from StorageStatsManager
                val uuid = StorageManager.UUID_DEFAULT
                val storageStatsManager =
                    getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
                totalSpace = storageStatsManager.getTotalBytes(uuid)
                usedSpace = totalSpace - storageStatsManager.getFreeBytes(uuid)
            } else {
                // StorageStatsManager doesn't work for volumes other than the primary volume since
                // the "UUID" available for non-primary volumes is not acceptable to
                // StorageStatsManager. We must revert to statvfs(path) for non-primary volumes.
                val stats = statvfs(storageVolume.getStorageVolumePath())
                val blockSize = stats.f_bsize
                totalSpace = stats.f_blocks * blockSize
                usedSpace = totalSpace - stats.f_bavail * blockSize
            }
            mStorageVolumesByPath[path] = VolumeStats(storageVolume, totalSpace, usedSpace)
        }
    }

    private fun showVolumeStats() {
        val sb = StringBuilder()
        mStorageVolumesByPath.forEach { (path, volumeStats) ->
            val (usedToShift, usedSizeUnits) = getShiftUnits(volumeStats.mUsedSpace)
            val usedSpace = (100f * volumeStats.mUsedSpace / usedToShift).roundToLong() / 100f
            val (totalToShift, totalSizeUnits) = getShiftUnits(volumeStats.mTotalSpace)
            val totalSpace = (100f * volumeStats.mTotalSpace / totalToShift).roundToLong() / 100f
            val volumeDescription =
                if (volumeStats.mStorageVolume.isPrimary) {
                    PRIMARY_STORAGE_LABEL
                } else {
                    volumeStats.mStorageVolume.getDescription(this)
                }
            sb.appendln("$volumeDescription ($path)")
                .appendln(" Used space: ${usedSpace.nice()} $usedSizeUnits")
                .appendln("Total space: ${totalSpace.nice()} $totalSizeUnits")
                .appendln("----------------")
        }
        volumeStats.text = sb.toString()

        releaseAccessButton.visibility =
            if (mStorageVolumeWeHaveAccessTo.size > 0) {
                View.VISIBLE
            } else {
                View.INVISIBLE
            }
    }

    private fun getShiftUnits(x: Long): Pair<Float, String> {
        val usedSpaceUnits: String
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

    companion object {
        fun Float.nice(fieldLength: Int = 6): String =
            String.format(Locale.US, "%$fieldLength.2f", this)

        // StorageVolume should have an accessible "getPath()" method that will do
        // the following so we don't have to resort to reflection.
        fun StorageVolume.getStorageVolumePath(): String {
            return try {
                javaClass
                    .getMethod("getPath")
                    .invoke(this) as String
            } catch (e: Exception) {
                e.printStackTrace()
                ""
            }
        }

        // These seem to be the values that work...
        const val KB = 1_000f
        const val MB = 1_000_000f
        const val GB = 1_000_000_000f

        const val PRIMARY_STORAGE_LABEL = "Internal Storage"

        @Suppress("unused")
        const val TAG = "AppLog"
    }

    data class VolumeStats(
        val mStorageVolume: StorageVolume,
        var mTotalSpace: Long = 0,
        var mUsedSpace: Long = 0
    )
}