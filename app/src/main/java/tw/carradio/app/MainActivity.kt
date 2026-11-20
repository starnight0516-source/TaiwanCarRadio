package tw.carradio.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import tw.carradio.app.ui.CarRadioScreen
import tw.carradio.app.ui.RadioViewModel

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_AUTO_PLAY_LAST = "extra_auto_play_last"
    }

    private val viewModel: RadioViewModel by viewModels()

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            val locationGranted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            if (locationGranted) {
                viewModel.startGpsWeatherUpdates(forceRefresh = true)
            }
        }

    private fun requestNeededPermissions() {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (needed.isNotEmpty()) {
            requestPermissionsLauncher.launch(needed.toTypedArray())
        } else {
            viewModel.startGpsWeatherUpdates(forceRefresh = true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestNeededPermissions()

        if (intent?.getBooleanExtra(EXTRA_AUTO_PLAY_LAST, false) == true) {
            viewModel.requestAutoPlayLast()
        }

        setContent {
            val uiState by viewModel.uiState.collectAsState()

            LaunchedEffect(uiState.keepScreenOn) {
                if (uiState.keepScreenOn) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            MaterialTheme(colorScheme = darkColorScheme()) {
                CarRadioScreen(
                    uiState = uiState,
                    onPlayStation = viewModel::playStation,
                    onTogglePlayPause = viewModel::togglePlayPause,
                    onNext = viewModel::playNext,
                    onPrevious = viewModel::playPrevious,
                    onSelectCategory = viewModel::selectCategory,
                    onSearchChange = viewModel::updateSearchQuery,
                    onToggleFavorite = viewModel::toggleFavorite,
                    onRefresh = viewModel::refreshStationsFromNetwork,
                    onRefreshWeather = {
                        if (viewModel.hasLocationPermission()) {
                            viewModel.startGpsWeatherUpdates(forceRefresh = true)
                        } else {
                            requestNeededPermissions()
                        }
                    },
                    onAddCustomStation = viewModel::addCustomStation,
                    onDeleteCustomStation = viewModel::deleteCustomStation,
                    onKeepScreenOnChange = viewModel::setKeepScreenOn,
                    onAutoResumeChange = viewModel::setAutoResumeOnBoot
                )
            }
        }
    }
}
