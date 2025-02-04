using Toybox.WatchUi;
using Toybox.Graphics;
using Toybox.Communications;
using Toybox.System;

class CommView extends WatchUi.View {
  var screenWidth, screenHeight;
  var page = 0; // 0 = Intro, 1 = Messaggi ricevuti
  var strings = ["Messaggio 1", "Messaggio 2", "Messaggio 3"]; // Test
  var hasDirectMessagingSupport = true; // Simulazione supporto

  function initialize() {
    View.initialize();
  }

  function onLayout(dc) {
    screenWidth = dc.getWidth();
    screenHeight = dc.getHeight();
  }

  function drawIntroPage(dc) {
    var centerX = screenWidth / 2;
    var yOffset = 50;

    dc.drawText(
      centerX,
      yOffset,
      Graphics.FONT_LARGE,
      "D3A APP",
      Graphics.TEXT_JUSTIFY_CENTER
    );
    dc.drawText(
      centerX,
      yOffset + 90,
      Graphics.FONT_TINY,
      "Connect your phone",
      Graphics.TEXT_JUSTIFY_CENTER
    );
    dc.drawText(
      centerX,
      yOffset + 120,
      Graphics.FONT_TINY,
      "to send your data",
      Graphics.TEXT_JUSTIFY_CENTER
    );
  }

  function drawReceivedStrings(dc) {
    var centerX = screenWidth / 2;
    var y = 70;

    dc.drawText(
      centerX,
      30,
      Graphics.FONT_LARGE,
      "Strings Received:",
      Graphics.TEXT_JUSTIFY_CENTER
    );

    for (var i = 0; i < strings.size(); i++) {
      dc.drawText(
        centerX,
        y,
        Graphics.FONT_MEDIUM,
        strings[i],
        Graphics.TEXT_JUSTIFY_CENTER
      );
      y += 40; // Maggiore spaziatura tra le righe
    }
  }

  function onUpdate(dc) {
    dc.setColor(Graphics.COLOR_TRANSPARENT, Graphics.COLOR_BLACK);
    dc.clear();
    dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);

    if (hasDirectMessagingSupport) {
      if (page == 0) {
        drawIntroPage(dc);
      } else {
        drawReceivedStrings(dc);
      }
    } else {
      dc.drawText(
        screenWidth / 2,
        screenHeight / 3,
        Graphics.FONT_LARGE,
        "Direct Messaging API\nNot Supported",
        Graphics.TEXT_JUSTIFY_CENTER
      );
    }
  }

  function onTap(x, y) {
    // Non è più necessario gestire il tocco sulla "X"
  }
}
