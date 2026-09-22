# Walkthrough: Bổ sung Tự động phát hiện di chuyển tốc độ cao, Background Service khi tắt màn hình & Tối ưu pin

Tài liệu đặc tả [driver_drowsiness_detection_spec.md](file:///Users/thanh/Downloads/driver_drowsiness_detection_spec.md) đã được cập nhật toàn diện theo đúng các yêu cầu của bạn.

---

## Các nội dung chính đã bổ sung & nâng cấp

### 1. Tự động phát hiện di chuyển tốc độ cao (Automatic Driving Detection)
- **Cơ chế nhận diện (`Mục 2.2` & `Mục 3.2`)**:
  - Tích hợp **Google Activity Recognition API** để lắng nghe sự kiện `ActivityTransition.ACTIVITY_TRANSITION_ENTER` với `DetectedActivity.IN_VEHICLE`.
  - Kết hợp **GPS Speed Tracker** (`FusedLocationProviderClient`): Khi vận tốc thực tế của xe đạt trên ngưỡng cấu hình (mặc định **> 25 km/h**) duy trì liên tục trong **>= 20 giây**, hệ thống tự động khởi chạy `DrowsinessMonitoringService` và phát tín hiệu chào/rung nhẹ báo hiệu đã kích hoạt an toàn.
- **Xử lý dừng xe tạm thời (Grace Period)**:
  - Khi xe dừng đèn đỏ, kẹt xe hoặc tạm dừng ngắn (tốc độ về 0 hoặc < 5 km/h), hệ thống kích hoạt bộ đếm thời gian chờ (mặc định 5–10 phút) để tránh bật/tắt service liên tục (*flapping*).
  - Khi xe dừng vượt quá thời gian Grace Period hoặc người dùng chuyển sang đi bộ (`WALKING`/`STILL`), hệ thống tự động kết thúc chuyến đi, lưu lịch sử và giải phóng camera.

### 2. Giám sát dưới nền qua Foreground Service khi tắt màn hình (Screen-off Monitoring)
- **Kiến trúc Service độc lập (`Mục 36`)**:
  - Sử dụng **Android Foreground Service** với `foregroundServiceType="camera|location"` (tuân thủ nghiêm ngặt chuẩn bảo mật Android 14+ / API 34).
  - Duy trì **Ongoing Sticky Notification** minh bạch với icon an toàn, thông tin trạng thái và nút bấm `[Dừng giám sát]` nhanh.
- **Xử lý khi người dùng tắt màn hình (`Mục 36.2`)**:
  - Lắng nghe `Intent.ACTION_SCREEN_OFF`.
  - Giữ `PowerManager.PARTIAL_WAKE_LOCK`: chỉ giữ CPU hoạt động để chạy vòng lặp AI, trong khi màn hình hiển thị (Display) và chip đồ họa (GPU) chuyển sang ngủ sâu hoàn toàn.
- **Cơ chế cảnh báo khẩn cấp khi màn hình đang khóa (`Mục 29.1`)**:
  - Sử dụng **Full-screen Intent Notification** mức ưu tiên `PRIORITY_MAX`.
  - Tự động bật sáng màn hình (`turnScreenOn = true`, `setShowWhenLocked(true)`) và hiển thị màn hình cảnh báo đỏ rực đè lên màn hình khóa khi đạt mức `DROWSY` hoặc `CRITICAL`.
  - Định tuyến âm thanh qua `AudioManager.STREAM_ALARM` để âm thanh luôn phát to rõ ngay cả khi điện thoại ở chế độ Rung hoặc Im lặng.

### 3. Tối ưu hóa tiêu thụ pin cực đại (Ultra-low Power Strategy)
- **Chiến lược hạ FPS thích ứng (Dynamic Adaptive FPS - `Mục 5` & `Mục 37`)**:
  - **Màn hình sáng (Foreground UI)**: 5–8 FPS (giao diện xem trước mượt, độ trễ thấp).
  - **Màn hình tắt (Trạng thái NORMAL)**: Giảm xuống **2–3 FPS** (frame interval ~350–500ms). Vừa đủ để giám sát PERCLOS và chớp mắt dài (> 1s), nhưng giảm tới **65–75%** tải tính toán của NPU/CPU.
  - **Màn hình tắt (Trạng thái nghi ngờ - ATTENTION/Chớm nhắm mắt/Gật đầu)**: Tự động burst lên **8–10 FPS** trong 1.5–2.5 giây để xác thực nhanh chóng trước khi phát còi báo động.
  - Sau khi an toàn trở lại (Recovery): Tự động hạ về 2–3 FPS.
  - Bỏ qua frame ngay tại `ImageAnalysis.Analyzer` nếu frame tới sớm hơn ngưỡng `minIntervalMs` để không tốn CPU/NPU.
- **Zero UI Rendering / Xử lý Headless (`Mục 5` & `Mục 38`)**:
  - Hủy liên kết (unbind) hoàn toàn usecase `Preview` khỏi CameraX khi màn hình tắt hoặc app chạy nền.
  - Hoàn toàn không render Surface, không chạy Compose/View tree, không vẽ landmark overlay (tiết kiệm 100% tài nguyên GPU và compositing pipeline của OS).
  - Dùng trực tiếp buffer `ImageProxy` (YUV_420_888) chuyển vào input tensor của MediaPipe Face Landmarker mà không qua bước tạo Bitmap RGB trung gian.
  - Giới hạn phân giải camera ở mức 480p (640x480) hoặc 720p hạ mẫu.

### 4. Bổ sung Quyền, Kiến trúc, Module & Settings
- **Permissions (`Mục 35`)**: Bổ sung `FOREGROUND_SERVICE_CAMERA`, `FOREGROUND_SERVICE_LOCATION`, `ACCESS_FINE_LOCATION`, `ACCESS_BACKGROUND_LOCATION`, `ACTIVITY_RECOGNITION`, `WAKE_LOCK`, `USE_FULL_SCREEN_INTENT`.
- **Sơ đồ kiến trúc & Module (`Mục 39` & `Mục 40`)**: Thêm các module `motion/` (`MotionDetector`, `SpeedTracker`, `AutoDrivingController`), `service/` (`DrowsinessMonitoringService`, `MonitoringNotificationManager`), `power/` (`PowerAdaptiveManager`, `WakeLockManager`).
- **Data Models & Config (`Mục 41` & `Mục 44`)**: Thêm `MotionActivity`, `DrivingMotionInfo`, `PowerMode`, `FpsStrategy`, cấu hình ngưỡng vận tốc kích hoạt, Grace period, và cấu hình FPS thích ứng.
- **Giao diện & Cài đặt (`Mục 48` & `Mục 49`)**: Thêm mockups Notification nền và Lockscreen Alert; thêm các thiết lập bật/tắt tự động phát hiện, ngưỡng tốc độ, và chế độ siêu tiết kiệm pin.
- **MVP Scope & Development Priority (`Mục 52` & `Mục 54`)**: Bổ sung đầy đủ vào lộ trình phát triển.
