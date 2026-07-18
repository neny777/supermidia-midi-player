package br.com.supermidia.mixer;

import java.util.Objects;

public record MidiChannelInfo(
        int channel,
        String trackName,
        String instrumentName,
        int program,
        int initialVolume,
        boolean used) {

    public MidiChannelInfo {
        if (channel < 0 || channel > 15) {
            throw new IllegalArgumentException("Canal MIDI fora do intervalo: " + channel);
        }
        Objects.requireNonNull(trackName, "trackName");
        Objects.requireNonNull(instrumentName, "instrumentName");
        if (program < 0 || program > 127 || initialVolume < 0 || initialVolume > 127) {
            throw new IllegalArgumentException("Programa ou volume MIDI inválido");
        }
    }

    public int displayChannel() {
        return channel + 1;
    }

    public boolean percussion() {
        return channel == 9;
    }
}
