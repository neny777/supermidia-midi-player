package br.com.supermidia.midi;

/**
 * Evita saltos ao encostar num controle contínuo da controladora.
 *
 * <p>Um fader ou knob absoluto informa a posição em que está, não o quanto girou. Se o
 * player obedecesse de imediato, tocar no controle levaria o valor direto para a posição
 * física dele — no volume geral, um pulo de nível no meio da música.</p>
 *
 * <p>Enquanto armado, o pickup ignora o que chega até o controle <em>alcançar</em> o valor
 * que está na tela, de duas maneiras: encostando nele (dentro de {@link #THRESHOLD}) ou
 * cruzando por ele entre duas mensagens. A segunda existe porque um movimento rápido pode
 * pular por cima do alvo sem nunca cair perto o suficiente.</p>
 *
 * <p>Valores são normalizados de 0 a 1, o que torna a regra independente da faixa do
 * controle — serve tanto para o volume (0 a 100) quanto para o tom (−12 a 12).</p>
 */
public final class ControllerPickup {
    /** Tolerância de encontro: cerca de dois passos e meio na escala de 0 a 127. */
    public static final double THRESHOLD = 2.5 / 127.0;

    private final double threshold;
    private boolean armed = true;
    private boolean valueKnown;
    private double lastValue;

    public ControllerPickup() {
        this(THRESHOLD);
    }

    public ControllerPickup(double threshold) {
        this.threshold = threshold;
    }

    /** Cria um pickup por controle, para conjuntos como os faders de um banco do mixer. */
    public static ControllerPickup[] array(int size) {
        ControllerPickup[] pickups = new ControllerPickup[size];
        for (int index = 0; index < size; index++) {
            pickups[index] = new ControllerPickup();
        }
        return pickups;
    }

    /**
     * Faz o controle precisar alcançar o valor de novo, após uma mudança por outro caminho.
     *
     * <p>Esquece também a última posição conhecida: guardá-la permitiria detectar um
     * cruzamento entre uma posição anterior a esta mudança e a primeira mensagem depois
     * dela, o que assumiria o controle sem que o knob tivesse realmente se movido.</p>
     */
    public void arm() {
        armed = true;
        valueKnown = false;
    }

    public boolean isArmed() {
        return armed;
    }

    /**
     * Decide se esta mensagem deve alterar o valor.
     *
     * @param incoming posição do controle físico, de 0 a 1
     * @param target   valor atualmente na tela, de 0 a 1
     * @return {@code true} quando o controle já assumiu o comando
     */
    public boolean accepts(double incoming, double target) {
        double previous = lastValue;
        boolean known = valueKnown;
        lastValue = incoming;
        valueKnown = true;

        if (!armed) {
            return true;
        }
        boolean closeEnough = Math.abs(incoming - target) <= threshold;
        boolean crossedTarget = known && (previous - target) * (incoming - target) <= 0;
        if (!closeEnough && !crossedTarget) {
            return false;
        }
        armed = false;
        return true;
    }
}
