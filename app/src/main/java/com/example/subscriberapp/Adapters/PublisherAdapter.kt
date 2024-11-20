package com.example.subscriberapp.Adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.subscriberapp.DatabaseHelper
import com.example.subscriberapp.R
import kotlin.math.max

class PublisherAdapter(
    private val studentIDs: MutableList<Int>,
    private val studentColorMap: Map<Int, Int>,
    private val updateUICallback: (Int) -> Unit) :
    RecyclerView.Adapter<PublisherAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvStudentID: TextView = view.findViewById(R.id.tvStudentID)
        val tvMinSpeed: TextView = view.findViewById(R.id.tvMinSpeed)
        val tvMaxSpeed: TextView = view.findViewById(R.id.tvMaxSpeed)
        val btnViewMore: Button = view.findViewById(R.id.btnViewMore)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.publisher_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val studentID = studentIDs[position]
        holder.tvStudentID.text = studentID.toString()

        // Set the text color to assigned color
        val color = studentColorMap[studentID]?: Color.BLACK
        holder.tvStudentID.setTextColor(color)

        // Fetch min and max speeds for the student
        val dbHelper = DatabaseHelper(holder.itemView.context, null)
        val minSpeed = dbHelper.getSpeed(studentID, "MIN")
        val maxSpeed = dbHelper.getSpeed(studentID, "MAX")
        holder.tvMinSpeed.text = "Min Speed: $minSpeed"
        holder.tvMaxSpeed.text = "Max Speed: $maxSpeed"

        // View more
        holder.btnViewMore.setOnClickListener {
            // Handle "View More" click
            updateUICallback(studentID)
        }
    }

    override fun getItemCount(): Int {
        return studentIDs.size
    }

    fun hasStudentID(studentID: Int): Boolean {
        return (studentIDs.indexOf(studentID) != -1)
    }

    fun addStudentID(studentID: Int) {
        studentIDs.add(studentID)
        notifyItemInserted(studentIDs.size - 1)
    }

    fun updateItem(studentID: Int) {
        val position = studentIDs.indexOf(studentID)
        if (position != -1) {
            notifyItemChanged(position)
        }
    }
}
