package br.com.supermidia.midi;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.SysexMessage;
import java.util.Arrays;
import java.util.Objects;

public final class MidiTransformReceiver implements Receiver {
    private static final int MIDI_CHANNELS = 16;
    private static final int MIDI_NOTES = 128;
    private static final int PERCUSSION_CHANNEL = 9;
    private static final int CHANNEL_VOLUME = 7;
    private static final int ALL_SOUND_OFF = 120;
    private static final int ALL_NOTES_OFF = 123;

    /**
     * Pausa após o reset, antes de liberar a próxima música.
     *
     * <p>Um módulo externo leva dezenas de milissegundos reinicializando e ignora o que
     * chega nesse intervalo. Sem a pausa, o reset e os Program Changes do início do
     * arquivo seguinte caem os dois nesse buraco, e o aparelho continua com os
     * instrumentos da música anterior.</p>
     *
     * <p>Foi assim que o defeito se manifestou: reiniciar o programa corrigia, porque
     * entre conectar a saída e escolher uma música passavam-se segundos; trocar de
     * música não, porque tudo acontecia em milissegundos. Sintetizadores por software
     * não sofrem disso, e por isso o mesmo arquivo soava certo no Gervill.</p>
     */
    private static final long RESET_SETTLE_MILLIS = 150;

    private final Receiver delegate;
    private final int[][] activeOutputNotes = new int[MIDI_CHANNELS][MIDI_NOTES];
    private final int[] sourceVolumes = new int[MIDI_CHANNELS];
    private final int[] volumeOverrides = new int[MIDI_CHANNELS];
    private final boolean[] mutedChannels = new boolean[MIDI_CHANNELS];
    private final boolean[] soloChannels = new boolean[MIDI_CHANNELS];
    private final long[] lastActivityNanos = new long[MIDI_CHANNELS];

    private int transpose;
    private double masterVolume = 1.0;
    private boolean closed;
    /**
     * Barra por padrão o SysEx de fabricante que vem no arquivo.
     *
     * <p>A assimetria decide: quem tem o aparelho da marca do arquivo perde apenas
     * ajustes de efeito ao barrá-lo, enquanto quem tem aparelho de outra marca perde o
     * som por completo ao deixá-lo passar. Repertório circula entre marcas — arquivos
     * feitos para Yamaha carregam XG Reset e parâmetros XG — então o padrão protege o
     * caso grave, e quem quiser o SysEx pode liberá-lo em Configurações.</p>
     */
    private boolean blockVendorSysex = true;

    public MidiTransformReceiver(Receiver delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        for (int[] channel : activeOutputNotes) {
            Arrays.fill(channel, -1);
        }
        Arrays.fill(sourceVolumes, 100);
        Arrays.fill(volumeOverrides, -1);
    }

    @Override
    public synchronized void send(MidiMessage message, long timeStamp) {
        if (closed) {
            return;
        }
        if (!(message instanceof ShortMessage shortMessage)) {
            if (blockVendorSysex && isVendorSpecificSysex(message)) {
                return;
            }
            delegate.send(message, timeStamp);
            return;
        }

        int command = shortMessage.getCommand();
        int channel = shortMessage.getChannel();
        int data1 = shortMessage.getData1();
        int data2 = shortMessage.getData2();

        if (command == ShortMessage.NOTE_ON && data2 > 0) {
            lastActivityNanos[channel] = System.nanoTime();
            if (!isEffectivelyMuted(channel)) {
                sendNoteOn(channel, data1, data2, timeStamp);
            }
            return;
        }
        if (command == ShortMessage.NOTE_OFF
                || (command == ShortMessage.NOTE_ON && data2 == 0)) {
            sendNoteOff(command, channel, data1, data2, timeStamp);
            return;
        }
        if (command == ShortMessage.CONTROL_CHANGE && data1 == CHANNEL_VOLUME) {
            sourceVolumes[channel] = data2;
            sendChannelVolume(channel, timeStamp);
            return;
        }

        delegate.send(message, timeStamp);
    }

    /**
     * Define se SysEx específico de fabricante, vindo do arquivo, chega ao sintetizador.
     *
     * <p>Bloquear é o padrão porque o repertório circula entre marcas: arquivos feitos
     * para Yamaha carregam XG Reset e parâmetros de efeito XG, e mandá-los a um módulo
     * Roland deixa canais mudos por um bom tempo. O mesmo valeria ao contrário, com GS
     * num Yamaha. Quem tem o aparelho da marca certa pode liberar.</p>
     */
    public synchronized void setBlockVendorSysex(boolean block) {
        blockVendorSysex = block;
    }

    public synchronized boolean isBlockingVendorSysex() {
        return blockVendorSysex;
    }

    /**
     * Diz se a mensagem é SysEx de fabricante, e não uma mensagem universal.
     *
     * <p>Os IDs {@code 7E} (não em tempo real) e {@code 7F} (tempo real) são universais —
     * é neles que vive o GM System On, que todo aparelho entende. Qualquer outro ID é de
     * um fabricante: {@code 43} Yamaha, {@code 41} Roland, {@code 44} Casio, e assim por
     * diante. Só esses são barrados.</p>
     */
    private static boolean isVendorSpecificSysex(MidiMessage message) {
        if (!(message instanceof SysexMessage sysex)) {
            return false;
        }
        byte[] data = sysex.getData();
        if (data.length == 0) {
            return false;
        }
        int manufacturer = data[0] & 0xFF;
        return manufacturer != 0x7E && manufacturer != 0x7F;
    }

    public synchronized void setTranspose(int semitones) {
        transpose = Math.max(-24, Math.min(24, semitones));
    }

    public synchronized int getTranspose() {
        return transpose;
    }

    public synchronized void setMasterVolume(double factor) {
        masterVolume = Math.max(0.0, Math.min(1.0, factor));
        for (int channel = 0; channel < MIDI_CHANNELS; channel++) {
            sendChannelVolume(channel, -1);
        }
    }

    public synchronized void setChannelVolume(int channel, int volume) {
        requireChannel(channel);
        volumeOverrides[channel] = Math.max(0, Math.min(127, volume));
        sendChannelVolume(channel, -1);
    }

    public synchronized void setChannelMuted(int channel, boolean muted) {
        requireChannel(channel);
        boolean[] previousState = effectiveMuteState();
        mutedChannels[channel] = muted;
        silenceNewlyMutedChannels(previousState);
    }

    public synchronized void setChannelSolo(int channel, boolean solo) {
        requireChannel(channel);
        boolean[] previousState = effectiveMuteState();
        soloChannels[channel] = solo;
        silenceNewlyMutedChannels(previousState);
    }

    /**
     * Notas que estão soando neste instante, na altura em que saem para o sintetizador.
     *
     * <p>O resultado já reflete o que o ouvinte escuta, e não o que está escrito no
     * arquivo: as alturas são as de saída, portanto <strong>com o transpose aplicado</strong>,
     * e canais silenciados por mute ou solo não constam, porque suas notas nunca chegam a
     * ser registradas.</p>
     *
     * @param includePercussion se o canal de percussão entra; para leitura de harmonia
     *                          ele é ruído e deve ficar de fora
     * @return vetor de 128 posições, {@code true} onde a nota está soando
     */
    public synchronized boolean[] soundingNotes(boolean includePercussion) {
        boolean[] sounding = new boolean[MIDI_NOTES];
        for (int channel = 0; channel < MIDI_CHANNELS; channel++) {
            if (!includePercussion && channel == PERCUSSION_CHANNEL) {
                continue;
            }
            for (int outputNote : activeOutputNotes[channel]) {
                if (outputNote >= 0) {
                    sounding[outputNote] = true;
                }
            }
        }
        return sounding;
    }

    public synchronized boolean isChannelActive(int channel, long activityWindowMillis) {
        requireChannel(channel);
        long lastActivity = lastActivityNanos[channel];
        return lastActivity > 0
                && System.nanoTime() - lastActivity <= activityWindowMillis * 1_000_000L;
    }

    public synchronized void resetMixer(int[] originalVolumes) {
        if (originalVolumes.length != MIDI_CHANNELS) {
            throw new IllegalArgumentException("A mixagem deve conter 16 canais");
        }
        silence();
        for (int channel = 0; channel < MIDI_CHANNELS; channel++) {
            sourceVolumes[channel] = Math.max(0, Math.min(127, originalVolumes[channel]));
        }
        Arrays.fill(volumeOverrides, -1);
        Arrays.fill(mutedChannels, false);
        Arrays.fill(soloChannels, false);
        Arrays.fill(lastActivityNanos, 0);
        for (int channel = 0; channel < MIDI_CHANNELS; channel++) {
            sendChannelVolume(channel, -1);
        }
    }

    public synchronized void silence() {
        for (int channel = 0; channel < MIDI_CHANNELS; channel++) {
            silenceChannel(channel);
        }
    }

    /**
     * Devolve o sintetizador ao estado padrão antes de carregar outra música.
     *
     * <p>Sem isso o dispositivo guarda o que a música anterior deixou: instrumentos,
     * bancos, pitch bend, sustain. O sintoma mais visível é a bateria da música
     * seguinte tocando com o som melódico que sobrou no canal de percussão, porque
     * a maioria dos arquivos só envia Program Change para os canais que usa e conta
     * com o dispositivo nos valores de fábrica.</p>
     *
     * <p>Manda o SysEx padrão e, em seguida, repete a limpeza canal a canal em
     * mensagens comuns — alguns dispositivos ignoram SysEx, e o custo de repetir
     * entre duas músicas é irrelevante.</p>
     */
    /**
     * Reinicializa o sintetizador antes de carregar outra música.
     *
     * <p>Envia uma única mensagem — o reset do modo escolhido — e aguarda o aparelho
     * digeri-la. Não dispara mensagens por canal: uma rajada de dezenas delas sobrecarrega
     * o buffer de entrada de módulos de hardware, que passam a descartar a configuração da
     * música seguinte.</p>
     */
    public synchronized void resetInstruments(SynthResetMode mode) {
        if (closed) {
            return;
        }
        for (int channel = 0; channel < MIDI_CHANNELS; channel++) {
            Arrays.fill(activeOutputNotes[channel], -1);
        }
        if (!mode.hasSysex()) {
            return;
        }
        try {
            byte[] sysex = mode.sysex();
            delegate.send(new SysexMessage(sysex, sysex.length), -1);
        } catch (InvalidMidiDataException exception) {
            throw new IllegalStateException(
                    "SysEx de reset inválido no modo " + mode.name(), exception);
        }
        settleAfterReset();
    }

    /**
     * Segura a linha até o sintetizador concluir o reset.
     *
     * <p>Roda entre duas músicas, nunca durante a reprodução, então a pausa não
     * interrompe nada que esteja soando.</p>
     */
    private void settleAfterReset() {
        try {
            Thread.sleep(RESET_SETTLE_MILLIS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    public synchronized void panic() {
        for (int channel = 0; channel < MIDI_CHANNELS; channel++) {
            sendShortMessage(ShortMessage.CONTROL_CHANGE, channel, ALL_SOUND_OFF, 0, -1);
            sendShortMessage(ShortMessage.CONTROL_CHANGE, channel, ALL_NOTES_OFF, 0, -1);
            Arrays.fill(activeOutputNotes[channel], -1);
        }
    }

    @Override
    public synchronized void close() {
        if (!closed) {
            panic();
            closed = true;
            delegate.close();
        }
    }

    private void sendNoteOn(int channel, int inputNote, int velocity, long timeStamp) {
        int outputNote = transposedNote(channel, inputNote);
        activeOutputNotes[channel][inputNote] = outputNote;
        sendShortMessage(ShortMessage.NOTE_ON, channel, outputNote, velocity, timeStamp);
    }

    private void sendNoteOff(int command, int channel, int inputNote, int velocity, long timeStamp) {
        int mappedNote = activeOutputNotes[channel][inputNote];
        if (mappedNote < 0 && isEffectivelyMuted(channel)) {
            return;
        }
        int outputNote = mappedNote >= 0 ? mappedNote : transposedNote(channel, inputNote);
        activeOutputNotes[channel][inputNote] = -1;
        sendShortMessage(command, channel, outputNote, velocity, timeStamp);
    }

    private void sendChannelVolume(int channel, long timeStamp) {
        int sourceVolume = volumeOverrides[channel] >= 0
                ? volumeOverrides[channel] : sourceVolumes[channel];
        int scaledVolume = (int) Math.round(sourceVolume * masterVolume);
        sendShortMessage(ShortMessage.CONTROL_CHANGE, channel,
                CHANNEL_VOLUME, Math.max(0, Math.min(127, scaledVolume)), timeStamp);
    }

    private int transposedNote(int channel, int inputNote) {
        if (channel == PERCUSSION_CHANNEL) {
            return inputNote;
        }
        return Math.max(0, Math.min(127, inputNote + transpose));
    }

    private boolean isEffectivelyMuted(int channel) {
        if (mutedChannels[channel]) {
            return true;
        }
        boolean anySolo = false;
        for (boolean solo : soloChannels) {
            anySolo |= solo;
        }
        return anySolo && !soloChannels[channel];
    }

    private boolean[] effectiveMuteState() {
        boolean[] state = new boolean[MIDI_CHANNELS];
        for (int channel = 0; channel < MIDI_CHANNELS; channel++) {
            state[channel] = isEffectivelyMuted(channel);
        }
        return state;
    }

    private void silenceNewlyMutedChannels(boolean[] previousState) {
        for (int channel = 0; channel < MIDI_CHANNELS; channel++) {
            if (!previousState[channel] && isEffectivelyMuted(channel)) {
                silenceChannel(channel);
            }
        }
    }

    private void silenceChannel(int channel) {
        sendShortMessage(ShortMessage.CONTROL_CHANGE, channel, ALL_NOTES_OFF, 0, -1);
        Arrays.fill(activeOutputNotes[channel], -1);
    }

    private void requireChannel(int channel) {
        if (channel < 0 || channel >= MIDI_CHANNELS) {
            throw new IllegalArgumentException("Canal MIDI fora do intervalo: " + channel);
        }
    }

    private void sendShortMessage(int command, int channel, int data1, int data2, long timeStamp) {
        try {
            ShortMessage transformed = new ShortMessage();
            transformed.setMessage(command, channel, data1, data2);
            delegate.send(transformed, timeStamp);
        } catch (InvalidMidiDataException exception) {
            throw new IllegalStateException("Falha ao transformar mensagem MIDI", exception);
        }
    }
}
