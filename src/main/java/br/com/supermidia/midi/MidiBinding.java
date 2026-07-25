package br.com.supermidia.midi;

import javax.sound.midi.ShortMessage;
import java.util.Optional;

public record MidiBinding(int command, int channel, int data1, ValueMode valueMode) {
    private static final int ANY_DATA = -1;

    public enum ValueMode {
        TRIGGER,
        ABSOLUTE,
        RELATIVE
    }

    public static Optional<MidiBinding> learn(MidiControlMessage message, boolean continuousAction) {
        if (continuousAction) {
            if (message.command() == ShortMessage.PITCH_BEND) {
                return Optional.of(new MidiBinding(
                        ShortMessage.PITCH_BEND, message.channel(), ANY_DATA, ValueMode.ABSOLUTE));
            }
            if (message.command() == ShortMessage.CONTROL_CHANGE) {
                return Optional.of(new MidiBinding(
                        ShortMessage.CONTROL_CHANGE, message.channel(),
                        message.data1(), ValueMode.ABSOLUTE));
            }
            return Optional.empty();
        }

        if (message.command() == ShortMessage.NOTE_ON && message.data2() > 0) {
            return Optional.of(new MidiBinding(
                    ShortMessage.NOTE_ON, message.channel(), message.data1(), ValueMode.TRIGGER));
        }
        if (message.command() == ShortMessage.CONTROL_CHANGE && message.data2() > 0) {
            return Optional.of(new MidiBinding(
                    ShortMessage.CONTROL_CHANGE, message.channel(), message.data1(), ValueMode.TRIGGER));
        }
        return Optional.empty();
    }

    public boolean matches(MidiControlMessage message) {
        return command == message.command()
                && channel == message.channel()
                && (data1 == ANY_DATA || data1 == message.data1());
    }

    public boolean isTriggeredBy(MidiControlMessage message) {
        return matches(message) && message.data2() > 0;
    }

    public double absoluteValue(MidiControlMessage message) {
        if (command == ShortMessage.PITCH_BEND) {
            return ((message.data2() << 7) | message.data1()) / 16383.0;
        }
        return message.data2() / 127.0;
    }

    public int relativeDelta(MidiControlMessage message) {
        int value = message.data2();
        if (value == 64 || value == 0) {
            return 0;
        }
        return value < 64 ? value : -(value - 64);
    }

    public String encode() {
        return command + ":" + channel + ":" + data1 + ":" + valueMode.name();
    }

    public static Optional<MidiBinding> decode(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return Optional.empty();
        }
        try {
            String[] parts = encoded.split(":", -1);
            if (parts.length != 4) {
                return Optional.empty();
            }
            return Optional.of(new MidiBinding(
                    Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]),
                    ValueMode.valueOf(parts[3])));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public String description() {
        String channelText = "canal " + (channel + 1);
        return switch (command) {
            case ShortMessage.PITCH_BEND -> "Pitch Bend · " + channelText;
            case ShortMessage.CONTROL_CHANGE -> "CC " + data1 + " · " + channelText
                    + (valueMode == ValueMode.RELATIVE ? " · relativo" : "");
            case ShortMessage.NOTE_ON -> "Nota " + data1 + " · " + channelText;
            default -> "Comando " + command + " · " + channelText;
        };
    }
}
