# Backlog do SuperMidia

## BL-001 — Mixer compacto na tela principal

**Status:** proposta aprovada para estudo; não implementar até nova confirmação.

**Objetivo:** otimizar a operação ao vivo em uma tela com resolução mínima de 1366 × 768, sem maximizar a janela e preservando ao máximo o visual atual.

### Comportamento desejado

- Manter os 16 canais do mixer simultaneamente visíveis na tela principal.
- Usar strips mais estreitos, pois os volumes dos arquivos MIDI serão previamente editados e o mouse será apenas uma alternativa de emergência.
- Mostrar em cada strip somente número do canal, instrumento abreviado, atividade MIDI, fader, Mute e Solo.
- Não repetir o nome do arquivo ou nomes longos em todos os canais; deixar informações completas na tela detalhada do Mixer ou em dicas de mouse.
- Manter a tela detalhada do Mixer para ajustes cuidadosos.
- Destacar com um retângulo o banco de oito canais atualmente controlado pela SMC-Mixer:
  - banco 1: canais 1–8;
  - banco 2: canais 9–16.
- Mover o destaque visual quando o estado de Shift/banco da controladora mudar.
- Deixar o banco inativo visível, com destaque visual mais discreto.

### Dependência pendente

Quando a SMC-Mixer chegar, verificar se o botão Shift envia uma mensagem MIDI própria ou se apenas altera as mensagens dos demais controles. Essa descoberta definirá como o indicador de banco será sincronizado.

### Antes de implementar

- Reunir os demais refinamentos de interface.
- Produzir e aprovar um esboço do novo arranjo em 1366 × 768.
- Confirmar que música atual, próxima música, letras, transporte, transpose, velocidade e volume geral continuam legíveis.
