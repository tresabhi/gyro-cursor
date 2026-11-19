import src.pedals
import src.socket_server
import src.usb_host
import src.wifi

src.wifi.init()
src.socket_server.init()
src.usb_host.init()

while True:
    if src.socket_server.poll():
        # Bring a fresh client up to date.
        for index, pressed in enumerate(src.pedals.states):
            src.socket_server.send(src.pedals.message(index, pressed))

    report = src.usb_host.poll()

    if report is not None:
        for index, pressed in src.pedals.update(report):
            src.socket_server.send(src.pedals.message(index, pressed))
