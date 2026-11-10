# Plague Inc. Scratch rebuild

Scratch 3 simulation remake using the **Unity assets supplied by the user**. The exported game is an `.sb3` file, not an HTML application.

## Download the game
The current exported `.sb3` is attached in the ChatGPT conversation as `Plague_Inc_Country_Map_Rebuild.sb3`. This branch stores the **rebuild source**, not the licensed Unity asset packs. Do not publicly redistribute the extracted commercial assets unless you have permission.

## What's implemented
- 72 selectable world regions, each tracking population, active infection, dead, recovered and local outbreak data.
- International land links based on nearby geography, plus independent airport and seaport importation.
- Local response escalation, regional border closures, cure research, changing lethality and seven disease types.
- 36 evolutions with prerequisite checks, selectable genes, cheats, scenarios, and infection milestones.
- Scratch sprites for moving flight and cargo routes, DNA pickup, and news notifications.
- Authentic Unity textures and sound effects loaded from the provided game-data export.

## Rebuild on Linux or in GitHub Actions
Requires Python 3.11+, Pillow, numpy, and countryinfo. The `visual_rebuild.py` step uses 72 geographic polygon masks to make the countries interactive and infection-colored, replaces the original circle-marker sprite, and builds a compact world-map HUD with icons and textures from the user-provided Unity assets.

1. Put the **previous Scratch project** into `plague-scratch/input/Plague_Inc_Unity_Assets_Scratch.sb3`.
2. Put the extracted Unity texture ZIP into `plague-scratch/input/Plague_Inc_Unity_Textures_Extracted.zip`.
3. Run:

```bash
python -m pip install Pillow countryinfo numpy
PLAGUE_TEXTURES=plague-scratch/input/Plague_Inc_Unity_Textures_Extracted.zip \
  python plague-scratch/remaster.py \
  plague-scratch/input/Plague_Inc_Unity_Assets_Scratch.sb3 \
  plague-scratch/Plague_Inc_Remastered_Scratch.sb3
python plague-scratch/validate.py plague-scratch/Plague_Inc_Remastered_Scratch.sb3
```

The rebuilt .sb3 has a redesigned world screen, but is not a pixel-exact or complete reproduction of the Unity game. Its menus outside the world screen are still work in progress.

The game contains licensed third-party images and audio extracted **only from user-supplied files**. The script uses them when present; it doesn't download the game. The code in this repository is a work-in-progress source build, not a representation that all original DLC scenarios or game rules are already faithfully implemented.

## GitHub Actions
The `Build Plague Scratch SB3` workflow can be dispatched manually **after** the two user-provided inputs have been placed in `plague-scratch/input/`. The workflow uploads a resulting `.sb3` artifact. There is deliberately no automatic public upload of the licensed game asset bundle.
