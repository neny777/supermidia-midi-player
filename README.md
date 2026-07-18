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
