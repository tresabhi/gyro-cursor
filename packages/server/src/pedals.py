# Stream Deck Pedal input report: 01 00 03 00 <left> <middle> <right> 00.
# Each pedal byte is 1 while held and 0 otherwise.
FIRST_PEDAL_OFFSET = 4
PEDAL_COUNT = 3

states = [False] * PEDAL_COUNT


def update(report):
    """Returns a list of (index, pressed) for every pedal that changed."""

    changes = []

    if len(report) < FIRST_PEDAL_OFFSET + PEDAL_COUNT:
        return changes

    for index in range(PEDAL_COUNT):
        pressed = report[FIRST_PEDAL_OFFSET + index] != 0

        if pressed != states[index]:
            states[index] = pressed
            changes.append((index, pressed))

    return changes


def message(index, pressed):
    return f"pedal {index} {1 if pressed else 0}"
