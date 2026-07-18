package br.com.supermidia.midi;

import javax.sound.midi.MidiDevice;
import java.util.Objects;

public record MidiOutputDevice(MidiDevice.Info info) {
    public MidiOutputDevice {
        Objects.requireNonNull(info, "info");
    }

    public String name() {
        return info.getName();
    }

    @Override
    public String toString() {
        String description = info.getDescription();
        if (description == null || description.isBlank()
                || description.equalsIgnoreCase(info.getName())) {
            return info.getName();
        }
        return info.getName() + " — " + description;
    }
}
