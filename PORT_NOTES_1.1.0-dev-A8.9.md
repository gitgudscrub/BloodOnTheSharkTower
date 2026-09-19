# 1.1.0-dev A8.9 — Clock Hand Aspect Ratio Hotfix

- Fixed the nomination/vote clock hands rendering as unnaturally thin/squashed.
- World-space quad width now follows each source texture's native aspect ratio:
  - minute hand: 11x48
  - hour hand: 11x35
- Clock-hand scale still changes the overall size, but no longer distorts the artwork as the hand length/radius changes.
