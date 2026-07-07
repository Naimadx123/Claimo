# Claimo

Voucher / code redemption plugin for **PaperMC 1.21.1 - 26.2**. Players redeem codes with a
configurable command; each code runs one or more server commands and can gate
redemption behind **requirements** (blocks mined, playtime, …). The requirement
system is a public API so addons can plug in new checks — e.g. "follows us on
TikTok / YouTube / X / Instagram".

## Download

- https://modrinth.com/plugin/claimo
- https://builtbybit.com/resources/claimo.115049/
- https://voxel.shop/product/9982/claimo

## Building

```bash
./gradlew build
```

The shaded plugin jar is written to `build/libs/Claimo-v<version>.jar`.

## Commands & permissions

| Command | Description | Permission |
| --- | --- | --- |
| `/<command>` | Open the paginated voucher GUI (players); print usage (console) | `claimo.use` (default: everyone) |
| `/<command> <voucher>` | Redeem a voucher | `claimo.use` (default: everyone) |
| `/<command> create` | Open the in-game code creator dialog (1.21.7+) | `claimo.admin` (default: op) |
| `/<command> reload` | Reload config and voucher files | `claimo.admin` (default: op) |

`<command>` defaults to `code` and is set by the `command:` config key (alias:
`/claimo`). Voucher names tab-complete. Running the command with no argument
opens a paginated GUI listing the available codes; clicking one redeems it.
Vouchers with `hide: true` are excluded from both the GUI and tab-completion
(they remain redeemable by typing the code directly).

## Creating codes in-game (1.21.7+)

`/<command> create` opens a multi-page **Dialog** wizard. Page 1 is the code itself
(name, command with `%player%` support, run-as-console, hide-from-list, and an optional
usage limit — max uses with a per-player / shared toggle). The
following page(s) list every **registered** requirement type — built-in *and* any
added by addons — each with an on/off toggle and its configurable fields; toggle the
ones you want and set their values. Requirements are paginated, so if there are too
many for one page the wizard adds another. Use « Back / Next » to move between pages
and **Create** on the last page to write `vouchers/<name>.yml` and reload. For advanced
options (multiple commands, whitelist/blacklist) edit the generated file.

The Dialog API only exists on Paper **1.21.7+**. On 1.21.1–1.21.6 the creator is
automatically disabled (the plugin still loads and everything else works);
`/<command> create` reports that it's unavailable and you author voucher files
manually. The jar is built against the 1.21.8 API but keeps `api-version: 1.21`,
and all Dialog code is isolated so it's never loaded on older servers.

## Configuration

Config is split across a few files in the plugin's data folder, each created on
first run. Edit them and run `/<command> reload`.

```
plugins/Claimo/
├── config.yml            # core settings
├── messages.yml          # all player-facing text (MiniMessage)
├── gui.yml               # the voucher list GUI
└── vouchers/             # one .yml file per voucher; the file name is the code
    └── test.yml
```

### `config.yml`

```yaml
# Command players type to redeem. Set to "kod" for /kod <voucher>.
# Changing this requires a server restart.
command: code
# Open the paginated voucher GUI when the bare command is run with no argument.
gui-list-enabled: true

# Where redemption counts (global pools and per-player usage) are stored.
# type: yaml | sqlite | mysql | postgresql | mongodb
#   yaml   -> plugins/Claimo/usage.yml   (no setup, single server)
#   sqlite -> plugins/Claimo/data.db     (no setup, single server)
#   mysql / postgresql / mongodb -> shared database (multi-server friendly)
# MariaDB is served by the mysql backend (its driver speaks the MariaDB protocol),
# so "mysql" or "mariadb" both work.
# host/port/database/username/password/table-prefix apply to the network backends.
# For mongodb you may instead set `uri` to a full connection string
# (e.g. mongodb+srv://...); when set it overrides host/port/credentials, while
# `database` selects the database and `table-prefix` prefixes the collections.
# Changing the storage type requires a server restart.
storage:
  type: yaml
  uri: ""
  host: localhost
  port: 3306
  database: claimo
  username: root
  password: ""
  table-prefix: claimo_
  pool-size: 10
```

### `messages.yml`

Placeholders: `<command>`, `<voucher>`, `<type>`, and `<description>` (the
per-requirement line injected into `requirement-met` / `requirement-unmet`).

```yaml
prefix: "<gray>[<aqua>Claimo</aqua>]</gray> "
usage: "<gray>Redeem a reward code with <white>/<command> <code></white>."
no-such-voucher: "<red>Voucher code <white><voucher></white> was not found."
players-only: "<red>This command can only be used by a player."
requirements-not-met: "<red>Requirements for <white><voucher></white>:"
requirement-met: "<green>✔</green> <gray><description></gray>"
requirement-unmet: "<red>✖</red> <white><description></white>"
requirement-unavailable: "Requirement '<type>' is unavailable."
requirement-error: "Failed to check this requirement."
requirement-blocks-mined: "Mine blocks: <mined>/<amount>"
requirement-playtime: "Spend time on the server: <played>/<required>"
requirement-account-age: "Account age: <current>/<required> days"
success: "<green>Successfully redeemed the code <white><voucher></white>!"
reloaded: "<green>Claimo configuration has been reloaded."
```

When a redeem fails, the whole requirement checklist is printed — met lines use
`requirement-met`, unmet lines use `requirement-unmet`, each wrapping the
requirement's own `<description>`.

### `gui.yml`

Title placeholders: `<page>`, `<pages>`. `voucher-name`/`voucher-lore`
placeholders: `<voucher>` and `<expires>` (human-friendly remaining time, or
`never`). Set `filler: NONE` for no background. `rows` is clamped to 2–6 (the
bottom row holds the page navigation).

```yaml
title: "<dark_gray>Available codes (<page>/<pages>)"
rows: 6
filler: GRAY_STAINED_GLASS_PANE
voucher-material: PAPER
voucher-name: "<aqua><voucher>"
voucher-lore:
  - "<gray>Click to redeem this code."
previous-material: ARROW
previous-name: "<yellow>« Previous page"
next-material: ARROW
next-name: "<yellow>Next page »"
```

### `vouchers/<code>.yml`

Each file in `vouchers/` defines one voucher; the file name (without `.yml`) is
the code players redeem. Add a code by dropping in a new file.

```yaml
# vouchers/test.yml  →  redeemed with /code test

# One command (string) or several (list). A leading "/" is optional.
# %player% is replaced with the player's name. If PlaceholderAPI is installed,
# any other %placeholder% (e.g. %player_uuid%, %vault_eco_balance%) is resolved too.
cmd: "lp user %player% parent addtemp vip 7d"
# Run as console (true) or as the player (false). Default: true.
console: true
# Hide from the GUI and tab-completion (still redeemable by code). Default: false.
hide: false
# Optional expiry as a human-friendly duration: s (seconds), m (minutes),
# h (hours), d (days), w (weeks) — e.g. 500s, 10m, 5d, 10w, or combined 1d12h.
# Counted from `created` if present (epoch millis, written by the in-game creator),
# otherwise from the file's last-modified time. Omit for a code that never expires.
expires: 30d
# Optional redemption limit. Omit for unlimited.
#   mode: global      -> shared pool of `amount` one-time redemptions: the first
#                        `amount` distinct players each redeem once, then it's gone
#         per-player  -> each player may redeem `amount` times (no shared pool)
#   amount: pool size (global) or per-player allowance (per-player). Default 1.
limit:
  mode: per-player
  amount: 1
# All requirements must pass before the code is redeemable. Optional.
requirements:
  - type: blocks_mined
    amount: 100
    # Optional: only blocks of these material types are counted.
    whitelist:
      - DIAMOND_ORE
      - DEEPSLATE_DIAMOND_ORE
    # Optional: blocks of these material types are never counted.
    # blacklist:
    #   - STONE
  - type: playtime
    seconds: 3600
  - type: messages_sent
    amount: 25
    min-length: 10
    delay-seconds: 20
  - type: account_age
    days: 7
  - type: permission
    permissions: [claimo.vip, claimo.mvp]   # must have at least one
    denied-permissions: [claimo.redeemed]   # must have none
  - type: rank
    denied-ranks: [admin, owner]            # staff can't redeem this one
```

### Built-in requirement types

| `type` | Parameters | Meaning |
| --- | --- | --- |
| `blocks_mined` | `amount: <int>`, `whitelist: <material list>` (optional), `blacklist: <material list>` (optional) | Player has broken at least `amount` blocks (tracked by Claimo, persists across restarts). With a `whitelist`, only those material types count; with a `blacklist`, those material types are never counted. Material names are Bukkit constants, e.g. `DIAMOND_ORE`. |
| `playtime` | `seconds: <int>` | Player's total time on the server is at least `seconds` (vanilla play-time statistic). |
| `messages_sent` | `amount: <int>`, `min-length: <int>` (default 10), `delay-seconds: <int>` (default 20) | Player has sent at least `amount` qualifying chat messages. A message only counts if it is at least `min-length` characters and at least `delay-seconds` have passed since their last counted message — this stops players spamming short messages to farm the requirement. Each distinct (`min-length`, `delay-seconds`) pair is tracked separately, so different vouchers can use different rules. |
| `account_age` | `days: <int>` | Player's account first joined the server at least `days` days ago — gates rewards away from brand-new accounts and alts. |
| `permission` | `permissions: <node list>`, `denied-permissions: <node list>` | Player must have **at least one** of `permissions` (omit it for no positive requirement) and **none** of `denied-permissions`. Use the denied list to lock a code away from players who already hold a permission. Both accept a YAML list or a comma-separated string; the singular `permission` / `denied-permission` keys also work. |
| `rank` | `ranks: <group list>`, `denied-ranks: <group list>` | Same as `permission` but for permission groups resolved via **Vault**: player must be in at least one of `ranks` and in none of `denied-ranks`. Requires a Vault provider (LuckPerms, etc.); without one a positive `ranks` requirement can't pass. The singular `rank` / `denied-rank` keys also work. |

Claimo stores a running total of blocks mined per player in their PDC. To keep
player data small, a **per-material** counter is only kept for materials that
actually appear in some voucher's `blocks_mined` whitelist/blacklist — every
other block just bumps the total. The total is always exact; a material's
per-type count begins accruing from when it first appears in the config.

## API — adding your own requirements

Addons extend Claimo by registering new requirement **types**. A type is just a
key (used in `config.yml`) bound to a factory that builds a `Requirement`.

### 1. Depend on Claimo

Compile against the published API artifact (it ships only the API classes; the
Claimo plugin provides the implementation at runtime):

```kotlin
repositories {
    maven("https://repo.vao.zone/releases")   // or /snapshots for -SNAPSHOT versions
}
dependencies {
    compileOnly("zone.vao:claimo-api:1.0-SNAPSHOT")
}
```

`plugin.yml`:

```yaml
depend: [Claimo]   # or softdepend: [Claimo]
```

Depending on Claimo guarantees your plugin enables *after* it, so the API is ready.

### 2. Implement a `Requirement`

`Requirement.check(...)` returns a `CompletableFuture<RequirementResult>`, so
network calls (a social-media follow check, a web API, …) run off the main
thread. Return `RequirementResult.satisfied(description)` or
`RequirementResult.unsatisfied(description)`. The `description` is the line shown
in the requirement checklist (wrapped in `requirement-met` / `requirement-unmet`
from `messages.yml`), so phrase it neutrally — e.g. `Follow @Naimadx123 on TikTok`.

```kotlin
import net.kyori.adventure.text.Component
import zone.vao.claimo.requirement.Requirement
import zone.vao.claimo.requirement.RequirementContext
import zone.vao.claimo.requirement.RequirementResult
import java.util.concurrent.CompletableFuture

class TikTokFollowRequirement(private val account: String) : Requirement {
    override fun check(context: RequirementContext): CompletableFuture<RequirementResult> {
        return myTikTokClient.isFollowing(context.player.uniqueId, account).thenApply { follows ->
            val description = Component.text("Follow @$account on TikTok")
            if (follows) RequirementResult.satisfied(description)
            else RequirementResult.unsatisfied(description)
        }
    }
}
```

### 3. Register the type

From your plugin's `onEnable`:

```kotlin
import zone.vao.claimo.ClaimoApi
import zone.vao.claimo.requirement.RequirementInput

ClaimoApi.registerRequirement(
    "tiktok_follow",
    { cfg -> TikTokFollowRequirement(cfg.getString("account")!!) },
    // Optional: makes the requirement appear (and be configurable) in /code create.
    listOf(RequirementInput.TextInput("account", "TikTok account")),
)
```

`cfg` is the `RequirementConfig` for that entry; read your own parameters from it
(`getString`, `getInt`, `getLong`, `getDouble`, `getBoolean`, `getStringList`,
`has`). Throwing inside the factory marks the requirement as failed for that
redeem rather than crashing the command.

The optional `inputs` list (`RequirementInput.NumberInput` / `TextInput` / `BoolInput`)
tells the in-game creator what fields to render for your type and which config keys to
write them to. Omit it (the two-argument overload) and the type still works in files and
appears in the creator as a plain on/off toggle with no parameters.

### 4. Use it in a voucher

```yaml
# vouchers/social_reward.yml
cmd: "give %player% diamond 5"
requirements:
  - type: tiktok_follow
    account: vao.zone
```

Requirements are resolved **lazily** on each redeem, so registering a type after
Claimo has already loaded its config (the normal case for addons) works fine.

### Events

Listen to these (`zone.vao.claimo.event`) like any Bukkit event:

| Event | When | Notes |
| --- | --- | --- |
| `PlayerRedeemVoucherEvent` | Requirements passed, before commands run | `Cancellable` — cancel to block the redemption (no commands, no success message). Exposes `player`, `voucher`. |
| `VoucherRedeemedEvent` | After the commands were dispatched | Informational. Exposes `player`, `voucher`. |

### API surface

`zone.vao.claimo.ClaimoApi`

| Member | Purpose |
| --- | --- |
| `registerRequirement(type, factory)` | Register a requirement type. |
| `unregisterRequirement(type)` | Remove a registered type. |
| `requirements` | The `RequirementRegistry` (`isRegistered`, `types`, …). |
| `stats` | `ClaimoStats` — read-only `blocksMined(player)`, `blocksMined(player, whitelist, blacklist)`, `playtimeSeconds(player)`. |
| `vouchers()` / `voucher(id)` | List loaded vouchers / look one up. |
| `redeem(player, voucherId)` | Run the full redeem flow programmatically. |
| `reload()` | Reload config and voucher files. |
