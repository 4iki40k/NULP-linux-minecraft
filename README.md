# nulp-minecraft

Minecraft 1.7.10 GTNH-style pack (Forge 10.13.4.1614), packwiz, modern Java.

## Server
```bash
./server-setup.sh
```

## Client
[PrismLauncher](https://prismlauncher.org/) + the lwjgl3ify [instance template](https://github.com/GTNewHorizons/lwjgl3ify/releases/download/3.0.25/lwjgl3ify-3.0.25-multimc.zip), Java 17+, then:
```bash
java -jar packwiz-installer-bootstrap.jar -g -s client \
  "https://raw.githubusercontent.com/GribanovIvan/nulp-minecraft/lwjgl3ify-support/pack.toml"
```
