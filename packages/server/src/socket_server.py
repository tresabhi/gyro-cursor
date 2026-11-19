import errno

import socketpool
import wifi

import src.config_loader

# Messages are newline-terminated ASCII lines, e.g. b"pedal 1 1\n".

_pool = None
_server = None
_client = None


def init():
    global _pool, _server

    _pool = socketpool.SocketPool(wifi.radio)

    _server = _pool.socket(_pool.AF_INET, _pool.SOCK_STREAM)
    _server.setsockopt(_pool.SOL_SOCKET, _pool.SO_REUSEADDR, 1)
    _server.bind(("0.0.0.0", src.config_loader.SERVER_PORT))
    _server.listen(1)
    _server.setblocking(False)

    print(f"Socket server listening on port {src.config_loader.SERVER_PORT}")


def _drop_client():
    global _client

    if _client is not None:
        try:
            _client.close()
        except OSError:
            pass

        _client = None


def poll():
    """Accepts new clients and notices dropped ones. Returns True when a new
    client just connected, so the caller can send it the current state."""

    global _client

    connected = False

    try:
        new_client, address = _server.accept()
    except OSError as error:
        if error.errno != errno.EAGAIN:
            print(f"Socket accept error: {error!r}")
    else:
        # A reconnecting client usually means the old one died without us
        # noticing, so the newest connection wins.
        _drop_client()

        new_client.settimeout(0.5)

        # Small messages; don't let Nagle hold them back.
        if hasattr(_pool, "TCP_NODELAY"):
            try:
                new_client.setsockopt(_pool.IPPROTO_TCP, _pool.TCP_NODELAY, 1)
            except OSError:
                pass

        _client = new_client
        connected = True

        print(f"Socket client connected: {address}")

    return connected


def send(line):
    if _client is None:
        return

    try:
        _client.send((line + "\n").encode())
    except OSError as error:
        print(f"Socket client dropped: {error!r}")
        _drop_client()
