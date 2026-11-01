import os
import time

import board
import digitalio
import socketpool
import wifi
import adafruit_httpserver

PORT = 8080

led = digitalio.DigitalInOut(board.LED)
led.direction = digitalio.Direction.OUTPUT

state = "disabled"  # "cursor" | "disabled"

wifi.radio.connect(
    os.getenv("CIRCUITPY_WIFI_SSID"), os.getenv("CIRCUITPY_WIFI_PASSWORD")
)
print(f"http://{wifi.radio.ipv4_address}:{PORT}")

pool = socketpool.SocketPool(wifi.radio)
server = adafruit_httpserver.Server(pool, debug=True)


@server.route("/state", "GET")
def get_state(request: adafruit_httpserver.Request):
    return adafruit_httpserver.JSONResponse(request, {"state": state})


@server.route("/toggle", "POST")
def toggle(request: adafruit_httpserver.Request):
    global state
    state = "cursor" if state == "disabled" else "disabled"
    led.value = state == "cursor"
    return adafruit_httpserver.JSONResponse(request, {"state": state})


server.start(str(wifi.radio.ipv4_address), PORT)

while True:
    server.poll()
    time.sleep(0.01)
