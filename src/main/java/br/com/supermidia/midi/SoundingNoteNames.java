package br.com.supermidia.midi;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Converte as notas em execução na lista de alturas que estão soando.
 *
 * <p>Isto <strong>não nomeia acordes</strong>, e a diferença é proposital. As mesmas
 * notas podem formar Am7 ou C6 conforme o contexto, e sem saber a tonalidade não há
 * como escolher entre uma altura e sua enarmonia. Listar o que soa não inventa nada —
 * quem lê decide, com o ouvido e o contexto que o algoritmo não tem.</p>
 *
 * <p>A ordem é do grave para o agudo, e não alfabética: assim a primeira altura da
 * lista é a do baixo, que é o que distingue um C de um C/E.</p>
 */
public final class SoundingNoteNames {
    private static final String[] PITCH_CLASS_NAMES = {
        "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"
    };
    private static final int SEMITONES_PER_OCTAVE = 12;
    public static final String SEPARATOR = " · ";

    private SoundingNoteNames() {
    }

    /**
     * Lista as alturas distintas que estão soando, do grave para o agudo.
     *
     * @return algo como {@code "E · G · C"}, ou vazio quando nada soa
     */
    public static String format(boolean[] soundingNotes) {
        return String.join(SEPARATOR, distinctPitchClasses(soundingNotes));
    }

    /** Alturas distintas na ordem em que aparecem, do grave para o agudo. */
    public static Set<String> distinctPitchClasses(boolean[] soundingNotes) {
        Set<String> names = new LinkedHashSet<>();
        if (soundingNotes == null) {
            return names;
        }
        for (int note = 0; note < soundingNotes.length; note++) {
            if (soundingNotes[note]) {
                names.add(nameOf(note));
            }
        }
        return names;
    }

    /** Quantas alturas distintas estão soando, independentemente da oitava. */
    public static int distinctPitchClassCount(boolean[] soundingNotes) {
        return distinctPitchClasses(soundingNotes).size();
    }

    public static String nameOf(int note) {
        return PITCH_CLASS_NAMES[Math.floorMod(note, SEMITONES_PER_OCTAVE)];
    }
}
