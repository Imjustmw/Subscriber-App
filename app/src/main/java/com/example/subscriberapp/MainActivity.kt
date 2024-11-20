package com.example.subscriberapp

import android.app.DatePickerDialog
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.DatePicker
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.subscriberapp.Adapters.PublisherAdapter
import com.example.subscriberapp.Models.LocationData
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.PolylineOptions
import com.hivemq.client.mqtt.MqttGlobalPublishFilter
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client
import org.json.JSONException
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

class MainActivity : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var mMap: GoogleMap
    private lateinit var mqttClient: Mqtt5BlockingClient
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var rvStudentData: RecyclerView
    private lateinit var adapter: PublisherAdapter
    private var currentStudentID : Int? = null
    private val studentColorMap = mutableMapOf<Int, Int>()

    private val colorMap = listOf(
        Color.RED, Color.BLUE, Color.GREEN, Color.YELLOW, Color.MAGENTA,
        Color.CYAN, Color.DKGRAY, Color.LTGRAY, Color.BLACK, Color.WHITE
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize DatabaseHelper
        dbHelper = DatabaseHelper(this, null)
        //dbHelper.resetLocationTable() // reset data (for debugging)

        // Assign colors to existing publishers within the database
        val publishers = dbHelper.getStudentIDs()
        publishers.forEachIndexed {index, studentId ->
            val color = colorMap[index % colorMap.size]
            studentColorMap[studentId] = color
        }

        // Set up RecyclerView
        rvStudentData = findViewById(R.id.rvStudentData)
        rvStudentData.layoutManager = LinearLayoutManager(this)
        adapter = PublisherAdapter(publishers, studentColorMap) {studentID ->
            updateUI(studentID)
        }
        rvStudentData.adapter = adapter

        // Initialize Google Map
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Initialize MQTT client and subscribe to topic
        mqttClient = Mqtt5Client.builder()
            .identifier(UUID.randomUUID().toString())
            .serverHost("broker-816032311.sundaebytestt.com")
            .serverPort(1883)
            .build()
            .toBlocking()

        try {
            mqttClient.connect()
            mqttClient.subscribeWith().topicFilter("assignment/location").send()
            mqttClient.toAsync().publishes(MqttGlobalPublishFilter.ALL) { publish ->
                val message = String(publish.payloadAsBytes)
                processIncomingLocationData(message)
            }
        } catch (e: Exception) {
            Log.e("MQTT", "Failed to connect to subscribe: ${e.message}")
        }

        // Set up Button
        findViewById<Button>(R.id.btnReturn).setOnClickListener {updateUI(null)}

        // Set up DialogPicker
        val etStartDate: EditText = findViewById(R.id.dpStart)
        val etEndDate: EditText = findViewById(R.id.dpEnd)
        setupDatePicker(etStartDate)
        setupDatePicker(etEndDate)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        runOnUiThread {
            drawPolyline()
        }
        //mMap.setOnMapClickListener { latLng -> addMarkerAtLocation(latLng) }
    }

    private fun addMarkerAtLocation(latLng: LatLng, color: Int) {
        mMap.addCircle(
            CircleOptions()
                .center(latLng)
                .radius(0.5)
                .fillColor(color)
                .strokeColor(color)
                .strokeWidth(0f)
        )

        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 10f))
    }

    private fun drawPolyline() {
        try {
            mMap.clear() // Clear previous points
            val bounds = LatLngBounds.builder() // needed for camera

            val publishers = dbHelper.getStudentIDs()
            publishers.forEachIndexed { index, studentId ->
                val color = studentColorMap[studentId]!!

                val currentDate = Date()
                val calender = Calendar.getInstance()
                calender.time = currentDate
                calender.add(Calendar.MINUTE, -5)
                val last5Minutes = calender.time

                val pointsList = dbHelper.getAllLocations(last5Minutes, currentDate, studentId)
                if (pointsList.isEmpty()) {
                    Log.d("MAP", "No points to plot on map for studentID. $studentId")
                    return@forEachIndexed
                }

                val latLngPoints = pointsList.map { LatLng(it.latitude, it.longitude) }

                // Add the markers
                latLngPoints.forEach { addMarkerAtLocation(it, color) }

                val polylineOptions = PolylineOptions()
                    .addAll(latLngPoints)
                    .color(color)
                    .width(5f)
                    .geodesic(true)

                mMap.addPolyline(polylineOptions)

                latLngPoints.forEach { bounds.include(it) }
            }
            mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 100))
        } catch (e : Exception) {
           e.printStackTrace()
        }
    }

    private fun drawPolylineStudent(startDate: Date, endDate: Date,studentId: Int) {
        try {
            mMap.clear() // clear previous lines
            val color = studentColorMap[studentId]!!

            val pointsList = dbHelper.getAllLocations(startDate, endDate,studentId)
            if (pointsList.isEmpty()) {
                Log.d("MAP", "No points to plot on map for studentID. $studentId")
                return
            }

            val latLngPoints = pointsList.map { LatLng(it.latitude, it.longitude) }

            // Add the markers
            latLngPoints.forEach { addMarkerAtLocation(it, color) }

            val polylineOptions = PolylineOptions()
                .addAll(latLngPoints)
                .color(color)
                .width(5f)
                .geodesic(true)

            mMap.addPolyline(polylineOptions)

            val bounds = LatLngBounds.builder() // needed for camera
            latLngPoints.forEach { bounds.include(it) }
            mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 100))
        } catch (e : Exception) {
            e.printStackTrace()
        }
    }


    private fun processIncomingLocationData(message: String) {
        // Parse message, extract latitude, longitude, and speed
        val data = parseLocationData(message)
        if (data != null) {
            dbHelper.createLocation(data) // Insert into SQLite database
            runOnUiThread {
                // Check if the student ID is already in the list
                if (!adapter.hasStudentID(data.studentId)) {
                    // Update the adapter with the new student ID
                    val color = colorMap[(dbHelper.getStudentIDs().size) % colorMap.size]
                    studentColorMap[data.studentId] = color // assign a color
                    adapter.addStudentID(data.studentId)
                } else {
                    adapter.updateItem(data.studentId)
                }

                drawPolyline() // Update the map
            }
        } else {
            Log.e("MQTT", "Failed to process message: $message")
        }
    }

    private fun parseLocationData(message: String): LocationData? {
        return try {
            val json = JSONObject(message)

            val studentId = when {
                json.has("studentId") -> json.getInt("studentId")
                json.has("id") -> json.getInt("id")
                json.has("studentID") -> json.getInt("studentID")
                else -> throw JSONException("No valid key for studentId found in the JSON object.")
            }
            LocationData(
                studentId = studentId,
                latitude = json.getDouble("latitude"),
                longitude = json.getDouble("longitude"),
                speed = json.getDouble("speed").toFloat(),
                timestamp = json.getString("timestamp")
            )
        } catch (e: Exception) {
            Log.e("JSON", "Failed to parse location data: ${e.message}")
            null
        }
    }

    private fun updateUI(studentID: Int?) {
        // Set current student ID for Updates
        currentStudentID = studentID

        // Hide the RecyclerView and show the detailed view
        val recyclerView = findViewById<RecyclerView>(R.id.rvStudentData)
        val detailedView = findViewById<ConstraintLayout>(R.id.clDetails)
        val subTitle = findViewById<TextView>(R.id.tvLiveView)
        val dateView = findViewById<ConstraintLayout>(R.id.clDateRange)
        val title = findViewById<TextView>(R.id.tvTitle)

        if (studentID != null) {
            title.text = "Summary of $studentID"
            recyclerView.visibility = View.GONE
            subTitle.visibility = View.GONE
            detailedView.visibility = View.VISIBLE
            dateView.visibility = View.VISIBLE

            // Set default date to update map
            val startDate = findViewById<EditText>(R.id.dpStart)
            val endDate = findViewById<EditText>(R.id.dpEnd)

            val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())

            val calender = Calendar.getInstance()
            calender.time = Date()
            val startCalender = Calendar.getInstance()
            startCalender.time = calender.time
            startCalender.add(Calendar.MONTH, -1) // Show results 1 month prior (default)

            startDate.setText(dateFormat.format(startCalender.time))
            endDate.setText(dateFormat.format(calender.time))

            // Update the detailed view UI elements
            updateSpeed(studentID, startCalender.time, calender.time)

            // Update the map
            drawPolylineStudent(startCalender.time, calender.time, studentID)
        } else {
            // Update back to main screen
            title.text = "Assignment Two - Subscriber"
            recyclerView.visibility = View.VISIBLE
            subTitle.visibility = View.VISIBLE
            detailedView.visibility = View.GONE
            dateView.visibility = View.GONE

            drawPolyline() // Update map with all students for past 5 minutes
        }
    }

    private fun updateSpeed(studentID : Int, startDate : Date, endDate : Date) {
        // Update the detailed view UI elements
        val tvMaxSpeed = findViewById<TextView>(R.id.tvMaxSpeed2)
        val tvMinSpeed = findViewById<TextView>(R.id.tvMinSpeed2)
        val tvAvgSpeed = findViewById<TextView>(R.id.tvAvgSpeed)

        tvMaxSpeed.text = "Maximum Speed: ${ dbHelper.getSpeed(studentID, "MAX", startDate, endDate) }"
        tvMinSpeed.text = "Minimum Speed: ${ dbHelper.getSpeed(studentID, "MIN", startDate, endDate) }"
        tvAvgSpeed.text = "Average Speed: ${ dbHelper.getSpeed(studentID, "AVG", startDate, endDate) }"
    }

    private fun setupDatePicker(editText: EditText) {
        val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        val calendar = Calendar.getInstance()
        editText.setOnClickListener {
            DatePickerDialog(
                this,
                { _, year, month, dayOfMonth ->
                    calendar.set(year, month, dayOfMonth)
                    val selectedDate = calendar.time

                    val startDateText = findViewById<EditText>(R.id.dpStart)
                    val endDateText = findViewById<EditText>(R.id.dpEnd)

                    val startDate = dateFormat.parse(startDateText.text.toString())!!
                    val endDate = dateFormat.parse(endDateText.text.toString())!!

                    if (editText.id == R.id.dpStart && selectedDate.after(endDate)) {
                        Toast.makeText(this, "Start date cannot be after end date.", Toast.LENGTH_SHORT).show()
                        editText.setText(dateFormat.format(startDate))
                    } else if (editText.id == R.id.dpEnd && selectedDate.before(startDate)) {
                        Toast.makeText(this, "End date cannot be before start date.", Toast.LENGTH_SHORT).show()
                        editText.setText(dateFormat.format(endDate))
                    } else {
                        editText.setText(dateFormat.format(selectedDate))
                        // Update
                        val updatedStartDate = dateFormat.parse(startDateText.text.toString())!!
                        val updatedEndDate = dateFormat.parse(endDateText.text.toString())!!
                        if (currentStudentID != null) {
                            updateSpeed(currentStudentID!!, updatedStartDate, updatedEndDate)
                            drawPolylineStudent(updatedStartDate, updatedEndDate, currentStudentID!!)
                        }

                    }
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
    }
}