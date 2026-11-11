package org.khylo.tv.core.integration.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import coil3.ImageLoader
import coil3.asDrawable
import coil3.request.ImageRequest
import coil3.request.error
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.khylo.tv.core.BuildConfig
import org.khylo.tv.core.R
import org.khylo.tv.core.util.AndroidVersion
import org.khylo.sdk.api.client.ApiClient
import org.khylo.tv.data.ServerAuthInterceptor
import org.koin.android.ext.android.inject
import java.io.FileNotFoundException
import java.io.IOException

/**
 * Which pictures the provider will hand to another app: the signed-in server's own pictures and Khylo's own bundled
 * ones. The provider is open to the launcher (its home-screen rows load their artwork through it), so what it will fetch
 * has to be limited, or any app could use it to make Khylo fetch any address or file.
 */
object ImageSources {
	fun isAllowed(src: String, server: HttpUrl?, ownPackage: String): Boolean {
		if (src.startsWith("android.resource://$ownPackage/")) return true
		val url = src.toHttpUrlOrNull() ?: return false
		return server != null && ServerAuthInterceptor.isSameServer(server, url)
	}
}

class ImageProvider : ContentProvider() {
	private val imageLoader by inject<ImageLoader>()
	private val api by inject<ApiClient>()

	override fun onCreate(): Boolean = true

	override fun getType(uri: Uri) = null
	override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?) = null
	override fun insert(uri: Uri, values: ContentValues?) = null
	override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
	override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0

	override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
		val wanted = uri.getQueryParameter("src")
		if (wanted == null || !ImageSources.isAllowed(wanted, api.baseUrl?.toHttpUrlOrNull(), context!!.packageName)) {
			throw FileNotFoundException("Not one of Khylo's pictures")
		}
		val src = wanted.toUri()

		val (read, write) = ParcelFileDescriptor.createPipe()
		val outputStream = ParcelFileDescriptor.AutoCloseOutputStream(write)

		imageLoader.enqueue(ImageRequest.Builder(context!!).apply {
			data(src)
			error(R.drawable.placeholder_icon)
			target(
				onSuccess = { image -> writeDrawable(image.asDrawable(context!!.resources), outputStream) },
				onError = { image -> writeDrawable(requireNotNull(image?.asDrawable(context!!.resources)), outputStream) }
			)
		}.build())

		return read
	}

	private fun writeDrawable(
		drawable: Drawable,
		outputStream: ParcelFileDescriptor.AutoCloseOutputStream
	) {
		@Suppress("DEPRECATION")
		val format = when {
			AndroidVersion.isAtLeastR -> Bitmap.CompressFormat.WEBP_LOSSY
			else -> Bitmap.CompressFormat.WEBP
		}

		try {
			outputStream.use {
				drawable.toBitmap().compress(format, COMPRESSION_QUALITY, outputStream)
			}
		} catch (_: IOException) {
			// Ignore IOException as this is commonly thrown when the load request is cancelled
		}
	}

	companion object {
		private const val COMPRESSION_QUALITY = 95

		/**
		 * Get a [Uri] that uses the [ImageProvider] to load an image. The input should be a valid
		 * Khylo image URL created using the SDK.
		 */
		fun getImageUri(src: String): Uri = Uri.Builder()
			.scheme("content")
			.authority("${BuildConfig.APPLICATION_ID}.integration.provider.ImageProvider")
			.appendQueryParameter("src", src)
			.appendQueryParameter("v", BuildConfig.VERSION_NAME)
			.build()
	}
}
