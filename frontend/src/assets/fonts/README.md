# Self-hosted fonts

All fonts are licensed under the SIL Open Font License 1.1.

| File | Family | Source | Size |
| --- | --- | --- | --- |
| `InterVariable.woff2` | Inter | `@fontsource-variable/inter@5.2.5` (latin, variable) | 47 KB |
| `NotoSansSC-GB2312.woff2` | Noto Sans SC | notofonts/noto-cjk `Sans/Variable/TTF/Subset/NotoSansSC-VF.ttf`, subset | 1.8 MB |
| `JetBrainsMono.woff2` | JetBrains Mono | `@fontsource-variable/jetbrains-mono@5.2.5` (latin, variable) | 39 KB |

## Chinese subset

`NotoSansSC-GB2312.woff2` contains:

- all 6,763 GB2312 characters
- ASCII / Latin-1 / general punctuation / CJK punctuation / fullwidth forms
- every non-ASCII character used in `frontend/src` (safety margin for UI copy)

Generated with fonttools:

```bash
pyftsubset NotoSansSC-VF.ttf \
  --text-file=chars.txt \
  --flavor=woff2 \
  --output-file=NotoSansSC-GB2312.woff2 \
  --layout-features='*' \
  --glyph-names --symbol-cmap --legacy-cmap \
  --notdef-glyph --notdef-outline --recommended-glyphs \
  --name-IDs='*' --name-languages='*'
```

Coverage note: dynamic product names may contain characters outside GB2312.
Those glyphs fall back to the platform Chinese font; this is intentional to keep
the font payload under 2 MB.

## Licensing

- `LICENSE-Inter.txt`
- `LICENSE-NotoSansSC.txt`
- `LICENSE-JetBrainsMono.txt`

Copies are also emitted to `dist/fonts/` from `frontend/public/fonts/`.
