package ir.aris.downloadimage

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Streaming
import retrofit2.http.Url
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

interface ImageDownloadRepo {
    suspend fun downloadSaveImage(
        imageUrl: String, context: Context,
    ): Flow<String>
}

interface DownloadImageApi {
    @Streaming
    @GET
    suspend fun downloadImage(@Url imageUrl: String): Response<ResponseBody>
}

@Module
@InstallIn(ViewModelComponent::class)
object AppModule {

    @Provides
    @ViewModelScoped
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                }
            )
            .build()
    }

    @Provides
    @ViewModelScoped
    fun provideDownloadImageApi(client: OkHttpClient): DownloadImageApi {
        return Retrofit.Builder()
            .baseUrl("https://fakeurl.com/")
            .client(client)
            .build()
            .create(DownloadImageApi::class.java)
    }

    @Provides
    @ViewModelScoped
    fun provideImageDownloadRepository(api: DownloadImageApi): ImageDownloadRepo {
        return ImageDownloadImpl(api)
    }


}

class ImageDownloadImpl @Inject constructor(
    private val api: DownloadImageApi,
) : ImageDownloadRepo {

    override suspend fun downloadSaveImage(
        imageUrl: String, context: Context,
    ): Flow<String> {
        return flow {
            try {
                val data = api.downloadImage(imageUrl)

                val inputStream = data.body()?.byteStream()

                if (inputStream != null) {
                    saveDownloadImage(context, inputStream)
                    emit("Download success")
                } else {
                    emit("no data")
                }
            } catch (ex: Exception) {
                Log.e("7171", ex.message.toString())
                emit(ex.message.toString())
            }
        }
    }

    private fun saveDownloadImage(context: Context, stream: InputStream) {
        val fileName = SimpleDateFormat("yyyy-MM-dd HH:mm:ss SSS", Locale.ENGLISH)
            .format(System.currentTimeMillis())

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
        }

        val resolver = context.contentResolver

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val uri = resolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                contentValues
            )
            if (uri != null) {
                try {
                    stream.use { input ->
                        resolver.openOutputStream(uri).use { output ->
                            input.copyTo(output!!, DEFAULT_BUFFER_SIZE)
                        }

                    }
                } catch (ex: Exception) {
                    Log.e("7171", ex.message.toString())
                }
            }
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val target = File(
                Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS
                ),
                "$fileName.jpeg"
            )
            try {
                FileOutputStream(target).use { output ->
                    stream.copyTo(output)
                }
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
    }
}

data class MainScreenState(
    val imageUrl: String = "",
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repo: ImageDownloadRepo,
) : ViewModel() {

    var state by mutableStateOf(MainScreenState())
        private set

    fun downloadAndSaveImage(imageUrl: String, context: Context) {
        viewModelScope.launch {
            repo.downloadSaveImage(imageUrl, context)
                .collect {
                    Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                }
        }
    }

    fun onTextFieldValueChange(value: String) {
        viewModelScope.launch {
            state = state.copy(
                imageUrl = value
            )
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel = hiltViewModel(),
) {

    val state = viewModel.state
    val context = LocalContext.current

    val permissionState =
        rememberPermissionState(permission = android.Manifest.permission.WRITE_EXTERNAL_STORAGE)

    SideEffect {
        if (permissionState.status.isGranted)
            permissionState.launchPermissionRequest()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        OutlinedTextField(value = state.imageUrl,
            onValueChange = { viewModel.onTextFieldValueChange(it) },
            label = { Text(text = "Enter Image Url") },
            maxLines = 1,
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Button(onClick = {
            viewModel.downloadAndSaveImage(state.imageUrl,context)
        }) {
            Text(text = "Download")
        }

    }

}

