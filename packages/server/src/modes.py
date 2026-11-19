# Turns raw pedal changes into what the watch should do. Two independent
# states feed the mode the watch sees:
#   enabled: toggled by pressing left and right together ("off" when False)
#   scroll:  true while the left pedal is held ("scroll" vs "cursor")
# The middle pedal is left click and the right pedal is right click.

# Stream Deck Pedal: 0 = left, 1 = middle, 2 = right
LEFT = 0
MIDDLE = 1
RIGHT = 2

_held = [False, False, False]
_enabled = True
# Left and right went down together; neither scrolls nor clicks until both
# are back up, so the toggle doesn't leak into other actions.
_chorded = False
# Whether the watch was told the right pedal (right click) is down.
_right_sent = False


def current():
    if not _enabled:
        return "off"

    if _held[LEFT] and not _chorded:
        return "scroll"

    return "cursor"


def _mode_message():
    return f"mode {current()}"


def _pedal_message(index, pressed):
    return f"pedal {index} {1 if pressed else 0}"


def sync_messages():
    """Everything a freshly connected watch needs to match our state."""

    return [
        _mode_message(),
        _pedal_message(MIDDLE, _held[MIDDLE]),
        _pedal_message(RIGHT, _right_sent),
    ]


def handle(index, pressed):
    """Applies one pedal change and returns the messages to send."""

    global _enabled, _chorded, _right_sent

    before = current()
    messages = []

    _held[index] = pressed

    if index == MIDDLE:
        messages.append(_pedal_message(MIDDLE, pressed))

    elif pressed and _held[LEFT] and _held[RIGHT]:
        if not _chorded:
            _chorded = True
            _enabled = not _enabled

            # Right went down first and already clicked; let go of it.
            if _right_sent:
                _right_sent = False
                messages.append(_pedal_message(RIGHT, False))

    elif index == RIGHT:
        if pressed and not _chorded:
            _right_sent = True
            messages.append(_pedal_message(RIGHT, True))

        elif not pressed and _right_sent:
            _right_sent = False
            messages.append(_pedal_message(RIGHT, False))

    if not _held[LEFT] and not _held[RIGHT]:
        _chorded = False

    if current() != before:
        print(f"Mode: {current()}")
        messages.append(_mode_message())

    return messages
