/**
 * Copyright (C) 2015 Garmin International Ltd.
 * Subject to Garmin SDK License Agreement and Wearables Application Developer Agreement.
 */
package com.garmin.android.apps.connectiq.sample.comm.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.garmin.android.apps.connectiq.sample.comm.Message
import com.garmin.android.apps.connectiq.sample.comm.R

class MessagesAdapter(
    private val onItemClickListener: (Any) -> Unit
) : ListAdapter<Message, MessageViewHolder>(MessageItemDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_data, parent, false)
        return MessageViewHolder(view, onItemClickListener)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bindTo(getItem(position))
    }
}

private class MessageItemDiffCallback : DiffUtil.ItemCallback<Message>() {
    override fun areItemsTheSame(oldItem: Message, newItem: Message): Boolean =
        oldItem == newItem

    override fun areContentsTheSame(oldItem: Message, newItem: Message): Boolean =
        oldItem == newItem

}

class MessageViewHolder(
    private val view: View,
    private val onItemClickListener: (Any) -> Unit
) : RecyclerView.ViewHolder(view) {

    private val tvTimestamp: TextView = view.findViewById(R.id.tvTimestamp)
    private val tvHeartRate: TextView = view.findViewById(R.id.tvHeartRate)
    private val tvStress: TextView = view.findViewById(R.id.tvStress)
    private val tvSteps: TextView = view.findViewById(R.id.tvSteps)

    fun bindTo(message: Message) {
        try {
            val jsonObject = org.json.JSONObject(message.text)
            tvTimestamp.text = "Time: " + jsonObject.getString("timestamp")
            tvHeartRate.text = "Heart Rate: " + jsonObject.getString("heartRate")
            tvStress.text = "Stress: " + jsonObject.getString("stressScore")
            tvSteps.text = "Steps: " + jsonObject.getString("steps")
        } catch (e: Exception) {
            tvTimestamp.text = message.text
            tvHeartRate.text = ""
            tvStress.text = ""
            tvSteps.text = ""
        }

        view.setOnClickListener {
            onItemClickListener(message.payload)
        }
    }
}
