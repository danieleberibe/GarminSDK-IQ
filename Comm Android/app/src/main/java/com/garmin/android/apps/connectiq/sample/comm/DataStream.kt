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

class DataStream : Service() {

    private val TAG = "DataStreamService"
    private lateinit var connectIQ: ConnectIQ
    private var device: IQDevice? = null
    private lateinit var myApp: IQApp

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Servizio DataStream creato")

        createNotificationChannel()
        startForeground(1, createNotification())

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
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, com.garmin.android.apps.connectiq.sample.comm.activities.MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, "DataStreamChannel")
            .setContentTitle("Data Stream attivo")
            .setContentText("Monitoraggio dati da Garmin...")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun saveDataToSharedPreferences(data: String) {
        try {
            val sharedPreferences = getSharedPreferences("GarminData", MODE_PRIVATE)
            val editor = sharedPreferences.edit()

            // Recupera i dati già salvati
            val existingData = sharedPreferences.getString("data_list", "[]") ?: "[]"
            val jsonArray = JSONArray(existingData)

            // Converte i dati in un JSON valido
            val jsonObject = try {
                JSONObject(data)
            } catch (e: Exception) {
                Log.e(TAG, "Errore nel parsing del JSON, creando un oggetto manuale: $data")
                JSONObject().apply {
                    put("time", System.currentTimeMillis()) // Timestamp
                    put("steps", data.toIntOrNull() ?: 0) // Prova a convertire in numero
                }
            }

            jsonArray.put(jsonObject)

            // Salva i dati aggiornati
            editor.putString("data_list", jsonArray.toString())
            editor.apply()

            Log.d(TAG, "✅ Dati salvati su SharedPreferences: $jsonArray")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Errore nel salvataggio dei dati", e)
        }
    }



}