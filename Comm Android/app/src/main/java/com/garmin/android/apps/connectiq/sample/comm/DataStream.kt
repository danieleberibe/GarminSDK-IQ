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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DataStream : Service() {

    private val TAG = "DataStreamService"
    private lateinit var connectIQ: ConnectIQ
    private var device: IQDevice? = null
    private lateinit var myApp: IQApp

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Servizio DataStream creato")

        createNotificationChannel()
        val notification = createNotification()
        startForeground(1, notification)

        setupGarminConnection()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Servizio DataStream avviato")
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Servizio DataStream distrutto")

        try {
            connectIQ.unregisterForDeviceEvents(device)
            connectIQ.unregisterForApplicationEvents(device, myApp)
        } catch (_: InvalidStateException) { }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
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

    private fun loadDevices() {
        try {
            val devices = connectIQ.knownDevices ?: listOf()

            devices.forEach {
                it.status = connectIQ.getDeviceStatus(it)
            }

            if (devices.isNotEmpty()) {
                device = devices[0]  // Prendiamo il primo dispositivo connesso
                myApp = IQApp("a3421feed289106a538cb9547ab12095") // Sostituisci con il tuo ID

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

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "DataStreamChannel",
                "Data Stream Service",
                NotificationManager.IMPORTANCE_HIGH  // 🔥 Usa HIGH per renderla visibile
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
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, "DataStreamChannel")
            .setContentTitle("D3A - Data Stream Attivo")
            .setContentText("Monitoraggio in corso dei dati Garmin...")
            .setSmallIcon(android.R.drawable.star_big_on)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)  // 🔥 Mostra sempre la notifica
            .setOngoing(true)  // 🔥 Impedisce che venga rimossa con uno swipe
            .build()
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

            // 🔥 Divide il messaggio in righe
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

                    // 🕐 Estrai solo l'orario, ignorando il resto della riga
                    line.matches(Regex("\\d{2}:\\d{2}:\\d{2}.*")) ->
                        time = line.substring(0, 8) // Prende solo le prime 8 cifre (hh:mm:ss)
                }
            }

            // 🔥 Se il valore time è null, usa il timestamp corrente
            val formattedTime = time ?: SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(
                Date()
            )

            val jsonObject = JSONObject().apply {
                put("time", formattedTime)
                put("hr", hr ?: 0)
                put("stress", stress ?: 0)
                put("steps", steps ?: 0)
            }

            jsonArray.put(jsonObject)

            editor.putString("data_list", jsonArray.toString())
            editor.apply()

            Log.d(TAG, "✅ Dati salvati su SharedPreferences: $jsonObject")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Errore nel salvataggio dei dati", e)
        }
    }





}