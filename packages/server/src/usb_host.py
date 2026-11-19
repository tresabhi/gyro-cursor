import time

import board
import max3421e
import usb.core

# USB Host FeatherWing default jumpers.
CHIP_SELECT_PIN = board.D10
IRQ_PIN = board.D9

ENDPOINT_IN = 0x81

_host = None
_device = None
# Must equal the endpoint's max packet size (8 for the pedal); a bigger buffer
# makes the host wait for more packets and the read fails.
_buffer = bytearray(8)
_last_report = None


def init():
    global _host

    # Must stay referenced, or the host controller is torn down.
    _host = max3421e.Max3421E(board.SPI(), chip_select=CHIP_SELECT_PIN, irq=IRQ_PIN)

    print("USB host started")


def _connect():
    global _device, _last_report

    _device = usb.core.find()

    if _device is None:
        return

    _last_report = None

    if _device.is_kernel_driver_active(0):
        _device.detach_kernel_driver(0)

    _device.set_configuration()

    print(f"Listening to {_device.product}")


def poll():
    """Returns the new report when it differs from the last one, else None."""

    global _last_report

    if _device is None:
        _connect()

        if _device is None:
            time.sleep(0.5)
            return None

    try:
        count = _device.read(ENDPOINT_IN, _buffer, timeout=1000)
    except usb.core.USBTimeoutError:
        return None
    except usb.core.USBError as error:
        print(f"USB read error: {error!r}")
        return None

    report = bytes(_buffer[:count])

    if report == _last_report:
        return None

    print(report.hex(" "))
    _last_report = report

    return report
