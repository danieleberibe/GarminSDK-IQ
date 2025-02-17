using Toybox.WatchUi;
using Toybox.System;
using Toybox.Communications;
using Toybox.Sensor;
using Toybox.Timer;
using Toybox.ActivityMonitor;
using Toybox.Time;
using Toybox.Time.Gregorian;

// Classe per inviare dati continuamente
class ContinuousDataSender {
    var dataTimer;

    function initialize() {
        // Inizializza il Timer per inviare i dati ogni secondo
        dataTimer = new Timer.Timer();
        dataTimer.start(method(:sendData), 5000, true); // Esegui ogni secondo
    }

    // Metodo che invia i dati al telefono
function sendData() as Void {
    var sensor = Sensor.getInfo();
    var activityMonitor = ActivityMonitor.getInfo();
    var listener = new CommListener();
    var dataToSend = ""; // Buffer per i dati da inviare

    // Ottieni il momento corrente
    var currentMoment =  System.getClockTime();

    // Formatta data e ora
    var timestamp = currentMoment.hour.format("%02d") + ":" + currentMoment.min.format("%02d") + ":" + currentMoment.sec.format("%02d");

    // Controlla se i dati sulla frequenza cardiaca sono disponibili
    if (sensor.heartRate != null) {
        var rate = sensor.heartRate.toString();
        dataToSend += timestamp + " - Heart Rate: " + rate + "\n";
    } else {
        System.println("Heart Rate data not available.");
    }

    // Controlla se i dati sullo stress sono disponibili
    if (activityMonitor.stressScore != null) {
        var stress = activityMonitor.stressScore.toString();
        dataToSend += timestamp + " - Stress Score: " + stress + "\n";
    } else {
        System.println("Stress data not available.");
    }

    // Controlla se i dati sui passi sono disponibili
    if (activityMonitor.steps != null) {
        var steps = activityMonitor.steps.toString();
        dataToSend += timestamp + " - Steps: " + steps + "\n";
    } else {
        System.println("Steps data not available.");
    }

    // Respiration
    if (activityMonitor.respirationRate != null) {
        var respiration = activityMonitor.respirationRate.toString();
        dataToSend += timestamp + " - Respiration: " + respiration + "\n";
    } else {
        System.println("Respiration data not available.");
    }
    //Calories
    if (activityMonitor.calories != null) {
        var calories = activityMonitor.calories.toString();
        dataToSend += timestamp + " - Calories: " + calories + "\n";
    } else {
        System.println("Calories data not available.");
    }
    //Distance
    if (activityMonitor.distance != null) {
        var distance = activityMonitor.distance.toString();
        dataToSend += timestamp + " - Distance: " + distance + "\n";
    } else {
        System.println("Distance data not available.");
    }
    //Active Minutes Day
    if (activityMonitor.activeMinutesDay != null) {
        var activeMinutesDay = activityMonitor.activeMinutesDay.toString();
        dataToSend += timestamp + " - Active Minutes: " + activeMinutesDay + "\n";
    } else {
        System.println("Active Minutes data not available.");
    }

    //Spo 2
    if (sensor.oxygenSaturation != null) {
        var spo2 = sensor.oxygenSaturation.toString();
        dataToSend += timestamp + " - Spo2: " + spo2 + "\n";
    } else {
        System.println("Spo2 data not available.");
    }

    //temperature
    if (sensor.temperature != null) {
        var temperature = sensor.temperature.toString();
        dataToSend += timestamp + " - Temperature: " + temperature + "\n";
    } else {
        System.println("Temperature data not available.");
    }


    // Invia i dati raccolti, se presenti
    if (dataToSend != "") {
        Communications.transmit(dataToSend, null, listener);
    } else {
        System.println("No data available to send.");
    }
}


}

// Listener per gestire il completamento della trasmissione
class CommListener extends Communications.ConnectionListener {
    function initialize() {
        Communications.ConnectionListener.initialize();
    }

    function onComplete() as Void {
        System.println("Transmit Complete");
    }

    function onError() as Void {
        System.println("Transmit Failed");
    }
}

// Classe principale per l'interazione con il menu e l'UI
class CommInputDelegate extends WatchUi.BehaviorDelegate {
    var dataSender;

    function initialize() {
        WatchUi.BehaviorDelegate.initialize();
        dataSender = new ContinuousDataSender(); // Inizia l'invio continuo dei dati
        dataSender.initialize();
    }

    function onResume() as Void {
        // Riprendi il timer quando l'app è attiva
        if (dataSender != null) {
            dataSender.initialize();
        }
    }

    function onPause() as Void {
        // Sospendi il timer se necessario quando l'app è in pausa
        if (dataSender != null && dataSender.dataTimer != null) {
            dataSender.dataTimer.stop();
        }
    }
}

// Menu delegate
class BaseMenuDelegate extends WatchUi.MenuInputDelegate {
    function initialize() {
        WatchUi.MenuInputDelegate.initialize();
    }

    function onMenuItem(item) as Void {
        var menu = new WatchUi.Menu();
        var delegate = null;

        if(item == :sendData) {
            menu.addItem("Hello World.", :hello);
            menu.addItem("Ackbar", :trap);
            menu.addItem("Garmin", :garmin);
            delegate = new SendMenuDelegate();
        }

        WatchUi.pushView(menu, delegate, WatchUi.SLIDE_IMMEDIATE);
    }
}

// Classe che gestisce le trasmissioni dei menu
class SendMenuDelegate extends WatchUi.MenuInputDelegate {
    function initialize() {
        WatchUi.MenuInputDelegate.initialize();
    }

    function onMenuItem(item) as Void {
        var listener = new CommListener();

        if (item == :hello) {
            Communications.transmit("Hello World.", null, listener);
        } else if (item == :trap) {
            Communications.transmit("IT'S A TRAP!", null, listener);
        } else if (item == :garmin) {
            Communications.transmit("ConnectIQ", null, listener);
        } 
        WatchUi.popView(WatchUi.SLIDE_IMMEDIATE);
    }
}

