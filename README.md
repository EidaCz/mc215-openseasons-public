![OpenSeasons](.github/banner.webp)

# MC-215 OpenSeasons CE

Seasons and a configurable calendar for Paper servers. Every world gets its own date and season,
the landscape changes colour on the client, days get longer and shorter through the year, plants,
animals and sky phenomena follow the season and calendar events such as Christmas or Halloween come
with their own mechanics.

This is the public copy of the OpenSeasons plugin, which is part of the non-public MC-215 project.

> **Note:** the public version does not contain the compatibility layer for migrating a server from
> RealisticSeasons (reading its configuration and data, its placeholders, commands and API).

## Features

- **Calendar** – any number of months and days of the week with your own names and lengths, season
  start dates, day and night length per month. A calendar day is a game day, or optionally a real day.
  The date follows the age of the world, so it survives restarts and world copies.
- **Seasons** – winter, spring, summer and fall per world, automatic cycling with five sub-seasons,
  manual setting of the season or date, worlds with seasons turned off.
- **Day and night length** – OpenSeasons drives the world clock by the month (long summer days, long
  winter nights) including skipping the night by sleeping and pausing the time.
- **Landscape colours on the client** (with [packetevents](https://github.com/retrooper/packetevents)) –
  grass, leaves, sky, water and fog by season and biome, colourful autumn patches across forests,
  spring blossom, occasional morning fog in late autumn, snow instead of rain in winter and
  client-side block replacements (e.g. birch leaves turning orange). The world itself is not changed.
- **Sky and particles** – falling leaves, fireflies, shooting stars, winter night sparks, aurora on
  clear winter nights and the occasional UFO fly-over.
- **Animals and monsters** – extra seasonal animals (bees and rabbits in spring, parrots in summer,
  foxes, mooshrooms and bats in fall, wolves, polar bears and snow golems in winter), husks in summer,
  strays in winter, pumpkins on monster heads in fall.
- **Seasonal plants** – flowers, grass, mushrooms, berry bushes, leaf litter, wildflowers, pink petals,
  dry grass, bushes, cactus flowers, firefly bushes and pumpkins appear and vanish with the seasons.
  Every change is journaled and can be restored with `/openseasons restore`.
- **Calendar events** – Christmas (presents to unwrap, decorated spruces, sparkles), New Year
  (firework boxes), Halloween (changed monsters, pumpkin heads, bats, witches with extra potions),
  Easter (hidden eggs, killer bunnies) and any number of your own dated, weekly or daily events with
  messages and console commands.
- **Real calendar** – a world or a region can follow the real astronomical season instead of the game
  calendar, and real date ranges can force a season and events (e.g. Halloween from 25/10 to 4/11
  regardless of the game date).
- **WorldGuard regions** – flags `permanent-season`, `season-effects`, `season-block-changes`,
  `season-mob-spawning`, `season-source` and `real-periods`.
- **Per-player settings** – players can turn the season colours (`/toggleseasoncolors`) and particles
  (`/toggleseasonparticles`) off for themselves.
- **PlaceholderAPI** – `%mc215os_<key>%` for the player's world or `%mc215os_<key>_<world>%`, keys
  `season`, `season_id`, `next_season`, `days_until_next_season`, `season_length`, `season_progress`,
  `sub_season`, `day`, `month`, `month_name`, `year`, `weekday`, `date`, `time`, `enabled`,
  `active_events`, `next_event`, `days_until_next_event`, and `local_season` / `local_season_id` for
  the season at the player's position (regions, real calendar).
- **API** – `OpenSeasonsApi` as a Bukkit service, events `SeasonChangeEvent`, `DayChangeEvent`,
  `CalendarEventStartEvent` and `CalendarEventEndEvent`.

## Requirements

- Paper 26.2 or newer (tested on 26.2 and 26.3), Java 25
- Optional: packetevents (landscape colours), WorldGuard with WorldEdit or FAWE (region flags),
  PlaceholderAPI (placeholders), Multiverse-Core

## Installation

1. Put `OpenSeasons-<version>.jar` into `plugins/` and start the server.
2. Edit the files in `plugins/OpenSeasons/` (`config.yml`, `calendar.yml`, `lang.yml`, `names.yml`,
   `flora.yml`, `real-calendar.yml`, `events/`, `biomes/`) and run `/openseasons reload`.

On the very first start all loaded worlds get seasons with the date by their age; later new worlds
follow `on-new-world-creation` in `config.yml`.

## Commands

| Command | Permission | Description |
|---|---|---|
| `/season [world]` | `mc215os.getinfo` | season, date, time and events of a world |
| `/toggleseasoncolors` | `mc215os.toggleseasons` | season colours on or off for yourself |
| `/toggleseasonparticles` | `mc215os.toggleparticles` | season particles on or off for yourself |
| `/openseasons info [world]` | `mc215os.admin` | configuration sources, calendar, clock and regions |
| `/openseasons set <season> [world]` | `mc215os.admin` | season without changing the date |
| `/openseasons setdate <date> [world]` | `mc215os.admin` | date of a world |
| `/openseasons nextseason [world]` | `mc215os.admin` | next season |
| `/openseasons disable [world]` | `mc215os.admin` | turns seasons off in a world |
| `/openseasons pausetime [world]` | `mc215os.admin` | stops or resumes the time |
| `/openseasons colors [<season> <percent> [fog] \| off] [world]` | `mc215os.admin` | preview of the landscape colours |
| `/openseasons restore <world> [radius] [confirm]` | `mc215os.admin` | restores blocks changed by seasonal plants |
| `/openseasons region [world x z]` | `mc215os.admin` | season settings of regions |
| `/openseasons reload` | `mc215os.admin` | reloads the configuration |
| `/openseasons debug <phenomenon> [player]` | `mc215os.admin` | triggers a phenomenon now (with `debug-enabled: true`) |

OP may use everything.

## Building

```sh
mvn verify
```

The plugin jar is `target/OpenSeasons-<version>.jar`. Versions are numbered `YY.MM-public`
(e.g. `26.10-public`).

## License

GNU General Public License v3.0, see [LICENSE](LICENSE).
