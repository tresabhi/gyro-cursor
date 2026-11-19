# The left pedal steps through these in order, wrapping around.
NAMES = ["off", "cursor", "scroll"]
DEFAULT = "cursor"

_index = NAMES.index(DEFAULT)


def current():
    return NAMES[_index]


def cycle():
    global _index

    _index = (_index + 1) % len(NAMES)

    return current()


def message():
    return f"mode {current()}"
