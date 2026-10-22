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

## [2026-10-22 17:45] - Kotlin Compile Error in Manager (Theme syntax & AutoMirrored Icons)

- **Type**: Syntax
- **Severity**: High
- **File**: `launcher/manager/src/main/java/zone/vgs/tvsach/dulieu/Theme.kt:21`
- **Agent**: mon
- **Root Cause**: Khai báo `data class Theme` bị thiếu dấu đóng ngoặc đơn `)` và mở ngoặc nhọn `{` giữa danh sách constructor parameters và phần body chứa getters/methods. Ngoài ra, việc dùng `Icons.AutoMirrored.Rounded.*` cho `FactCheck` và `MenuBook` không tương thích với bộ thư viện Compose hiện tại.
- **Error Message**:
  ```
  e: .../dulieu/Theme.kt:21:33 Syntax error: Expecting a top level declaration.
  e: .../dulieu/Theme.kt:32:111 No value passed for parameter 'ten'.
  e: .../ui/UngDung.kt:520:70 Unresolved reference. None of the following candidates is applicable because of a receiver type mismatch: val Icons.Rounded.FactCheck: ImageVector
  ```
- **Fix Applied**: 
  1. Đóng ngoặc `)` cho constructor của `data class Theme` và đưa getters `ten`, `moTa` cùng hàm `ghiVao` vào body `{ ... }`.
  2. Phục hồi sử dụng `Icons.Rounded.FactCheck` và `Icons.Rounded.MenuBook` trong `UngDung.kt`.
- **Prevention**: Luôn kiểm tra cấu trúc cú pháp `data class (...) { ... }` trong Kotlin khi bổ sung custom property getter.
- **Status**: Fixed

## [2026-10-22 17:53] - Unit Test Failures in KeHoachTest & NangCaoTest

- **Type**: Process
- **Severity**: Medium
- **File**: `launcher/manager/src/test/java/zone/vgs/tvsach/dulieu/KeHoachTest.kt:84`, `NangCaoTest.kt:25`
- **Agent**: mon
- **Root Cause**:
  1. Trong `KeHoach.kt`, regex `APP_XUAT_XUONG` chỉ kiểm tra `factorytest|factory_test|factorymode`, bỏ sót tiền tố `factorymenu` (như gói `mediatek.factorymenu.ui`), khiến gói này lọt vào kế hoạch và làm fail test `factoryTest_khongBaoGioVaoDanhSachTat`.
  2. Trong `NangCaoTest.kt`, assertion cũ kiểm tra tất cả các khóa trong `KHOA_DA_CO_SAN` không được có trong `nang-cao.json`, nhưng bản v2.0.1 đã mở rộng hỗ trợ 52 tùy chọn nâng cao và chỉ loại trừ 4 khóa do app tự quản lý (`dock_apps`, `lunar_date`, `always_show_names`, `wallpaper`).
- **Error Message**:
  ```
  KeHoachTest > factoryTest_khongBaoGioVaoDanhSachTat FAILED
      java.lang.AssertionError at KeHoachTest.kt:84
  NangCaoTest > danhMucHopLe FAILED
      java.lang.AssertionError at NangCaoTest.kt:25
  ```
- **Fix Applied**: 
  1. Mở rộng regex `APP_XUAT_XUONG = Regex("""factory|hkcfac|fac\.menu""", RegexOption.IGNORE_CASE)`.
  2. Đồng bộ assertion trong `NangCaoTest.kt` khớp với upstream v2.0.1: `listOf("dock_apps", "lunar_date", "always_show_names", "wallpaper")`.
- **Prevention**: Luôn chạy unit test bộ dữ liệu và kiểm tra regex bao phủ đầy đủ các biến thể package hệ thống.
- **Status**: Fixed

---
