package br.com.supermidia.app;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Garante uma única cópia do player, e entrega a ela os arquivos clicados no Explorer.
 *
 * <p>Sem isso, cada clique em um MIDI abre um processo novo, e as cópias disputam o
 * dispositivo de saída — o Windows devolve {@code Undefined external error} para a
 * segunda, que fica muda.</p>
 *
 * <p>O combinado é uma porta na interface de loopback, inacessível de fora da máquina.
 * Quem consegue reservá-la é a cópia dona; quem não consegue manda os caminhos para lá
 * e encerra. Se a porta estiver ocupada por um programa alheio, o diálogo não se completa
 * e o player abre normalmente: duas cópias incomodam menos do que um player que se
 * recusa a abrir.</p>
 */
public final class SingleInstance {
    /** Porta alta e fixa, fora das faixas que o Windows entrega a sockets efêmeros. */
    private static final int PORT = 50_505;
    /** Primeira linha do diálogo. Prova que do outro lado está o player, e não outro programa. */
    private static final String HANDSHAKE = "SUPERMIDIA-MIDI-PLAYER-1";
    private static final String ACCEPTED = "OK";
    private static final int HANDOFF_TIMEOUT_MILLIS = 2_000;

    private static ServerSocket server;

    /** Protege {@link #consumer} e {@link #pending} entre a thread de atendimento e a da interface. */
    private static final Object GATE = new Object();
    /**
     * Caminhos que chegaram antes de a janela existir.
     *
     * <p>A porta é reservada no {@code main}, mas a interface só fica pronta alguns
     * segundos depois, enquanto o JavaFX carrega. Quem clicar num arquivo nesse intervalo
     * seria atendido e ignorado; aqui o caminho espera e é entregue quando a janela abre.</p>
     */
    private static final List<String> pending = new ArrayList<>();
    private static Consumer<String> consumer;

    private SingleInstance() {
    }

    /**
     * Tenta virar a cópia dona reservando a porta.
     *
     * @return {@code true} se conseguiu, {@code false} se outra já a tem
     */
    public static boolean acquire() {
        try {
            server = new ServerSocket(PORT, 8, InetAddress.getLoopbackAddress());
        } catch (IOException exception) {
            return false;
        }
        // Atender começa agora, não quando a janela abrir: reservar a porta sem responder
        // faria a outra cópia desistir por tempo e abrir uma segunda janela.
        startAttendant(server);
        return true;
    }

    private static void startAttendant(ServerSocket listener) {
        Thread attendant = new Thread(() -> {
            while (!listener.isClosed()) {
                try (Socket socket = listener.accept()) {
                    serve(socket);
                } catch (IOException exception) {
                    // Conexão malformada ou socket fechado no encerramento: nada a fazer.
                    if (listener.isClosed()) {
                        return;
                    }
                }
            }
        }, "supermidia-instancia-unica");
        attendant.setDaemon(true);
        attendant.start();
    }

    private static void deliver(String path) {
        Consumer<String> target;
        synchronized (GATE) {
            if (consumer == null) {
                pending.add(path);
                return;
            }
            target = consumer;
        }
        target.accept(path);
    }

    /**
     * Entrega os caminhos para a cópia que já está rodando.
     *
     * @return {@code true} se a outra cópia confirmou o recebimento; {@code false} se não
     *         houve resposta válida, caso em que este processo deve abrir normalmente
     */
    public static boolean handOff(String[] arguments) {
        try (Socket socket = new Socket()) {
            socket.connect(
                    new java.net.InetSocketAddress(InetAddress.getLoopbackAddress(), PORT),
                    HANDOFF_TIMEOUT_MILLIS);
            socket.setSoTimeout(HANDOFF_TIMEOUT_MILLIS);

            Writer writer = new OutputStreamWriter(
                    socket.getOutputStream(), StandardCharsets.UTF_8);
            writer.write(HANDSHAKE);
            writer.write('\n');
            for (String argument : arguments) {
                // Um caminho por linha: nenhum caminho do Windows contém quebra de linha.
                writer.write(argument.replace('\n', ' ').replace('\r', ' '));
                writer.write('\n');
            }
            writer.flush();
            socket.shutdownOutput();

            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    socket.getInputStream(), StandardCharsets.UTF_8));
            return ACCEPTED.equals(reader.readLine());
        } catch (IOException exception) {
            return false;
        }
    }

    /**
     * Liga a entrega à interface e esvazia o que chegou antes de ela existir.
     *
     * <p>O consumidor é chamado na thread de atendimento, não na da interface: quem usa
     * precisa devolver o trabalho ao JavaFX.</p>
     */
    public static void listen(Consumer<String> newConsumer) {
        List<String> queued;
        synchronized (GATE) {
            consumer = newConsumer;
            queued = new ArrayList<>(pending);
            pending.clear();
        }
        for (String path : queued) {
            newConsumer.accept(path);
        }
    }

    private static void serve(Socket socket) throws IOException {
        socket.setSoTimeout(HANDOFF_TIMEOUT_MILLIS);
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                socket.getInputStream(), StandardCharsets.UTF_8));
        if (!HANDSHAKE.equals(reader.readLine())) {
            return;
        }

        Writer writer = new OutputStreamWriter(
                socket.getOutputStream(), StandardCharsets.UTF_8);
        writer.write(ACCEPTED);
        writer.write('\n');
        writer.flush();

        String line;
        while ((line = reader.readLine()) != null) {
            if (!line.isBlank()) {
                deliver(line);
            }
        }
    }

    /** Solta a porta. Chamado no encerramento. */
    public static void release() {
        if (server != null) {
            try {
                server.close();
            } catch (IOException exception) {
                // Encerrando de qualquer forma.
            }
            server = null;
        }
    }
}
