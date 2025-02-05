package com.garmin.android.apps.connectiq.sample.comm

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.garmin.android.connectiq.ConnectIQ
import com.garmin.android.connectiq.IQApp
import com.garmin.android.connectiq.IQDevice
import com.garmin.android.connectiq.exception.InvalidStateException
import com.garmin.android.connectiq.exception.ServiceUnavailableException
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class DataStream : Service() {

    private val TAG = "DataStreamService"
    private lateinit var connectIQ: ConnectIQ
    private var device: IQDevice? = null
    private lateinit var myApp: IQApp
    private val timer = Timer()
    private val client = OkHttpClient()

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Servizio DataStream creato")

        createNotificationChannel()
        startForeground(1, createNotification())

        setupGarminConnection()

        // ⏳ Avvia il timer per inviare i dati ogni minuto
        timer.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                sendDataToBackend()
            }
        }, 0, TimeUnit.MINUTES.toMillis(1)) // Ogni minuto
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Servizio DataStream distrutto")
        timer.cancel() // ⛔ Stoppa il timer

        try {
            connectIQ.unregisterForDeviceEvents(device)
            connectIQ.unregisterForApplicationEvents(device, myApp)
        } catch (_: InvalidStateException) { }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun sendDataToBackend() {
        val sharedPreferences = getSharedPreferences("GarminData", MODE_PRIVATE)
        val jsonData = sharedPreferences.getString("data_list", "[]") ?: "[]"

        if (jsonData == "[]" || jsonData.isEmpty()) {
            Log.d(TAG, "📭 Nessun dato da inviare al backend.")
            return
        }

        try {
            val jsonArray = JSONArray(jsonData)
            val jsonPayload = JSONObject().apply {
                put("data", jsonArray) // ✅ Formato corretto richiesto dal backend
            }

            val requestBody = RequestBody.create("application/json; charset=utf-8".toMediaType(), jsonPayload.toString())

            val request = Request.Builder()
                .url("https://54b93b24e2b0.ngrok.app/rest/garmin/data") // 🔥 Sostituisci con il tuo endpoint API
                .post(requestBody)
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e(TAG, "❌ Errore nell'invio dei dati: ${e.message}")
                }

                override fun onResponse(call: Call, response: Response) {
                    if (response.isSuccessful) {
                        Log.d(TAG, "✅ Dati inviati con successo!")

                        // 🔥 Svuota la memoria solo dopo un invio riuscito
                        clearStoredData()
                    } else {
                        Log.e(TAG, "❌ Errore nella risposta del server: ${response.code}")
                    }
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "❌ Errore nell'invio dei dati al backend", e)
        }
    }

    private fun clearStoredData() {
        val sharedPreferences = getSharedPreferences("GarminData", MODE_PRIVATE)
        sharedPreferences.edit().putString("data_list", "[]").apply()
        Log.d(TAG, "🗑️ Memoria dati svuotata dopo l'invio.")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "DataStreamChannel",
                "Data Stream Service",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Monitora i dati del dispositivo Garmin"
                setShowBadge(true)
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, com.garmin.android.apps.connectiq.sample.comm.activities.MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, "DataStreamChannel")
            .setContentTitle("D3A - Data Stream Attivo")
            .setContentText("Monitoraggio in corso dei dati Garmin...")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun setupGarminConnection() {
        connectIQ = ConnectIQ.getInstance(applicationContext, ConnectIQ.IQConnectType.WIRELESS)

        connectIQ.initialize(applicationContext, true, object : ConnectIQ.ConnectIQListener {
            override fun onInitializeError(errStatus: ConnectIQ.IQSdkErrorStatus) {
                Log.e(TAG, "Errore inizializzazione ConnectIQ: ${errStatus.name}")
            }

            override fun onSdkReady() {
                Log.d(TAG, "SDK Garmin pronto")
                loadDevices()
            }

            override fun onSdkShutDown() {
                Log.d(TAG, "SDK Garmin chiuso")
            }
        })
    }

    private fun saveDataToSharedPreferences(data: String) {
        try {
            val sharedPreferences = getSharedPreferences("GarminData", MODE_PRIVATE)
            val editor = sharedPreferences.edit()

            val existingData = sharedPreferences.getString("data_list", "[]") ?: "[]"
            val jsonArray = try {
                JSONArray(existingData)
            } catch (e: Exception) {
                JSONArray()
            }

            val jsonObject = JSONObject().apply {
                put("time", SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()))
                put("data", data)  // 🔥 Salviamo i dati come JSON
            }

            jsonArray.put(jsonObject)

            editor.putString("data_list", jsonArray.toString())
            editor.apply()

            Log.d(TAG, "✅ Dati salvati su SharedPreferences: $jsonObject")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Errore nel salvataggio dei dati", e)
        }
    }

    private fun loadDevices() {
        try {
            val devices = connectIQ.knownDevices ?: listOf()
            devices.forEach { it.status = connectIQ.getDeviceStatus(it) }

            if (devices.isNotEmpty()) {
                device = devices[0]
                myApp = IQApp("a3421feed289106a538cb9547ab12095")
                registerForDataEvents()
            } else {
                Log.w(TAG, "Nessun dispositivo Garmin trovato.")
            }
        } catch (e: InvalidStateException) {
            Log.e(TAG, "ConnectIQ non è inizializzato correttamente.")
        } catch (e: ServiceUnavailableException) {
            Log.e(TAG, "Il servizio ConnectIQ non è disponibile.")
        }
    }

    private fun registerForDataEvents() {
        try {
            device?.let { d ->
                connectIQ.registerForDeviceEvents(d) { _, status ->
                    Log.d(TAG, "Stato dispositivo: $status")
                }

                connectIQ.registerForAppEvents(d, myApp) { _, _, message, _ ->
                    if (message.isNotEmpty()) {
                        for (data in message) {
                            Log.d(TAG, "Dati ricevuti: $data")
                            saveDataToSharedPreferences(data.toString())
                        }
                    } else {
                        Log.d(TAG, "Messaggio vuoto ricevuto.")
                    }
                }
            }
        } catch (e: InvalidStateException) {
            Log.e(TAG, "Errore nel registrare gli eventi: ConnectIQ non è in uno stato valido.")
        }
    }
}
