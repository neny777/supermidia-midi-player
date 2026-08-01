# Backlog do SuperMídia MIDI Player

## Direção visual aprovada — implementação autorizada

**Resolução de referência:** 1366 × 768.

- Usar fundo escuro com o laranja da marca como cor de destaque.
- Montar a marca no cabeçalho com o símbolo oficial, `SUPER` em Arial branco e
  negrito e `MÍDIA` em Arial branco e itálico, sem o complemento `MIDI PLAYER`.
- Usar Source Sans 3 como fonte preferencial da interface, com fallback para as fontes do sistema.
- Remover do cabeçalho `AO VIVO`, `Abrir MIDI`, seleção de saída MIDI e `Piano`.
- Manter no cabeçalho o nome completo da música atual, permitir até duas linhas
  também para a próxima música e preservar `PANIC`.
- Concentrar a inclusão de arquivos MIDI na tela Playlist.
- Manter na navegação apenas Ao vivo, Playlist, Piano e Configurações.
- Piano como aba de navegacao, no mesmo padrao de Playlist e Configuracoes.
- Remover as telas independentes Mixer e Letras.
- Exibir três frases sincronizadas na tela principal, todas com a mesma cor,
  tamanho e peso, mantendo a frase atual na posição central.
- Exibir os 16 canais do mixer simultaneamente, sem repetir nomes longos de arquivo.
- Destacar o banco 1–8 ou 9–16 controlado pela SMC-Mixer.
- Colocar transporte antes de Transpose, Velocidade e Volume.
- Abrir Tom, Velocidade e Volume em sliders verticais temporários, mantendo
  apenas seus valores compactos na barra inferior.
- Iniciar o volume geral em 100%.
- Usar apenas um controle `Autoplay`: desligado significa avanço manual e a
  última opção escolhida deve ser restaurada na próxima execução.
- Remover `Repetir` e a indicação textual `Tocando`.
- Limitar visualmente nomes longos de saída MIDI, preservando o nome completo em dica de mouse e Configurações.
- Adicionar uma janela simples Sobre em Configurações.

## Dependências futuras

### Banco da SMC-Mixer

O diagnóstico da entrada MIDI, em Configurações, responde a esta pergunta: pressionar o Shift
e observar se aparece um controle novo na lista. Se aparecer, ele pode ser vinculado
diretamente a `Mixer · Alternar banco`. Se não aparecer, o Shift só altera as mensagens dos
demais controles e o banco precisa de um botão próprio — é o que o layout de referência
assume, com uma nota dedicada à troca de banco.

### Piano — concluído

Aba própria mostrando as notas em execução num teclado de 88 teclas, com a lista das
alturas soando. Substitui o reconhecimento de acordes, que foi abandonado: as mesmas
notas nomeiam acordes diferentes conforme o contexto, e listar o que soa não inventa
nada que o algoritmo não possa saber.

