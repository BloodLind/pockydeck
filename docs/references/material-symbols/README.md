# Material Symbols from the HTML preview

The launcher uses the Material Symbols Outlined glyphs named in [the HTML design source](../design-source.txt). The user's 8 September 2026 correction restores its lighter icon weight. Official static SVG exports were retrieved on that date; exact URLs and SHA-256 hashes are in [sources.json](sources.json).

| Role | Symbol | Weight | Fill |
| --- | --- | --- | --- |
| Home | `cottage` | 200 | 0 |
| Library | `grid_view` | 200 | 0 |
| Apps | `apps` | 200 | 0 |
| Favorites | `grade` | 200 | 0 |
| Settings | `tune` | 200 | 0 |
| Search | `search` | 200 | 0 |
| More filters | `expand_more` | 200 | 0 |
| Sort | `sort` | 200 | 0 |
| Temperature | `device_thermostat`, `thermometer_loss`, `thermometer_gain` | 200 | 0 |
| Memory | `memory` | 200 | 0 |
| Storage | `sd_card`, `sd_card_alert` | 200 | 0 |
| Wi-Fi | `wifi`, `wifi_off` | 200 | 0 |
| Battery | `battery_0_bar` through `battery_6_bar`, `battery_full`, `battery_unknown` | 200 | 0 |

The SVGs are unmodified reference assets. Android resources in `core/designsystem/src/main/res/drawable/ic_material_*.xml` copy their path data without redrawing or adding a stroke. A group translation of 960 converts the source `viewBox="0 -960 960 960"` to Android's positive viewport. Compose tints these vectors using existing theme colors. Layout sizes, touch targets and accessibility labels stay with the existing controls; no runtime font download is required.

Material Symbols are distributed under Apache 2.0, as documented in the [official guide](https://developers.google.com/fonts/docs/material_symbols). The unmodified upstream license is bundled in [the APK's license assets](../../../app/src/main/assets/licenses/material-symbols-LICENSE.txt).
