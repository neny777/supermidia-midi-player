package br.com.supermidia.app;

import br.com.supermidia.core.PlaybackMode;
import br.com.supermidia.lyrics.LyricLine;
import br.com.supermidia.lyrics.MidiLyrics;
import br.com.supermidia.midi.ControllerProfile;
import br.com.supermidia.midi.MidiBinding;
import br.com.supermidia.midi.MidiControlMessage;
import br.com.supermidia.midi.MidiInputDevice;
import br.com.supermidia.midi.MidiInputDiagnostics;
import br.com.supermidia.midi.MidiInputMonitor;
import br.com.supermidia.midi.MidiLearnAction;
import br.com.supermidia.midi.MidiMappingSession;
import br.com.supermidia.midi.MidiOutputDevice;
import br.com.supermidia.midi.MidiPlaybackEngine;
import br.com.supermidia.midi.SmcMixerLayout;
import br.com.supermidia.mixer.MidiChannelInfo;
import br.com.supermidia.mixer.MidiSongAnalysis;
import br.com.supermidia.playlist.PlaylistFileService;
import br.com.supermidia.playlist.PlaylistItem;
import br.com.supermidia.playlist.PlaylistManager;
import javafx.animation.KeyFrame;
import javafx.animation.Interpolator;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;
import javafx.scene.control.Slider;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.Scene;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.ShortMessage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.IntFunction;
import java.util.prefs.Preferences;

public final class MainController {
    /** Recorte de funções que o assistente de mapeamento percorre em uma sessão. */
    public enum MappingScope {
        ALL("Tudo · transporte, gerais e mixer"),
        TRANSPORT_AND_GLOBAL("Só transporte e controles gerais"),
        MIXER("Só o mixer · faders, mute, solo e banco");

        private final String label;

        MappingScope(String label) {
            this.label = label;
        }

        private boolean matches(MidiLearnAction action) {
            boolean mixerAction = switch (action.kind()) {
                case BANK_PREVIOUS, BANK_NEXT, BANK_TOGGLE,
                     BANK_VOLUME, BANK_MUTE, BANK_SOLO -> true;
                default -> false;
            };
            return switch (this) {
                case ALL -> true;
                case MIXER -> mixerAction;
                case TRANSPORT_AND_GLOBAL -> !mixerAction;
            };
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static final int MIDI_CHANNEL_COUNT = 16;
    private static final int MIXER_BANK_SIZE = 8;
    private static final Duration LYRIC_SCROLL_DURATION = Duration.millis(260);
    private static final String AUTOPLAY_PREFERENCE_KEY = "autoplayEnabled";
    private static final String MIDI_INPUT_PREFERENCE_KEY = "midiInputDevice";
    private static final String MIDI_OUTPUT_PREFERENCE_KEY = "midiOutputDevice";
    private static final String MIDI_BINDING_PREFERENCE_PREFIX = "midiBinding.";
    private static final long MIDI_INPUT_ACTIVITY_TIMEOUT_NANOS = 350_000_000L;
    private static final long MIDI_LEARN_RELEASE_TIMEOUT_NANOS = 400_000_000L;
    private static final long MAPPING_COOLDOWN_NANOS = 700_000_000L;
    private static final double MIXER_PICKUP_THRESHOLD = 2.5 / 127.0;
    @FXML
    private Label currentSongLabel;
    @FXML
    private Label nextSongLabel;
    @FXML
    private Label stageLyricsStatusLabel;
    @FXML
    private Label stagePreviousLyricLabel;
    @FXML
    private Label stageCurrentLyricLabel;
    @FXML
    private Label stageNextLyricLabel;
    @FXML
    private Label stageIncomingLyricLabel;
    @FXML
    private Label statusLabel;
    @FXML
    private Label currentTimeLabel;
    @FXML
    private Label durationTimeLabel;
    @FXML
    private Label midiOutputStatusLabel;
    @FXML
    private Label midiInputConnectionDot;
    @FXML
    private Label midiInputStatusLabel;
    @FXML
    private Label midiInputMonitorLabel;
    @FXML
    private Label midiLearnStatusLabel;
    @FXML
    private Label midiDiagnosticsSummaryLabel;
    @FXML
    private Label mappingWizardProgressLabel;
    @FXML
    private Label mappingWizardActionLabel;
    @FXML
    private Label controllerProfileLabel;
    @FXML
    private Label playlistFileLabel;
    @FXML
    private Label playlistCountLabel;
    @FXML
    private Label chordValueLabel;

    @FXML
    private ComboBox<MidiOutputDevice> midiOutputComboBox;
    @FXML
    private ComboBox<MidiInputDevice> midiInputComboBox;
    @FXML
    private ComboBox<MidiLearnAction> midiLearnActionComboBox;
    @FXML
    private ComboBox<MappingScope> mappingWizardScopeComboBox;
    @FXML
    private ListView<PlaylistItem> playlistListView;

    @FXML
    private ToggleButton automaticModeToggle;
    @FXML
    private ToggleButton presentationNavButton;
    @FXML
    private ToggleButton playlistNavButton;
    @FXML
    private ToggleButton settingsNavButton;

    @FXML
    private Slider transposeSlider;
    @FXML
    private Slider speedSlider;
    @FXML
    private Slider masterVolumeSlider;
    @FXML
    private MenuButton transposeMenuButton;
    @FXML
    private MenuButton speedMenuButton;
    @FXML
    private MenuButton masterVolumeMenuButton;
    @FXML
    private Slider progressSlider;

    @FXML
    private Button playButton;
    @FXML
    private Button pauseButton;
    @FXML
    private Button stopButton;
    @FXML
    private Button previousButton;
    @FXML
    private Button nextButton;
    @FXML
    private Button panicButton;
    @FXML
    private Button savePlaylistButton;
    @FXML
    private Button moveUpButton;
    @FXML
    private Button moveDownButton;
    @FXML
    private Button removePlaylistButton;
    @FXML
    private Button midiLearnButton;
    @FXML
    private Button midiLearnClearButton;
    @FXML
    private Button mappingWizardStartButton;
    @FXML
    private Button mappingWizardSkipButton;
    @FXML
    private Button mappingWizardBackButton;

    @FXML
    private VBox presentationView;
    @FXML
    private VBox playlistView;
    @FXML
    private VBox settingsView;
    @FXML
    private StackPane lyricsViewport;
    @FXML
    private VBox lyricsLinesContainer;
    @FXML
    private HBox mixerBankOneContainer;
    @FXML
    private HBox mixerBankTwoContainer;
    @FXML
    private VBox mixerBankOnePanel;
    @FXML
    private VBox mixerBankTwoPanel;

    private final PlaylistManager playlist = new PlaylistManager();
    private final PlaylistFileService playlistFileService = new PlaylistFileService();
    private final Preferences preferences = Preferences.userNodeForPackage(MainController.class);
    private final ConcurrentLinkedQueue<MidiControlMessage> pendingMidiInputMessages =
            new ConcurrentLinkedQueue<>();
    private final MidiInputMonitor midiInputMonitor =
            new MidiInputMonitor(pendingMidiInputMessages::offer);
    private final List<MidiLearnAction> midiLearnActions = MidiLearnAction.defaultActions();
    private final Map<String, MidiBinding> midiBindings = new HashMap<>();
    private final MidiInputDiagnostics midiDiagnostics = new MidiInputDiagnostics();
    private final VBox[] mixerChannelStrips = new VBox[MIDI_CHANNEL_COUNT];
    private final Slider[] channelVolumeSliders = new Slider[MIDI_CHANNEL_COUNT];
    private final Label[] channelVolumeLabels = new Label[MIDI_CHANNEL_COUNT];
    private final Label[] channelInstrumentLabels = new Label[MIDI_CHANNEL_COUNT];
    private final Label[] channelActivityLabels = new Label[MIDI_CHANNEL_COUNT];
    private final ToggleButton[] channelMuteButtons = new ToggleButton[MIDI_CHANNEL_COUNT];
    private final ToggleButton[] channelSoloButtons = new ToggleButton[MIDI_CHANNEL_COUNT];

    private PlaybackMode playbackMode = PlaybackMode.MANUAL;
    private MidiPlaybackEngine engine;
    private Timeline progressTimeline;
    private TranslateTransition lyricScrollTransition;
    private Path currentPlaylistFile;
    private MidiLyrics currentLyrics = MidiLyrics.empty();
    private MidiSongAnalysis currentSongAnalysis = MidiSongAnalysis.empty();
    private int displayedLyricIndex = Integer.MIN_VALUE;
    private boolean updatingMixerControls;
    private boolean playlistDirty;
    private boolean refreshingOutputs;
    private boolean refreshingInputs;
    private long lastMidiInputActivityNanos;
    private MidiLearnAction learningMidiAction;
    private MidiBinding recentlyLearnedContinuousBinding;
    private long recentlyLearnedBindingReleaseNanos;
    private MidiMappingSession mappingSession;
    private MidiBinding mappingCooldownBinding;
    private long mappingCooldownReleaseNanos;
    private int activeMixerBank;
    private final boolean[] mixerPickupArmed = new boolean[MIXER_BANK_SIZE];
    private final boolean[] mixerControllerValueKnown = new boolean[MIXER_BANK_SIZE];
    private final double[] mixerLastControllerValue = new double[MIXER_BANK_SIZE];
    private Stage pianoStage;

    @FXML
    private void initialize() {
        configureControlListeners();
        configurePlaylistView();
        configureMixerView();
        configureMidiLearn();
        configureLyricsViewport();
        setLyricLabels("", "", "");
        playbackMode = loadPlaybackModePreference();
        refreshPlaybackMode();
        refreshPlaylistView(-1);

        try {
            engine = new MidiPlaybackEngine();
            engine.setTranspose((int) Math.round(transposeSlider.getValue()));
            engine.setTempoFactor((float) (speedSlider.getValue() / 100.0));
            engine.setMasterVolume(masterVolumeSlider.getValue() / 100.0);
            engine.setPlaybackFinishedHandler(
                    () -> Platform.runLater(this::handlePlaybackFinished));
            refreshMidiOutputs();
        } catch (MidiUnavailableException exception) {
            statusLabel.setText("MIDI INDISPONÍVEL");
            midiOutputStatusLabel.setText("Sequenciador MIDI não encontrado");
            disableMidiControls();
        }
        startProgressUpdates();
        refreshMidiInputs();
        updateTransportControls();
    }

    public void shutdown() {
        stopLyricScrollAnimation();
        midiInputMonitor.close();
        if (progressTimeline != null) {
            progressTimeline.stop();
        }
        if (engine != null) {
            engine.close();
        }
    }

    public boolean confirmClose() {
        if (!playlistDirty || playlist.isEmpty()) {
            return true;
        }
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                "A playlist possui alterações não salvas. Deseja fechar mesmo assim?",
                ButtonType.OK, ButtonType.CANCEL);
        confirmation.setTitle("SuperMídia MIDI Player");
        confirmation.setHeaderText("Fechar o aplicativo");
        confirmation.initOwner(currentSongLabel.getScene().getWindow());
        return confirmation.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    @FXML
    private void handleShowPresentation() {
        showPresentation();
    }

    @FXML
    private void handleShowPlaylist() {
        presentationView.setVisible(false);
        presentationView.setManaged(false);
        settingsView.setVisible(false);
        settingsView.setManaged(false);
        playlistView.setVisible(true);
        playlistView.setManaged(true);
        playlistNavButton.setSelected(true);
    }

    @FXML
    private void handleShowSettings() {
        presentationView.setVisible(false);
        presentationView.setManaged(false);
        playlistView.setVisible(false);
        playlistView.setManaged(false);
        settingsView.setVisible(true);
        settingsView.setManaged(true);
        settingsNavButton.setSelected(true);
    }

    @FXML
    private void handleOpenPiano() {
        if (pianoStage == null) {
            Label title = new Label("Piano");
            title.getStyleClass().add("page-title");
            Label message = new Label(
                    "A visualização das notas ativas será adicionada na próxima etapa.");
            message.getStyleClass().add("muted-label");
            message.setWrapText(true);

            VBox content = new VBox(12, title, message);
            content.setAlignment(Pos.CENTER);
            content.getStyleClass().addAll("app-shell", "piano-placeholder");

            Scene scene = new Scene(content, 760, 260);
            scene.getStylesheets().add(
                    getClass().getResource("theme.css").toExternalForm());

            pianoStage = new Stage();
            pianoStage.setTitle("Piano — SuperMídia MIDI Player");
            pianoStage.initOwner(currentSongLabel.getScene().getWindow());
            pianoStage.setScene(scene);
            pianoStage.setMinWidth(520);
            pianoStage.setMinHeight(220);
            pianoStage.setOnHidden(ignored -> pianoStage = null);
        }
        pianoStage.show();
        pianoStage.toFront();
    }

    @FXML
    private void handleShowAbout() {
        Alert about = new Alert(Alert.AlertType.INFORMATION);
        about.setTitle("Sobre o SuperMídia MIDI Player");
        about.setHeaderText("SuperMídia MIDI Player · versão 0.1.0");
        about.setContentText("Player MIDI desenvolvido para apresentações ao vivo.\n\n"
                + "SuperMídia Alfenas\n\n"
                + "Desenvolvedor: Denis Antonio Rocha\n"
                + "Projeto: https://github.com/neny777/supermidia-midi-player\n\n"
                + "Tipografia Source Sans 3 · licença OFL 1.1");
        about.initOwner(currentSongLabel.getScene().getWindow());
        about.showAndWait();
    }

    @FXML
    private void handleAddPlaylistItems() {
        FileChooser chooser = midiFileChooser("Adicionar músicas à playlist");
        setInitialDirectory(chooser);
        List<File> selectedFiles = chooser.showOpenMultipleDialog(playlistListView.getScene().getWindow());
        if (selectedFiles == null || selectedFiles.isEmpty()) {
            return;
        }

        int previousSize = playlist.size();
        for (File file : selectedFiles) {
            playlist.add(file.toPath());
        }
        if (playlist.size() != previousSize) {
            markPlaylistModified();
        }

        if (playlist.currentIndex() < 0 && !playlist.isEmpty()) {
            loadPlaylistIndex(0);
        }
        refreshPlaylistView(playlistListView.getSelectionModel().getSelectedIndex());
    }

    @FXML
    private void handleOpenPlaylist() {
        if (!confirmDiscardPlaylistChanges()) {
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Abrir playlist");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Playlists M3U", "*.m3u8", "*.m3u", "*.M3U8", "*.M3U"));
        setInitialDirectory(chooser);
        File selectedFile = chooser.showOpenDialog(playlistListView.getScene().getWindow());
        if (selectedFile == null) {
            return;
        }

        try {
            List<PlaylistItem> loadedItems = playlistFileService.load(selectedFile.toPath());
            resetLoadedSong();
            playlist.replaceAll(loadedItems);
            currentPlaylistFile = selectedFile.toPath().toAbsolutePath().normalize();
            playlistDirty = false;

            int firstPlayable = firstExistingItemIndex();
            if (firstPlayable >= 0) {
                loadPlaylistIndex(firstPlayable);
            }
            refreshPlaylistView(firstPlayable);
        } catch (IOException | RuntimeException exception) {
            showError("Não foi possível abrir a playlist",
                    "O arquivo selecionado não pôde ser lido.", exception);
        }
    }

    @FXML
    private void handleSavePlaylist() {
        if (playlist.isEmpty()) {
            return;
        }
        Path destination = currentPlaylistFile;
        if (destination == null) {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Salvar playlist");
            chooser.setInitialFileName("repertorio.m3u8");
            chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("Playlist M3U8", "*.m3u8"));
            setInitialDirectory(chooser);
            File selectedFile = chooser.showSaveDialog(playlistListView.getScene().getWindow());
            if (selectedFile == null) {
                return;
            }
            destination = ensureM3u8Extension(selectedFile.toPath());
        }

        try {
            playlistFileService.save(destination, playlist.items());
            currentPlaylistFile = destination.toAbsolutePath().normalize();
            playlistDirty = false;
            updatePlaylistFileLabel();
        } catch (IOException exception) {
            showError("Não foi possível salvar a playlist",
                    "Verifique se a pasta permite gravação.", exception);
        }
    }

    @FXML
    private void handleRemovePlaylistItem() {
        int selectedIndex = playlistListView.getSelectionModel().getSelectedIndex();
        if (selectedIndex < 0) {
            return;
        }
        boolean removedCurrent = playlist.removeAt(selectedIndex);
        markPlaylistModified();

        if (removedCurrent) {
            resetLoadedSong();
            if (playlist.currentIndex() >= 0) {
                loadPlaylistIndex(playlist.currentIndex());
            }
        }
        int nextSelection = playlist.isEmpty() ? -1 : Math.min(selectedIndex, playlist.size() - 1);
        refreshPlaylistView(nextSelection);
    }

    @FXML
    private void handleMovePlaylistItemUp() {
        int selectedIndex = playlistListView.getSelectionModel().getSelectedIndex();
        if (selectedIndex > 0) {
            int newIndex = playlist.moveUp(selectedIndex);
            markPlaylistModified();
            refreshPlaylistView(newIndex);
        }
    }

    @FXML
    private void handleMovePlaylistItemDown() {
        int selectedIndex = playlistListView.getSelectionModel().getSelectedIndex();
        if (selectedIndex >= 0 && selectedIndex < playlist.size() - 1) {
            int newIndex = playlist.moveDown(selectedIndex);
            markPlaylistModified();
            refreshPlaylistView(newIndex);
        }
    }

    @FXML
    private void handlePlaylistClick(MouseEvent event) {
        if (event.getClickCount() == 2) {
            int selectedIndex = playlistListView.getSelectionModel().getSelectedIndex();
            if (selectedIndex >= 0 && loadPlaylistIndex(selectedIndex)) {
                showPresentation();
            }
        }
    }

    @FXML
    private void handlePrevious() {
        if (playlist.hasPrevious()) {
            loadPlaylistIndex(playlist.currentIndex() - 1);
        }
    }

    @FXML
    private void handleNext() {
        if (playlist.hasNext()) {
            loadPlaylistIndex(playlist.currentIndex() + 1);
        }
    }

    @FXML
    private void handleRefreshMidiOutputs() {
        refreshMidiOutputs();
    }

    @FXML
    private void handleRefreshMidiInputs() {
        refreshMidiInputs();
    }

    @FXML
    private void handleMidiInputSelection() {
        if (refreshingInputs) {
            return;
        }

        MidiInputDevice selection = midiInputComboBox.getSelectionModel().getSelectedItem();
        if (selection == null) {
            selection = MidiInputDevice.none();
        }

        try {
            midiInputMonitor.selectInput(selection);
            pendingMidiInputMessages.clear();
            if (selection.isNone()) {
                removePreference(MIDI_INPUT_PREFERENCE_KEY);
                midiInputStatusLabel.setText("Entrada: nenhuma");
                midiInputStatusLabel.setTooltip(null);
                midiInputMonitorLabel.setText("Nenhum dispositivo de entrada selecionado.");
                setMidiInputConnected(false);
            } else {
                savePreference(MIDI_INPUT_PREFERENCE_KEY, selection.name());
                midiInputStatusLabel.setText("Entrada: " + selection.name());
                midiInputStatusLabel.setTooltip(new Tooltip("Entrada MIDI: " + selection.name()));
                midiInputMonitorLabel.setText("Conectada. Aguardando mensagens MIDI.");
                setMidiInputConnected(true);
            }
        } catch (MidiUnavailableException exception) {
            selectNoMidiInput();
            removePreference(MIDI_INPUT_PREFERENCE_KEY);
            midiInputStatusLabel.setText("Entrada: falha de conexão");
            midiInputStatusLabel.setTooltip(null);
            midiInputMonitorLabel.setText("Não foi possível abrir o dispositivo de entrada.");
            setMidiInputConnected(false);
            showError("Entrada MIDI indisponível",
                    "O dispositivo pode estar sendo usado por outro programa.", exception);
        }
    }

    @FXML
    private void handleStartMidiLearn() {
        if (learningMidiAction != null) {
            learningMidiAction = null;
            midiLearnButton.setText("Aprender");
            refreshMidiLearnStatus();
            return;
        }
        if (!midiInputMonitor.hasInput()) {
            midiLearnStatusLabel.setText("Selecione e conecte uma entrada MIDI primeiro.");
            return;
        }

        MidiLearnAction action = midiLearnActionComboBox.getValue();
        if (action == null) {
            return;
        }
        learningMidiAction = action;
        midiLearnButton.setText("Cancelar");
        midiLearnStatusLabel.setText("Aguardando: mova ou pressione o controle desejado.");
    }

    @FXML
    private void handleClearMidiLearnBinding() {
        MidiLearnAction action = midiLearnActionComboBox.getValue();
        if (action == null) {
            return;
        }
        learningMidiAction = null;
        midiLearnButton.setText("Aprender");
        midiBindings.remove(action.id());
        removePreference(MIDI_BINDING_PREFERENCE_PREFIX + action.id());
        refreshMidiLearnStatus();
        refreshControllerProfileLabel();
    }

    @FXML
    private void handleMidiOutputSelection() {
        if (engine == null || refreshingOutputs) {
            return;
        }
        MidiOutputDevice selection = midiOutputComboBox.getSelectionModel().getSelectedItem();
        try {
            engine.selectOutput(selection);
            String outputName = selection == null ? "desconectada" : selection.name();
            midiOutputStatusLabel.setText("Saída: " + outputName);
            midiOutputStatusLabel.setTooltip(new Tooltip("Saída MIDI: " + outputName));
            if (selection == null) {
                removePreference(MIDI_OUTPUT_PREFERENCE_KEY);
            } else {
                savePreference(MIDI_OUTPUT_PREFERENCE_KEY, selection.name());
            }
            updateTransportControls();
        } catch (MidiUnavailableException exception) {
            midiOutputStatusLabel.setText("Saída: falha de conexão");
            midiOutputStatusLabel.setTooltip(null);
            refreshingOutputs = true;
            midiOutputComboBox.getSelectionModel().clearSelection();
            refreshingOutputs = false;
            updateTransportControls();
            showError("Saída MIDI indisponível",
                    "O dispositivo pode estar sendo usado por outro programa.", exception);
        }
    }

    @FXML
    private void handlePlaybackMode() {
        playbackMode = automaticModeToggle.isSelected()
                ? PlaybackMode.AUTOMATIC : PlaybackMode.MANUAL;
        savePlaybackModePreference();
        refreshPlaybackMode();
    }

    private PlaybackMode loadPlaybackModePreference() {
        try {
            return preferences.getBoolean(AUTOPLAY_PREFERENCE_KEY, false)
                    ? PlaybackMode.AUTOMATIC : PlaybackMode.MANUAL;
        } catch (SecurityException exception) {
            return PlaybackMode.MANUAL;
        }
    }

    private void savePlaybackModePreference() {
        try {
            preferences.putBoolean(
                    AUTOPLAY_PREFERENCE_KEY, playbackMode == PlaybackMode.AUTOMATIC);
        } catch (SecurityException ignored) {
            // A preferência é opcional; o player continua funcionando sem persistência.
        }
    }

    private String loadPreference(String key) {
        try {
            return preferences.get(key, null);
        } catch (SecurityException exception) {
            return null;
        }
    }

    private void savePreference(String key, String value) {
        try {
            preferences.put(key, value);
        } catch (SecurityException ignored) {
            // A preferência é opcional; o player continua funcionando sem persistência.
        }
    }

    private void removePreference(String key) {
        try {
            preferences.remove(key);
        } catch (SecurityException ignored) {
            // A preferência é opcional; o player continua funcionando sem persistência.
        }
    }

    @FXML
    private void handlePlay() {
        if (engine == null) {
            return;
        }
        try {
            engine.play();
            statusLabel.setText("TOCANDO");
            updateTransportControls();
        } catch (IllegalStateException exception) {
            showError("Não foi possível iniciar", exception.getMessage(), exception);
        }
    }

    @FXML
    private void handlePause() {
        if (engine != null) {
            engine.pause();
            statusLabel.setText("PAUSADA");
            updateTransportControls();
        }
    }

    @FXML
    private void handleStop() {
        if (engine != null) {
            engine.stop();
            progressSlider.setValue(0);
            currentTimeLabel.setText("00:00");
            refreshLyricsAtTick(0);
            statusLabel.setText("PRONTA");
            updateTransportControls();
        }
    }

    @FXML
    private void handlePanic() {
        if (engine != null) {
            engine.panic();
            statusLabel.setText("PANIC");
        }
    }

    private boolean loadPlaylistIndex(int index) {
        if (engine == null || index < 0 || index >= playlist.size()) {
            return false;
        }
        PlaylistItem item = playlist.get(index);
        if (!item.exists()) {
            showError("Arquivo não encontrado",
                    "A música não está mais no caminho salvo:\n" + item.path(),
                    new IOException(item.path().toString()));
            return false;
        }

        try {
            engine.load(item.path().toFile());
            playlist.select(index);
            loadMixer(item);
            loadLyrics(item);
            setLoadedSongUi(item);
            statusLabel.setText("PRONTA");
            refreshPlaylistView(index);
            updateTransportControls();
            return true;
        } catch (IOException | InvalidMidiDataException exception) {
            showError("Não foi possível abrir o MIDI",
                    "O arquivo selecionado não pôde ser lido como MIDI válido.", exception);
            return false;
        }
    }

    private void setLoadedSongUi(PlaylistItem item) {
        currentSongLabel.setText(item.displayName());
        currentSongLabel.setTooltip(new Tooltip(item.path().toString()));
        long duration = engine.getDurationMicroseconds();
        progressSlider.setMax(Math.max(1, duration));
        progressSlider.setValue(0);
        currentTimeLabel.setText("00:00");
        durationTimeLabel.setText(formatTime(duration));
        updateSongCards();
    }

    private void resetLoadedSong() {
        if (engine != null) {
            engine.unload();
        }
        currentSongLabel.setText("Nenhuma música carregada");
        currentSongLabel.setTooltip(null);
        progressSlider.setValue(0);
        progressSlider.setMax(1);
        currentTimeLabel.setText("00:00");
        durationTimeLabel.setText("00:00");
        statusLabel.setText("SEM MÚSICA");
        currentLyrics = MidiLyrics.empty();
        currentSongAnalysis = MidiSongAnalysis.empty();
        if (engine != null) {
            engine.resetChannelMix(currentSongAnalysis.initialVolumes());
        }
        updateMixerControls("Nenhuma música carregada");
        displayedLyricIndex = Integer.MIN_VALUE;
        refreshLyricsAtTick(0);
        updateSongCards();
        updateTransportControls();
    }

    private void configureControlListeners() {
        configureScaledSlider(transposeSlider, transposeMenuButton, 1,
                MainController::formatSemitones);
        configureScaledSlider(speedSlider, speedMenuButton, 5,
                value -> value + "%");
        configureScaledSlider(masterVolumeSlider, masterVolumeMenuButton, 5,
                value -> value + "%");

        transposeSlider.valueProperty().addListener((ignored, oldValue, newValue) -> {
            int value = roundedSliderValue(newValue.doubleValue(), 1);
            transposeMenuButton.setText(formatSemitones(value));
            if (engine != null) {
                engine.setTranspose(value);
            }
        });
        speedSlider.valueProperty().addListener((ignored, oldValue, newValue) -> {
            int value = roundedSliderValue(newValue.doubleValue(), 5);
            speedMenuButton.setText(value + "%");
            if (engine != null) {
                engine.setTempoFactor(value / 100.0f);
            }
        });
        masterVolumeSlider.valueProperty().addListener((ignored, oldValue, newValue) -> {
            int value = roundedSliderValue(newValue.doubleValue(), 5);
            masterVolumeMenuButton.setText(value + "%");
            if (engine != null) {
                engine.setMasterVolume(value / 100.0);
            }
        });
        progressSlider.setOnMouseReleased(ignored -> seekToSliderPosition());
    }

    private void configureScaledSlider(Slider slider,
                                       MenuButton menuButton,
                                       int step,
                                       IntFunction<String> formatter) {
        int value = roundedSliderValue(slider.getValue(), step);
        slider.setValue(value);
        menuButton.setText(formatter.apply(value));
    }

    private int roundedSliderValue(double value, int step) {
        return (int) Math.round(value / step) * step;
    }

    private void configureMidiLearn() {
        for (MidiLearnAction action : midiLearnActions) {
            MidiBinding.decode(loadPreference(MIDI_BINDING_PREFERENCE_PREFIX + action.id()))
                    .ifPresent(binding -> midiBindings.put(action.id(), binding));
        }
        midiLearnActionComboBox.setItems(FXCollections.observableArrayList(midiLearnActions));
        midiLearnActionComboBox.getSelectionModel().selectFirst();
        midiLearnActionComboBox.valueProperty().addListener((ignored, oldValue, newValue) -> {
            if (learningMidiAction != null) {
                learningMidiAction = null;
                midiLearnButton.setText("Aprender");
            }
            refreshMidiLearnStatus();
        });
        mappingWizardScopeComboBox.setItems(
                FXCollections.observableArrayList(MappingScope.values()));
        mappingWizardScopeComboBox.getSelectionModel().select(MappingScope.ALL);
        refreshMidiLearnStatus();
        refreshMappingWizard();
        refreshDiagnosticsSummary();
        refreshControllerProfileLabel();
        updateActiveMixerBankStyles();
    }

    // ----------------------------------------------------------------------
    // Assistente de mapeamento
    // ----------------------------------------------------------------------

    @FXML
    private void handleStartMappingWizard() {
        if (mappingSession != null) {
            cancelMappingWizard();
            return;
        }
        if (!midiInputMonitor.hasInput()) {
            mappingWizardActionLabel.setText("Selecione e conecte uma entrada MIDI primeiro.");
            return;
        }

        MappingScope scope = mappingWizardScopeComboBox.getValue();
        if (scope == null) {
            scope = MappingScope.ALL;
        }
        List<MidiLearnAction> selectedActions = new ArrayList<>();
        for (MidiLearnAction action : midiLearnActions) {
            if (scope.matches(action)) {
                selectedActions.add(action);
            }
        }
        if (selectedActions.isEmpty()) {
            return;
        }

        learningMidiAction = null;
        midiLearnButton.setText("Aprender");
        mappingSession = new MidiMappingSession(selectedActions);
        mappingCooldownBinding = null;
        pendingMidiInputMessages.clear();
        refreshMappingWizard();
    }

    @FXML
    private void handleSkipMappingStep() {
        if (mappingSession == null) {
            return;
        }
        mappingSession.skip();
        mappingCooldownBinding = null;
        if (mappingSession.isFinished()) {
            finishMappingWizard();
        } else {
            refreshMappingWizard();
        }
    }

    @FXML
    private void handleBackMappingStep() {
        if (mappingSession == null) {
            return;
        }
        mappingSession.back();
        mappingCooldownBinding = null;
        refreshMappingWizard();
    }

    private void advanceMappingWizard(MidiControlMessage message) {
        long now = System.nanoTime();
        if (mappingCooldownBinding != null) {
            if (mappingCooldownBinding.matches(message)) {
                mappingCooldownReleaseNanos = now + MAPPING_COOLDOWN_NANOS;
                return;
            }
            if (now >= mappingCooldownReleaseNanos) {
                mappingCooldownBinding = null;
            }
        }

        Optional<MidiBinding> learned = mappingSession.submit(message);
        if (learned.isEmpty()) {
            return;
        }
        mappingCooldownBinding = learned.get();
        mappingCooldownReleaseNanos = now + MAPPING_COOLDOWN_NANOS;

        if (mappingSession.isFinished()) {
            finishMappingWizard();
        } else {
            refreshMappingWizard();
        }
    }

    private void finishMappingWizard() {
        Map<String, MidiBinding> learned = mappingSession.result();
        int mapped = learned.size();
        int total = mappingSession.total();
        mappingSession = null;
        mappingCooldownBinding = null;

        learned.forEach(this::assignMidiBinding);
        armMixerPickup();
        refreshMappingWizard();
        refreshMidiLearnStatus();
        refreshControllerProfileLabel();
        mappingWizardActionLabel.setText("Assistente concluído: " + mapped
                + " de " + total + (total == 1 ? " função mapeada." : " funções mapeadas."));
    }

    private void cancelMappingWizard() {
        mappingSession = null;
        mappingCooldownBinding = null;
        refreshMappingWizard();
        mappingWizardActionLabel.setText("Assistente cancelado. Nada foi alterado.");
    }

    private void refreshMappingWizard() {
        boolean running = mappingSession != null;
        mappingWizardStartButton.setText(running ? "Cancelar" : "Iniciar assistente");
        mappingWizardSkipButton.setDisable(!running);
        mappingWizardBackButton.setDisable(!running || mappingSession.isAtFirstAction());
        mappingWizardScopeComboBox.setDisable(running);
        midiLearnButton.setDisable(running);
        midiLearnActionComboBox.setDisable(running);

        if (!running) {
            mappingWizardProgressLabel.setText("");
            mappingWizardActionLabel.setText(
                    "O assistente percorre cada função e aprende o controle que você mover.");
            return;
        }

        MidiLearnAction action = mappingSession.current().orElse(null);
        mappingWizardProgressLabel.setText("Passo " + mappingSession.position()
                + " de " + mappingSession.total() + " · " + mappingSession.mappedCount()
                + " mapeadas");
        if (action == null) {
            mappingWizardActionLabel.setText("");
            return;
        }
        String instruction = action.kind().isContinuous()
                ? "Mova de ponta a ponta o controle para: "
                : "Pressione o botão para: ";
        mappingWizardActionLabel.setText(instruction + action.displayName());
    }

    private void assignMidiBinding(String actionId, MidiBinding binding) {
        midiBindings.entrySet().removeIf(entry -> {
            if (!entry.getKey().equals(actionId) && entry.getValue().equals(binding)) {
                removePreference(MIDI_BINDING_PREFERENCE_PREFIX + entry.getKey());
                return true;
            }
            return false;
        });
        midiBindings.put(actionId, binding);
        savePreference(MIDI_BINDING_PREFERENCE_PREFIX + actionId, binding.encode());
    }

    // ----------------------------------------------------------------------
    // Diagnóstico da controladora
    // ----------------------------------------------------------------------

    @FXML
    private void handleShowMidiDiagnostics() {
        showTextDialog("Diagnóstico da controladora",
                midiDiagnostics.summary(),
                midiDiagnostics.report(currentMidiInputName()));
    }

    @FXML
    private void handleClearMidiDiagnostics() {
        midiDiagnostics.clear();
        refreshDiagnosticsSummary();
    }

    @FXML
    private void handleSaveMidiDiagnosticsReport() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Salvar diagnóstico da controladora");
        chooser.setInitialFileName("diagnostico-controladora.txt");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Arquivo de texto", "*.txt"));
        File target = chooser.showSaveDialog(settingsWindow());
        if (target == null) {
            return;
        }
        try {
            Files.writeString(target.toPath(),
                    midiDiagnostics.report(currentMidiInputName()), StandardCharsets.UTF_8);
            midiDiagnosticsSummaryLabel.setText(
                    midiDiagnostics.summary() + " · salvo em " + target.getName());
        } catch (IOException exception) {
            showError("Não foi possível salvar o diagnóstico",
                    "Escolha outra pasta e tente novamente.", exception);
        }
    }

    private void refreshDiagnosticsSummary() {
        midiDiagnosticsSummaryLabel.setText(midiDiagnostics.summary());
    }

    private String currentMidiInputName() {
        MidiInputDevice input = midiInputComboBox.getValue();
        return input == null || input.isNone() ? "" : input.name();
    }

    // ----------------------------------------------------------------------
    // Perfis de controladora
    // ----------------------------------------------------------------------

    @FXML
    private void handleExportControllerProfile() {
        if (midiBindings.isEmpty()) {
            controllerProfileLabel.setText("Não há vínculos configurados para exportar.");
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Exportar perfil da controladora");
        chooser.setInitialFileName("controladora" + ControllerProfile.FILE_EXTENSION);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Perfil de controladora", "*" + ControllerProfile.FILE_EXTENSION));
        File target = chooser.showSaveDialog(settingsWindow());
        if (target == null) {
            return;
        }

        Map<String, MidiBinding> ordered = new LinkedHashMap<>();
        for (MidiLearnAction action : midiLearnActions) {
            MidiBinding binding = midiBindings.get(action.id());
            if (binding != null) {
                ordered.put(action.id(), binding);
            }
        }

        String profileName = currentMidiInputName();
        try {
            ControllerProfile profile = new ControllerProfile(
                    profileName.isBlank() ? "Controladora" : profileName, ordered);
            profile.save(target.toPath());
            controllerProfileLabel.setText(
                    "Perfil exportado com " + profile.size() + " vínculos.");
        } catch (IOException exception) {
            showError("Não foi possível exportar o perfil",
                    "Escolha outra pasta e tente novamente.", exception);
        }
    }

    @FXML
    private void handleImportControllerProfile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Importar perfil da controladora");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Perfil de controladora", "*" + ControllerProfile.FILE_EXTENSION));
        File source = chooser.showOpenDialog(settingsWindow());
        if (source == null) {
            return;
        }

        try {
            Optional<ControllerProfile> profile = ControllerProfile.load(source.toPath());
            if (profile.isEmpty()) {
                controllerProfileLabel.setText("O arquivo não contém vínculos válidos.");
                return;
            }
            applyControllerProfile(profile.get());
        } catch (IOException exception) {
            showError("Não foi possível importar o perfil",
                    "Confira se o arquivo existe e pode ser lido.", exception);
        }
    }

    @FXML
    private void handleApplySmcMixerLayout() {
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                "Isso substitui todos os vínculos atuais pelo layout de referência da "
                        + "SMC-Mixer. A controladora precisa estar programada com a tabela "
                        + "correspondente. Deseja continuar?",
                ButtonType.OK, ButtonType.CANCEL);
        confirmation.setTitle("SuperMídia MIDI Player");
        confirmation.setHeaderText("Aplicar layout da SMC-Mixer");
        confirmation.initOwner(settingsWindow());
        if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }
        applyControllerProfile(SmcMixerLayout.profile());
    }

    @FXML
    private void handleShowSmcMixerTable() {
        showTextDialog("Layout de referência da SMC-Mixer",
                "Programe estes valores no editor da controladora.",
                SmcMixerLayout.setupTable());
    }

    private void applyControllerProfile(ControllerProfile profile) {
        for (MidiLearnAction action : midiLearnActions) {
            midiBindings.remove(action.id());
            removePreference(MIDI_BINDING_PREFERENCE_PREFIX + action.id());
        }
        profile.bindings().forEach((actionId, binding) -> {
            midiBindings.put(actionId, binding);
            savePreference(MIDI_BINDING_PREFERENCE_PREFIX + actionId, binding.encode());
        });
        armMixerPickup();
        refreshMidiLearnStatus();
        controllerProfileLabel.setText("Perfil aplicado: " + profile.name()
                + " · " + profile.size() + " de " + midiLearnActions.size()
                + " funções com controle atribuído.");
    }

    private void refreshControllerProfileLabel() {
        int configured = 0;
        for (MidiLearnAction action : midiLearnActions) {
            if (midiBindings.containsKey(action.id())) {
                configured++;
            }
        }
        controllerProfileLabel.setText(configured + " de " + midiLearnActions.size()
                + " funções com controle atribuído.");
    }

    private javafx.stage.Window settingsWindow() {
        return currentSongLabel.getScene() == null
                ? null : currentSongLabel.getScene().getWindow();
    }

    private void showTextDialog(String title, String header, String content) {
        TextArea textArea = new TextArea(content);
        textArea.setEditable(false);
        textArea.setWrapText(false);
        textArea.setPrefColumnCount(72);
        textArea.setPrefRowCount(24);
        textArea.setStyle("-fx-font-family: 'Consolas', 'DejaVu Sans Mono', monospace;");

        Alert dialog = new Alert(Alert.AlertType.INFORMATION);
        dialog.setTitle("SuperMídia MIDI Player");
        dialog.setHeaderText(title + (header == null || header.isBlank() ? "" : "\n" + header));
        dialog.getDialogPane().setContent(textArea);
        dialog.setResizable(true);
        dialog.initOwner(settingsWindow());
        dialog.showAndWait();
    }

    private void refreshMidiLearnStatus() {
        MidiLearnAction action = midiLearnActionComboBox.getValue();
        MidiBinding binding = action == null ? null : midiBindings.get(action.id());
        if (binding == null) {
            midiLearnStatusLabel.setText("Ainda não configurado.");
            midiLearnClearButton.setDisable(true);
        } else {
            midiLearnStatusLabel.setText("Configurado: " + binding.description());
            midiLearnClearButton.setDisable(false);
        }
    }

    private void configurePlaylistView() {
        playlistListView.setItems(FXCollections.observableArrayList());
        playlistListView.setCellFactory(ignored -> new ListCell<>() {
            @Override
            protected void updateItem(PlaylistItem item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().remove("playlist-current-item");
                if (empty || item == null) {
                    setText(null);
                    return;
                }
                boolean current = getIndex() == playlist.currentIndex();
                String missing = item.exists() ? "" : "  · arquivo não encontrado";
                setText((current ? "▶  " : "    ")
                        + String.format("%02d", getIndex() + 1) + "  " + item.displayName() + missing);
                if (current) {
                    getStyleClass().add("playlist-current-item");
                }
            }
        });
        playlistListView.getSelectionModel().selectedIndexProperty().addListener(
                (ignored, oldValue, newValue) -> updatePlaylistEditingButtons());
    }

    private void configureMixerView() {
        for (int channel = 0; channel < MIDI_CHANNEL_COUNT; channel++) {
            mixerChannelStrips[channel] = createMixerChannelStrip(channel);
            HBox bank = channel < MIXER_BANK_SIZE
                    ? mixerBankOneContainer : mixerBankTwoContainer;
            bank.getChildren().add(mixerChannelStrips[channel]);
        }
        updateMixerControls("Nenhuma música carregada");
    }

    private VBox createMixerChannelStrip(int channel) {
        Label activity = new Label("●");
        activity.getStyleClass().add("activity-dot");
        channelActivityLabels[channel] = activity;

        Label channelLabel = new Label(String.format("%02d", channel + 1));
        channelLabel.getStyleClass().add("mixer-channel-number");

        Label instrumentLabel = new Label("SEM SOM");
        instrumentLabel.setWrapText(true);
        instrumentLabel.setMaxWidth(Double.MAX_VALUE);
        instrumentLabel.setAlignment(Pos.CENTER);
        instrumentLabel.getStyleClass().add("mixer-instrument-name");
        channelInstrumentLabels[channel] = instrumentLabel;

        Slider volumeSlider = new Slider(0, 127, 100);
        volumeSlider.setOrientation(Orientation.VERTICAL);
        volumeSlider.setBlockIncrement(1);
        volumeSlider.setMajorTickUnit(127.0 / 4.0);
        volumeSlider.setMinorTickCount(1);
        volumeSlider.setShowTickMarks(true);
        volumeSlider.setShowTickLabels(false);
        volumeSlider.setPrefHeight(150);
        volumeSlider.setMaxHeight(170);
        channelVolumeSliders[channel] = volumeSlider;

        Label volumeLabel = new Label("100");
        volumeLabel.getStyleClass().add("mixer-volume-value");
        channelVolumeLabels[channel] = volumeLabel;

        ToggleButton muteButton = new ToggleButton("M");
        muteButton.getStyleClass().addAll("mixer-state-button", "mute-button");
        muteButton.setMaxWidth(Double.MAX_VALUE);
        muteButton.setOnAction(ignored -> {
            if (engine != null && !updatingMixerControls) {
                engine.setChannelMuted(channel, muteButton.isSelected());
            }
        });
        channelMuteButtons[channel] = muteButton;

        ToggleButton soloButton = new ToggleButton("S");
        soloButton.getStyleClass().addAll("mixer-state-button", "solo-button");
        soloButton.setMaxWidth(Double.MAX_VALUE);
        soloButton.setOnAction(ignored -> {
            if (engine != null && !updatingMixerControls) {
                engine.setChannelSolo(channel, soloButton.isSelected());
            }
        });
        channelSoloButtons[channel] = soloButton;

        HBox stateButtons = new HBox(3, muteButton, soloButton);
        HBox.setHgrow(muteButton, Priority.ALWAYS);
        HBox.setHgrow(soloButton, Priority.ALWAYS);

        volumeSlider.valueProperty().addListener((ignored, oldValue, newValue) -> {
            int volume = (int) Math.round(newValue.doubleValue());
            volumeLabel.setText(Integer.toString(volume));
            if (engine != null && !updatingMixerControls) {
                engine.setChannelVolume(channel, volume);
            }
        });

        VBox strip = new VBox(4,
                channelLabel, instrumentLabel, volumeSlider,
                volumeLabel, stateButtons, activity);
        strip.setAlignment(Pos.TOP_CENTER);
        strip.setMaxWidth(90);
        strip.getStyleClass().add("mixer-channel-strip");
        HBox.setHgrow(strip, Priority.ALWAYS);
        return strip;
    }

    private void loadMixer(PlaylistItem item) {
        try {
            currentSongAnalysis = MidiSongAnalysis.fromFile(item.path().toFile());
        } catch (IOException | InvalidMidiDataException exception) {
            currentSongAnalysis = MidiSongAnalysis.empty();
        }
        engine.resetChannelMix(currentSongAnalysis.initialVolumes());
        updateMixerControls(item.displayName());
    }

    private void applyOriginalMixerState() {
        if (engine == null || !engine.hasSequence()) {
            return;
        }
        engine.resetChannelMix(currentSongAnalysis.initialVolumes());
        updateMixerControls(playlist.current()
                .map(PlaylistItem::displayName)
                .orElse("Música carregada"));
        statusLabel.setText("MIX ORIGINAL");
    }

    private void updateMixerControls(String songName) {
        updatingMixerControls = true;
        try {
            boolean songLoaded = engine != null && engine.hasSequence();
            for (int channel = 0; channel < MIDI_CHANNEL_COUNT; channel++) {
                MidiChannelInfo info = currentSongAnalysis.channel(channel);
                String instrument = songLoaded && info.used()
                        ? abbreviatedInstrument(info, channel) : "";
                channelInstrumentLabels[channel].setText(instrument);
                channelInstrumentLabels[channel].setTooltip(songLoaded && info.used()
                        ? new Tooltip(info.instrumentName() + "\n" + info.trackName()) : null);
                channelVolumeSliders[channel].setValue(
                        songLoaded ? info.initialVolume() : 100);
                channelVolumeLabels[channel].setText(
                        songLoaded ? Integer.toString(info.initialVolume()) : "");
                channelVolumeSliders[channel].setDisable(!songLoaded);
                channelMuteButtons[channel].setDisable(!songLoaded);
                channelSoloButtons[channel].setDisable(!songLoaded);
                channelMuteButtons[channel].setSelected(false);
                channelSoloButtons[channel].setSelected(false);
                channelActivityLabels[channel].setVisible(songLoaded);
                channelActivityLabels[channel].getStyleClass().remove("mixer-active");
                mixerChannelStrips[channel].getStyleClass().remove("mixer-unused-channel");
                if (songLoaded && !info.used()) {
                    mixerChannelStrips[channel].getStyleClass().add("mixer-unused-channel");
                }
            }
        } finally {
            updatingMixerControls = false;
        }
        armMixerPickup();
    }

    private String abbreviatedInstrument(MidiChannelInfo info, int channel) {
        if (channel == 9) {
            return "DRUM";
        }
        String name = info.instrumentName().toUpperCase(Locale.ROOT);
        if (name.contains("ELECTRIC PIANO")) {
            return "E.PIANO";
        }
        if (name.contains("PIANO")) {
            return "PIANO";
        }
        if (name.contains("ELECTRIC") && name.contains("BASS")) {
            return "E.BASS";
        }
        if (name.contains("BASS")) {
            return "BASS";
        }
        if (name.contains("GUITAR")) {
            return name.contains("ELECTRIC") ? "E.GUIT" : "GUITAR";
        }
        if (name.contains("STRING")) {
            return "STRINGS";
        }
        if (name.contains("ORGAN")) {
            return "ORGAN";
        }
        if (name.contains("BRASS")) {
            return "BRASS";
        }
        if (name.contains("CHOIR") || name.contains("VOICE")) {
            return "CHOIR";
        }
        if (name.contains("SYNTH")) {
            return "SYNTH";
        }
        String firstWord = name.split("\\s+", 2)[0];
        return firstWord.length() <= 8 ? firstWord : firstWord.substring(0, 8);
    }

    private void refreshMixerActivity() {
        if (engine == null) {
            return;
        }
        for (int channel = 0; channel < MIDI_CHANNEL_COUNT; channel++) {
            boolean active = engine.isChannelActive(channel);
            List<String> styleClasses = channelActivityLabels[channel].getStyleClass();
            if (active && !styleClasses.contains("mixer-active")) {
                styleClasses.add("mixer-active");
            } else if (!active) {
                styleClasses.remove("mixer-active");
            }
        }
    }

    private void refreshMidiOutputs() {
        if (engine == null) {
            return;
        }
        refreshingOutputs = true;
        try {
            String previousName = midiOutputComboBox.getValue() == null
                    ? loadPreference(MIDI_OUTPUT_PREFERENCE_KEY)
                    : midiOutputComboBox.getValue().name();
            List<MidiOutputDevice> outputs = engine.listOutputDevices();
            midiOutputComboBox.setItems(FXCollections.observableArrayList(outputs));
            midiOutputComboBox.getSelectionModel().select(findPreferredOutput(outputs, previousName));
            if (outputs.isEmpty()) {
                midiOutputStatusLabel.setText("Saída: nenhuma encontrada");
            }
        } finally {
            refreshingOutputs = false;
        }
        handleMidiOutputSelection();
    }

    private void refreshMidiInputs() {
        MidiInputDevice currentInput = midiInputComboBox.getValue();
        String preferredName = currentInput == null || currentInput.isNone()
                ? loadPreference(MIDI_INPUT_PREFERENCE_KEY)
                : currentInput.name();
        List<MidiInputDevice> detectedInputs = midiInputMonitor.listInputDevices();
        List<MidiInputDevice> choices = new ArrayList<>(detectedInputs.size() + 1);
        choices.add(MidiInputDevice.none());
        choices.addAll(detectedInputs);

        MidiInputDevice preferredInput = findPreferredInput(detectedInputs, preferredName);
        refreshingInputs = true;
        try {
            midiInputComboBox.setItems(FXCollections.observableArrayList(choices));
            midiInputComboBox.getSelectionModel().select(preferredInput);
        } finally {
            refreshingInputs = false;
        }

        handleMidiInputSelection();
        if (preferredName != null && preferredInput.isNone()) {
            midiInputMonitorLabel.setText("A entrada usada anteriormente não foi encontrada.");
        }
    }

    private MidiInputDevice findPreferredInput(List<MidiInputDevice> inputs, String preferredName) {
        if (preferredName != null) {
            for (MidiInputDevice input : inputs) {
                if (input.name().equals(preferredName)) {
                    return input;
                }
            }
        }
        return MidiInputDevice.none();
    }

    private MidiOutputDevice findPreferredOutput(List<MidiOutputDevice> outputs, String previousName) {
        if (previousName != null) {
            for (MidiOutputDevice output : outputs) {
                if (output.name().equals(previousName)) {
                    return output;
                }
            }
        }
        for (MidiOutputDevice output : outputs) {
            String normalizedName = output.name().toLowerCase(Locale.ROOT);
            if (normalizedName.contains("jm-5") || normalizedName.contains("vima")) {
                return output;
            }
        }
        return outputs.isEmpty() ? null : outputs.getFirst();
    }

    private void selectNoMidiInput() {
        try {
            midiInputMonitor.selectInput(MidiInputDevice.none());
        } catch (MidiUnavailableException ignored) {
            // Desconectar a entrada não abre nenhum dispositivo e não deve falhar.
        }
        refreshingInputs = true;
        try {
            midiInputComboBox.getSelectionModel().select(MidiInputDevice.none());
        } finally {
            refreshingInputs = false;
        }
    }

    private void setMidiInputConnected(boolean connected) {
        List<String> styleClasses = midiInputConnectionDot.getStyleClass();
        styleClasses.remove("connection-dot-active");
        if (connected) {
            styleClasses.remove("connection-dot-disconnected");
        } else if (!styleClasses.contains("connection-dot-disconnected")) {
            styleClasses.add("connection-dot-disconnected");
        }
    }

    private void startProgressUpdates() {
        progressTimeline = new Timeline(new KeyFrame(Duration.millis(150), ignored -> refreshProgress()));
        progressTimeline.setCycleCount(Timeline.INDEFINITE);
        progressTimeline.play();
    }

    private void refreshProgress() {
        refreshMidiInputActivity();
        if (engine == null || !engine.hasSequence() || progressSlider.isValueChanging()) {
            return;
        }
        long position = engine.getPositionMicroseconds();
        progressSlider.setValue(position);
        currentTimeLabel.setText(formatTime(position));
        refreshLyricsAtTick(engine.getTickPosition());
        refreshMixerActivity();
    }

    private void refreshMidiInputActivity() {
        MidiControlMessage message;
        MidiControlMessage lastMessage = null;
        while ((message = pendingMidiInputMessages.poll()) != null) {
            lastMessage = message;
            midiDiagnostics.observe(message);
            if (mappingSession != null) {
                advanceMappingWizard(message);
            } else if (!tryCompleteMidiLearn(message)) {
                executeMidiBindings(message);
            }
        }

        if (lastMessage != null) {
            midiInputMonitorLabel.setText(describeMidiInputMessage(lastMessage));
            refreshDiagnosticsSummary();
            if (!midiInputConnectionDot.getStyleClass().contains("connection-dot-active")) {
                midiInputConnectionDot.getStyleClass().add("connection-dot-active");
            }
            lastMidiInputActivityNanos = System.nanoTime();
        } else if (lastMidiInputActivityNanos > 0
                && System.nanoTime() - lastMidiInputActivityNanos > MIDI_INPUT_ACTIVITY_TIMEOUT_NANOS) {
            midiInputConnectionDot.getStyleClass().remove("connection-dot-active");
        }
    }

    private boolean tryCompleteMidiLearn(MidiControlMessage message) {
        if (learningMidiAction == null) {
            return false;
        }

        Optional<MidiBinding> learned = MidiBinding.learn(
                message, learningMidiAction.kind().isContinuous());
        if (learned.isEmpty()) {
            return true;
        }

        MidiLearnAction action = learningMidiAction;
        MidiBinding binding = learned.get();
        assignMidiBinding(action.id(), binding);
        refreshControllerProfileLabel();
        if (action.kind().isContinuous()) {
            recentlyLearnedContinuousBinding = binding;
            recentlyLearnedBindingReleaseNanos =
                    System.nanoTime() + MIDI_LEARN_RELEASE_TIMEOUT_NANOS;
            if (action.kind() == MidiLearnAction.Kind.BANK_VOLUME) {
                mixerPickupArmed[action.slot()] = true;
                mixerControllerValueKnown[action.slot()] = false;
            }
        }
        learningMidiAction = null;
        midiLearnButton.setText("Aprender");
        midiLearnStatusLabel.setText("Aprendido: " + binding.description());
        midiLearnClearButton.setDisable(false);
        return true;
    }

    private void executeMidiBindings(MidiControlMessage message) {
        long now = System.nanoTime();
        if (recentlyLearnedContinuousBinding != null
                && now >= recentlyLearnedBindingReleaseNanos) {
            recentlyLearnedContinuousBinding = null;
        }
        if (recentlyLearnedContinuousBinding != null
                && recentlyLearnedContinuousBinding.matches(message)) {
            recentlyLearnedBindingReleaseNanos = now + MIDI_LEARN_RELEASE_TIMEOUT_NANOS;
            return;
        }

        for (MidiLearnAction action : midiLearnActions) {
            MidiBinding binding = midiBindings.get(action.id());
            if (binding == null || !binding.matches(message)) {
                continue;
            }
            if (action.kind().isContinuous()) {
                executeContinuousMidiAction(action, binding, message);
            } else if (binding.isTriggeredBy(message)) {
                executeTriggeredMidiAction(action);
            }
        }
    }

    private void executeTriggeredMidiAction(MidiLearnAction action) {
        switch (action.kind()) {
            case PLAY -> {
                if (!playButton.isDisable()) {
                    handlePlay();
                }
            }
            case PAUSE -> {
                if (!pauseButton.isDisable()) {
                    handlePause();
                }
            }
            case STOP -> {
                if (!stopButton.isDisable()) {
                    handleStop();
                }
            }
            // Um botão só para o transporte: para se está tocando, toca se está parado.
            // O estado vem do sequenciador, a mesma fonte que updateTransportControls() usa.
            case PLAY_STOP_TOGGLE -> {
                if (engine != null && engine.isRunning()) {
                    if (!stopButton.isDisable()) {
                        handleStop();
                    }
                } else if (!playButton.isDisable()) {
                    handlePlay();
                }
            }
            case PREVIOUS -> {
                if (!previousButton.isDisable()) {
                    handlePrevious();
                }
            }
            case NEXT -> {
                if (!nextButton.isDisable()) {
                    handleNext();
                }
            }
            case PANIC -> {
                if (!panicButton.isDisable()) {
                    handlePanic();
                }
            }
            case AUTOPLAY_TOGGLE -> automaticModeToggle.fire();
            // Tom por botão: cada toque anda um semitom no mesmo slider do controle contínuo.
            case TRANSPOSE_UP -> nudgeSlider(transposeSlider, 1);
            case TRANSPOSE_DOWN -> nudgeSlider(transposeSlider, -1);
            case BANK_PREVIOUS -> setActiveMixerBank(0);
            case BANK_NEXT -> setActiveMixerBank(1);
            case BANK_TOGGLE -> setActiveMixerBank(activeMixerBank == 0 ? 1 : 0);
            case BANK_MUTE -> fireMixerToggle(channelMuteButtons, action.slot());
            case BANK_SOLO -> fireMixerToggle(channelSoloButtons, action.slot());
            default -> {
                // Ações contínuas são tratadas separadamente.
            }
        }
    }

    private void executeContinuousMidiAction(MidiLearnAction action,
                                             MidiBinding binding,
                                             MidiControlMessage message) {
        boolean relative = binding.valueMode() == MidiBinding.ValueMode.RELATIVE;
        int delta = relative ? binding.relativeDelta(message) : 0;
        double absolute = relative ? 0 : binding.absoluteValue(message);

        switch (action.kind()) {
            case TRANSPOSE -> applyMidiToSlider(transposeSlider, absolute, delta, relative, 1);
            case SPEED -> applyMidiToSlider(speedSlider, absolute, delta, relative, 5);
            case MASTER_VOLUME -> applyMidiToSlider(masterVolumeSlider, absolute, delta, relative, 5);
            case BANK_VOLUME -> applyMidiToMixerFader(action.slot(), absolute, delta, relative);
            default -> {
                // Ações de disparo são tratadas separadamente.
            }
        }
    }

    /**
     * Anda um passo fixo num slider, respeitando os limites dele.
     * Usado pelas ações de tom em botão, que não carregam valor próprio como um knob.
     */
    private void nudgeSlider(Slider slider, int delta) {
        if (slider.isDisable()) {
            return;
        }
        double value = slider.getValue() + delta;
        slider.setValue(Math.max(slider.getMin(), Math.min(slider.getMax(), value)));
    }

    private void applyMidiToSlider(Slider slider,
                                   double absolute,
                                   int delta,
                                   boolean relative,
                                   int step) {
        double value = relative
                ? slider.getValue() + delta * step
                : slider.getMin() + absolute * (slider.getMax() - slider.getMin());
        slider.setValue(roundedSliderValue(
                Math.max(slider.getMin(), Math.min(slider.getMax(), value)), step));
    }

    private void applyMidiToMixerFader(int slot,
                                       double absolute,
                                       int delta,
                                       boolean relative) {
        int channel = activeMixerBank * MIXER_BANK_SIZE + slot;
        Slider slider = channelVolumeSliders[channel];
        if (slider.isDisable()) {
            return;
        }
        if (relative) {
            slider.setValue(Math.max(slider.getMin(),
                    Math.min(slider.getMax(), slider.getValue() + delta)));
            return;
        }

        double target = slider.getValue() / slider.getMax();
        double previous = mixerLastControllerValue[slot];
        mixerLastControllerValue[slot] = absolute;
        if (mixerPickupArmed[slot]) {
            boolean closeEnough = Math.abs(absolute - target) <= MIXER_PICKUP_THRESHOLD;
            boolean crossedTarget = mixerControllerValueKnown[slot]
                    && (previous - target) * (absolute - target) <= 0;
            mixerControllerValueKnown[slot] = true;
            if (!closeEnough && !crossedTarget) {
                return;
            }
            mixerPickupArmed[slot] = false;
        }
        slider.setValue(Math.round(absolute * slider.getMax()));
    }

    private void fireMixerToggle(ToggleButton[] buttons, int slot) {
        int channel = activeMixerBank * MIXER_BANK_SIZE + slot;
        ToggleButton button = buttons[channel];
        if (!button.isDisable()) {
            button.fire();
        }
    }

    private void setActiveMixerBank(int bank) {
        if (bank < 0 || bank > 1 || activeMixerBank == bank) {
            return;
        }
        activeMixerBank = bank;
        armMixerPickup();
        updateActiveMixerBankStyles();
    }

    private void armMixerPickup() {
        for (int slot = 0; slot < MIXER_BANK_SIZE; slot++) {
            mixerPickupArmed[slot] = true;
            mixerControllerValueKnown[slot] = false;
        }
    }

    private void updateActiveMixerBankStyles() {
        setMixerBankStyle(mixerBankOnePanel, activeMixerBank == 0);
        setMixerBankStyle(mixerBankTwoPanel, activeMixerBank == 1);
    }

    private void setMixerBankStyle(VBox panel, boolean active) {
        panel.getStyleClass().removeAll("mixer-bank-active", "mixer-bank-inactive");
        panel.getStyleClass().add(active ? "mixer-bank-active" : "mixer-bank-inactive");
    }

    private String describeMidiInputMessage(MidiControlMessage message) {
        int channel = message.channel() + 1;
        return switch (message.command()) {
            case ShortMessage.CONTROL_CHANGE -> "Sinal recebido · CC " + message.data1()
                    + " · valor " + message.data2() + " · canal " + channel;
            case ShortMessage.PITCH_BEND -> "Sinal recebido · Pitch Bend · valor "
                    + ((message.data2() << 7) | message.data1()) + " · canal " + channel;
            case ShortMessage.NOTE_ON -> message.data2() == 0
                    ? "Sinal recebido · Note Off " + message.data1() + " · canal " + channel
                    : "Sinal recebido · Note On " + message.data1()
                            + " · valor " + message.data2() + " · canal " + channel;
            case ShortMessage.NOTE_OFF -> "Sinal recebido · Note Off " + message.data1()
                    + " · canal " + channel;
            default -> "Sinal MIDI recebido · comando " + message.command()
                    + " · canal " + channel;
        };
    }

    private void seekToSliderPosition() {
        if (engine != null && engine.hasSequence()) {
            engine.setPositionMicroseconds((long) progressSlider.getValue());
            currentTimeLabel.setText(formatTime((long) progressSlider.getValue()));
            refreshLyricsAtTick(engine.getTickPosition());
        }
    }

    private void handlePlaybackFinished() {
        if (engine == null || !engine.hasSequence()) {
            return;
        }
        if (playlist.hasNext() && loadPlaylistIndex(playlist.currentIndex() + 1)) {
            if (playbackMode == PlaybackMode.AUTOMATIC) {
                engine.play();
                statusLabel.setText("TOCANDO");
            } else {
                statusLabel.setText("PRONTA");
            }
            updateTransportControls();
            return;
        }

        statusLabel.setText("FINALIZADA");
        progressSlider.setValue(engine.getDurationMicroseconds());
        currentTimeLabel.setText(formatTime(engine.getDurationMicroseconds()));
        updateTransportControls();
    }

    private void refreshPlaylistView(int selectedIndex) {
        playlistListView.getItems().setAll(playlist.items());
        playlistListView.refresh();
        if (selectedIndex >= 0 && selectedIndex < playlist.size()) {
            playlistListView.getSelectionModel().select(selectedIndex);
            playlistListView.scrollTo(selectedIndex);
        }
        playlistCountLabel.setText(playlist.size() == 1
                ? "1 música" : playlist.size() + " músicas");
        savePlaylistButton.setDisable(playlist.isEmpty());
        updatePlaylistFileLabel();
        updatePlaylistEditingButtons();
        updateSongCards();
        updateTransportControls();
    }

    private void updatePlaylistEditingButtons() {
        int selectedIndex = playlistListView.getSelectionModel().getSelectedIndex();
        boolean selected = selectedIndex >= 0;
        removePlaylistButton.setDisable(!selected);
        moveUpButton.setDisable(!selected || selectedIndex == 0);
        moveDownButton.setDisable(!selected || selectedIndex >= playlist.size() - 1);
    }

    private void updateSongCards() {
        playlist.next().ifPresentOrElse(item -> {
            nextSongLabel.setText(item.displayName());
            nextSongLabel.setTooltip(new Tooltip(item.displayName()));
        }, () -> {
            nextSongLabel.setText(playlist.isEmpty()
                    ? "Playlist vazia" : "Fim da playlist");
            nextSongLabel.setTooltip(null);
        });
        playlistListView.refresh();
    }

    private void updateTransportControls() {
        boolean available = engine != null;
        boolean loaded = available && engine.hasSequence();
        boolean outputConnected = available && engine.hasOutput();
        boolean running = available && engine.isRunning();

        playButton.setDisable(!loaded || !outputConnected || running);
        pauseButton.setDisable(!running);
        stopButton.setDisable(!loaded);
        progressSlider.setDisable(!loaded);
        panicButton.setDisable(!outputConnected);
        previousButton.setDisable(!playlist.hasPrevious());
        nextButton.setDisable(!playlist.hasNext());
    }

    private void disableMidiControls() {
        midiOutputComboBox.setDisable(true);
        playButton.setDisable(true);
        pauseButton.setDisable(true);
        stopButton.setDisable(true);
        panicButton.setDisable(true);
        progressSlider.setDisable(true);
    }

    private void refreshPlaybackMode() {
        automaticModeToggle.setSelected(playbackMode == PlaybackMode.AUTOMATIC);
        updateSongCards();
    }

    private void showPresentation() {
        playlistView.setVisible(false);
        playlistView.setManaged(false);
        settingsView.setVisible(false);
        settingsView.setManaged(false);
        presentationView.setVisible(true);
        presentationView.setManaged(true);
        presentationNavButton.setSelected(true);
    }

    private void loadLyrics(PlaylistItem item) {
        try {
            currentLyrics = MidiLyrics.fromFile(item.path().toFile());
        } catch (IOException | InvalidMidiDataException exception) {
            currentLyrics = MidiLyrics.empty();
        }
        displayedLyricIndex = Integer.MIN_VALUE;
        refreshLyricsAtTick(0);
    }

    private void refreshLyricsAtTick(long tick) {
        int lyricIndex = currentLyrics.currentIndex(tick);
        if (lyricIndex == displayedLyricIndex) {
            return;
        }

        if (lyricScrollTransition != null) {
            showLyricsImmediately(displayedLyricIndex);
        }
        int previousIndex = displayedLyricIndex;
        displayedLyricIndex = lyricIndex;

        if (currentLyrics.isEmpty()) {
            stopLyricScrollAnimation();
            setLyricLabels("", "", "");
            stageLyricsStatusLabel.setText("SEM LETRA INCORPORADA");
            return;
        }

        if (lyricIndex < 0) {
            stopLyricScrollAnimation();
            setLyricLabels("", "", lyricText(0));
            stageLyricsStatusLabel.setText("LETRA SINCRONIZADA · 0 / " + currentLyrics.size());
            return;
        }

        boolean nextSequentialLine = previousIndex >= -1
                && lyricIndex == previousIndex + 1
                && lyricsLinesContainer.getScene() != null;
        if (nextSequentialLine) {
            animateLyricsForward(lyricIndex);
        } else {
            showLyricsImmediately(lyricIndex);
        }
        stageLyricsStatusLabel.setText("LETRA SINCRONIZADA · "
                + (lyricIndex + 1) + " / " + currentLyrics.size());
    }

    private void configureLyricsViewport() {
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(lyricsViewport.widthProperty());
        clip.heightProperty().bind(lyricsViewport.heightProperty());
        lyricsViewport.setClip(clip);
    }

    private void animateLyricsForward(int lyricIndex) {
        stopLyricScrollAnimation();
        stageIncomingLyricLabel.setText(lyricText(lyricIndex + 1));

        double lineDistance = stageCurrentLyricLabel.getLayoutY()
                - stagePreviousLyricLabel.getLayoutY();
        if (lineDistance <= 0) {
            lineDistance = 60;
        }

        lyricScrollTransition = new TranslateTransition(
                LYRIC_SCROLL_DURATION, lyricsLinesContainer);
        lyricScrollTransition.setFromY(0);
        lyricScrollTransition.setToY(-lineDistance);
        lyricScrollTransition.setInterpolator(Interpolator.EASE_BOTH);
        lyricScrollTransition.setOnFinished(ignored -> {
            lyricScrollTransition = null;
            lyricsLinesContainer.setTranslateY(0);
            setLyricLabelsForIndex(lyricIndex);
        });
        lyricScrollTransition.play();
    }

    private void showLyricsImmediately(int lyricIndex) {
        stopLyricScrollAnimation();
        if (lyricIndex < 0) {
            setLyricLabels("", "", lyricText(0));
            return;
        }
        setLyricLabelsForIndex(lyricIndex);
    }

    private void setLyricLabelsForIndex(int lyricIndex) {
        setLyricLabels(
                lyricText(lyricIndex - 1),
                lyricText(lyricIndex),
                lyricText(lyricIndex + 1));
    }

    private void stopLyricScrollAnimation() {
        if (lyricScrollTransition != null) {
            lyricScrollTransition.stop();
            lyricScrollTransition = null;
        }
        if (lyricsLinesContainer != null) {
            lyricsLinesContainer.setTranslateY(0);
        }
        if (stageIncomingLyricLabel != null) {
            stageIncomingLyricLabel.setText("");
        }
    }

    private void setLyricLabels(String previous,
                                String current,
                                String next) {
        stagePreviousLyricLabel.setText(previous);
        stageCurrentLyricLabel.setText(current);
        stageCurrentLyricLabel.setVisible(!current.isBlank());
        stageNextLyricLabel.setText(next);
        stageIncomingLyricLabel.setText("");
    }

    private String lyricText(int index) {
        return currentLyrics.lineAt(index).map(LyricLine::text).orElse("");
    }

    private void markPlaylistModified() {
        playlistDirty = true;
        updatePlaylistFileLabel();
    }

    private void updatePlaylistFileLabel() {
        String label = currentPlaylistFile == null
                ? "Playlist não salva" : currentPlaylistFile.getFileName().toString();
        playlistFileLabel.setText(label + (playlistDirty ? " · alterações não salvas" : ""));
    }

    private boolean confirmDiscardPlaylistChanges() {
        if (!playlistDirty || playlist.isEmpty()) {
            return true;
        }
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                "A playlist atual possui alterações não salvas. Deseja descartá-las?",
                ButtonType.OK, ButtonType.CANCEL);
        confirmation.setTitle("SuperMídia MIDI Player");
        confirmation.setHeaderText("Abrir outra playlist");
        confirmation.initOwner(playlistListView.getScene().getWindow());
        return confirmation.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    private int firstExistingItemIndex() {
        for (int index = 0; index < playlist.size(); index++) {
            if (playlist.get(index).exists()) {
                return index;
            }
        }
        return -1;
    }

    private FileChooser midiFileChooser(String title) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(
                        "Arquivos MIDI", "*.mid", "*.midi", "*.kar", "*.MID", "*.MIDI", "*.KAR"));
        return chooser;
    }

    private void setInitialDirectory(FileChooser chooser) {
        File directory = null;
        if (currentPlaylistFile != null && currentPlaylistFile.getParent() != null) {
            directory = currentPlaylistFile.getParent().toFile();
        } else if (engine != null && engine.getLoadedFile() != null) {
            directory = engine.getLoadedFile().getParentFile();
        }
        if (directory != null && directory.isDirectory()) {
            chooser.setInitialDirectory(directory);
        }
    }

    private Path ensureM3u8Extension(Path path) {
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return fileName.endsWith(".m3u8") ? path : Path.of(path.toString() + ".m3u8");
    }

    private void showError(String title, String message, Exception exception) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("SuperMídia MIDI Player");
        alert.setHeaderText(title);
        alert.setContentText(message + (exception.getMessage() == null
                ? "" : "\n\nDetalhe: " + exception.getMessage()));
        if (currentSongLabel.getScene() != null) {
            alert.initOwner(currentSongLabel.getScene().getWindow());
        }
        alert.showAndWait();
    }

    private static String formatSemitones(int value) {
        return value > 0 ? "+" + value : Integer.toString(value);
    }

    private static String formatTime(long microseconds) {
        long totalSeconds = Math.max(0, microseconds) / 1_000_000;
        long hours = totalSeconds / 3_600;
        long minutes = (totalSeconds % 3_600) / 60;
        long seconds = totalSeconds % 60;
        return hours > 0
                ? String.format("%d:%02d:%02d", hours, minutes, seconds)
                : String.format("%02d:%02d", minutes, seconds);
    }
}
