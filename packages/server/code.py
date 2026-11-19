import src.modes
import src.pedals
import src.socket_server
import src.usb_host
import src.wifi

# Stream Deck Pedal: 0 = left, 1 = middle, 2 = right
MODE_CYCLE_PEDAL = 0

src.wifi.init()
src.socket_server.init()
src.usb_host.init()

while True:
    if src.socket_server.poll():
        # Bring a fresh client up to date.
        src.socket_server.send(src.modes.message())

        for index, pressed in enumerate(src.pedals.states):
            src.socket_server.send(src.pedals.message(index, pressed))

    report = src.usb_host.poll()

    if report is not None:
        for index, pressed in src.pedals.update(report):
            src.socket_server.send(src.pedals.message(index, pressed))

            if index == MODE_CYCLE_PEDAL and pressed:
                src.modes.cycle()
                print(f"Mode: {src.modes.current()}")
                src.socket_server.send(src.modes.message())
