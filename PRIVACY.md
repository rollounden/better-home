# Privacy & security audit — Xiaomi Home / Xiome app

Static analysis of the vendor app, done to decide what Local Home has to avoid.

| | |
| --- | --- |
| Package | `com.xiaomi.smarthome` |
| Version | 11.9.622 (11090622) |
| Size | 229 MB XAPK (154 MB base APK, 9 dex files) |
| minSdk / targetSdk | 24 / 36 |
| Method | manifest + dex string analysis (androguard), no dynamic run |

## Summary

The app is a full smart-home platform, not a thin remote. It carries several
third-party SDKs, an advertising identifier, broad device permissions, and it
permits cleartext HTTP for the whole app. None of that is unusual — but almost
none of it is needed to switch a purifier on.

## Third-party SDKs found

| SDK | Evidence | What it does |
| --- | --- | --- |
| Facebook | 5 555 refs; `com/facebook/appevents`, `login`, `share` | event analytics, social login, sharing |
| Google Analytics for Firebase | `com/google/firebase/analytics`, `com/google/android/gms/measurement` | usage analytics |
| Firebase Cloud Messaging | `com/google/firebase/messaging` | push |
| Google Mobile Ads identifier | `com/google/android/gms/ads/identifier/AdvertisingIdClient` | reads the AD_ID |
| Tencent Bugly | `com/tencent/bugly` | crash reporting |
| Getui (个推) | `com/igexin` | push |
| Xiaomi Push | `com/xiaomi/push`, `com/xiaomi/mipush` | push |
| Alipay / WeChat / Weibo | `com/alipay`, `com/tencent/mm`, `com/sina/weibo` | payments, social login |
| Amazon auth | `com.amazon.identity.auth` | login |

No AppsFlyer, Adjust, Umeng, TalkingData, Sentry, OneSignal, Pangle or Baidu.

## Permissions worth noting

* `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` — required for Wi-Fi scans.
* `READ_PHONE_STATE` / `READ_PRIVILEGED_PHONE_STATE` — device identifiers.
* `com.google.android.gms.permission.AD_ID` — advertising ID.
* `RECORD_AUDIO`, `CAMERA` — voice control, QR pairing, camera devices.
* `GET_ACCOUNTS`, `AUTHENTICATE_ACCOUNTS`, `USE_CREDENTIALS` — Xiaomi account.
* `NEARBY_WIFI_DEVICES`, `BLUETOOTH_SCAN/CONNECT/ADVERTISE` — provisioning.
* `TRANSMIT_IR`, `NFC` — IR blaster and tap-to-pair.
* `SYSTEM_ALERT_WINDOW`, `INTERNAL_SYSTEM_WINDOW`, `STATUS_BAR_SERVICE` — MIUI
  integration.

## Network posture

`res/xml/network_security_config.xml`:

```xml
<base-config cleartextTrafficPermitted="true">
  <trust-anchors><certificates src="system"/></trust-anchors>
</base-config>
```

* **Cleartext HTTP is allowed app-wide**, not just for a few hosts.
* Only system CAs are trusted; **no certificate pinning**.
* Hardcoded cleartext endpoints in the dex:
  `http://112.124.46.149:8080`, `http://115.29.222.198:8080`,
  `http://192.168.0.108:8080` (dev), `http://api.ximalaya.com`.

Telemetry / backend hosts observed in strings:

```
home.mi.com              account.xiaomi.com       data.sec.miui.com
tracker.xiaomixiaoai.com log.miwifi.com           www.google-analytics.com
pagead2.googlesyndication.com
```

## Attack surface

* **142 exported components** (activities, services, receivers, providers),
  most without a signature-level permission. Deep links and the
  `com.xiaomi.smarthome.platform.provider` providers are reachable by any app
  on the device.
* Remote JavaScript: `assets/plugin_rn_sdk` plus a React-Native plugin host —
  device UIs are downloaded and executed, so device pages are not covered by
  the APK signature.
* `allowBackup=false` (good — tokens are not in ADB backups).
* Not debuggable.

## What Local Home does differently

| | Vendor app | Local Home |
| --- | --- | --- |
| Account | required | none |
| Cloud round-trip to toggle | yes | no |
| Analytics / ads ID | yes | no |
| Third-party SDKs | 10+ | 0 |
| Cleartext allowed | app-wide | LAN only, miIO is AES-CBC |
| Remote code | RN plugin host | static files |
| Data location | Xiaomi cloud | local SQLite |

## Caveats

Static analysis only. It shows what is *linked in*, not what runs on every
launch; some SDKs may be dormant. A dynamic run (mitmproxy + `dumpsys`) would
be needed to prove actual traffic. The APK was fetched from APKPure
(`d.apkpure.net`), not Play, so the signature should be checked before trusting
it as canonical.
