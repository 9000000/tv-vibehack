# Nhật Ký Lỗi Dự Án (Error Log)

## [2026-10-22 13:00] - CleanYourTV-Manager.apk crash on launch (NPE in ViewModel init)

- **Type**: Runtime
- **Severity**: Critical
- **File**: `launcher/manager/src/main/java/zone/vgs/tvsach/ui/PhienLamViec.kt:372`
- **Agent**: mon
- **Root Cause**: Trong Kotlin, thuộc tính khai báo bên dưới khối `init` sẽ chưa được khởi tạo (mang giá trị null trên JVM bytecode) khi `init` thực thi. Trong `PhienLamViec.kt`, `init` gọi `napHoSoTheme(themeChon)`, truy cập `hoSoTheoTheme[th.id]` khi `hoSoTheoTheme` được khai báo bên dưới `init`, gây `NullPointerException` ngay khi mở ứng dụng Manager.
- **Error Message**:
  ```
  java.lang.NullPointerException: Attempt to invoke virtual method 'java.lang.Object java.util.Map.get(java.lang.Object)' on a null object reference
      at zone.vgs.tvsach.ui.PhienLamViec.napHoSoTheme(PhienLamViec.kt)
      at zone.vgs.tvsach.ui.PhienLamViec.<init>(PhienLamViec.kt)
  ```
- **Fix Applied**: 
  1. Di chuyển toàn bộ khai báo `hoSoTheoTheme`, `taoHoSoMacDinh`, `luuHoSoTheme`, `napHoSoTheme`, `chonTheme` lên trước khối `init`.
  2. Bổ sung ép kiểu an toàn (`as? Number`, `toBooleanStrictOrNull`, safe parsing) cho tất cả các giá trị đọc từ JSON/SharedPreferences để tránh ClassCastException.
  3. Xóa bỏ đoạn mã trùng lặp ở cuối file.
  4. Biên dịch và kiểm thử thành công bằng Gradle release unit test & assembleRelease.
- **Prevention**: Luôn đảm bảo thứ tự khai báo thuộc tính trước khối `init` trong các class Kotlin khi thuộc tính đó được tham chiếu trực tiếp hoặc gián tiếp trong `init`.
- **Status**: Fixed

---
