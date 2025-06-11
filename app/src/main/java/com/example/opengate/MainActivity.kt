package com.example.opengate

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.delay
import android.os.Looper
import android.widget.Toast
import androidx.core.app.ActivityCompat
import kotlin.math.roundToInt
import android.os.Build

class MainActivity : ComponentActivity() {
    private lateinit var gateSettings: GateSettings
    private lateinit var locationUpdates: LocationUpdates
    private var locationCallback: LocationCallback? = null
    private val fusedLocationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(this)
    }

    // State to track permission
    private var _hasLocationPermission = mutableStateOf(false)
    val hasLocationPermission: State<Boolean> = _hasLocationPermission

    private val requiredPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.READ_PHONE_STATE
    )

    private val backgroundLocationPermission = Manifest.permission.ACCESS_BACKGROUND_LOCATION

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            // If we have all basic permissions, request background location
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                requestBackgroundLocation()
            } else {
                startLocationUpdates()
                startGateService()
            }
        } else {
            showPermissionRationale()
        }
    }

    private val backgroundLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startLocationUpdates()
            startGateService()
        } else {
            showPermissionRationale()
        }
    }

    private var showBackgroundLocationDialog by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        gateSettings = GateSettings(this)
        locationUpdates = LocationUpdates(this)

        // Check if we need to request permissions
        if (!hasAllPermissions()) {
            // Request permissions first
            requestPermissions()
        } else {
            // We already have all permissions, set the state and show UI
            _hasLocationPermission.value = true
            showMainUI()
        }
    }

    private fun hasAllPermissions(): Boolean {
        return requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(
                this,
                permission
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            requiredPermissions,
            PERMISSIONS_REQUEST_CODE
        )
    }

    private fun showMainUI() {
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (showBackgroundLocationDialog) {
                        AlertDialog(
                            onDismissRequest = { showBackgroundLocationDialog = false },
                            title = { Text("Background Location Required") },
                            text = { Text("This app needs background location access to automatically call the gate when you approach it, even when the app is closed or the phone is locked.") },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        showBackgroundLocationDialog = false
                                        backgroundLocationLauncher.launch(backgroundLocationPermission)
                                    }
                                ) {
                                    Text("Grant Permission")
                                }
                            },
                            dismissButton = {
                                TextButton(
                                    onClick = { showBackgroundLocationDialog = false }
                                ) {
                                    Text("Not Now")
                                }
                            }
                        )
                    }
                    MainScreen(
                        hasLocationPermission = hasLocationPermission,
                        onSetGateNumber = { number ->
                            lifecycleScope.launch {
                                gateSettings.setGateNumber(number)
                                Toast.makeText(this@MainActivity, "Gate number saved", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onSetHomeLocation = {
                            if (checkLocationPermission()) {
                                getCurrentLocation { location ->
                                    lifecycleScope.launch {
                                        gateSettings.setHomeLocation(location)
                                        Toast.makeText(this@MainActivity, "Gate location set", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } else {
                                requestPermissions()
                            }
                        },
                        onSetRadius = { radius ->
                            lifecycleScope.launch {
                                gateSettings.setRadius(radius)
                            }
                        }
                    )
                }
            }
        }
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

    private fun checkLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun getCurrentLocation(callback: (Location) -> Unit) {
        if (checkLocationPermission()) {
            try {
                fusedLocationClient.lastLocation
                    .addOnSuccessListener { location ->
                        if (location != null) {
                            callback(location)
                        }
                    }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error getting location", e)
            }
        }
    }

    private fun requestBackgroundLocation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    backgroundLocationPermission
                ) == PackageManager.PERMISSION_GRANTED -> {
                    startLocationUpdates()
                    startGateService()
                }
                shouldShowRequestPermissionRationale(backgroundLocationPermission) -> {
                    showBackgroundLocationRationale()
                }
                else -> {
                    backgroundLocationLauncher.launch(backgroundLocationPermission)
                }
            }
        }
    }

    private fun showBackgroundLocationRationale() {
        showBackgroundLocationDialog = true
    }

    override fun onDestroy() {
        super.onDestroy()
        locationCallback?.let { callback ->
            locationUpdates.stopLocationUpdates(callback)
        }
        // Stop the gate service when the activity is destroyed
        stopService(Intent(this, GateService::class.java))
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                // All permissions were granted, update state and show UI
                _hasLocationPermission.value = true
                showMainUI()
            } else {
                // Some permissions were denied, show UI with permission required message
                _hasLocationPermission.value = false
                showMainUI()
            }
        }
    }

    companion object {
        private const val PERMISSIONS_REQUEST_CODE = 1001
    }
}

@Composable
fun MainScreen(
    hasLocationPermission: State<Boolean>,
    onSetGateNumber: (String) -> Unit,
    onSetHomeLocation: () -> Unit,
    onSetRadius: (Float) -> Unit
) {
    val context = LocalContext.current
    val gateSettings = remember { GateSettings(context) }
    var gateNumber by remember { mutableStateOf("") }
    var currentRadius by remember { mutableStateOf(5f) }
    var currentLocation by remember { mutableStateOf<Location?>(null) }
    var distanceToGate by remember { mutableStateOf<Float?>(null) }
    var isEditing by remember { mutableStateOf(false) }
    var showClearConfirmation by remember { mutableStateOf(false) }
    var savedGateNumber by remember { mutableStateOf("") }
    var gateLocation by remember { mutableStateOf<Location?>(null) }
    var wasInRadius by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    // Function to check if we should call the gate
    fun checkAndCallGate(distance: Float) {
        // Only proceed if we have a gate location set
        if (gateLocation == null) return

        val isInRadius = distance <= currentRadius
        if (isInRadius && !wasInRadius) {
            // We just entered the radius, call the gate
            scope.launch {
                val number = gateSettings.getGateNumber()
                if (number.isNotEmpty()) {
                    val gateCaller = GateCaller(context, gateSettings)
                    gateCaller.callGate()
                }
            }
        }
        wasInRadius = isInRadius
    }

    // Load initial values and start location updates
    LaunchedEffect(hasLocationPermission.value) {
        savedGateNumber = gateSettings.getGateNumber()
        currentRadius = gateSettings.getRadius()
        gateLocation = gateSettings.getHomeLocation()
        
        if (hasLocationPermission.value) {
            try {
                val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                // Get last known location immediately
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    if (location != null) {
                        currentLocation = location
                        if (gateLocation != null) {
                            val distance = location.distanceTo(gateLocation!!)
                            distanceToGate = distance
                            // Don't check for gate call on initial location load
                            wasInRadius = distance <= currentRadius
                        }
                    }
                }
                
                // Then start continuous updates
                fusedLocationClient.requestLocationUpdates(
                    LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
                        .setWaitForAccurateLocation(false)
                        .setMinUpdateIntervalMillis(2000)
                        .setMaxUpdateDelayMillis(5000)
                        .build(),
                    object : LocationCallback() {
                        override fun onLocationResult(locationResult: LocationResult) {
                            locationResult.lastLocation?.let { location ->
                                currentLocation = location
                                if (gateLocation != null) {
                                    val distance = location.distanceTo(gateLocation!!)
                                    distanceToGate = distance
                                    checkAndCallGate(distance)
                                }
                            }
                        }
                    },
                    Looper.getMainLooper()
                )
            } catch (e: Exception) {
                Log.e("MainScreen", "Error starting location updates", e)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Text(
            text = "OpenGate",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary
        )

        // Current Settings Card
        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Current Settings",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Current Location
                SettingItem(
                    icon = Icons.Default.LocationOn,
                    label = "Current Location",
                    value = currentLocation?.let {
                        "Lat: ${it.latitude}, Lon: ${it.longitude}"
                    } ?: if (hasLocationPermission.value) {
                        "Waiting for location..."
                    } else {
                        "Location permission required"
                    }
                )

                // Distance to Gate
                if (hasLocationPermission.value && currentLocation != null && gateSettings.getHomeLocation() != null) {
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    SettingItem(
                        icon = Icons.Default.LocationOn,
                        label = "Distance to Gate",
                        value = distanceToGate?.let {
                            "${it.toInt()}m"
                        } ?: "Calculating..."
                    )
                }
            }
        }

        // Current Settings Card
        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Current Settings",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                SettingItem(
                    icon = Icons.Default.Phone,
                    label = "Gate Number",
                    value = savedGateNumber.ifEmpty { "Not set" }
                ) {
                    if (isEditing) {
                        OutlinedTextField(
                            value = gateNumber,
                            onValueChange = { gateNumber = it },
                            label = { Text("Enter gate number") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = {
                                    isEditing = false
                                    gateNumber = savedGateNumber
                                }
                            ) {
                                Text("Cancel")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    onSetGateNumber(gateNumber)
                                    savedGateNumber = gateNumber
                                    isEditing = false
                                }
                            ) {
                                Text("Save Gate Number")
                            }
                        }
                    } else {
                        Button(
                            onClick = {
                                isEditing = true
                                gateNumber = savedGateNumber
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit"
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Edit Gate Number")
                        }
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 8.dp))

                // Gate Location
                SettingItem(
                    icon = Icons.Default.LocationOn,
                    label = "Gate Location",
                    value = gateLocation?.let {
                        "Lat: ${it.latitude}, Lon: ${it.longitude}"
                    } ?: "Not set"
                ) {
                    Button(
                        onClick = {
                            if (hasLocationPermission.value) {
                                val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                                    if (location != null) {
                                        scope.launch {
                                            gateSettings.setHomeLocation(location)
                                            gateLocation = location
                                            if (currentLocation != null) {
                                                distanceToGate = currentLocation!!.distanceTo(location)
                                            }
                                            Toast.makeText(context, "Gate location set", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            } else {
                                onSetHomeLocation()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "Set Location"
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Set Gate Location")
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 8.dp))

                // Radius Slider
                SettingItem(
                    icon = Icons.Default.LocationOn,
                    label = "Radius",
                    value = "${currentRadius.toInt()}m",
                    content = {
                        Slider(
                            value = currentRadius,
                            onValueChange = { currentRadius = it },
                            valueRange = 5f..1000f,
                            steps = 199,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                )
            }
        }

        // Clear Settings Card
        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Clear Settings Button
                Button(
                    onClick = { showClearConfirmation = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Clear"
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Clear All Settings")
                }
            }
        }

        // Clear Confirmation Dialog
        if (showClearConfirmation) {
            AlertDialog(
                onDismissRequest = { showClearConfirmation = false },
                title = { Text("Clear All Settings?") },
                text = { Text("This will delete all saved settings including gate number, home location, and radius. This action cannot be undone.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                gateSettings.clearAllSettings()
                                // Reset all local state
                                gateNumber = ""
                                savedGateNumber = ""
                                currentRadius = 5f
                                gateLocation = null
                                distanceToGate = null
                                wasInRadius = false
                                showClearConfirmation = false
                            }
                        }
                    ) {
                        Text("Clear", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showClearConfirmation = false }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun SettingItem(
    icon: ImageVector,
    label: String,
    value: String,
    content: @Composable (() -> Unit)? = null
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (content != null) {
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}