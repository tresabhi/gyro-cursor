import time

import wifi

import src.config_loader


def init():
    wifi.radio.start_ap(
        src.config_loader.SERVER_SSID, src.config_loader.SERVER_PASSWORD
    )

    print(f"Access point {src.config_loader.SERVER_SSID!r} started")

    deadline = time.monotonic() + src.config_loader.SERVER_IP_TIMEOUT

    while wifi.radio.ipv4_address_ap is None and time.monotonic() < deadline:
        time.sleep(src.config_loader.SERVER_IP_POLL_INTERVAL)

    print(f"IP: {wifi.radio.ipv4_address_ap}")
