package br.com.supermidia.midi;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Conduz o mapeamento da controladora ação por ação, em uma passada só.
 *
 * <p>A cada mensagem aceita a sessão avança para a próxima ação, evitando o ciclo
 * manual de escolher a função, clicar em Aprender e repetir dezenas de vezes.</p>
 */
public final class MidiMappingSession {
    private final List<MidiLearnAction> actions;
    private final Map<String, MidiBinding> learned = new LinkedHashMap<>();
    private int index;

    public MidiMappingSession(List<MidiLearnAction> actions) {
        Objects.requireNonNull(actions, "actions");
        if (actions.isEmpty()) {
            throw new IllegalArgumentException("A sessão precisa de pelo menos uma ação.");
        }
        this.actions = List.copyOf(actions);
    }

    public boolean isFinished() {
        return index >= actions.size();
    }

    public Optional<MidiLearnAction> current() {
        return isFinished() ? Optional.empty() : Optional.of(actions.get(index));
    }

    /** Posição atual, começando em 1. Igual ao total quando a sessão termina. */
    public int position() {
        return Math.min(index + 1, actions.size());
    }

    public int total() {
        return actions.size();
    }

    public int mappedCount() {
        return learned.size();
    }

    public boolean isAtFirstAction() {
        return index == 0;
    }

    /**
     * Tenta usar a mensagem recebida como vínculo da ação atual.
     *
     * @return o vínculo aprendido, ou vazio quando a mensagem não serve para esta ação
     *         (por exemplo, um Note On em uma ação contínua).
     */
    public Optional<MidiBinding> submit(MidiControlMessage message) {
        Optional<MidiLearnAction> action = current();
        if (action.isEmpty()) {
            return Optional.empty();
        }

        Optional<MidiBinding> binding = MidiBinding.learn(message, action.get().kind().isContinuous());
        if (binding.isEmpty()) {
            return Optional.empty();
        }

        MidiBinding value = binding.get();
        String actionId = action.get().id();
        learned.entrySet().removeIf(entry ->
                !entry.getKey().equals(actionId) && entry.getValue().equals(value));
        learned.put(actionId, value);
        index++;
        return binding;
    }

    /** Deixa a ação atual sem vínculo e avança. */
    public void skip() {
        if (!isFinished()) {
            index++;
        }
    }

    /** Volta uma ação e descarta o vínculo que havia sido aprendido para ela. */
    public void back() {
        if (index == 0) {
            return;
        }
        index--;
        learned.remove(actions.get(index).id());
    }

    /** Vínculos aprendidos até aqui, na ordem em que foram definidos. */
    public Map<String, MidiBinding> result() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(learned));
    }

    public ControllerProfile toProfile(String name) {
        return new ControllerProfile(name, learned);
    }
}
