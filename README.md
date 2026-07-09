# Claimo

Voucher and reward code plugin for Paper 1.21.1+ (and Folia). Players redeem codes with a
command or an in-game dialog, and every code can run commands, gate itself behind
requirements (playtime, blocks mined, permissions, ranks, or any PlaceholderAPI value),
limit how many times it can be used, and expire after a set time. Requirements are a public
API, so addons can add their own checks.

## Links

- Documentation: https://claimo.vao.zone (setup, config, requirements, placeholders, API)
- Modrinth: https://modrinth.com/plugin/claimo
- BuiltByBit: https://builtbybit.com/resources/claimo.115049/
- Voxel: https://voxel.shop/product/9982/claimo
- Issues and source: https://github.com/Naimadx123/Claimo

The full documentation lives on the docs site above. This README only covers the basics so
the same content is not duplicated in two places.

## Quick start

1. Drop the jar into `plugins/` and start the server.
2. Edit the files in `plugins/Claimo/` (`config.yml`, `messages.yml`, `gui.yml`, and one file
   per code in `vouchers/`), then run `/code reload`.
3. On Paper 1.21.7+ you can also build codes in game with `/code create` (and `/code edit`,
   `/code delete`).
4. Players redeem with `/code <name>`, or open the code list with `/code`.

## Compatibility

- Paper 1.21.1+ and compatible forks. Folia is supported (same jar).
- Optional soft dependencies: PlaceholderAPI (command placeholders, the `custom` requirement,
  and the `claimo` placeholder expansion) and Vault (the `rank` requirement).
- The in-game dialog creator needs 1.21.7+. On 1.21.1 to 1.21.6 everything else works and you
  author codes in the `vouchers/` files.

## Building

```bash
./gradlew build
```

The build produces two jars in `build/libs/`. The one to install is the shaded plugin jar
with the `v` prefix, `Claimo-v<version>.jar`. The other jar is not shaded and should not be
used on a server.

## For developers

The public API is published separately, so an addon can compile against it without shipping
the whole plugin:

```kotlin
repositories {
    maven("https://repo.vao.zone/releases") // use /snapshots for SNAPSHOT versions
}
dependencies {
    compileOnly("zone.vao:claimo-api:<version>")
}
```

Registering a requirement type, the events, and the full `ClaimoApi` surface are documented
under the API section of the docs site.

## License

See [LICENSE](LICENSE).
