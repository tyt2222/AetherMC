# AetherMC - Mapa do Projeto e Arquitetura

Este documento serve como um índice ("GPS") para guiar a IA e os desenvolvedores sobre a estrutura do projeto. Sempre que for pedir uma alteração, você pode pedir para a IA consultar este arquivo primeiro.

## Estrutura Principal

`src/main/java/br/com/seuservidor/skyblock/`
- **`SkyblockPlugin.java`** → Ponto de entrada do plugin. Registra todos os comandos, eventos e inicializa os gerenciadores.

### Gerenciadores (Managers - Lógica de Dados)
- **`IslandManager.java`** & **`Island.java`** → Sistema de Ilhas (Criação, teleporte, cálculo do centro, proteção e dados `islands.yml`).
- **`GeneratorManager.java`** → Sistema de Geradores (Gera as cédulas de dinheiro).
- **`EconomyManager.java`** → Banco de dados da Economia (Saldos salvos em `economy.yml`).
- **`LobbyManager.java`** → Sistema de Lobby (Mundo do lobby, spawn point, plataforma 3x3).

### Eventos e Proteção (Listeners)
- **`PlayerSessionListener.java`** → Gerencia o que acontece quando o jogador entra (criação automática da ilha, teleporte pro lobby, criação e atualização do Scoreboard e Barra de XP).
- **`ProtectionListener.java`** → Regras do mundo (Imortalidade, cancela a fome, protege contra quebra/colocação de blocos fora da ilha, e gerencia o Auto-Respawn).
- **`EconomyListener.java`** → Detecta quando o jogador clica com o botão direito nas cédulas de dinheiro (papéis) para depositar na conta.

### Comandos (Commands)
- **`IslandCommand.java`** (`/island`) → Comandos básicos da ilha (`create`, `home`, `info`).
- **`GeneratorCommand.java`** (`/generator`) → Comando admin para dar geradores.
- **`ShopCommand.java`** (`/shop`) → Interface de loja (GUI de Baú) que cobra dinheiro da economia para dar geradores.
- **`HelpCommand.java`** (`/help`) → Entrega o livro com o guia do servidor.

---

## Fluxos de Sistemas Principais

### Sistema de Economia e Geradores
**Arquivos envolvidos:** `GeneratorManager`, `EconomyListener`, `EconomyManager`, `ShopCommand`.
- Geradores no chão (`GeneratorManager`) dropam um item (Papel com NBT "money_value").
- Jogador clica no Papel → `EconomyListener` lê o NBT, apaga os papéis, e soma na conta (`EconomyManager`).
- Jogador usa o dinheiro no `/shop` (`ShopCommand`) para comprar mais geradores.
- Tudo isso reflete no placar (`PlayerSessionListener#updateScoreboard`).

### Sistema de Proteção e Morte
**Arquivos envolvidos:** `ProtectionListener`, `IslandManager`.
- Fome e Dano estão desativados no `ProtectionListener`.
- Se o jogador cair no void na ilha, ele é teleportado magicamente de volta (sem morrer).
- Se morrer forçado (ex: `/kill`), a tela de morte é pulada (`player.spigot().respawn()`) e o `PlayerRespawnEvent` manda pra ilha.
