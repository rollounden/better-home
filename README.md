# Local Home

A private, local-first Android app for Xiaomi/Xiome smart-home devices. It talks
to devices **directly over your Wi-Fi**. No account, no cloud, no telemetry, no
Google Play Services.

```
┌──────────────┐   miIO / UDP 54321   ┌──────────────┐
│  Local Home  │ ───────────────────► │ air purifier │
│  (Android)   │ ◄─────────────────── │              │
└──────────────┘   AES-128-CBC        └──────────────┘
       │
       └── device tokens: encrypted with an Android Keystore key, never backed up
```

Built for GrapheneOS, installed via [Obtainium](https://github.com/ImranR98/Obtainium).

## Why

The vendor app is a 230 MB bundle with Facebook, Firebase Analytics, Tencent
Bugly, Getui and an advertising-ID client, and it allows cleartext HTTP
app-wide. See [PRIVACY.md](PRIVACY.md). Local Home is ~2.5 MB with zero
third-party SDKs.

## Supported devices

| Model | Sold as | Status |
| --- | --- | --- |
| `xiaomi.airp.cpa4` | Xiaomi / Xiome Air Purifier 4 Compact | power, mode, fan speed, PM2.5, filter, child lock, sounds, display |

The property map comes from the official MIoT spec
(`urn:miot-spec-v2:device:air-purifier:0000A007:xiaomi-cpa4:2`). New device
types go in `android/app/src/main/java/app/localhome/devices/`.

## Install on the phone (Obtainium)

1. In Obtainium, **Add app** → paste this repo's GitHub URL.
2. The repo is private, so add a GitHub personal access token in
   Obtainium's settings (fine-grained, read-only **Contents** on this repo).
3. Install. Updates arrive whenever a new release is tagged.

On GrapheneOS, keep the app's **Network** permission on, because it needs it to
reach devices on your Wi-Fi.

## Adding a device

* **Pair a new device:** hold the purifier's button until the Wi-Fi light
  blinks, enter your 2.4 GHz Wi-Fi details, then choose the device's setup
  network when Android asks. Your normal Wi-Fi stays connected.
* **Find on this network:** for devices already on your Wi-Fi.
* **Enter IP and token:** if you already have the 32-character token.

## Build

Needs JDK 17+ and the Android SDK (platform 35).

```bash
cd android
./gradlew testDebugUnitTest   # protocol tests
./gradlew assembleRelease     # signed if keystore.properties exists
```

Release signing reads `android/keystore.properties` (git-ignored):

```properties
storeFile=/absolute/path/to/release.jks
storePassword=…
keyAlias=localhome
keyPassword=…
```

**Back up the keystore.** Obtainium only accepts updates signed with the same
key. If you lose it, you'll have to uninstall and reinstall the app.

## Layout

| path | purpose |
| --- | --- |
| `miio/` | miIO protocol: packet codec, encrypted client, LAN discovery |
| `devices/` | per-model drivers (property maps, typed state) |
| `provision/` | pairing over the device's setup network |
| `data/` | encrypted device store, live state |
| `ui/` | Compose screens: home, purifier, add device |

## Licence

AGPL-3.0-or-later.
