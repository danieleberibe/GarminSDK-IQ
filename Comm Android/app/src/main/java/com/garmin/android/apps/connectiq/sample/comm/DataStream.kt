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
        val authPreferences = getSharedPreferences("AuthPrefs", MODE_PRIVATE)
        val jsonData = sharedPreferences.getString("data_list", "[]") ?: "[]"
        val jwtToken = authPreferences.getString("jwt", null)

        if (jwtToken.isNullOrEmpty()) {
            Log.e(TAG, "❌ Nessun token JWT trovato. Interrompo l'invio.")
            return
        }

        if (jsonData == "[]" || jsonData.isEmpty()) {
            Log.d(TAG, "📭 Nessun dato da inviare al backend.")
            return
        }

        try {
            val jsonArray = JSONArray(jsonData)
            val formattedArray = JSONArray()

            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                val timestamp = item.optString("timestamp", getCurrentTimestamp())
                val dataObject = item.optJSONObject("data") ?: JSONObject()

                val formattedObject = JSONObject().apply {
                    put("timestamp", timestamp)
                    put("data", dataObject)
                }

                formattedArray.put(formattedObject)
            }

            val requestBody = RequestBody.create(
                "application/json; charset=utf-8".toMediaType(),
                formattedArray.toString()
            )

            val request = Request.Builder()
                .url("https://d3a.atlantica.it/api/cognitive-monitoring/v2/devicedata/saveAll")
                .addHeader("x-auth-token", " $jwtToken")
                .post(requestBody)
                .build()

            // ✅ LOG della richiesta per il debug
            Log.d(TAG, "📤 REQUEST:")
            Log.d(TAG, "URL: ${request.url}")
            Log.d(TAG, "HEADERS: ${request.headers}")
            Log.d(TAG, "BODY: ${formattedArray.toString(2)}") // Formattato per leggibilità

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e(TAG, "❌ Errore nell'invio dei dati: ${e.message}")
                }

                override fun onResponse(call: Call, response: Response) {
                    if (response.isSuccessful) {
                        Log.d(TAG, "✅ Dati inviati con successo!")
                        clearStoredData()
                    } else {
                        Log.e(TAG, "❌ Errore nella risposta del server: ${response.code} - ${response.message}")
                        Log.e(TAG, "❌ RESPONSE BODY: ${response.body?.string()}") // ✅ Risposta del server
                    }
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "❌ Errore nell'invio dei dati al backend", e)
        }
    }


    private fun getCurrentTimestamp(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
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

            // 🔄 Converte la stringa ricevuta in un JSON strutturato
            val dataMap = parseDataString(data)

            val jsonObject = JSONObject().apply {
                put("timestamp", getCurrentTimestamp()) // Timestamp ISO 8601
                put("data", JSONObject(dataMap))        // Inserisci i dati come oggetto JSON
            }

            jsonArray.put(jsonObject)

            editor.putString("data_list", jsonArray.toString())
            editor.apply()

            Log.d(TAG, "✅ Dati salvati su SharedPreferences: $jsonObject")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Errore nel salvataggio dei dati", e)
        }
    }

    private fun parseDataString(data: String): Map<String, Int> {
        val dataMap = mutableMapOf<String, Int>()
        val lines = data.split("\n")  // Divide la stringa su ogni nuova riga

        for (line in lines) {
            val regex = Regex("""- (.*?): (\d+)""")  // Cerca pattern come "- Heart Rate: 77"
            val matchResult = regex.find(line)

            matchResult?.let {
                val key = it.groupValues[1].trim()    // Estrae "Heart Rate", "Stress Score", "Steps"
                val value = it.groupValues[2].toInt() // Estrae il valore numerico come intero

                // Mappatura delle chiavi
                val mappedKey = when (key) {
                    "Heart Rate" -> "hr_garmin"
                    "Stress Score" -> "stress_garmin"
                    "Steps" -> "steps_garmin"
                    else -> key // In caso ci siano altri dati non previsti
                }

                dataMap[mappedKey] = value
            }
        }
        return dataMap
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
