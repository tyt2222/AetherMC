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

- `/island create|home|info` or `/is create|home|info`: player island.
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

## Build/Test

```powershell
.\dev_run.ps1
mvn package
```

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

## Known Notes

- `/opme` is intentionally kept for the test environment.
- Global no-damage and no-hunger are intentional for now.
- `logo.png` is stored in the project root. Vanilla TAB cannot render PNG images without a resource pack/custom font, so TAB uses a styled text header.
- Generated pack: `resource-pack/AetherMC-resource-pack.zip`.
- The pack maps `logo.png` to font glyph `\uE000`; TAB header uses that glyph.
- Set `resource-pack.url` to a direct HTTPS download URL before expecting clients to receive it.
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
