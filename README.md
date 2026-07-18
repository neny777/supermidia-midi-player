# SuperMidia MIDI Player

Aplicativo multiplataforma para apresentações ao vivo com reprodução MIDI, letras sincronizadas, playlist, mixer de 16 canais e suporte a controladores físicos.

## Plataforma técnica

- Java 21 LTS
- JavaFX 21 LTS
- Gradle Wrapper
- Java Sound MIDI
- FXML e CSS para a interface

## Estado atual

A branch `rewrite-javafx` contém a nova fundação do produto. A versão inicial em Swing permanece preservada no histórico da branch `main`.

O player já permite:

- detectar e selecionar saídas MIDI disponíveis;
- abrir arquivos `.mid`, `.midi` e `.kar`;
- tocar, pausar, parar e mover a posição da música;
- alterar velocidade entre 50% e 150%;
- transpor notas melódicas entre -12 e +12 sem alterar o canal de bateria;
- controlar o volume geral do acompanhamento;
- enviar `All Sound Off` e `All Notes Off` com o botão Panic.
- montar, remover e reordenar um repertório;
- abrir e salvar playlists UTF-8 no formato `.m3u8`;
- preparar a próxima música automaticamente no modo Manual;
- iniciar a próxima música automaticamente no modo Automático.
- extrair letras incorporadas em eventos `Lyric` de MIDI e `Text` de KAR;
- acompanhar linha anterior, atual e próxima usando o relógio do sequenciador;
- exibir a letra completa em uma área separada da interface.
- analisar os 16 canais, nomes de pista, instrumentos GM e volumes originais;
- controlar volume, Mute e Solo por canal em bancos 1–8 e 9–16;
- visualizar atividade MIDI e restaurar a mixagem original da música.

A saída de letras para um segundo monitor, os perfis de mixagem por música e o mapeamento da SMC-Mixer serão implementados nas próximas etapas.

## Executar

No Windows:

```powershell
.\gradlew.bat run
```

No Linux:

```bash
./gradlew run
```

## Direção do produto

O aplicativo será orientado a palco, com transporte sempre visível, letras em destaque, playlist manual ou automática, controles de transpose e velocidade, mixer de 16 canais e integração com o Roland VIMA JM-5 e a SMC-Mixer.
