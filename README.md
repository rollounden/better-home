# Better Home

A private, local-first Android app for Xiaomi/Xiome smart-home devices. It talks
to devices **directly over your Wi-Fi**. No account, no cloud, no telemetry, no
Google Play Services.

```
┌──────────────┐   miIO / UDP 54321   ┌──────────────┐
│  Better Home  │ ───────────────────► │ air purifier │
│  (Android)   │ ◄─────────────────── │              │
└──────────────┘   AES-128-CBC        └──────────────┘
       │
       └── device tokens: encrypted with an Android Keystore key, never backed up
```

Built for GrapheneOS, installed via [Obtainium](https://github.com/ImranR98/Obtainium).

## Why

The vendor app is a 230 MB bundle with Facebook, Firebase Analytics, Tencent
Bugly, Getui and an advertising-ID client, and it allows cleartext HTTP
app-wide. See [PRIVACY.md](PRIVACY.md). Better Home is ~2.5 MB with zero
third-party SDKs.

## Supported devices

| Model | Sold as | Status |
| --- | --- | --- |
| `xiaomi.airp.cpa4` | Xiaomi / Xiome Air Purifier 4 Compact | power, mode, fan speed, PM2.5, filter, child lock, sounds, display |

The property map comes from the official MIoT spec
(`urn:miot-spec-v2:device:air-purifier:0000A007:xiaomi-cpa4:2`). New device
types go in `android/app/src/main/java/app/betterhome/devices/`.

## Install on the phone (Obtainium)

1. In Obtainium, **Add app** → paste `https://github.com/rollounden/better-home`.
2. Install. Updates arrive whenever a new release is tagged.

Releases are signed with a certificate whose SHA-256 is
`dcde5ff4310721613d72619e68ebf4e4d69b44480e84c1e7884089c6b3a27cb3`.
Check it with AppVerifier if you're installing by hand.

On GrapheneOS, keep the app's **Network** permission on, because it needs it to
reach devices on your Wi-Fi.

## Adding a device

Better Home adds devices by **IP address and token**. Purifiers it finds on
your Wi-Fi are offered as tap-to-fill chips.

### Getting the token, without leaving Xiaomi a way in

The cpa4 firmware generates a new, secret token when it joins Wi-Fi. That
happens even with internet blocked, and it ignores a token passed in
`miIO.config_router`. The Xiaomi app receives the new token from the device
during pairing (it's a `_mibt` Bluetooth combo device) and stores it in your
Xiaomi account. So the token has to come from the account once:

1. **Block the purifier's internet at your router** (by MAC address) before
   pairing. Pairing still works, because the phone relays the token.
2. Pair it once in the Xiaomi app.
3. Run [Xiaomi-cloud-tokens-extractor](https://github.com/PiotrMachowski/Xiaomi-cloud-tokens-extractor)
   and log in with its QR code option. It lists each device's IP and token.
4. In Better Home: **Add device**, tap the IP, paste the token.

With internet blocked, the token stays valid until the purifier is re-paired,
and Xiaomi can't reach the device.

To put a Compact 4 back into setup mode, hold the **light button for 7
seconds** until it beeps and the Wi-Fi light blinks. Holding light + mode
together turns Wi-Fi off and on instead.

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

[AGPL-3.0-or-later](LICENSE).
