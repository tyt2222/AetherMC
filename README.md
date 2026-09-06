# AetherMC / SkyblockGenerators

Paper **1.21.4** / **Java 21** skyblock plugin with islands, protection, generators, economy, shop, minions, player market, and milestones.

## Stack

- Build file: `pom.xml`
- API: Paper `1.21.4-R0.1-SNAPSHOT`
- Java: 21
- Output jar: `target/SkyblockGenerators.jar`
- Local test server: `test-server/`
- Test script: `dev_run.ps1`

## Commands

- `/island create|home|info|invite|kick|leave|members` or `/is ...`: island and coop management.
- `/lobby`: teleport to lobby.
- `/shop`: open generator/block/minion shop.
- `/market` or `/ah`: open player market.
- `/market sell <price>`: list the held item stack for sale.
- Market UI has `All Listings`, `My Listings`, previous/next page, buy, and cancel-own-listing click behavior.
- `/rank set <player> <rank>` or `/setrank set <player> <rank>`: admin, set player rank.
- `/trash`: open trash inventory.
- `/milestones`: view progress and limits.
- `/generator give <player> <type> [amount]`: admin, give generators.
- `/adminreset <player>`: admin, reset player progress.
- `/opme`: test-only OP command; valid for local testing.

## Edit Map

- Main plugin/registers: `src/main/java/br/com/seuservidor/skyblock/SkyblockPlugin.java`
- Generator/shop config: `src/main/resources/config.yml`
- Commands/permissions: `src/main/resources/plugin.yml`
- Islands/positions/base protection: `src/main/java/br/com/seuservidor/skyblock/IslandManager.java`
- Island model: `src/main/java/br/com/seuservidor/skyblock/Island.java`
- Island command: `src/main/java/br/com/seuservidor/skyblock/IslandCommand.java`
- Generators/drops/money items: `src/main/java/br/com/seuservidor/skyblock/GeneratorManager.java`
- Player market/auction house: `src/main/java/br/com/seuservidor/skyblock/AuctionManager.java`
- Ranks, chat prefixes, TAB names/header/footer: `src/main/java/br/com/seuservidor/skyblock/RankManager.java`
- Mandatory resource pack prompt/status: `src/main/java/br/com/seuservidor/skyblock/ServerResourcePackManager.java`
- Protection, damage, hunger, void, respawn: `src/main/java/br/com/seuservidor/skyblock/ProtectionListener.java`
- Economy/balance: `src/main/java/br/com/seuservidor/skyblock/EconomyManager.java`
- Money deposit listener: `src/main/java/br/com/seuservidor/skyblock/EconomyListener.java`
- Shop menus/prices usage: `src/main/java/br/com/seuservidor/skyblock/ShopCommand.java`
- Minions/collectors: `src/main/java/br/com/seuservidor/skyblock/MinionManager.java`
- Milestones/limits/progress: `src/main/java/br/com/seuservidor/skyblock/MilestoneManager.java`
- Lobby: `src/main/java/br/com/seuservidor/skyblock/LobbyManager.java`
- Help book: `src/main/java/br/com/seuservidor/skyblock/HelpCommand.java`
- Admin reset: `src/main/java/br/com/seuservidor/skyblock/AdminResetCommand.java`

## Runtime Data

Generated at runtime inside `plugins/SkyblockGenerators/` on the server:

- `config.yml`: runtime config copy.
- `islands.yml`: island owners and centers.
- `generators.yml`: placed generators.
- `economy.yml`: balances.
- `milestones.yml`: progress.
- `auctions.yml`: player market listings.
- `ranks.yml`: player ranks.

## Config Keys

- `generators`: generator types, intervals, money value, block type.
- `generator-output-search-limit`: max blocks searched above a generator.
- `data-save-interval-seconds`: delayed save interval for dirty data.
- `shop.generators`: generator prices.
- `shop.blocks`: block unit prices.
- `shop.minions`: minion prices.
- `ranks`: rank prefixes and suggested VIP prices.
- `resource-pack`: mandatory pack URL, SHA-1, prompt.
- `limits`: per-island item and entity caps.

## Build/Test

```powershell
.\dev_run.ps1
.\dev_run.ps1 -BuildOnly
```

O script prepara automaticamente o Maven local quando necessário. Use
`-BuildOnly` para compilar e copiar o JAR para `test-server\plugins` sem iniciar o servidor de testes.

## Git

Repository:

```text
https://github.com/tyt2222/AetherMC.git
```

Local identity:

```powershell
git config user.name "tyt2222"
git config user.email "fabio.paiva.dev@gmail.com"
```

Project authorship:

```text
All project code, commits, and releases are attributed to tyt2222.
```

## Known Notes

- `/opme` is intentionally kept for the test environment.
- Desative `test-mode.opme-enabled` no `config.yml` runtime antes de produção.
- O script de testes define `enforce-secure-profile=false` para permitir chat em clientes locais sem chave pública de perfil. Em produção, revise essa opção junto com `online-mode` e os requisitos de segurança das contas.
- Global no-damage and no-hunger are intentional for now.
- `logo.png` is stored in the project root and packaged as a custom-font asset used by the player list header.
- Generated pack: `resource-pack/AetherMC-resource-pack.zip`.
- The pack maps a 128x64 version of `logo.png` to glyph `\uE238` in the custom `aethermc:logo` font; the TAB header renders that font explicitly.
- `dev_run.ps1` serves the resource pack locally at `http://127.0.0.1:8765/` for local testing; production servers must change `resource-pack.url` to a public direct HTTPS URL.
- If the ZIP changes, calculate its SHA-1 with `(Get-FileHash .\resource-pack\AetherMC-resource-pack.zip -Algorithm SHA1).Hash.ToLower()` and update `resource-pack.sha1`.
- The player list logo is rendered by the `\uE000` glyph from `assets/aethermc/font/logo.json`; clients must accept the pack and reconnect/reload it before the glyph appears.
- Shop prices live in `src/main/resources/config.yml`.
- Economy uses `long` balances.
- Economy and milestones save on interval/on shutdown instead of every small event.
- Sidebar scoreboard is split into `STATISTICS` and `PLAYER`.
- Milestones show next-level remaining progress.

## Future Ideas

- Island coops, invites, roles, and visitor permissions.
- Island upgrades: size, generator slots, multiplier, spawn options.
- Generator/minion upgrade menus.
- Configurable island auto-sell.
- Rankings for money, island level, playtime, generators, and market sales.
- Daily/weekly quests.
- Prestige with voluntary reset and permanent bonuses.
- Better market UI flow using chat/anvil/sign price input.
- Player warps/public islands.
- Crates and rewards with careful economy balance.
