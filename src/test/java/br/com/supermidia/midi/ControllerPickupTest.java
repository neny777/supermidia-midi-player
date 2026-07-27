package br.com.supermidia.midi;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ControllerPickupTest {
    @Test
    void ignoresTheControllerUntilItReachesTheValueOnScreen() {
        ControllerPickup pickup = new ControllerPickup();

        // Volume na tela em 20%, knob parado no topo: encostar nele não pode saltar o nível.
        assertFalse(pickup.accepts(1.0, 0.2));
        assertFalse(pickup.accepts(0.8, 0.2));
        assertFalse(pickup.accepts(0.5, 0.2));
    }

    @Test
    void takesOverWhenTheControllerLandsOnTheValue() {
        ControllerPickup pickup = new ControllerPickup();

        assertFalse(pickup.accepts(0.9, 0.5));
        assertTrue(pickup.accepts(0.5, 0.5), "posição igual ao alvo assume o controle");
        assertTrue(pickup.accepts(0.1, 0.5), "depois de assumir, obedece livremente");
    }

    @Test
    void takesOverWhenAFastMoveJumpsOverTheValue() {
        ControllerPickup pickup = new ControllerPickup();

        // Um giro rápido pode pular o alvo entre duas mensagens, sem nunca cair perto dele.
        // Sem esta regra o controle ficaria preso, sem nunca assumir.
        assertFalse(pickup.accepts(0.90, 0.50));
        assertTrue(pickup.accepts(0.10, 0.50), "cruzar o alvo também assume o controle");
    }

    @Test
    void acceptsWhenTheControllerIsAlreadyNextToTheValue() {
        ControllerPickup pickup = new ControllerPickup();

        // Dentro da tolerância na primeira mensagem: não faz sentido exigir movimento.
        assertTrue(pickup.accepts(0.50 + ControllerPickup.THRESHOLD / 2, 0.50));
    }

    @Test
    void requiresReachingTheValueAgainAfterBeingArmed() {
        ControllerPickup pickup = new ControllerPickup();
        pickup.accepts(0.5, 0.5);
        assertTrue(pickup.accepts(0.7, 0.7));

        // O valor mudou pelo mouse: o knob perde o comando até reencontrá-lo.
        pickup.arm();
        assertTrue(pickup.isArmed());
        assertFalse(pickup.accepts(0.7, 0.2), "knob longe do novo valor não pode saltar");
        assertTrue(pickup.accepts(0.2, 0.2));
    }

    @Test
    void startsArmedSoTheFirstTouchNeverJumps() {
        assertTrue(new ControllerPickup().isArmed());
    }

    @Test
    void forgetsThePreviousPositionWhenArmed() {
        ControllerPickup pickup = new ControllerPickup();
        pickup.accepts(0.90, 0.90);

        // Trocar de banco muda o alvo com o knob parado no alto. Se a posição anterior
        // sobrevivesse, a primeira mensagem seria lida como um cruzamento e o fader
        // saltaria sem que o controle tivesse se movido.
        pickup.arm();
        assertFalse(pickup.accepts(0.10, 0.50));
    }

    @Test
    void createsOneIndependentPickupPerControl() {
        ControllerPickup[] pickups = ControllerPickup.array(3);

        pickups[0].accepts(0.5, 0.5);
        assertFalse(pickups[0].isArmed());
        assertTrue(pickups[1].isArmed(), "um controle assumir não libera os outros");
        assertTrue(pickups[2].isArmed());
    }
}
