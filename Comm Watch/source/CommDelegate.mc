using Toybox.WatchUi;
using Toybox.System;
using Toybox.Communications;
using Toybox.Sensor;
using Toybox.Timer;
using Toybox.ActivityMonitor;

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
            var activityMonitor = ActivityMonitor.getInfo();
            var listener = new CommListener();

            // Controlla se i dati sui passi sono disponibili
            if (activityMonitor.steps != null) {
                var steps = activityMonitor.steps.toString();
                Communications.transmit("Steps: " + steps, null, listener);
            } else {
                System.println("Steps data not available.");
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
