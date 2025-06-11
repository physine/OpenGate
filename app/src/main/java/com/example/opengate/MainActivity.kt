package com.example.opengate

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MainActivity : ComponentActivity() {
    private lateinit var gateSettings: GateSettings
    private lateinit var locationUpdates: LocationUpdates
    private var locationCallback: LocationCallback? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val requiredPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.READ_PHONE_STATE
    )

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            startLocationUpdates()
            startGateService()
        } else {
            showPermissionRationale()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        gateSettings = GateSettings(this)
        locationUpdates = LocationUpdates(this)

        setContent {
            val snackbarHostState = remember { SnackbarHostState() }
            val scope = rememberCoroutineScope()
            var showSettingsSnackbar by remember { mutableStateOf(false) }

            MaterialTheme {
                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) }
                ) { innerPadding ->
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        MainScreen(
                            onSetGateNumber = { number -> 
                                lifecycleScope.launch {
                                    gateSettings.setGateNumber(number)
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Gate number saved")
                                    }
                                }
                            },
                            onSetHomeLocation = { 
                                if (ContextCompat.checkSelfPermission(
                                        this,
                                        Manifest.permission.ACCESS_FINE_LOCATION
                                    ) == PackageManager.PERMISSION_GRANTED
                                ) {
                                    lifecycleScope.launch {
                                        try {
                                            val location = fusedLocationClient.lastLocation.await()
                                            if (location != null) {
                                                gateSettings.setHomeLocation(location)
                                                scope.launch {
                                                    snackbarHostState.showSnackbar("Home location set successfully")
                                                }
                                            } else {
                                                scope.launch {
                                                    snackbarHostState.showSnackbar("Could not get current location")
                                                }
                                            }
                                        } catch (e: Exception) {
                                            scope.launch {
                                                snackbarHostState.showSnackbar("Error getting location: ${e.message}")
                                            }
                                        }
                                    }
                                } else {
                                    showSettingsSnackbar = true
                                }
                            },
                            onSetRadius = { radius -> 
                                lifecycleScope.launch {
                                    gateSettings.setRadius(radius)
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Radius set to ${radius.toInt()} meters")
                                    }
                                }
                            }
                        )

                        if (showSettingsSnackbar) {
                            LaunchedEffect(Unit) {
                                val result = snackbarHostState.showSnackbar(
                                    message = "Location permission required",
                                    actionLabel = "Settings",
                                    duration = SnackbarDuration.Indefinite
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.fromParts("package", packageName, null)
                                    })
                                }
                                showSettingsSnackbar = false
                            }
                        }
                    }
                }
            }
        }

        checkPermissions()
    }

    private fun checkPermissions() {
        if (requiredPermissions.all {
                ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
            }) {
            startLocationUpdates()
            startGateService()
        } else {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    private fun startGateService() {
        val serviceIntent = Intent(this, GateService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun startLocationUpdates() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    // Send location update to the service
                    val intent = Intent(this@MainActivity, GateService::class.java).apply {
                        action = "LOCATION_UPDATE"
                        putExtra("location", location)
                    }
                    startService(intent)
                }
            }
        }.also { callback ->
            lifecycleScope.launch {
                locationUpdates.startLocationUpdates(callback)
            }
        }
    }

    private fun showPermissionRationale() {
        // This is now handled in the Compose UI
    }

    override fun onDestroy() {
        super.onDestroy()
        locationCallback?.let { callback ->
            locationUpdates.stopLocationUpdates(callback)
        }
        // Stop the gate service when the activity is destroyed
        stopService(Intent(this, GateService::class.java))
    }
}

@Composable
fun MainScreen(
    onSetGateNumber: (String) -> Unit,
    onSetHomeLocation: () -> Unit,
    onSetRadius: (Float) -> Unit
) {
    var gateNumber by remember { mutableStateOf("") }
    var radius by remember { mutableStateOf(100f) }
    val context = LocalContext.current
    val gateSettings = remember { GateSettings(context) }
    
    // Collect current settings
    val currentGateNumber by gateSettings.gateNumber.collectAsState(initial = "")
    val currentHomeLocation by gateSettings.homeLocation.collectAsState(initial = null)
    val currentRadius by gateSettings.radius.collectAsState(initial = 100f)

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Header
        Text(
            text = "OpenGate",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary
        )

        // Current Settings Card
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Current Settings",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Divider()

                // Gate Number
                SettingItem(
                    icon = Icons.Default.Phone,
                    label = "Gate Number",
                    value = currentGateNumber.ifEmpty { "Not set" }
                )

                // Home Location
                SettingItem(
                    icon = Icons.Default.LocationOn,
                    label = "Home Location",
                    value = currentHomeLocation?.let { 
                        "Lat: ${String.format("%.6f", it.latitude)}\nLon: ${String.format("%.6f", it.longitude)}"
                    } ?: "Not set"
                )

                // Radius
                SettingItem(
                    icon = Icons.Default.LocationOn,
                    label = "Radius",
                    value = "${currentRadius.toInt()} meters"
                )
            }
        }

        // Update Settings Card
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Update Settings",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Divider()

                // Gate Number Input
                OutlinedTextField(
                    value = gateNumber,
                    onValueChange = { gateNumber = it },
                    label = { Text("Gate Phone Number") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = "Phone"
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                )

                Button(
                    onClick = {
                        onSetGateNumber(gateNumber)
                        onSetRadius(currentRadius)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Save"
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save Settings")
                }

                // Home Location Button
                OutlinedButton(
                    onClick = onSetHomeLocation,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "Location"
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Set Current Location as Home")
                }

                // Radius Slider
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Gate Radius",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "${radius.toInt()} meters",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = radius,
                        onValueChange = { radius = it },
                        valueRange = 10f..1000f,
                        steps = 99,
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }

                Button(
                    onClick = { onSetRadius(radius) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Save"
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save Radius")
                }
            }
        }
    }
}

@Composable
private fun SettingItem(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}