package br.com.supermidia.midi;

import java.util.ArrayList;
import java.util.List;

public record MidiLearnAction(String id, String displayName, Kind kind, int slot) {
    public enum Kind {
        PLAY,
        PAUSE,
        STOP,
        /** Toca quando parado e para quando tocando: um único botão para o transporte. */
        PLAY_STOP_TOGGLE,
        PREVIOUS,
        NEXT,
        PANIC,
        AUTOPLAY_TOGGLE,
        TRANSPOSE,
        /** Sobe o tom em um semitom. Botão, não knob: útil para ajustar a tonalidade ao vivo. */
        TRANSPOSE_UP,
        /** Desce o tom em um semitom. */
        TRANSPOSE_DOWN,
        SPEED,
        MASTER_VOLUME,
        BANK_PREVIOUS,
        BANK_NEXT,
        BANK_TOGGLE,
        BANK_VOLUME,
        BANK_MUTE,
        BANK_SOLO;

        public boolean isContinuous() {
            return this == TRANSPOSE || this == SPEED
                    || this == MASTER_VOLUME || this == BANK_VOLUME;
        }
    }

    public MidiLearnAction {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("O identificador da ação é obrigatório.");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("O nome da ação é obrigatório.");
        }
        if (kind == null) {
            throw new IllegalArgumentException("O tipo da ação é obrigatório.");
        }
    }

    public static List<MidiLearnAction> defaultActions() {
        List<MidiLearnAction> actions = new ArrayList<>();
        actions.add(new MidiLearnAction("transport.play", "Transporte · Play", Kind.PLAY, -1));
        actions.add(new MidiLearnAction("transport.pause", "Transporte · Pause", Kind.PAUSE, -1));
        actions.add(new MidiLearnAction("transport.stop", "Transporte · Stop", Kind.STOP, -1));
        actions.add(new MidiLearnAction("transport.playStop", "Transporte · Play/Stop",
                Kind.PLAY_STOP_TOGGLE, -1));
        actions.add(new MidiLearnAction("transport.previous", "Transporte · Anterior", Kind.PREVIOUS, -1));
        actions.add(new MidiLearnAction("transport.next", "Transporte · Próxima", Kind.NEXT, -1));
        actions.add(new MidiLearnAction("transport.panic", "Transporte · Panic", Kind.PANIC, -1));
        actions.add(new MidiLearnAction("transport.autoplay", "Transporte · Alternar Autoplay", Kind.AUTOPLAY_TOGGLE, -1));
        actions.add(new MidiLearnAction("global.transpose", "Geral · Tom", Kind.TRANSPOSE, -1));
        actions.add(new MidiLearnAction("global.transpose.up", "Geral · Tom +1 semitom",
                Kind.TRANSPOSE_UP, -1));
        actions.add(new MidiLearnAction("global.transpose.down", "Geral · Tom −1 semitom",
                Kind.TRANSPOSE_DOWN, -1));
        actions.add(new MidiLearnAction("global.speed", "Geral · Velocidade", Kind.SPEED, -1));
        actions.add(new MidiLearnAction("global.volume", "Geral · Volume", Kind.MASTER_VOLUME, -1));
        actions.add(new MidiLearnAction("mixer.bank.previous", "Mixer · Banco anterior", Kind.BANK_PREVIOUS, -1));
        actions.add(new MidiLearnAction("mixer.bank.next", "Mixer · Próximo banco", Kind.BANK_NEXT, -1));
        actions.add(new MidiLearnAction("mixer.bank.toggle", "Mixer · Alternar banco 1–8 / 9–16", Kind.BANK_TOGGLE, -1));

        for (int slot = 0; slot < 8; slot++) {
            int number = slot + 1;
            actions.add(new MidiLearnAction("mixer.slot." + number + ".volume",
                    "Mixer · Fader " + number, Kind.BANK_VOLUME, slot));
            actions.add(new MidiLearnAction("mixer.slot." + number + ".mute",
                    "Mixer · Mute " + number, Kind.BANK_MUTE, slot));
            actions.add(new MidiLearnAction("mixer.slot." + number + ".solo",
                    "Mixer · Solo " + number, Kind.BANK_SOLO, slot));
        }
        return List.copyOf(actions);
    }

    @Override
    public String toString() {
        return displayName;
    }
}
