KEYS = [
    "SERVER_SSID",
    "SERVER_PASSWORD",
    "SERVER_IP_TIMEOUT",
    "SERVER_IP_POLL_INTERVAL",
    "SERVER_PORT",
]

_defaults = {}

with open("/config.template.py") as f:
    exec(f.read(), _defaults)

try:
    import config

except ImportError:
    print("WARNING: config.py not found; using defaults. Copy config.template.py to config.py.")

    config = None

for _key in KEYS:
    if config is not None and hasattr(config, _key):
        globals()[_key] = getattr(config, _key)

    else:
        if config is not None:
            print(f"WARNING: {_key} missing from config.py; using default.")

        globals()[_key] = _defaults[_key]
