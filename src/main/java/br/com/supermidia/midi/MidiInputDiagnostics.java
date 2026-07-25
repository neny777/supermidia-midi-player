package br.com.supermidia.midi;

import javax.sound.midi.ShortMessage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registra tudo o que a controladora envia para que o mapeamento seja feito
 * a partir do comportamento real do equipamento, e não de suposições.
 *
 * <p>Instância de uso exclusivo da thread da interface: as mensagens já chegam
 * enfileiradas pelo {@link MidiInputMonitor} e são consumidas em um único ponto.</p>
 */
public final class MidiInputDiagnostics {
    private static final int MAX_SIGNATURES = 512;

    public enum Behaviour {
        CONTINUOUS_ABSOLUTE("Contínuo absoluto (fader ou knob)"),
        CONTINUOUS_RELATIVE("Contínuo relativo (encoder infinito)"),
        TRIGGER("Gatilho (botão)"),
        UNDETERMINED("Indefinido — mova ou pressione mais vezes");

        private final String label;

        Behaviour(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public static final class Entry {
        private final MidiSignature signature;
        private final boolean[] valuesSeen = new boolean[128];
        private long messageCount;
        private int distinctValues;
        private int minValue = Integer.MAX_VALUE;
        private int maxValue = Integer.MIN_VALUE;

        private Entry(MidiSignature signature) {
            this.signature = signature;
        }

        private void record(int value) {
            messageCount++;
            if (value >= 0 && value < valuesSeen.length && !valuesSeen[value]) {
                valuesSeen[value] = true;
                distinctValues++;
            }
            minValue = Math.min(minValue, value);
            maxValue = Math.max(maxValue, value);
        }

        public MidiSignature signature() {
            return signature;
        }

        public long messageCount() {
            return messageCount;
        }

        public int distinctValues() {
            return distinctValues;
        }

        public int minValue() {
            return minValue == Integer.MAX_VALUE ? 0 : minValue;
        }

        public int maxValue() {
            return maxValue == Integer.MIN_VALUE ? 0 : maxValue;
        }

        private boolean sawOnly(int... allowed) {
            for (int value = 0; value < valuesSeen.length; value++) {
                if (!valuesSeen[value]) {
                    continue;
                }
                boolean permitted = false;
                for (int candidate : allowed) {
                    if (value == candidate) {
                        permitted = true;
                        break;
                    }
                }
                if (!permitted) {
                    return false;
                }
            }
            return true;
        }

        private boolean saw(int value) {
            return value >= 0 && value < valuesSeen.length && valuesSeen[value];
        }

        public Behaviour behaviour() {
            if (signature.command() == ShortMessage.PITCH_BEND) {
                return Behaviour.CONTINUOUS_ABSOLUTE;
            }
            if (signature.command() == ShortMessage.NOTE_ON) {
                return Behaviour.TRIGGER;
            }
            if (signature.command() != ShortMessage.CONTROL_CHANGE) {
                return Behaviour.TRIGGER;
            }
            if (distinctValues >= 8 && maxValue() - minValue() >= 32) {
                return Behaviour.CONTINUOUS_ABSOLUTE;
            }
            if (distinctValues <= 4 && sawOnly(1, 63, 64, 65, 127)
                    && (saw(63) || saw(65) || (saw(1) && saw(127)))) {
                return Behaviour.CONTINUOUS_RELATIVE;
            }
            if (distinctValues <= 2 && sawOnly(0, 64, 127)) {
                return Behaviour.TRIGGER;
            }
            return Behaviour.UNDETERMINED;
        }

        /** Sugestão de vínculo para este controle, quando o comportamento já está claro. */
        public MidiBinding.ValueMode suggestedValueMode() {
            return switch (behaviour()) {
                case CONTINUOUS_ABSOLUTE -> MidiBinding.ValueMode.ABSOLUTE;
                case CONTINUOUS_RELATIVE -> MidiBinding.ValueMode.RELATIVE;
                default -> MidiBinding.ValueMode.TRIGGER;
            };
        }
    }

    private final Map<MidiSignature, Entry> entries = new LinkedHashMap<>();
    private long totalMessages;

    public void observe(MidiControlMessage message) {
        MidiSignature signature = MidiSignature.of(message);
        Entry entry = entries.get(signature);
        if (entry == null) {
            if (entries.size() >= MAX_SIGNATURES) {
                return;
            }
            entry = new Entry(signature);
            entries.put(signature, entry);
        }
        entry.record(MidiSignature.value(message));
        totalMessages++;
    }

    public void clear() {
        entries.clear();
        totalMessages = 0;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public int controlCount() {
        return entries.size();
    }

    public long messageCount() {
        return totalMessages;
    }

    /** Controles na ordem em que apareceram pela primeira vez. */
    public List<Entry> entries() {
        return List.copyOf(new ArrayList<>(entries.values()));
    }

    public String summary() {
        if (entries.isEmpty()) {
            return "Nenhuma mensagem registrada ainda.";
        }
        return controlCount() + (controlCount() == 1 ? " controle distinto · " : " controles distintos · ")
                + totalMessages + (totalMessages == 1 ? " mensagem" : " mensagens");
    }

    public String report(String deviceName) {
        StringBuilder text = new StringBuilder();
        text.append("Diagnóstico da entrada MIDI\n");
        text.append("Dispositivo: ").append(deviceName == null || deviceName.isBlank()
                ? "não informado" : deviceName).append('\n');
        text.append(summary()).append("\n\n");

        if (entries.isEmpty()) {
            text.append("Conecte a controladora, mova cada fader de ponta a ponta, gire cada\n");
            text.append("encoder nos dois sentidos e pressione cada botão uma vez.\n");
            return text.toString();
        }

        text.append(String.format("%-28s %9s %9s %11s  %s%n",
                "CONTROLE", "MENSAGENS", "VALORES", "FAIXA", "COMPORTAMENTO"));
        text.append("-".repeat(96)).append('\n');
        for (Entry entry : entries.values()) {
            text.append(String.format("%-28s %9d %9d %5d–%-5d  %s%n",
                    entry.signature().description(),
                    entry.messageCount(),
                    entry.distinctValues(),
                    entry.minValue(),
                    entry.maxValue(),
                    entry.behaviour().label()));
        }

        text.append('\n');
        text.append("Como ler:\n");
        text.append("- Contínuo absoluto: use em Volume, Tom, Velocidade e nos faders do mixer.\n");
        text.append("- Contínuo relativo: encoder infinito; o player soma ou subtrai a cada passo.\n");
        text.append("- Gatilho: botão; use em Play, Stop, Mute, Solo e troca de banco.\n");
        text.append("- Um botão que só envia 0 e 127 e um encoder que envia 1 e 127 se parecem;\n");
        text.append("  se houver dúvida, gire o controle devagar e confira se aparece o valor 0.\n");
        text.append("- Se o botão Shift não aparecer nesta lista, ele apenas altera as mensagens\n");
        text.append("  dos outros controles e o banco precisa ser trocado por outro botão.\n");
        return text.toString();
    }
}
