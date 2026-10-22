# CleanTV by VGS

**Tivi hết quảng cáo.** App Android dọn quảng cáo, theo dõi và app rác của hãng trên Android TV / Google TV qua Wi-Fi, rồi thay màn Home đầy quảng cáo bằng một Home sạch. Không root, không gỡ gì (chỉ tắt), khôi phục một chạm.

**No more TV ads.** An Android app that switches off the maker's ads, tracking and junk on Android TV / Google TV over Wi-Fi and swaps the ad-filled Home for a clean one. No root, nothing uninstalled (only disabled), one-tap restore.

[Tải APK / Download](https://vgs.zone/cleantv) · [English page](https://cleantv.alexvgs.tech/en) · [Nhật ký cập nhật / Changelog](https://vgs.zone/cleantv#cap-nhat) · [Góp ý / Feedback](https://vgs.zone/gop-y)

<p>
  <img src="web/anh/tv-truoc.webp" width="49%" alt="Trước / before">
  <img src="web/anh/tv-sau.webp" width="49%" alt="Sau / after">
</p>
<p>
  <img src="web/anh/app-tim.webp" width="24%" alt="Tìm tivi">
  <img src="web/anh/app-chon.webp" width="24%" alt="Chọn gói">
  <img src="web/anh/app-en.webp" width="24%" alt="Theme">
</p>

---

## Tiếng Việt

### App làm gì
- **Kết nối tivi qua Wi-Fi** bằng ADB (cần bật Gỡ lỗi USB trên tivi, có hướng dẫn 7 bước có hình trong app).
- **So với 213 danh sách đã thử** của cộng đồng CleanYourTV (TCL, Sony, Xiaomi, Philips, Hisense, Skyworth, onn, Chromecast, máy chiếu…); tivi lạ dùng luật chung an toàn.
- **Tắt** (không gỡ) quảng cáo, theo dõi, app rác. Không đụng cổng HDMI, remote, bàn phím, CH Play, gói giữ Wi-Fi. Gói Google Home / cast và tìm kiếm giọng nói không bao giờ tích sẵn.
- **Home sạch**: đồng hồ, thời tiết, dock app hay xem, theme VGS / cực quang / bầu trời theo giờ, cụm nút Máy chiếu · Mạng · Màn hình chờ · Cài đặt. App tự bấm thử nút Home trên tivi; không được thì để tivi như cũ.
- **Sao lưu trước mọi thay đổi**, Khôi phục một chạm. Tự cập nhật, song ngữ Việt / Anh.
- Chạy trên **điện thoại Android** (điều khiển tivi qua mạng) hoặc **ngay trên tivi** (bản thử).

### Dựng từ mã nguồn
Cần JDK 17 và Android SDK (compileSdk 35).

```bash
scripts/dung-launcher.sh          # tải CleanYourTV, áp patches/launcher-vgs.patch, dựng Home sạch + dữ liệu 213 mẫu vào assets
./gradlew assembleRelease         # thiếu ky.properties thì APK ký bằng khoá debug
./gradlew :app:testReleaseUnitTest
```

`app/src/main/assets/tvhome.apk` và `du-lieu.json` đã có sẵn trong repo, nên chỉ dựng app thì không cần bước đầu.

### Cấu trúc
| Thư mục | Nội dung |
|---|---|
| `app/` | App (Kotlin + Jetpack Compose). Package `zone.vgs.cleantv`, mã ở `zone.vgs.tvsach` (tên biến tiếng Việt) |
| `app/.../adb/` | Kết nối ADB (libadb-android), các thao tác trên tivi: sao lưu, tắt gói, cài Home sạch, thử nút Home, khôi phục |
| `app/.../dulieu/` | Danh sách mẫu tivi, luật chọn gói (`KeHoach.kt`), theme, hướng dẫn |
| `patches/launcher-vgs.patch` | Phần VGS sửa trên launcher CleanYourTV (thời tiết, font Lexend, cụm nút, máy chiếu, màn chờ…) |
| `scripts/` | Dựng launcher, phát hành |
| `dem/` | Cloudflare Worker đếm ẩn danh (lượt tải, lượt dùng) |
| `web/` | Trang tiếng Anh, file cập nhật (`cap-nhat.json`), nhật ký (`nhat-ky.json`) |

### Dữ liệu thu thập
Chỉ số liệu ẩn danh: một mã máy ngẫu nhiên do app tự sinh, số bản app, tên mẫu tivi, số gói đã tắt, gửi khi mở app (mỗi ngày một lần), cập nhật, dọn xong, cài Home sạch. **Không** gửi email, tên, danh sách app, IP hay vị trí. Xem `app/src/main/java/zone/vgs/tvsach/Dem.kt` và `dem/src/index.js`.

### Đóng góp
Báo lỗi, xin hỗ trợ tivi mới: [vgs.zone/gop-y](https://vgs.zone/gop-y) (mục "App CleanTV") hoặc mở Issue. Danh sách tivi mới nên gửi thẳng cho [CleanYourTV](https://github.com/Gathin23/cleanyourtv) để mọi người cùng dùng; CleanTV tự lấy về.

---

## English

### What it does
- **Connects to the TV over Wi-Fi** with ADB (turn on USB debugging; the app has a 7-step picture guide).
- **Matches 213 tested lists** from the CleanYourTV community; unknown TVs get safe general rules.
- **Disables** (never uninstalls) ads, tracking and junk. Leaves HDMI inputs, the remote, keyboards, Play Store and the Wi-Fi owner alone. Google Home / casting and voice search are never pre-ticked.
- **Clean Home** with clock, weather, a dock, VGS / aurora / time-of-day themes and a Projector · Network · Screensaver · Settings cluster. The app tests the TV's real Home button and puts everything back if it can't take it over.
- **Backs up before any change**; one-tap Restore. Self-updating, Vietnamese / English.
- Runs on an **Android phone** (controls the TV over the network) or **on the TV itself** (test build).

### Build
JDK 17 + Android SDK (compileSdk 35): `scripts/dung-launcher.sh`, then `./gradlew assembleRelease`. Without `ky.properties` the release APK is signed with the debug key. Code identifiers are in Vietnamese.

### Privacy
Anonymous counts only (a random app-generated ID, app version, TV model, number of packages disabled). No email, name, app list, IP or location. See `Dem.kt` and `dem/src/index.js`.

---

## Ghi công / Credits
CleanTV is built on **[CleanYourTV](https://github.com/Gathin23/cleanyourtv)** by Gathin and its contributors (MIT): the clean Home launcher and the TV lists come from that project. Also libadb-android, Conscrypt, Bouncy Castle, Lexend (OFL) and Open-Meteo weather data. See [NOTICE](NOTICE).

## Giấy phép / License
Source code: [MIT](LICENSE). The names **CleanTV** and **VGS**, the VGS and CleanTV logos and the VGS wallpaper are not covered by the licence: modified builds should use their own name and artwork.

Made by [VGS — Vietnam Gaming Setup](https://vgs.zone).
