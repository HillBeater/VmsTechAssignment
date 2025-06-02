package com.hillbeater.vmstechsassignment

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hillbeater.vmstechsassignment.geofence.GeofenceHelper
import com.hillbeater.vmstechsassignment.service.LocationForegroundService
import com.hillbeater.vmstechsassignment.viewModel.LocationViewModel
import com.hillbeater.vmstechsassignment.work.scheduleLocationWork
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var geofenceHelper: GeofenceHelper
    private val geofenceId = "MY_HOME_GEOFENCE"

    private val prefsName = "geofence_prefs"
    private lateinit var prefs: SharedPreferences

    private var isGeofenceActive by mutableStateOf(false)

    private var hasBackgroundPermission by mutableStateOf(false)

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                Toast.makeText(this, "Notification permission granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Notification permission denied", Toast.LENGTH_SHORT).show()
            }
            requestForegroundLocationPermissions()
        }

    private val foregroundPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
            val coarseLocationGranted =
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

            if (fineLocationGranted || coarseLocationGranted) {
                requestBackgroundLocationPermission()
            } else {
                Toast.makeText(
                    this,
                    "Foreground location permission is required",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    private val backgroundPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                hasBackgroundPermission = true
            } else {
                hasBackgroundPermission = false
                Toast.makeText(this, "Background location permission denied", Toast.LENGTH_SHORT)
                    .show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        geofenceHelper = GeofenceHelper(this)
        prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        isGeofenceActive = prefs.getBoolean("geofence_active", false)
        hasBackgroundPermission = checkBackgroundLocationPermission(this)

        checkAndRequestNotificationPermission()

        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                lifecycleScope.launch {
                    delay(500)
                    val permissionNow = checkBackgroundLocationPermission(this@MainActivity)
                    if (hasBackgroundPermission != permissionNow) {
                        hasBackgroundPermission = permissionNow
                        if (!hasBackgroundPermission) {
                            val serviceIntent = Intent(this@MainActivity, LocationForegroundService::class.java)
                            stopService(serviceIntent)
                        }
                    }
                }
            }
        })

        setContent {
            MaterialTheme {
                if (hasBackgroundPermission) {
                    createNotificationChannel() // for geofence
                    createLocationChannel(this) // for foreground service
                    scheduleLocationWork(this)

                    val viewModel: LocationViewModel = viewModel()
                    val location by viewModel.locationLiveData.observeAsState()

                    val serviceIntent = Intent(this, LocationForegroundService::class.java)
                    ContextCompat.startForegroundService(this, serviceIntent)

                    LocationScreen(
                        location = location,
                        isGeofenceActive = isGeofenceActive,
                        onAddGeofence = {
                            //My Home Lat and Lon
                            geofenceHelper.addGeofence(32.2076968, 76.338846, 100f, geofenceId)
                            isGeofenceActive = true
                            prefs.edit().putBoolean("geofence_active", true).apply()
                        },
                        onRemoveGeofence = {
                            geofenceHelper.removeGeofence(geofenceId)
                            isGeofenceActive = false
                            prefs.edit().putBoolean("geofence_active", false).apply()
                        }
                    )
                } else {

                    NoBackgroundPermissionScreen(onOpenSettings = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        intent.data = Uri.fromParts("package", packageName, null)
                        startActivity(intent)
                    })
                }
            }
        }
    }

    private fun createLocationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                "location_channel",
                "Location Tracking",
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for location tracking in the background"
            }
            val notificationManager = context.getSystemService(android.app.NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                "geofence_channel",
                "Geofence Events",
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for Geofence transitions"
            }
            val notificationManager = getSystemService(android.app.NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notificationManager = androidx.core.app.NotificationManagerCompat.from(this)
            val areNotificationsEnabled = notificationManager.areNotificationsEnabled()
            if (!areNotificationsEnabled) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                requestForegroundLocationPermissions()
            }
        } else {
            requestForegroundLocationPermissions()
        }
    }

    private fun requestForegroundLocationPermissions() {
        foregroundPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun requestBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            hasBackgroundPermission = true
            scheduleLocationWork(this)
        }
    }

    @Composable
    fun LocationScreen(
        location: Location?,
        isGeofenceActive: Boolean,
        onAddGeofence: () -> Unit,
        onRemoveGeofence: () -> Unit
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
                .padding(16.dp)
        ) {
            Text(
                text = "Location Tracking",
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 24.dp)
            )

            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Your Current Location",
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            if (location == null) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Waiting for location update...")
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Place,
                                    contentDescription = "Location Icon",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Latitude: ${location.latitude}", style = MaterialTheme.typography.bodyMedium)
                                Text("Longitude: ${location.longitude}", style = MaterialTheme.typography.bodyMedium)
                                Text("Altitude: ${location.altitude}", style = MaterialTheme.typography.bodyMedium)
                                Text("Accuracy: ${location.accuracy} m", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = onAddGeofence,
                    enabled = !isGeofenceActive,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.addlocation),
                        contentDescription = "Remove Geofence",
                        modifier = Modifier
                            .size(24.dp)
                            .padding(end = 8.dp)
                    )
                    Text("Trigger Home Event")
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onRemoveGeofence,
                    enabled = isGeofenceActive,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.removecircle),
                        contentDescription = "Remove Geofence",
                        modifier = Modifier
                            .size(24.dp)
                            .padding(end = 8.dp)
                    )
                    Text("Stop Home Event")
                }
            }
        }
    }


    @Composable
    fun NoBackgroundPermissionScreen(onOpenSettings: () -> Unit) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.locationoff),
                        contentDescription = "Location Off Icon",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(60.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Background Location Needed",
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Text(
                        text = "Please grant background location permission (Allow all the time) for the app to work properly.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )

                    Button(
                        onClick = onOpenSettings,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings Icon",
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text("Open Settings")
                    }
                }
            }
        }
    }


    fun checkBackgroundLocationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}
