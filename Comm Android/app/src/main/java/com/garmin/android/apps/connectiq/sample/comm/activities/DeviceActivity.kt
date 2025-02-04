/**
 * Copyright (C) 2015 Garmin International Ltd.
 * Subject to Garmin SDK License Agreement and Wearables Application Developer Agreement.
 */
package com.garmin.android.apps.connectiq.sample.comm.activities

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.garmin.android.apps.connectiq.sample.comm.MessageFactory
import com.garmin.android.apps.connectiq.sample.comm.R
import com.garmin.android.apps.connectiq.sample.comm.adapter.EventAdapter
import com.garmin.android.apps.connectiq.sample.comm.adapter.MessagesAdapter
import com.garmin.android.connectiq.ConnectIQ
import com.garmin.android.connectiq.IQApp
import com.garmin.android.connectiq.IQDevice
import com.garmin.android.connectiq.exception.InvalidStateException
import com.garmin.android.connectiq.exception.ServiceUnavailableException
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

private const val TAG = "DeviceActivity"
private const val EXTRA_IQ_DEVICE = "IQDevice"
private const val COMM_WATCH_ID = "a3421feed289106a538cb9547ab12095"

// TODO Add a valid store app id.
private const val STORE_APP_ID = ""

class DeviceActivity : Activity() {

    companion object {
        fun getIntent(context: Context, device: IQDevice?): Intent {
            val intent = Intent(context, DeviceActivity::class.java)
            intent.putExtra(EXTRA_IQ_DEVICE, device)
            return intent
        }
    }

    private var deviceStatusView: TextView? = null
    private var openAppButtonView: TextView? = null

    private val connectIQ: ConnectIQ = ConnectIQ.getInstance()
    private lateinit var device: IQDevice
    private lateinit var myApp: IQApp

    private lateinit var recyclerView: RecyclerView
    private val eventsList = mutableListOf<String>()
    private lateinit var adapter: EventAdapter

    private var appIsOpen = false
    private val openAppListener = ConnectIQ.IQOpenApplicationListener { _, _, status ->
        Toast.makeText(applicationContext, "App Status: " + status.name, Toast.LENGTH_SHORT).show()

        if (status == ConnectIQ.IQOpenApplicationStatus.APP_IS_ALREADY_RUNNING) {
            appIsOpen = true
            openAppButtonView?.setText(R.string.open_app_already_open)
        } else {
            appIsOpen = false
            openAppButtonView?.setText(R.string.open_app_open)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device)

        device = intent.getParcelableExtra<Parcelable>(EXTRA_IQ_DEVICE) as IQDevice
        myApp = IQApp(COMM_WATCH_ID)

        val deviceNameView = findViewById<TextView>(R.id.devicename)
        deviceStatusView = findViewById(R.id.devicestatus)
        openAppButtonView = findViewById(R.id.openapp)
        val openAppStoreView = findViewById<View>(R.id.openstore)

        deviceNameView?.text = device.friendlyName
        deviceStatusView?.text = device.status?.name
        openAppButtonView?.setOnClickListener { openMyApp() }
        openAppStoreView?.setOnClickListener { openStore() }

        recyclerView = findViewById(R.id.eventsRecyclerView)
        adapter = EventAdapter(eventsList)
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this)

        // ✅ CARICA I DATI SALVATI
        loadSavedData()
    }



    private fun loadSavedData() {
        val sharedPreferences = getSharedPreferences("GarminData", MODE_PRIVATE)
        val jsonData = sharedPreferences.getString("data_list", "[]") ?: "[]"

        Log.d(TAG, "📥 Dati letti da SharedPreferences: $jsonData")  // ✅ Controlliamo cosa leggiamo

        val jsonArray = JSONArray(jsonData)
        eventsList.clear()  // Pulisce la lista prima di aggiungere nuovi dati

        for (i in 0 until jsonArray.length()) {
            try {
                val jsonObject = jsonArray.getJSONObject(i)
                val time = jsonObject.opt("time")?.toString() ?: "N/A"
                val steps = jsonObject.optInt("steps", -1)

                eventsList.add("⏱ Ora: $time, 🚶 Steps: $steps")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Errore nella lettura dei dati salvati", e)
            }
        }

        // Aggiorna la UI
        runOnUiThread {
            adapter.notifyDataSetChanged()
        }
    }


    public override fun onResume() {
        super.onResume()
        listenByDeviceEvents()
        listenByMyAppEvents()
        getMyAppStatus()
    }

    public override fun onPause() {
        super.onPause()

        // It is a good idea to unregister everything and shut things down to
        // release resources and prevent unwanted callbacks.
        try {
            connectIQ.unregisterForDeviceEvents(device)
            connectIQ.unregisterForApplicationEvents(device, myApp)
        } catch (_: InvalidStateException) {
        }
    }

    private fun openMyApp() {
        Toast.makeText(this, "Opening app...", Toast.LENGTH_SHORT).show()

        // Send a message to open the app
        try {
            connectIQ.openApplication(device, myApp, openAppListener)
        } catch (_: Exception) {
        }
    }

    private fun openStore() {
        Toast.makeText(this, "Opening ConnectIQ Store...", Toast.LENGTH_SHORT).show()

        // Send a message to open the store
        try {
            if (STORE_APP_ID.isBlank()) {
                AlertDialog.Builder(this@DeviceActivity)
                    .setTitle(R.string.missing_store_id)
                    .setMessage(R.string.missing_store_id_message)
                    .setPositiveButton(android.R.string.ok, null)
                    .create()
                    .show()
            } else {
                connectIQ.openStore(STORE_APP_ID)
            }
        } catch (_: Exception) {
        }
    }

    private fun listenByDeviceEvents() {
        // Get our instance of ConnectIQ. Since we initialized it
        // in our MainActivity, there is no need to do so here, we
        // can just get a reference to the one and only instance.
        try {
            connectIQ.registerForDeviceEvents(device) { _, status ->
                // Since we will only get updates for this device, just display the status
                deviceStatusView?.text = status.name
            }
        } catch (e: InvalidStateException) {
            Log.wtf(TAG, "InvalidStateException:  We should not be here!")
        }
    }


    // Let's register to receive messages from our application on the device.
    private fun listenByMyAppEvents() {
        try {
            connectIQ.registerForAppEvents(device, myApp) { _, _, message, _ ->
                if (message.isNotEmpty()) {
                    for (data in message) {
                        eventsList.add(data.toString()) // Aggiorniamo la lista mostrata
                        saveDataToFile(data.toString()) // Salviamo i dati su file
                    }
                } else {
                    eventsList.add("Messaggio vuoto ricevuto.")
                }

                // Aggiorniamo la UI con i nuovi dati
                runOnUiThread {
                    adapter.notifyDataSetChanged()
                }
            }
        } catch (e: InvalidStateException) {
            Toast.makeText(this, "ConnectIQ non è in uno stato valido", Toast.LENGTH_SHORT).show()
        }
    }



    // Let's check the status of our application on the device.
    private fun getMyAppStatus() {
        try {
            connectIQ.getApplicationInfo(COMM_WATCH_ID, device, object :
                ConnectIQ.IQApplicationInfoListener {
                override fun onApplicationInfoReceived(app: IQApp) {
                    if (::recyclerView.isInitialized) {
                        buildMessageList()
                    } else {
                        Log.e(TAG, "RecyclerView non inizializzata, impossibile aggiornare la lista!")
                    }
                }

                override fun onApplicationNotInstalled(applicationId: String) {
                    // The Comm widget is not installed on the device so we have
                    // to let the user know to install it.
                    AlertDialog.Builder(this@DeviceActivity)
                        .setTitle(R.string.missing_widget)
                        .setMessage(R.string.missing_widget_message)
                        .setPositiveButton(android.R.string.ok, null)
                        .create()
                        .show()
                }
            })
        } catch (_: InvalidStateException) {
        } catch (_: ServiceUnavailableException) {
        }
    }

    private fun buildMessageList() {
        val adapter = MessagesAdapter { onItemClick(it) }
        adapter.submitList(MessageFactory.getMessages(this@DeviceActivity))

        // Modifica per usare il corretto ID della RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }


    private fun onItemClick(message: Any) {
        try {
            connectIQ.sendMessage(device, myApp, message) { _, _, status ->
                Toast.makeText(this@DeviceActivity, status.name, Toast.LENGTH_SHORT).show()
            }
        } catch (e: InvalidStateException) {
            Toast.makeText(this, "ConnectIQ is not in a valid state", Toast.LENGTH_SHORT).show()
        } catch (e: ServiceUnavailableException) {
            Toast.makeText(
                this,
                "ConnectIQ service is unavailable.   Is Garmin Connect Mobile installed and running?",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun saveDataToFile(data: String) {
        try {
            val fileName = "garmin_data.json"
            val file = File(filesDir, fileName)

            val jsonArray: JSONArray = if (file.exists()) {
                JSONArray(file.readText()) // Se il file esiste, leggilo
            } else {
                JSONArray()
            }

            jsonArray.put(JSONObject(data)) // Aggiungi il nuovo dato
            file.writeText(jsonArray.toString()) // Sovrascrivi il file con i nuovi dati

            Log.d(TAG, "Dati salvati su $fileName")
        } catch (e: Exception) {
            Log.e(TAG, "Errore nel salvataggio dei dati", e)
        }
    }

    private fun readDataFromSharedPreferences(): List<String> {
        val dataList = mutableListOf<String>()
        try {
            val sharedPreferences = getSharedPreferences("GarminData", MODE_PRIVATE)
            val jsonData = sharedPreferences.getString("data_list", "[]") ?: "[]"

            val jsonArray = JSONArray(jsonData)
            for (i in 0 until jsonArray.length()) {
                dataList.add(jsonArray.getJSONObject(i).toString()) // Aggiungi ogni dato alla lista
            }
        } catch (e: Exception) {
            Log.e(TAG, "Errore nella lettura dei dati", e)
        }
        return dataList
    }
}