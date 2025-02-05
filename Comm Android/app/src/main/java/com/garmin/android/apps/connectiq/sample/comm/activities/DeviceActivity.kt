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
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

        Log.d(TAG, "📥 Dati letti da SharedPreferences: $jsonData")

        val jsonArray = JSONArray(jsonData)
        eventsList.clear()

        for (i in 0 until jsonArray.length()) {
            try {
                val jsonObject = jsonArray.getJSONObject(i)

                // ✅ Prendiamo i dati correttamente
                val time = jsonObject.optString("time", "N/A")
                val steps = jsonObject.optInt("steps", -1)
                val hr = jsonObject.optInt("hr", -1)
                val stress = jsonObject.optInt("stress", -1)

                eventsList.add("📅 Data: $time, 🚶 Passi: $steps , ❤️ HR: $hr , 😓 Stress: $stress")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Errore nella lettura dei dati salvati", e)
            }
        }

        // Aggiorna la UI
        runOnUiThread {
            adapter.notifyDataSetChanged()
        }
    }


    private fun convertTimestampToDate(timestamp: Long): String {
        return try {
            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            val date = Date(timestamp)
            sdf.format(date)
        } catch (e: Exception) {
            "Data non disponibile"
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
                        Log.d(TAG, "📩 Dati ricevuti live: $data")
                        saveDataToFile(data.toString())  // 🔥 Salva i dati, ma non li mostra subito nella lista
                    }
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
                        // 🔹 Invece di sostituire l’adapter, aggiorniamo la lista
                        val newMessages = MessageFactory.getMessages(this@DeviceActivity)

                        // 🔥 Filtra i messaggi di test prima di aggiungerli
                        val filteredMessages = newMessages.filterNot { msg ->
                            msg.text.contains("hello", ignoreCase = true) ||
                                    msg.text.contains("string", ignoreCase = true) ||
                                    msg.text.contains("array", ignoreCase = true) ||
                                    msg.text.contains("dictionary", ignoreCase = true) ||
                                    msg.text.contains("complex", ignoreCase = true)
                        }

                        eventsList.addAll(filteredMessages.map { it.payload.toString() })
                        runOnUiThread { adapter.notifyDataSetChanged() }

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

            // 🔥 Controlla se il dato ricevuto è un JSON valido
            val jsonObject = try {
                JSONObject(data)  // Se è un JSON valido, lo usa direttamente
            } catch (e: JSONException) {
                Log.e(TAG, "⚠️ Il dato ricevuto non è un JSON, parsing manuale: $data")

                // 🔥 Divide il messaggio in righe e processa ogni riga
                val lines = data.trim().split("\n")

                var time: String? = null
                var hr: Int? = null
                var stress: Int? = null
                var steps: Int? = null

                for (line in lines) {
                    when {
                        line.contains("Heart Rate:", ignoreCase = true) ->
                            hr = line.replace(Regex(".*Heart Rate:\\s*"), "").trim().toIntOrNull()

                        line.contains("Stress Score:", ignoreCase = true) ->
                            stress = line.replace(Regex(".*Stress Score:\\s*"), "").trim().toIntOrNull()

                        line.contains("Steps:", ignoreCase = true) ->
                            steps = line.replace(Regex(".*Steps:\\s*"), "").trim().toIntOrNull()

                        line.matches(Regex("\\d{2}:\\d{2}:\\d{2}.*")) ->
                            time = line.split(" -")[0].trim()  // Prendi solo l'orario
                    }
                }

                // 🔥 Controlla che i valori non siano nulli, altrimenti assegna 0
                JSONObject().apply {
                    put("time", time ?: "N/A")
                    put("hr", hr ?: 0)
                    put("stress", stress ?: 0)
                    put("steps", steps ?: 0)
                }
            }

            jsonArray.put(jsonObject)
            file.writeText(jsonArray.toString())

            Log.d(TAG, "✅ Dati salvati su $fileName: $jsonObject")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Errore nel salvataggio dei dati", e)
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