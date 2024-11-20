package com.example.subscriberapp

import android.content.ContentValues
import android.database.sqlite.SQLiteOpenHelper
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.example.subscriberapp.Models.LocationData
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val DB_NAME = "database.sql"
const val DB_VERSION = 1

class DatabaseHelper(context: Context, factory: SQLiteDatabase.CursorFactory?): SQLiteOpenHelper (context, DB_NAME, factory, DB_VERSION) {
    override fun onCreate(db: SQLiteDatabase) {
        val createTableQuery = ("CREATE TABLE Location (" +
                "locationID INTEGER PRIMARY KEY AUTOINCREMENT," +
                "studentID INTEGER," +
                "longitude DOUBLE," +
                "latitude DOUBLE," +
                "speed FLOAT," +
                "timestamp TEXT)")

        db.execSQL(createTableQuery)
    }

    // When upgrading DB versions, we have to carefully add columns in a non destructive way
    override fun onUpgrade(db: SQLiteDatabase?, p1: Int, p2: Int) {
        db?.execSQL("DROP TABLE IF EXISTS Location")
        onCreate(db!!)
    }

    fun createLocation(data: LocationData) {
        val values = ContentValues()
        values.put("studentID", data.studentId)
        values.put("longitude", data.longitude)
        values.put("latitude", data.latitude)
        values.put("speed", data.speed)
        values.put("timestamp", data.timestamp)

        val db = this.writableDatabase
        db.insert("Location", null, values)
        db.close()
    }

    fun getAllLocations(startDate: Date, endDate: Date, studentId: Int? = null): List<LocationData> {
        val locationList = mutableListOf<LocationData>()
        val db = this.readableDatabase

        // Format the start and end dates into the string format as the timestamp
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val startDateStr = dateFormat.format(startDate)
        val endDateStr = dateFormat.format(endDate)

        // Build the query with date range and optional studentID filter
        val query = if (studentId != null) {
            "SELECT * FROM Location WHERE studentID = ? AND timestamp BETWEEN ? AND ?"
        } else {
            "SELECT * FROM Location WHERE timestamp BETWEEN ? AND ?"
        }


        val cursor: Cursor = db.rawQuery(query, if (studentId != null) {
            arrayOf(studentId.toString(), startDateStr, endDateStr)
        } else {
            arrayOf(startDateStr, endDateStr)
        })

        // Process the results
        if (cursor.moveToFirst()) {
            do {
                val data = LocationData(
                    studentId = cursor.getInt(cursor.getColumnIndexOrThrow("studentID")),
                    latitude = cursor.getDouble(cursor.getColumnIndexOrThrow("latitude")),
                    longitude = cursor.getDouble(cursor.getColumnIndexOrThrow("longitude")),
                    speed = cursor.getFloat(cursor.getColumnIndexOrThrow("speed")),
                    timestamp = cursor.getString(cursor.getColumnIndexOrThrow("timestamp"))
                )
                locationList.add(data)
            } while (cursor.moveToNext())
        }

        cursor.close()
        db.close()
        return locationList
    }

    fun getStudentIDs(): MutableList<Int> {
        val studentIDList = mutableListOf<Int>()
        val db = this.readableDatabase

        val cursor = db.rawQuery("SELECT DISTINCT studentID FROM Location", null)
        if (cursor.moveToFirst()) {
            do {
                val studentID = cursor.getInt(cursor.getColumnIndexOrThrow("studentID"))
                studentIDList.add(studentID)
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return studentIDList
    }

    fun getSpeed(studentId: Int, type: String, startDate: Date? = null, endDate: Date? = null): Float {
        // Validate the type input to avoid SQL injection
        val validTypes = listOf("MIN", "MAX", "AVG")
        if (type !in validTypes) {
            throw IllegalArgumentException("Invalid type. Must be 'MIN', 'MAX', or 'AVG'.")
        }

        val db = this.readableDatabase
        val query: String
        val cursor: Cursor

        if (startDate != null && endDate != null) {
            // Format the start and end dates into the string format as the timestamp
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val startDateStr = dateFormat.format(startDate)
            val endDateStr = dateFormat.format(endDate)

            query = "SELECT $type(speed) as speed FROM Location WHERE studentID = ? AND timestamp BETWEEN ? AND ?"
            cursor = db.rawQuery(query, arrayOf(studentId.toString(), startDateStr, endDateStr))
        } else {
            query = "SELECT $type(speed) as speed FROM Location WHERE studentID = ?"
            cursor = db.rawQuery(query, arrayOf(studentId.toString()))
        }

        var speed = 0f

        if (cursor.moveToFirst()) {
            // Check if the result is null to avoid potential issues
            speed = cursor.getFloat(cursor.getColumnIndexOrThrow("speed"))
        } else {
            // Handle the case where no rows are returned
            Log.d("DATABASE", "No speed data found for studentID $studentId within the specified range.")
        }

        cursor.close()
        db.close()

        return speed
    }

    fun resetLocationTable() {
        val db = this.writableDatabase
        try {
            db.execSQL("DROP TABLE IF EXISTS Location") // Drop the existing table
            onCreate(db) // Recreate the table by calling onCreate()
            Log.e("DatabaseHelper", "Location table reset successfully.")
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "Error resetting Location table: ${e.message}")
        } finally {
            db.close()
        }
    }
}