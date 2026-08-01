# SuperMídia MIDI Player — contexto para o agente

Aplicativo desktop para apresentações ao vivo: reprodução MIDI, letras sincronizadas,
playlist, mixer de 16 canais e controle por controladora MIDI física.

Responda e escreva comentários, mensagens de commit e texto de interface em **português do Brasil**.

## Plataforma

- Java 21 LTS, JavaFX 21, Gradle Wrapper, Java Sound MIDI, FXML + CSS
- Projeto modular: `module-info.java` em `src/main/java`
- Empacotamento com `jpackage` (scripts `package-*.bat` e `package-ubuntu.sh`)
- Desenvolvimento no Windows; o pacote Linux precisa ser gerado em uma máquina Linux
- Repositório: https://github.com/neny777/supermidia-midi-player (branch `main`)

```powershell
.\gradlew.bat test     # testes
.\gradlew.bat run      # executar
```

## Estrutura

```
br.com.supermidia.app       SuperMidiaApplication, MainController (controller único do FXML)
br.com.supermidia.core      PlaybackMode
br.com.supermidia.lyrics    MidiLyrics, LyricLine — letras embutidas em MIDI/KAR
br.com.supermidia.midi      motor de reprodução, entrada MIDI, mapeamento de controles
br.com.supermidia.mixer     análise dos 16 canais e instrumentos GM
br.com.supermidia.playlist  playlist .m3u8
```

Interface em `src/main/resources/br/com/supermidia/app/`: `main-view.fxml` e `theme.css`.
Fundo escuro com laranja da marca, fonte Source Sans 3, resolução de referência 1366 × 768.

`MainController` é grande (mais de 1900 linhas) e concentra toda a interface. Ao mexer nele,
prefira edições cirúrgicas a reescritas. Toda função nova exposta ao FXML precisa de
`fx:id`/`onAction` correspondente — erro aí só aparece em tempo de execução.

## Controladora MIDI — estado atual

O alvo é uma **M-Vave SMC-Mixer** ligada por **USB-C** (o Java Sound não enxerga BLE MIDI
no Windows sem porta virtual intermediária).

Classes envolvidas, em `br.com.supermidia.midi`:

| Classe | Papel |
|---|---|
| `MidiInputMonitor` | abre a porta de entrada e enfileira `MidiControlMessage` |
| `MidiControlMessage` | mensagem crua: command, channel, data1, data2 |
| `MidiSignature` | identidade de um controle, sem o valor; funde Note Off em Note On |
| `MidiBinding` | vínculo de um controle a uma função; modos TRIGGER, ABSOLUTE, RELATIVE |
| `MidiLearnAction` | as 40 funções mapeáveis (transporte, gerais, 8 slots de mixer) |
| `MidiInputDiagnostics` | agrupa o que chega e classifica cada controle |
| `MidiMappingSession` | máquina de estados do assistente de mapeamento |
| `ControllerProfile` | conjunto de vínculos, gravável em `.smprofile` |
| `SmcMixerLayout` | layout de referência e tabela para o editor da controladora |

A fila de entrada é drenada em `MainController.refreshMidiInputActivity()`, chamada por um
`Timeline` de 150 ms. Toda mensagem passa pelo diagnóstico e então segue por um de três
caminhos: assistente em andamento, MIDI Learn avulso em andamento, ou execução dos vínculos.

Os vínculos são persistidos em `Preferences` com o prefixo `midiBinding.<actionId>`.

### Decisão de projeto importante

A SMC-Mixer **não tem mapa de fábrica documentado e confiável** — cada controle é
reprogramável pelo **MidiSuite**, o editor da M-VAVE (em `m-vave.com/download`; o CubeSuite,
apesar de citado em fóruns, é para pedaleiras e loopers e não lista a SMC-Mixer). Por isso o projeto não
embute um "preset de fábrica" adivinhado. Existem dois caminhos legítimos:

1. **Assistente de mapeamento** — aprende o que a controladora já envia, função por função.
2. **Layout de referência** (`SmcMixerLayout`) — o player define o que espera receber e o
   usuário programa a controladora uma vez com essa tabela. Faders em CC 20–27, master
   CC 28, tom CC 29, velocidade CC 30, botões nas notas 36–61, tudo no canal 1. A faixa
   CC 20–31 foi escolhida porque o General MIDI não a reserva para nada.

Ao mexer no layout, mantenha `SmcMixerLayoutTest` verde: ele garante cobertura de todas as
40 funções, ausência de controles repetidos e faders dentro da faixa livre.

Três funções são controles de gatilho que evitam gastar knobs ou botões extras:
`PLAY_STOP_TOGGLE` (um botão para tocar e parar, útil em controladora sem Stop físico)
e `TRANSPOSE_UP`/`TRANSPOSE_DOWN` (um semitom por toque, para ajustar a tonalidade
ao vivo sem usar o knob contínuo de tom).

### Detalhes do hardware que afetam o código

- Os faders suavizam o valor enviado; em movimento rápido com inversão de direção o extremo
  0 ou 127 pode não ser alcançado. Por isso os faders do mixer usam *pickup* (`mixerPickupArmed`)
  em vez de salto direto.
- Em modo DAW os faders enviam **pitch bend** e os encoders usam protocolo relativo próprio.
  `MidiBinding` já trata pitch bend como contínuo absoluto de 14 bits.
- Os LEDs têm mapeamento de notas fixo no firmware e não respondem a realimentação MIDI do
  host em modo CC. Não há feedback visual bidirecional.

## Pendências

- **Banco 1–8 / 9–16 e o botão Shift** — descobrir, pelo diagnóstico, se o Shift envia
  mensagem própria (vira botão de banco) ou apenas altera as mensagens dos demais controles
  (o banco precisa de botão dedicado, como assume o layout de referência).
- **Modo relativo no assistente** — `MidiInputDiagnostics` já sugere `RELATIVE`, mas o
  assistente sempre aprende contínuos como `ABSOLUTE`. Se os encoders forem relativos,
  usar a sugestão do diagnóstico.
- **Letras em segundo monitor** — janela separada, com posição, tamanho e monitor persistidos.
- **Perfis de mixagem por música**.

### Abandonados

- **Acordes** — o reconhecimento automático foi descartado. As mesmas notas nomeiam
  acordes diferentes conforme o contexto (Am7 ou C6), e sem tonalidade não há como
  escolher entre uma altura e sua enarmonia. Em vez de nomear com margem de erro, a
  tela do piano mostra o que soa e quem lê decide.
- **Prévia** — descartada. O controle e o bloco `ACORDE` do rodapé foram removidos
  da interface.

## Tela do piano

Aba de navegação, ao lado de Ao vivo, Playlist e Configurações — não é janela separada.
Serve para identificar harmonia em ensaio, não para tocar: não responde a cliques.

A fonte de dados é `MidiTransformReceiver.soundingNotes(boolean)`, que lê o
`activeOutputNotes` já mantido para o Note Off. Duas propriedades vêm de graça daí, e
há teste para ambas: as alturas são as de **saída**, portanto com transpose aplicado, e
canais em mute **não constam**, porque suas notas nunca chegam a ser registradas.

| Classe | Papel |
|---|---|
| `SoundingNoteNames` | Lista as alturas soando, do grave para o agudo — o baixo primeiro, que é o que distingue C de C/E |
| `PianoKeyboard` | 88 teclas em `Canvas`, na proporção real (tecla branca 1:6) |

`PianoKeyboard` é um `Canvas` redimensionável, e isso tem duas armadilhas que já
custaram falhas em execução: **não vincule** `width`/`height` (o layout chama `resize()`,
que atribui a elas) e **sobrescreva `maxWidth`/`maxHeight`** devolvendo valor ilimitado,
senão o contêiner conclui que o Canvas quer tamanho zero e nada aparece.

Ver `BACKLOG.md` para a direção visual aprovada.

## Testes

JUnit 5, em `src/test/java`. Cobrem lógica pura; não há teste de interface JavaFX.

`PlaylistFileServiceTest` falha em ambientes cujo `sun.jnu.encoding` não é UTF-8, porque usa
caminhos acentuados (`Músicas`, `Canção 01.mid`). No Windows do projeto ele passa. Se falhar
em contêiner Linux, é o ambiente, não o código.

## Privacidade

A pasta `midis/` contém repertório particular. Não entra em pacotes nem vai para o GitHub —
está no `.gitignore` e deve continuar assim.
