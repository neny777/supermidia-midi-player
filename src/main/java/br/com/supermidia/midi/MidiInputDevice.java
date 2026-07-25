package br.com.supermidia.midi;

import javax.sound.midi.MidiDevice;

public record MidiInputDevice(MidiDevice.Info info) {
    private static final MidiInputDevice NONE = new MidiInputDevice(null);

    public static MidiInputDevice none() {
        return NONE;
    }

    public boolean isNone() {
        return info == null;
    }

    public String name() {
        return isNone() ? "Nenhuma" : info.getName();
    }

    @Override
    public String toString() {
        if (isNone()) {
            return "Nenhuma entrada MIDI";
        }
        String description = info.getDescription();
        if (description == null || description.isBlank()
                || description.equalsIgnoreCase("No details available")
                || description.equalsIgnoreCase(info.getName())) {
            return info.getName();
        }
        return info.getName() + " — " + description;
    }
}
