package com.roxgps.update

import android.annotation.SuppressLint
import android.content.Context
import android.os.Parcelable
import com.roxgps.BuildConfig
import com.roxgps.utils.PrefManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import java.io.File
import javax.inject.Inject

//import kotlinx.parcelize

class UpdateChecker @Inject constructor(
    private val apiResponse : GitHubService,
    private val prefManager: PrefManager // <<< INJEKSI PrefManager DI SINI
) {


    fun getLatestRelease() = callbackFlow {
        withContext(Dispatchers.IO){
            getReleaseList()?.let { gitHubReleaseResponse ->
                val currentTag = gitHubReleaseResponse.tagName

                val isUpdateCheckDisabled = prefManager.isUpdateDisabled.value // <<< Ambil nilai dari StateFlow PrefManager
                if (currentTag != null && (currentTag != "v" + BuildConfig.TAG_NAME && isUpdateCheckDisabled)) {
                    //New update available!
                    val asset =
                        gitHubReleaseResponse.assets?.firstOrNull { it.name?.endsWith(".apk") == true }
                    val releaseUrl =
                        asset?.browserDownloadUrl?.replace("/download/", "/tag/")?.apply {
                            substring(0, lastIndexOf("/"))
                        }
                    val name = gitHubReleaseResponse.name ?: run {
                        this@callbackFlow.trySend(null).isSuccess
                        return@let
                    }
                    val body = gitHubReleaseResponse.body ?: run {
                        this@callbackFlow.trySend(null).isSuccess
                        return@let
                    }
                    val publishedAt = gitHubReleaseResponse.publishedAt ?: run {
                        this@callbackFlow.trySend(null).isSuccess
                        return@let
                    }
                    this@callbackFlow.trySend(
                        Update(
                            name,
                            body,
                            publishedAt,
                            asset?.browserDownloadUrl
                                ?: "https://github.com/codenrox/roxgps/releases",
                            asset?.name ?: "app-full-arm64-v8a-release.apk",
                            releaseUrl ?: "https://github.com/codenrox/roxgps/releases"
                        )
                    ).isSuccess
                }
            } ?: run {
                this@callbackFlow.trySend(null).isSuccess
            }
        }
        awaitClose {  }
    }


    private fun getReleaseList(): GitHubRelease? {

        runCatching {
            apiResponse.getReleases().execute().body()
        }.onSuccess {
            return it
        }.onFailure {
            return null
        }
        return null
    }

    @SuppressLint("ObsoleteSdkInt")
    fun clearCachedDownloads(context: Context) {
        context.externalCacheDir?.let { File(it, "updates").deleteRecursively() }
    }

    @Parcelize
    data class Update(
        val name: String,      // <<< Properti ini ada
        val changelog: String, // <<< Properti ini ada
        val timestamp: String, // <<< Properti ini ada
        val assetUrl: String,  // <<< Properti ini ada
        val assetName: String, // <<< Properti ini ada
        val releaseUrl: String // <<< Properti ini ada
    ): Parcelable
}

