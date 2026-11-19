import src.modes
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
        for line in src.modes.sync_messages():
            src.socket_server.send(line)

    report = src.usb_host.poll()

    if report is not None:
        for index, pressed in src.pedals.update(report):
            for line in src.modes.handle(index, pressed):
                src.socket_server.send(line)
