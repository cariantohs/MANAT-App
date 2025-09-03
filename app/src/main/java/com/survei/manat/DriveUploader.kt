package com.survei.manat

import android.content.Context
import android.util.Log
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.GoogleCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Collections

// Ganti dengan ID folder Google Drive Anda yang sebenarnya.
// Anda bisa mendapatkan ID ini dari URL folder di browser.
private const val DRIVE_FOLDER_ID = "1-k4nLOE3TGzX3wbKU2l51L2YlWEH74M8" // ID Folder dari kode Anda

/**
 * Sealed class untuk merepresentasikan hasil dari operasi unggah.
 */
sealed class UploadResult {
    data class Success(val fileId: String) : UploadResult()
    data class Error(val message: String) : UploadResult()
}

/**
 * Objek singleton untuk menangani semua operasi unggahan ke Google Drive.
 */
object DriveUploader {

    private const val TAG = "DriveUploader"

    /**
     * Mengunggah file foto ke folder spesifik di Google Drive menggunakan Akun Layanan.
     * Fungsi ini adalah suspend function dan harus dipanggil dari dalam coroutine.
     *
     * @param context Konteks aplikasi untuk mengakses sumber daya.
     * @param localFile File lokal yang akan diunggah.
     * @param targetFileName Nama file yang diinginkan di Google Drive.
     * @return UploadResult yang berisi ID file jika berhasil, atau pesan galat jika gagal.
     */
    suspend fun uploadPhoto(
        context: Context,
        localFile: java.io.File,
        targetFileName: String
    ): UploadResult {
        // Operasi jaringan harus dijalankan di background thread (IO dispatcher).
        return withContext(Dispatchers.IO) {
            try {
                // 1. Muat kredensial Akun Layanan dari res/raw/credentials.json
                // Pastikan file credentials.json ada di folder res/raw Anda.
                val inputStream = context.resources.openRawResource(R.raw.credentials)
                val credentials = GoogleCredentials.fromStream(inputStream)
                  .createScoped(Collections.singleton(DriveScopes.DRIVE_FILE))

                // 2. Bangun objek layanan Drive yang terautentikasi.
                val transport = NetHttpTransport()
                val jsonFactory = GsonFactory.getDefaultInstance()
                val driveService = Drive.Builder(
                    transport,
                    jsonFactory,
                    HttpCredentialsAdapter(credentials)
                ).setApplicationName("MANAT").build()

                // 3. Siapkan metadata file (nama dan folder induk).
                val fileMetadata = File().apply {
                    name = targetFileName
                    // Penting: setParents menerima List<String>, bukan String tunggal.
                    parents = Collections.singletonList(DRIVE_FOLDER_ID)
                }

                // 4. Siapkan konten media file.
                val mediaContent = FileContent("image/jpeg", localFile)

                // 5. Eksekusi permintaan unggah.
                val uploadedFile = driveService.files().create(fileMetadata, mediaContent)
                  .setFields("id") // Hanya minta field 'id' dalam respons untuk efisiensi.
                  .execute()

                // 6. Kembalikan hasil sukses dengan ID file.
                Log.i(TAG, "Foto berhasil diunggah. File ID: ${uploadedFile.id}")
                uploadedFile.id?.let {
                    UploadResult.Success(it)
                }?: UploadResult.Error("Unggah berhasil tetapi tidak ada ID file yang dikembalikan.")

            } catch (e: GoogleJsonResponseException) {
                // PENANGANAN GALAT KRITIS: Tangani kesalahan spesifik dari Google API.
                val errorDetails = e.details
                val errorMessage = "Gagal unggah: HTTP ${errorDetails.code}: ${errorDetails.message}"
                Log.e(TAG, errorMessage)
                Log.e(TAG, "Detail JSON Lengkap: ${e.content}") // Log ini sangat penting untuk debugging!
                UploadResult.Error(errorMessage) // Kembalikan pesan galat yang jelas.
            } catch (e: IOException) {
                // Tangani kesalahan jaringan atau I/O file.
                val errorMessage = "Kesalahan Jaringan/IO: ${e.message}"
                Log.e(TAG, errorMessage, e)
                UploadResult.Error(errorMessage)
            } catch (e: Exception) {
                // Tangani kesalahan tak terduga lainnya.
                val errorMessage = "Kesalahan tidak terduga: ${e.message}"
                Log.e(TAG, errorMessage, e)
                UploadResult.Error(errorMessage)
            }
        }
    }
}