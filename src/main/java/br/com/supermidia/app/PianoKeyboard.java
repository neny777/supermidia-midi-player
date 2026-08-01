package br.com.supermidia.app;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

/**
 * Teclado de 88 teclas que acende as notas em execução.
 *
 * <p>Não é um instrumento: não se toca nele, não responde a cliques. Serve para
 * enxergar a harmonia que está soando e identificar o acorde de relance — o que
 * um reconhecimento automático faria com margem de erro, já que as mesmas notas
 * podem nomear acordes diferentes conforme o contexto.</p>
 *
 * <p>Desenha em {@link Canvas} porque são 88 formas redesenhadas várias vezes por
 * segundo; uma árvore de nós equivalente custaria muito mais para o mesmo efeito.</p>
 */
public final class PianoKeyboard extends Canvas {
    /** Lá 0, a tecla mais grave de um piano de 88 teclas. */
    private static final int FIRST_NOTE = 21;
    /** Dó 8, a mais aguda. */
    private static final int LAST_NOTE = 108;
    private static final int SEMITONES_PER_OCTAVE = 12;

    /**
     * Quantas vezes a tecla branca é mais comprida que larga.
     *
     * <p>Num piano de verdade ela mede cerca de 23 mm por 145 mm. Sem respeitar essa
     * proporção o teclado é esticado até preencher a área e as teclas parecem estreitas
     * demais, atrapalhando justamente o reconhecimento visual que a tela existe para dar.</p>
     */
    private static final double WHITE_KEY_ASPECT = 6.0;
    /** Largura da tecla preta em relação à branca: perto de 9,5 mm contra 23 mm. */
    private static final double BLACK_KEY_WIDTH_RATIO = 0.58;
    /** Comprimento da preta em relação à branca: cerca de 95 mm contra 145 mm. */
    private static final double BLACK_KEY_HEIGHT_RATIO = 0.65;
    private static final double LABEL_MIN_KEY_WIDTH = 13;

    private static final Color BACKGROUND = Color.web("#0c131d");
    private static final Color WHITE_KEY = Color.web("#e6ebf2");
    private static final Color WHITE_KEY_EDGE = Color.web("#9aa5b4");
    private static final Color BLACK_KEY = Color.web("#151d29");
    private static final Color BLACK_KEY_EDGE = Color.web("#000000");
    private static final Color ACTIVE_KEY = Color.web("#f1613a");
    private static final Color ACTIVE_KEY_EDGE = Color.web("#ffa07f");
    private static final Color OCTAVE_LABEL = Color.web("#6b7684");

    private boolean[] soundingNotes = new boolean[128];

    public PianoKeyboard() {
        widthProperty().addListener(ignored -> draw());
        heightProperty().addListener(ignored -> draw());
    }

    /**
     * Atualiza as notas acesas.
     *
     * <p>Redesenha apenas quando algo mudou: o método é chamado dezenas de vezes por
     * segundo e, na maior parte delas, o acorde é o mesmo do quadro anterior.</p>
     */
    public void setSoundingNotes(boolean[] notes) {
        if (java.util.Arrays.equals(soundingNotes, notes)) {
            return;
        }
        soundingNotes = notes.clone();
        draw();
    }

    @Override
    public boolean isResizable() {
        return true;
    }

    // Canvas não é um Region: o layout descobre o tamanho que ele aceita por estes
    // métodos. Sem declarar um máximo ilimitado, o contêiner conclui que o Canvas quer
    // ter o tamanho de sua largura atual — zero, no primeiro layout — e o teclado nunca
    // chega a aparecer.
    @Override
    public double minWidth(double height) {
        return 0;
    }

    @Override
    public double minHeight(double width) {
        return 0;
    }

    @Override
    public double maxWidth(double height) {
        return Double.MAX_VALUE;
    }

    @Override
    public double maxHeight(double width) {
        return Double.MAX_VALUE;
    }

    @Override
    public double prefWidth(double height) {
        return getWidth();
    }

    @Override
    public double prefHeight(double width) {
        return getHeight();
    }

    @Override
    public void resize(double width, double height) {
        setWidth(width);
        setHeight(height);
        draw();
    }

    private void draw() {
        double areaWidth = getWidth();
        double areaHeight = getHeight();
        if (areaWidth <= 0 || areaHeight <= 0) {
            return;
        }

        GraphicsContext gc = getGraphicsContext2D();
        gc.setFill(BACKGROUND);
        gc.fillRect(0, 0, areaWidth, areaHeight);

        // O teclado ocupa o máximo possível SEM deformar a tecla: primeiro tenta usar
        // toda a largura, e recua para o limite de altura se ficar comprido demais.
        int whiteKeyCount = countWhiteKeys();
        double whiteKeyWidth = areaWidth / whiteKeyCount;
        double keyboardHeight = whiteKeyWidth * WHITE_KEY_ASPECT;
        if (keyboardHeight > areaHeight) {
            keyboardHeight = areaHeight;
            whiteKeyWidth = keyboardHeight / WHITE_KEY_ASPECT;
        }
        double keyboardWidth = whiteKeyWidth * whiteKeyCount;

        // Centralizado, para a sobra se distribuir em vez de acumular de um lado só.
        double offsetX = (areaWidth - keyboardWidth) / 2;
        double offsetY = (areaHeight - keyboardHeight) / 2;

        drawWhiteKeys(gc, offsetX, offsetY, whiteKeyWidth, keyboardHeight);
        // As pretas vão por cima: elas se sobrepõem às brancas vizinhas.
        drawBlackKeys(gc, offsetX, offsetY, whiteKeyWidth, keyboardHeight);
    }

    private void drawWhiteKeys(GraphicsContext gc, double offsetX, double offsetY,
                               double keyWidth, double height) {
        int whiteIndex = 0;
        for (int note = FIRST_NOTE; note <= LAST_NOTE; note++) {
            if (!isWhiteKey(note)) {
                continue;
            }
            double x = offsetX + whiteIndex * keyWidth;
            boolean active = soundingNotes[note];
            gc.setFill(active ? ACTIVE_KEY : WHITE_KEY);
            gc.fillRect(x, offsetY, keyWidth, height);
            gc.setStroke(active ? ACTIVE_KEY_EDGE : WHITE_KEY_EDGE);
            gc.setLineWidth(1);
            gc.strokeRect(x + 0.5, offsetY + 0.5, keyWidth - 1, height - 1);

            if (isC(note) && keyWidth >= LABEL_MIN_KEY_WIDTH) {
                drawOctaveLabel(gc, note, x, offsetY, keyWidth, height);
            }
            whiteIndex++;
        }
    }

    private void drawBlackKeys(GraphicsContext gc, double offsetX, double offsetY,
                               double whiteKeyWidth, double height) {
        double keyWidth = whiteKeyWidth * BLACK_KEY_WIDTH_RATIO;
        double keyHeight = height * BLACK_KEY_HEIGHT_RATIO;
        int whiteIndex = 0;
        for (int note = FIRST_NOTE; note <= LAST_NOTE; note++) {
            if (isWhiteKey(note)) {
                whiteIndex++;
                continue;
            }
            // A preta fica a cavaleiro da divisa entre a branca anterior e a seguinte.
            double x = offsetX + whiteIndex * whiteKeyWidth - keyWidth / 2;
            boolean active = soundingNotes[note];
            gc.setFill(active ? ACTIVE_KEY : BLACK_KEY);
            gc.fillRect(x, offsetY, keyWidth, keyHeight);
            gc.setStroke(active ? ACTIVE_KEY_EDGE : BLACK_KEY_EDGE);
            gc.setLineWidth(1);
            gc.strokeRect(x + 0.5, offsetY + 0.5, keyWidth - 1, keyHeight - 1);
        }
    }

    /** Marca os dós para servir de referência ao percorrer o teclado com os olhos. */
    private void drawOctaveLabel(GraphicsContext gc, int note, double x, double offsetY,
                                 double keyWidth, double height) {
        gc.setFill(OCTAVE_LABEL);
        gc.setFont(Font.font(Math.min(11, keyWidth * 0.62)));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText("C" + octaveOf(note), x + keyWidth / 2, offsetY + height - 6);
        gc.setTextAlign(TextAlignment.LEFT);
    }

    /**
     * Altura que o teclado precisa para caber nesta largura sem deformar a tecla.
     *
     * <p>Existe para o contêiner poder se limitar a esse tamanho. Sem isso ele reserva
     * toda a altura disponível e o teclado, desenhado na proporção certa, fica cercado
     * de vazio.</p>
     */
    public static double idealHeightFor(double width) {
        return width / countWhiteKeys() * WHITE_KEY_ASPECT;
    }

    private static int countWhiteKeys() {
        int total = 0;
        for (int note = FIRST_NOTE; note <= LAST_NOTE; note++) {
            if (isWhiteKey(note)) {
                total++;
            }
        }
        return total;
    }

    static boolean isWhiteKey(int note) {
        return switch (note % SEMITONES_PER_OCTAVE) {
            case 1, 3, 6, 8, 10 -> false;
            default -> true;
        };
    }

    private static boolean isC(int note) {
        return note % SEMITONES_PER_OCTAVE == 0;
    }

    /** Convenção científica: o dó central (nota 60) é o C4. */
    private static int octaveOf(int note) {
        return note / SEMITONES_PER_OCTAVE - 1;
    }
}
