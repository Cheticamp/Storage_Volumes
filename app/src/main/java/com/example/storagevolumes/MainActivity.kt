package com.example.storagevolumes

import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.system.Os.statvfs
import android.system.StructStatVfs
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
    private val mVolumeStats = HashMap<String, StructStatVfs>()
    private val mVolumeStatsToUse = HashMap<String, VolumeStats>()
    private val mStorageVolumePathsWeHaveAccessTo = HashSet<Uri>()
    private lateinit var mStorageVolumePaths: Array<String>
    private lateinit var mStorageVolumes: List<StorageVolume>
    private var mTotalPrimarySpace: Long = 0
    private var mTotalPrimaryUsedSpace: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mStorageManager = getSystemService(Context.STORAGE_SERVICE) as StorageManager
        mStorageVolumes = mStorageManager.storageVolumes
        mStorageVolumePaths = getStorageVolumes()

        getVolumeAccess()
        getVolumeStats()

        showVolumeStats()

        // Convenience button to release all access permissions for testing.
        releaseAccessButton.setOnClickListener {
            val takeFlags =
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

            mStorageVolumePathsWeHaveAccessTo.forEach {
                contentResolver.releasePersistableUriPermission(it, takeFlags)
            }
            val toast = Toast.makeText(
                this,
                "Primary volume permission released was released.",
                Toast.LENGTH_SHORT
            )
            toast.setGravity(Gravity.BOTTOM, 0, releaseAccessButton.height)
            toast.show()
            getVolumeStats()
            showVolumeStats()
        }
    }

    private fun getStorageVolumes(): Array<String> {
        var volumes = arrayOf<String>()
        try {
            volumes = mStorageManager.javaClass
                .getMethod("getVolumePaths", *arrayOfNulls(0))
                .invoke(mStorageManager, *arrayOfNulls(0)) as Array<String>
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return volumes
    }

    private fun getVolumeAccess() {
        val persistedUriPermissions = contentResolver.persistedUriPermissions
        mStorageVolumePathsWeHaveAccessTo.clear()
        persistedUriPermissions.forEach {
            mStorageVolumePathsWeHaveAccessTo.add(it.uri)
        }
    }

    private fun getVolumeStats() {
        mVolumeStats.clear()
        for (storageVolume in mStorageVolumePaths) {
            mVolumeStats[storageVolume] = getStatsFromPath(storageVolume)
        }
        val storageStatsManager =
            getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
        val uuid = StorageManager.UUID_DEFAULT
        mTotalPrimarySpace = storageStatsManager.getTotalBytes(uuid)
        mTotalPrimaryUsedSpace = mTotalPrimarySpace - storageStatsManager.getFreeBytes(uuid)

        val externalDirectory = Environment.getExternalStorageDirectory()
        mVolumeStats.forEach {
            if (it.key == externalDirectory.path) {
                mVolumeStatsToUse[it.key] = VolumeStats(mTotalPrimarySpace, mTotalPrimaryUsedSpace)
            } else {
                val blockSize = it.value.f_bsize
                val totalSpace = it.value.f_blocks * blockSize
                mVolumeStatsToUse[it.key] =
                    VolumeStats(totalSpace, totalSpace - it.value.f_bfree * blockSize)
            }
        }
    }

    private fun showVolumeStats() {
        val sb = StringBuilder()
        if (mVolumeStats.size == 0) {
            sb.appendln("Nothing to see here...")
        }
        mVolumeStatsToUse.forEach {
            val usedShiftUnits = getShiftUnits(it.value.mUsedSpace)
            val totalShiftUnits = getShiftUnits(it.value.mTotalSpace)

            val usedSpace = (100f * it.value.mUsedSpace / usedShiftUnits.first).roundToLong() / 100f
            val totalSpace = (100f * it.value.mTotalSpace / totalShiftUnits.first).roundToLong() / 100f
            sb.appendln(it.key)
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

        // These seem to be the values that work...
        const val KB = 1_000f
        const val MB = 1_000_000f
        const val GB = 1_000_000_000f
        @Suppress("unused")
        const val TAG = "AppLog"
    }

    data class VolumeStats(
        val mTotalSpace: Long = 0,
        val mUsedSpace: Long = 0
    )
}