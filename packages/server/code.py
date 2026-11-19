import src.usb_host
import src.wifi

src.wifi.init()
src.usb_host.init()

while True:
    src.usb_host.poll()
