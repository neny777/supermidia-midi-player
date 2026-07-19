# SuperMidia MIDI Player

Aplicativo multiplataforma para apresentações ao vivo com reprodução MIDI, letras sincronizadas, playlist, mixer de 16 canais e suporte futuro a controladores físicos.

## Instalação rápida

Os pacotes gerados são autocontidos: quem for utilizar o programa não precisa instalar Java nem JavaFX.

### Windows — pacote portátil

1. No projeto, dê dois cliques em `package-windows-portable.bat`.
2. Aguarde a mensagem de conclusão.
3. Abra a pasta `build\packages\windows`.
4. Descompacte `SuperMidia-MIDI-Player-0.1.0-windows-x64.zip`.
5. Entre na pasta descompactada e execute `SuperMidia MIDI Player.exe`.

O pacote portátil pode ser copiado para outro computador Windows. A pasta inteira deve ser mantida; não copie apenas o arquivo `.exe`.

### Windows — instalador EXE

Para montar um instalador tradicional, com atalhos no menu Iniciar e na área de trabalho, a máquina de desenvolvimento também precisa do WiX Toolset 3.x.

Depois de instalar o WiX e reiniciar o terminal:

1. Dê dois cliques em `package-windows-installer.bat`.
2. O instalador será criado em `build\packages\windows\installer`.

O instalador deve ser gerado no próprio Windows. O `jpackage` não cria instaladores de outro sistema operacional.

### Ubuntu, Linux Mint e derivados — pacote DEB

O pacote Linux deve ser gerado em uma máquina Linux. Na máquina de desenvolvimento, instale um JDK 21 completo e o `fakeroot`, depois confirme que os comandos `java` e `jpackage` estão disponíveis.

Para obter o projeto no Linux:

```bash
git clone --branch rewrite-javafx https://github.com/neny777/supermidia-midi-player.git
cd supermidia-midi-player
```

No Ubuntu ou Mint que ofereça o OpenJDK 21 nos repositórios:

```bash
sudo apt update
sudo apt install openjdk-21-jdk fakeroot
java -version
jpackage --version
```

Em seguida, dentro da pasta do projeto:

```bash
chmod +x gradlew package-ubuntu.sh
./package-ubuntu.sh
```

O arquivo `.deb` será criado em `build/packages/linux`. Para instalá-lo:

```bash
sudo apt install ./build/packages/linux/supermidia-midi-player_0.1.0-1_amd64.deb
```

Se o nome gerado variar ligeiramente, use o nome exato mostrado na pasta `build/packages/linux`.

## Privacidade do repertório

A pasta local `midis/` e arquivos de repertório não entram nos pacotes e não são enviados ao GitHub. Depois de instalar o programa, os MIDIs podem permanecer em qualquer pasta particular do computador e ser abertos normalmente pelo player.

## Executar durante o desenvolvimento

No Windows:

```powershell
.\gradlew.bat run
```

No Linux:

```bash
./gradlew run
```

## Testes

No Windows:

```powershell
.\gradlew.bat test
```

No Linux:

```bash
./gradlew test
```

## Plataforma técnica

- Java 21 LTS
- JavaFX 21
- Gradle Wrapper
- Java Sound MIDI
- FXML e CSS
- `jpackage` para distribuições autocontidas

## Estado atual

O player já permite:

- detectar e selecionar saídas MIDI disponíveis;
- abrir arquivos `.mid`, `.midi` e `.kar`;
- tocar, pausar, parar e mover a posição da música;
- alterar velocidade entre 50% e 150%;
- transpor notas melódicas entre -12 e +12 sem alterar o canal de bateria;
- controlar volume geral, volume, Mute e Solo por canal;
- enviar `All Sound Off` e `All Notes Off` com o botão Panic;
- montar, salvar e reordenar playlists `.m3u8`;
- avançar manual ou automaticamente na playlist;
- extrair e acompanhar letras incorporadas no MIDI ou KAR;
- analisar e exibir os 16 canais e instrumentos GM.

A saída de letras para um segundo monitor, os perfis de mixagem por música e o mapeamento da SMC-Mixer permanecem nas próximas etapas.
