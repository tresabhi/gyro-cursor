import time

import usb.core

ENDPOINT_IN = 0x81

devices = list(usb.core.find(find_all=True))
for d in devices:
    print(hex(d.idVendor), hex(d.idProduct), d.manufacturer, d.product)

device = devices[0]
print("logging", device.product)

if device.is_kernel_driver_active(0):
    device.detach_kernel_driver(0)
device.set_configuration()

buf = bytearray(64)
while True:
    try:
        count = device.read(ENDPOINT_IN, buf, timeout=10)
    except usb.core.USBTimeoutError:
        continue
    print(time.monotonic(), bytes(buf[:count]))
