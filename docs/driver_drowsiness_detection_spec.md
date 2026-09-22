# Driver Drowsiness Detection App — Functional Specification

## 1. Tổng quan

Xây dựng một ứng dụng Android hỗ trợ phát hiện tình trạng buồn ngủ của người lái xe bằng camera trước.

Mục tiêu chính:

- Theo dõi khuôn mặt người lái theo thời gian thực.
- Phân tích trạng thái mắt, miệng và đầu.
- Phát hiện các dấu hiệu buồn ngủ.
- Cảnh báo người lái trước khi tình trạng buồn ngủ trở nên nguy hiểm.
- **Tự động kích hoạt khi di chuyển tốc độ cao**: Sử dụng cảm biến và GPS để tự động bật chế độ giám sát khi xe đang lưu thông mà không cần người dùng thao tác thủ công.
- **Giám sát dưới nền khi tắt màn hình**: Chạy liên tục dưới dạng Android Foreground Service ngay cả khi người dùng tắt hoặc khóa màn hình.
- **Tối ưu hóa tiêu thụ pin cực hạn (Ultra-low Power)**: Sử dụng chiến lược hạ FPS thích ứng (2–3 FPS khi bình thường) và xử lý Headless hoàn toàn không render UI/Preview khi tắt màn hình, đảm bảo thiết bị mát mẻ và tiết kiệm pin tối đa trên các hành trình dài.
- Ưu tiên xử lý hoàn toàn trên thiết bị (On-device Edge AI).
- Không upload hình ảnh/video khuôn mặt lên server (Privacy-first).
- Hoạt động ổn định trong thời gian dài khi đang lái xe.

Ứng dụng không điều khiển xe và không thay thế trách nhiệm của người lái.

---

# 2. Luồng sử dụng chính

Hệ thống hỗ trợ song song hai luồng vận hành:
1. **Luồng thủ công (Manual Flow)**: Người dùng chủ động mở app và bấm bắt đầu.
2. **Luồng tự động thông minh (Automatic Driving Detection Flow)**: Ứng dụng tự phát hiện di chuyển tốc độ cao để kích hoạt và chuyển ngầm.

## 2.1. Mở ứng dụng thủ công

Khi mở app:

1. Hiển thị màn hình chính.
2. Kiểm tra quyền: Camera, Activity Recognition, Location, Foreground Service và Notification.
3. Nếu chưa có quyền:
   - Giải thích lý do minh bạch (Camera để nhận diện khuôn mặt, Location & Activity để tự động bật khi lái xe).
   - Yêu cầu cấp quyền theo luồng chuẩn.
4. Sau khi được cấp quyền:
   - Mở camera kiểm tra góc nhìn.
   - Kiểm tra khả năng nhận diện khuôn mặt.
5. Hiển thị hướng dẫn đặt điện thoại sao cho:
   - Camera nhìn rõ mặt người lái.
   - Hai mắt nằm trong vùng quan sát.
   - Không bị vật cản.
   - Ánh sáng đủ để nhận diện.

## 2.2. Luồng tự động phát hiện di chuyển tốc độ cao (Auto-detect Driving)

Người lái thường quên bật app trước mỗi chuyến đi. Hệ thống cung cấp cơ chế tự động kích hoạt thông minh:

```text
[Tác vụ nền tiêu thụ năng lượng cực thấp]
               │
               ▼
Google Activity Recognition API (Chờ event IN_VEHICLE)
               │
               ▼
Kiểm tra vận tốc GPS FusedLocation (> 25 km/h duy trì >= 20s)
               │
               ▼
Khởi động DrowsinessMonitoringService (Foreground Service)
               │
               ├─ Hiển thị Sticky Ongoing Notification
               ├─ Bật CameraX ở chế độ nền (Headless)
               └─ Rung nhẹ / Âm thanh thông báo: "Đã bật giám sát an toàn"
```

1. **Nhận diện trạng thái phương tiện**: Tận dụng Google Activity Recognition API để lắng nghe chuyển đổi trạng thái `ActivityTransition.ACTIVITY_TRANSITION_ENTER` đối với `DetectedActivity.IN_VEHICLE`.
2. **Xác thực tốc độ di chuyển thực tế**: Lấy dữ liệu vận tốc từ `FusedLocationProviderClient`. Khi tốc độ đạt trên ngưỡng cấu hình (mặc định > 25 km/h) liên tục trong khoảng thời gian xác nhận (ví dụ 20 giây):
   - Hệ thống xác định người dùng đang thực sự điều khiển ô tô trên đường.
   - Tự động đánh thức và khởi chạy `DrowsinessMonitoringService` (Foreground Service).
   - Tải hồ sơ hiệu chuẩn gần nhất (`CalibrationProfile`) và bắt đầu chu trình giám sát.
   - Phát âm thanh chào ngắn hoặc rung nhẹ để người lái an tâm biết hệ thống đang bảo vệ mình.

---

# 3. Start Driving / Bắt đầu giám sát

## 3.1. Kích hoạt thủ công

Người dùng nhấn:

`Start Monitoring`

Ứng dụng chuyển sang chế độ giám sát và hiển thị camera preview.

### Pre-check

Trước khi bắt đầu:

- Camera hoạt động.
- Phát hiện được khuôn mặt.
- Phát hiện được hai mắt.
- Khuôn mặt nằm trong vùng hợp lệ.
- Hình ảnh đủ rõ.

Nếu không đạt:

```text
Face not detected
Move the phone so your face is visible.
```

Không bắt đầu tính điểm buồn ngủ cho tới khi dữ liệu đủ tin cậy.

## 3.2. Cơ chế Tự động Dừng và Thời gian chờ (Auto-stop & Grace Period)

Khi đang trong phiên giám sát (đặc biệt là phiên tự động kích hoạt):

- **Xử lý dừng xe tạm thời (Đèn đỏ / Kẹt xe / Tạm dừng ngắn)**:
  - Khi xe giảm tốc độ về 0 hoặc < 5 km/h:
  - Hệ thống **KHÔNG** lập tức ngắt giám sát để tránh hiện tượng bật/tắt liên tục (flapping).
  - Kích hoạt bộ đếm thời gian chờ **Grace Period** (mặc định 5–10 phút).
  - Trong thời gian này, service tiếp tục duy trì trạng thái theo dõi ở mức FPS siêu thấp (2 FPS) hoặc tạm nghỉ để tiết kiệm pin tối đa.
- **Kết thúc hành trình (Trip Completion)**:
  - Nếu xe dừng liên tục vượt quá thời gian Grace Period, hoặc Activity Recognition phát hiện người dùng đã chuyển sang trạng thái `WALKING` hoặc `STILL` kéo dài:
  - Tự động đóng phiên giám sát, tổng kết các chỉ số (thời lượng lái, số lần cảnh báo) và lưu vào Local History.
  - Giải phóng Camera, gỡ bỏ Notification và đưa Service về chế độ lắng nghe chuyển động tiêu thụ điện siêu thấp.

---

# 4. Calibration

Khi bắt đầu một phiên lái xe mới:

- Thực hiện calibration khoảng 10–15 giây.
- Người dùng nhìn tự nhiên về phía trước.
- Hệ thống thu thập dữ liệu cơ bản của người dùng.

Calibration dùng để xác định:

- EAR trung bình.
- EAR khi mắt mở.
- Ngưỡng mắt nhắm.
- Đặc điểm khuôn mặt cơ bản.
- Một số thông số head pose.

Không sử dụng calibration để lưu ảnh khuôn mặt.

Chỉ lưu các thông số số học cần thiết.

---

# 5. Camera Processing

Sử dụng:

- Kotlin
- CameraX (với `ImageAnalysis` là usecase trung tâm)
- MediaPipe Face Landmarker (on-device vision)

Kiến trúc luồng xử lý:

```text
Camera Sensor (Front Camera)
      │
      ▼
CameraX ImageAnalysis (YUV_420_888 ImageProxy)
      │
      ▼
[Adaptive FPS Throttling Gate]
      │
      ├─ Màn hình bật: 5–8 FPS (Cho phép Preview Surface hiển thị)
      │
      └─ Màn hình tắt: 2–3 FPS (NORMAL) / 8–10 FPS Burst (ATTENTION/Chớm nghi ngờ)
      │
      ▼
MediaPipe Face Landmarker (Xử lý Headless trực tiếp trên buffer)
      │
      ▼
Feature Extraction (EAR, MAR, Head Pose)
      │
      ▼
Drowsiness Engine & State Machine
```

### Chế độ xử lý kép (Dual Processing Pipeline):

1. **Chế độ màn hình sáng (Foreground UI Active)**:
   - Use cases: Liên kết cả `Preview` (hiển thị giao diện xem trước) và `ImageAnalysis` (phân tích AI).
   - Tốc độ xử lý: **5–8 FPS** (đảm bảo độ trễ thấp, giao diện trực quan mượt mà mà không làm nóng máy).

2. **Chế độ màn hình tắt / Chạy nền (Screen-off / Background Headless Mode)**:
   - Use cases: **Chỉ liên kết duy nhất `ImageAnalysis`**, hủy hoàn toàn liên kết (unbind) `Preview` usecase khỏi CameraX.
   - **Zero UI Rendering (Hoàn toàn không render)**:
     - Không phân bổ Surface đồ họa.
     - Không vẽ landmark overlay, không render Jetpack Compose hay Android View tree.
     - Tiêu thụ 0% tài nguyên GPU và compositing pipeline của hệ điều hành.
   - **Hạ FPS thích ứng (Adaptive FPS Strategy)**:
     - **Trạng thái an toàn (NORMAL)**: Hạ xuống **2–3 FPS** (frame interval ~350–500ms). Tại tần suất này, hệ thống vẫn thừa đủ khả năng phát hiện chớp mắt dài (> 1s) và xu hướng PERCLOS, trong khi giảm tới 65–75% tải tính toán của NPU/CPU.
     - **Trạng thái nghi ngờ (ATTENTION hoặc EAR bắt đầu giảm/đầu gật)**: Tự động kích hoạt **Burst Step-up lên 8–10 FPS** trong 1.5–2.5 giây để xác thực nhanh chóng dấu hiệu buồn ngủ trước khi kích hoạt còi báo động.
     - Sau khi người lái tỉnh táo ổn định (Recovery): Tự động hạ trở về mức 2–3 FPS.

3. **Xử lý buffer trực tiếp (Direct Buffer Processing)**:
   - Dùng trực tiếp `ImageProxy` (định dạng `YUV_420_888`), chuyển đổi thẳng sang định dạng `MPImage` của MediaPipe mà không qua khâu tạo `Bitmap RGB` phục vụ hiển thị.
   - Giải phóng buffer tức thì (`imageProxy.close()`) ngay sau khi trích xuất landmark.

---

# 6. Face Detection

Mỗi frame cần xác định:

- Có khuôn mặt hay không.
- Có bao nhiêu khuôn mặt.
- Khuôn mặt nào là người lái.
- Confidence của detection.

MVP ưu tiên:

```text
1 người → 1 khuôn mặt
```

Nếu không phát hiện khuôn mặt:

```text
FACE_NOT_DETECTED
```

Không được lập tức coi đây là buồn ngủ.

Có thể hiển thị:

```text
Face not visible
```

và chờ một khoảng thời gian trước khi cảnh báo.

---

# 7. Eye Detection

Sử dụng facial landmarks để xác định:

- Mắt trái.
- Mắt phải.
- Các điểm quanh mí mắt.

Từ đó tính:

## EAR — Eye Aspect Ratio

EAR dùng để xác định mắt đang:

```text
OPEN
PARTIALLY_CLOSED
CLOSED
```

Không sử dụng một frame đơn lẻ để kết luận buồn ngủ.

Phải phân tích theo chuỗi thời gian.

---

# 8. PERCLOS

PERCLOS là một chỉ số quan trọng để phát hiện buồn ngủ.

Định nghĩa:

```text
PERCLOS =
thời gian mắt đóng
------------------
tổng thời gian quan sát
```

Ví dụ:

Trong cửa sổ 60 giây:

```text
Eyes closed = 18 seconds

PERCLOS = 18 / 60 = 30%
```

Hệ thống phải tính PERCLOS theo sliding window.

Có thể sử dụng nhiều cửa sổ:

```text
Short window
Medium window
Long window
```

để phát hiện cả tình trạng buồn ngủ tức thời và xu hướng buồn ngủ kéo dài.

---

# 9. Long Eye Closure

Phát hiện trường hợp người lái nhắm mắt liên tục.

Ví dụ:

```text
Eyes closed > threshold
```

thì tăng mức cảnh báo.

Không nên dùng một giá trị cố định duy nhất cho mọi người.

Ngưỡng cần có khả năng điều chỉnh dựa trên calibration và confidence.

Ví dụ logic:

```text
Short blink
    → Normal

Long blink
    → Attention

Very long closure
    → Drowsy / Critical
```

---

# 10. Blink Detection

Phân biệt:

- Chớp mắt bình thường.
- Chớp mắt dài.
- Mắt nhắm liên tục.

Không được coi mọi lần mắt đóng là buồn ngủ.

Ví dụ:

```text
Normal blink:
OPEN → CLOSED → OPEN

Drowsiness:
OPEN → CLOSED -------- CLOSED → OPEN
```

Phải phân tích duration.

---

# 11. Yawning Detection

Phân tích vùng miệng.

Tính:

## MAR — Mouth Aspect Ratio

Dùng để phát hiện:

```text
Mouth normal
Mouth opening
Yawning
```

Một lần mở miệng ngắn không nhất thiết là ngáp.

Cần kiểm tra:

- mức mở miệng;
- thời gian mở;
- pattern đóng/mở;
- số lần ngáp trong khoảng thời gian.

Yawning là một feature hỗ trợ, không được sử dụng đơn độc để kết luận buồn ngủ.

---

# 12. Head Pose

Phân tích hướng đầu:

```text
Pitch
Yaw
Roll
```

Quan tâm đặc biệt tới:

- đầu cúi xuống;
- đầu gật;
- đầu nghiêng kéo dài;
- khuôn mặt quay khỏi camera.

---

# 13. Head Nodding

Phát hiện pattern:

```text
Head normal
     ↓
Head moves downward
     ↓
Head moves upward
```

Nếu xảy ra lặp lại hoặc kết hợp với:

- mắt nhắm;
- PERCLOS cao;
- ngáp;

thì tăng Drowsiness Score.

Không được coi mọi chuyển động đầu là gật ngủ.

---

# 14. Head Pose Validation

Nếu người lái quay đầu sang bên:

```text
Yaw too large
```

không nên ngay lập tức cảnh báo buồn ngủ.

Phân loại riêng:

```text
FACE_AWAY
```

với:

```text
DROWSINESS
```

Điều này giúp giảm false positive.

---

# 15. Feature Fusion

Không sử dụng một feature duy nhất.

Các feature chính:

```text
EAR
PERCLOS
Eye Closure Duration
Blink Pattern
MAR
Yawning
Head Pitch
Head Nodding
Face Confidence
```

Tạo:

```text
Drowsiness Score
```

Ví dụ khái niệm:

```text
Drowsiness Score =
    Eye score
  + PERCLOS score
  + Long closure score
  + Yawning score
  + Head nodding score
```

Các trọng số phải được thiết kế để mắt/PERCLOS có vai trò lớn hơn các feature phụ.

Không cần cố định công thức trong MVP nếu chưa có dữ liệu thực tế.

Thiết kế code để các threshold/weight có thể điều chỉnh sau này.

---

# 16. Temporal Analysis

Không được đánh giá dựa trên một frame.

Hệ thống phải duy trì lịch sử dữ liệu.

Ví dụ:

```text
Frame
Frame
Frame
Frame
...
```

và tạo:

```text
Time Series
```

Từ đó phát hiện:

- mắt nhắm kéo dài;
- PERCLOS tăng;
- nhiều lần chớp mắt bất thường;
- ngáp liên tục;
- gật đầu;
- xu hướng buồn ngủ tăng.

---

# 17. State Machine

Ứng dụng sử dụng state machine.

```text
NORMAL
   ↓
ATTENTION
   ↓
DROWSY
   ↓
CRITICAL
```

Có thể giảm mức:

```text
CRITICAL
   ↓
DROWSY
   ↓
ATTENTION
   ↓
NORMAL
```

nhưng phải có hysteresis.

Không được chuyển trạng thái liên tục do một vài frame nhiễu.

---

# 18. NORMAL

Điều kiện:

- Mắt mở bình thường.
- PERCLOS thấp.
- Không có long eye closure.
- Không có dấu hiệu gật đầu bất thường.
- Không có pattern ngáp đáng kể.

UI:

```text
You're doing fine
```

hoặc trạng thái tương đương.

Không phát cảnh báo.

---

# 19. ATTENTION

Điều kiện có một hoặc nhiều dấu hiệu nhẹ:

- EAR giảm.
- Chớp mắt dài hơn bình thường.
- PERCLOS bắt đầu tăng.
- Có dấu hiệu ngáp.
- Head pose bất thường trong thời gian ngắn.

UI:

```text
Stay alert
```

Có thể:

- âm thanh nhẹ;
- rung nhẹ;

nhưng không tạo cảnh báo quá mạnh.

---

# 20. DROWSY

Điều kiện:

- PERCLOS cao.
- Mắt đóng lâu.
- Nhiều long blink.
- Ngáp lặp lại.
- Gật đầu.
- Nhiều feature cùng xuất hiện.

UI:

```text
You may be getting drowsy
Take a break
```

Kích hoạt:

- âm thanh cảnh báo;
- rung;
- hiển thị cảnh báo rõ ràng.

---

# 21. CRITICAL

Điều kiện:

- Mắt nhắm rất lâu.
- PERCLOS rất cao.
- Drowsiness Score cao.
- Có nhiều dấu hiệu đồng thời.
- Có pattern gật đầu + mắt nhắm.

Cảnh báo mạnh:

```text
DANGER
WAKE UP
TAKE A BREAK
```

Kích hoạt:

- âm thanh lớn;
- vibration;
- cảnh báo lặp lại cho tới khi người dùng tỉnh táo hoặc dừng monitoring.

Không tự động gọi điện thoại/cứu hộ trong MVP.

---

# 22. Anti False Positive

Hệ thống phải ưu tiên giảm cảnh báo giả.

## Không cảnh báo khi:

- Người dùng chớp mắt bình thường.
- Người dùng quay đầu nhìn gương.
- Người dùng nhìn sang bên.
- Ánh sáng thay đổi trong thời gian ngắn.
- Camera mất mặt trong vài frame.
- Người dùng nói chuyện.
- Người dùng cười.
- Người dùng há miệng trong thời gian rất ngắn.

Cần nhiều tín hiệu kết hợp để tạo cảnh báo mạnh.

Ví dụ:

```text
Long eye closure
+
High PERCLOS
+
Head nodding
=
High confidence drowsiness
```

---

# 23. Confidence

Mỗi feature phải có confidence.

Ví dụ:

```text
Face confidence
Eye confidence
Mouth confidence
Head pose confidence
```

Nếu confidence quá thấp:

```text
Do not update drowsiness score aggressively.
```

Ví dụ khi:

- ánh sáng quá tối;
- mặt bị che;
- đeo kính gây mất landmark;
- khuôn mặt nằm ngoài frame.

---

# 24. Glasses

Ứng dụng cần cố gắng hỗ trợ người dùng đeo kính.

Không yêu cầu tháo kính.

Nếu landmark mắt không đủ tin cậy:

```text
Eye confidence ↓
```

và giảm độ tin cậy của eye-based detection thay vì báo động sai.

---

# 25. Low Light

Nếu ánh sáng quá thấp:

Hiển thị:

```text
Low light
Please improve lighting.
```

Không được tự động kết luận người dùng buồn ngủ chỉ vì landmark kém.

---

# 26. Camera Orientation

Ưu tiên:

```text
Portrait
```

ở MVP nếu giao diện được thiết kế cho điện thoại đặt trên taplo.

Có thể hỗ trợ landscape trong phiên bản sau.

Camera preview phải hiển thị rõ vùng khuôn mặt.

---

# 27. Monitoring UI

Màn hình monitoring gồm:

```text
┌─────────────────────────┐
│                         │
│      Camera Preview     │
│                         │
│       Face Guide        │
│                         │
├─────────────────────────┤
│ Status: NORMAL          │
│                         │
│ Drowsiness: Low         │
│                         │
│ [ Stop Monitoring ]     │
└─────────────────────────┘
```

Không hiển thị quá nhiều thông số kỹ thuật cho người lái.

Màn hình phải đơn giản để không gây phân tâm.

---

# 28. Driving Safety UI

Khi đang monitoring:

- UI tối giản, độ tương phản cao, dễ nhìn khi lái xe ban ngày lẫn ban đêm.
- Nút Stop lớn, dễ bấm.
- Không yêu cầu người dùng thao tác thường xuyên.
- Không có quảng cáo che camera.
- Không hiển thị nội dung gây phân tâm.
- Không yêu cầu nhập dữ liệu trong lúc lái.

## 28.1. Hành vi giao diện khi người dùng tắt màn hình

- Người dùng có thể nhấn nút nguồn hoặc để màn hình tự tắt theo timeout.
- UI hoàn toàn ngắt kết nối khỏi bộ nhớ đồ họa để tiết kiệm pin.
- Hiển thị **Ongoing Notification** thường trực trên màn hình khóa:
  - Cho biết trạng thái bảo vệ (Ví dụ: "Đang bảo vệ an toàn lái xe • Trạng thái: Tỉnh táo").
  - Nút bấm nhanh: `[Dừng giám sát]`.

---

# 29. Alert System

Hệ thống cảnh báo gồm:

## Audio

Các mức:

```text
Attention sound    (Âm nhẹ nhàng nhắc nhở)
Drowsy alarm       (Âm thanh cảnh báo rõ ràng)
Critical alarm     (Còi báo động dồn dập, âm lượng cực đại)
```

Đặc biệt, hệ thống định tuyến âm thanh cảnh báo qua:

```text
AudioAttributes.USAGE_ALARM
hoặc AudioManager.STREAM_ALARM
```

Điều này đảm bảo âm thanh cảnh báo vẫn phát to rõ ngay cả khi người dùng để điện thoại ở chế độ Rung (Vibrate) hoặc Im lặng (Silent / Do Not Disturb).

## Vibration

```text
Short vibration     (Nhắc nhở nhẹ)
Long vibration      (Cảnh báo buồn ngủ)
Repeated vibration  (Rung dồn dập liên tục cho mức Critical)
```

## Voice

Có thể sử dụng Text-to-Speech:

```text
"Please stay alert."

"You appear to be getting sleepy."

"Please take a break."
```

Voice warning là thành phần bổ sung sau còi báo động.

## 29.1. Cơ chế cảnh báo khẩn cấp khi màn hình đang tắt / khóa

Khi phát hiện trạng thái **DROWSY** hoặc **CRITICAL** trong lúc màn hình đang tắt:

1. **Full-screen Intent Notification**:
   - Gửi một thông báo mức ưu tiên cao nhất (`PRIORITY_MAX`, `IMPORTANCE_HIGH`) chứa PendingIntent toàn màn hình.
2. **Đánh thức màn hình tức thì**:
   - Bật sáng màn hình bằng cờ Window: `turnScreenOn = true`, `setShowWhenLocked(true)` (Android 8.0+) hoặc `PowerManager.ACQUIRE_CAUSES_WAKEUP`.
   - Hiển thị trực tiếp màn hình cảnh báo đỏ rực (Screen 4 / Screen 5) đè lên màn hình khóa mà không bắt người dùng phải mở khóa máy.
3. **Phát chuông báo động và rung dồn dập** cho đến khi người lái tỉnh táo, bấm nút xác nhận hoặc dừng giám sát.

---

# 30. Alert Cooldown

Không phát cảnh báo liên tục mỗi frame.

Có:

```text
Alert cooldown
```

Ví dụ:

```text
Alert triggered
      ↓
Cooldown
      ↓
Evaluate again
```

Nếu trạng thái vẫn CRITICAL:

```text
Repeat alert
```

theo khoảng thời gian hợp lý.

---

# 31. Recovery

Khi người dùng tỉnh táo trở lại:

```text
CRITICAL
 ↓
DROWSY
 ↓
ATTENTION
 ↓
NORMAL
```

Phải yêu cầu trạng thái ổn định trong một khoảng thời gian trước khi giảm cảnh báo.

Không được:

```text
CRITICAL → NORMAL
```

chỉ vì một vài frame mắt mở.

---

# 32. Session

Mỗi lần người dùng nhấn:

```text
Start Monitoring
```

tạo một session.

Session gồm:

```text
sessionId
startTime
endTime
duration
maxDrowsinessLevel
numberOfAlerts
```

Không lưu video.

Không lưu ảnh khuôn mặt.

---

# 33. Local History

Có thể lưu lịch sử trên thiết bị:

```text
Today's sessions
Previous sessions
```

Ví dụ:

```text
22 Sep 2026
Duration: 1h 24m
Alerts: 3
Maximum level: Drowsy
```

Chỉ lưu dữ liệu thống kê.

---

# 34. Privacy

Privacy là yêu cầu quan trọng.

Ứng dụng phải:

- Xử lý camera trên thiết bị.
- Không upload video.
- Không upload ảnh khuôn mặt.
- Không nhận diện danh tính.
- Không yêu cầu tài khoản để sử dụng MVP.
- Không lưu biometric data.
- Không gửi facial landmarks lên server.

Nếu analytics được thêm sau này:

Chỉ gửi event không chứa hình ảnh hoặc dữ liệu nhận dạng cá nhân.

Ví dụ:

```text
session_started
session_ended
alert_triggered
alert_level
session_duration
```

---

# 35. Permissions

Hệ thống yêu cầu các quyền thực sự cần thiết phục vụ giám sát camera, chạy nền và phát hiện di chuyển:

## Quyền bắt buộc (Core Monitoring & Screen-off Service):

- `android.permission.CAMERA`: Truy cập camera trước để nhận diện khuôn mặt và trạng thái buồn ngủ.
- `android.permission.FOREGROUND_SERVICE`: Cho phép service chạy nền duy trì phiên giám sát.
- `android.permission.FOREGROUND_SERVICE_CAMERA`: Bắt buộc từ Android 14+ (API 34) để service có quyền truy cập camera khi app ở chế độ nền.
- `android.permission.WAKE_LOCK`: Giữ CPU chạy (`PARTIAL_WAKE_LOCK`) khi người dùng tắt màn hình để thuật toán AI tiếp tục xử lý frame.
- `android.permission.POST_NOTIFICATIONS`: Bắt buộc từ Android 13+ (API 33) để hiển thị thông báo trạng thái thường trực và cảnh báo.
- `android.permission.USE_FULL_SCREEN_INTENT`: Cho phép hiển thị giao diện báo động khẩn cấp đè lên màn hình khóa khi phát hiện buồn ngủ.

## Quyền tự động phát hiện tốc độ cao (Auto Driving Detection):

- `android.permission.ACTIVITY_RECOGNITION`: Nhận diện trạng thái di chuyển bằng ô tô (`IN_VEHICLE`).
- `android.permission.ACCESS_FINE_LOCATION` & `android.permission.ACCESS_COARSE_LOCATION`: Đọc dữ liệu GPS tính toán tốc độ di chuyển thực tế của xe.
- `android.permission.ACCESS_BACKGROUND_LOCATION`: Cho phép kiểm tra vận tốc xe khi ứng dụng đang chạy nền.
- `android.permission.FOREGROUND_SERVICE_LOCATION`: Bắt buộc từ Android 14+ nếu Foreground Service sử dụng GPS để theo dõi tốc độ.

## Tuyệt đối không yêu cầu:

- Contacts
- SMS
- Phone
- External Storage / Photos
- Audio Recording (Microphone)

---

# 36. Background Monitoring qua Foreground Service

Giám sát khi tắt màn hình là tính năng sống còn để ứng dụng có thể sử dụng thực tế trong suốt các chuyến đi dài, giúp điện thoại không bị quá nhiệt và tiết kiệm pin tối đa.

## 36.1. Kiến trúc Foreground Service

```text
Activity / UI (Mở / Đóng linh hoạt)
          │
          ├────── Khởi động / Gắn kết (Bind) ──────┐
          │                                        │
          ▼                                        ▼
DrowsinessMonitoringService (Foreground Service độc lập)
   │
   ├─ 1. Sticky Ongoing Notification (Báo hiệu hệ thống đang bảo vệ)
   ├─ 2. Partial WakeLock (Giữ CPU hoạt động, cho phép màn hình & GPU tắt)
   ├─ 3. Headless CameraX Pipeline (Chỉ chạy ImageAnalysis, không Preview)
   ├─ 4. Lắng nghe Intent.ACTION_SCREEN_OFF / SCREEN_ON
   └─ 5. Phối hợp với DrowsinessEngine & AlertManager
```

## 36.2. Cơ chế hoạt động khi tắt màn hình (Screen-off)

1. **Bắt sự kiện tắt màn hình**:
   - `DrowsinessMonitoringService` đăng ký một `BroadcastReceiver` lắng nghe `Intent.ACTION_SCREEN_OFF` và `Intent.ACTION_SCREEN_ON`.
2. **Kích hoạt Partial WakeLock**:
   - Khi nhận `ACTION_SCREEN_OFF`, service acquire một `PowerManager.PARTIAL_WAKE_LOCK`.
   - WakeLock này đảm bảo CPU tiếp tục chạy vòng lặp xử lý ảnh và tính toán AI, trong khi toàn bộ màn hình (Display) và chip xử lý đồ họa (GPU) chuyển sang trạng thái ngủ sâu hoàn toàn.
3. **Chuyển đổi sang chế độ Headless (Không render)**:
   - Hủy bỏ (unbind) usecase `Preview` khỏi CameraX.
   - Ngắt toàn bộ SurfaceProvider, vô hiệu hóa việc vẽ landmark overlay và cập nhật giao diện người dùng.
4. **Hạ tần số xử lý (Adaptive FPS)**:
   - Giảm frame rate phân tích xuống **2–3 FPS** khi người lái đang ở trạng thái NORMAL.
   - Khi phát hiện dấu hiệu nghi ngờ (nhắm mắt chớm lâu hoặc đầu bắt đầu gật), tự động burst lên **8–10 FPS** để xác thực trong 1.5–2.5 giây.

## 36.3. Tuân thủ chính sách bảo mật hệ điều hành

- Trên Android 14+ (API 34), việc chạy camera ngầm được quản lý rất nghiêm ngặt.
- Service phải khai báo rõ ràng trong `AndroidManifest.xml`:
  ```xml
  <service
      android:name=".service.DrowsinessMonitoringService"
      android:foregroundServiceType="camera|location"
      android:exported="false" />
  ```
- Hiển thị liên tục một **Ongoing Notification** minh bạch với icon camera an toàn, thông tin chuyến đi và nút bấm [Dừng giám sát] để người dùng luôn có toàn quyền kiểm soát.
- Khi người dùng dừng giám sát hoặc xe dừng hoàn toàn: Service lập tức unbind camera, giải phóng WakeLock và thu hồi Notification.

---

# 37. Performance

Mục tiêu hiệu năng phân tích:

```text
Màn hình sáng (Foreground UI):            5–8 FPS
Màn hình tắt  (Background - An toàn):      2–3 FPS (Frame interval ~350–500ms)
Màn hình tắt  (Background - Nghi ngờ):     8–10 FPS (Burst xác minh trong 1–2s)
```

## Các kỹ thuật tối ưu hóa hiệu năng tính toán:

- **Throttling thích ứng qua Timestamp (FPS Gate)**:
  Trong `ImageAnalysis.Analyzer`, so sánh timestamp của frame hiện tại với frame vừa xử lý:
  ```kotlin
  val minIntervalMs = if (isScreenOff) {
      if (currentState == DrowsinessState.NORMAL) 400L // ~2.5 FPS
      else 125L // 8 FPS burst
  } else {
      150L // ~6.6 FPS
  }
  if (currentTimeMs - lastAnalyzedTimestampMs < minIntervalMs) {
      imageProxy.close() // Bỏ qua frame ngay lập tức
      return
  }
  ```
  Nếu chưa tới thời điểm, frame bị drop ngay lập tức mà không tiêu tốn thêm bất kỳ chu kỳ CPU/NPU nào.
- **Zero UI Rendering (Xử lý Headless)**:
  Khi màn hình tắt hoặc app chạy nền, không chuyển đổi frame thành Bitmap, không vẽ Surface, không cập nhật View tree. Tiết kiệm 100% tài nguyên GPU và compositing pipeline của Android OS.
- **Tái sử dụng bộ nhớ đệm (Zero Allocation Loop)**:
  - Tuyệt đối không khởi tạo đối tượng mới (FloatArray, Point, Matrix, Rect) bên trong vòng lặp phân tích frame.
  - Sử dụng buffer tái sử dụng được cấp phát trước (Pre-allocated arrays) để lưu tọa độ landmark.
- **Phân tách luồng xử lý (Offloading)**:
  - Toàn bộ khâu trích xuất landmark và tính điểm buồn ngủ chạy trên `Executors.newSingleThreadExecutor()` hoặc Kotlin Coroutine Dispatcher (`Dispatchers.Default`).
  - Đảm bảo Main Thread luôn rảnh rỗi và mượt mà.
- **Độ phân giải đầu vào tối ưu**:
  - Cấu hình CameraX với resolution mục tiêu `640x480` (480p) hoặc `720p`. Không sử dụng 1080p hay 4K vì MediaPipe Face Landmarker chỉ cần ảnh đầu vào độ phân giải nhỏ để trích xuất điểm khuôn mặt.

---

# 38. Battery — Chiến lược Tiết kiệm Pin Cực đại

Giám sát bằng camera và AI liên tục là tác vụ tiêu thụ nhiều năng lượng. Ứng dụng áp dụng **5 trụ cột tiết kiệm pin**:

```text
┌─────────────────────────────────────────────────────────────┐
│                 5 TRỤ CỘT TIẾT KIỆM PIN                      │
├──────────────────────────┬──────────────────────────────────┤
│ 1. Hạ FPS thích ứng      │ 2–3 FPS khi bình thường          │
│                          │ 8–10 FPS burst khi nghi ngờ      │
├──────────────────────────┼──────────────────────────────────┤
│ 2. Zero UI Rendering     │ Không render surface khi tắt màn │
│                          │ hình, giải phóng 100% GPU        │
├──────────────────────────┼──────────────────────────────────┤
│ 3. Giảm phân giải cảm    │ Sử dụng 480p (640x480) thay vì   │
│    biến camera           │ 1080p / 4K                       │
├──────────────────────────┼──────────────────────────────────┤
│ 4. Tối ưu WakeLock       │ Chỉ dùng PARTIAL_WAKE_LOCK (CPU) │
│                          │ Màn hình OLED tắt đen hoàn toàn  │
├──────────────────────────┼──────────────────────────────────┤
│ 5. Quản lý dừng xe       │ Chế độ Grace Period tạm nghỉ khi │
│    thông minh            │ xe dừng đèn đỏ / kẹt xe          │
└──────────────────────────┴──────────────────────────────────┘
```

1. **Hạ FPS thích ứng (Adaptive Frame Rate)**:
   - Ở 2–3 FPS, số lượng phép tính AI giảm tới **70%** so với 10 FPS.
   - Nhiệt độ thiết bị giữ ở mức mát mẻ, ngăn chặn hiện tượng quá nhiệt (Thermal Throttling) làm sụt nguồn điện thoại.
2. **Headless Execution & Tắt màn hình**:
   - Màn hình điện thoại (đặc biệt là màn hình OLED/AMOLED) tiêu thụ từ 40% đến 60% tổng năng lượng của một ứng dụng đang mở.
   - Việc cho phép người dùng tắt màn hình trong khi Service vẫn hoạt động giúp giảm hơn 50% mức tiêu hao pin tổng thể của thiết bị.
3. **Không tạo rác bộ nhớ (Zero Garbage Collection Pressure)**:
   - Giảm thiểu việc GC (Garbage Collector) phải chạy liên tục, tránh CPU bị đánh thức và tiêu thụ điện không cần thiết.
4. **Cảm biến chuyển động siêu tiết kiệm năng lượng**:
   - Module nhận diện lái xe ban đầu chỉ sử dụng cảm biến phần cứng bước thấp (Step/Activity Sensor) và chỉ bật GPS định kỳ khi cần xác nhận tốc độ, không giữ GPS liên tục 24/7.

---

# 39. Architecture

Đề xuất kiến trúc đa tầng (Multi-tier Architecture):

```text
┌─────────────────────────────────────────────────────────────┐
│                 Motion & Trigger Layer                      │
│   ActivityTransition (IN_VEHICLE) + FusedLocation (Speed)   │
└──────────────────────────────┬──────────────────────────────┘
                               │ Tự động kích hoạt khi tốc độ cao
                               ▼
┌─────────────────────────────────────────────────────────────┐
│            DrowsinessMonitoringService (Foreground)         │
│  - Partial WakeLock Controller (giữ CPU khi tắt màn hình)   │
│  - PowerAdaptiveManager (Điều khiển FPS: 2-3 FPS <-> 8-10)  │
│  - Ongoing Notification & Full-Screen Intent Dispatcher     │
└──────────────┬──────────────────────────────┬───────────────┘
               │                              │
     Màn hình BẬT (UI Active)         Màn hình TẮT (Headless)
               │                              │
               ▼                              ▼
     PreviewView + Surface         Headless Frame Buffer
     (Hiển thị 5–8 FPS)            (Zero UI Render, 2–3 FPS)
               │                              │
               └──────────────┬───────────────┘
                              ▼
                      DrowsinessEngine
                              │
               ├── FaceLandmarkAnalyzer (MediaPipe)
               ├── EyeAnalyzer (EAR, Blink, Long Closure)
               ├── PERCLOS Calculator (Sliding Windows)
               ├── MouthAnalyzer (MAR, Yawning)
               ├── HeadPoseAnalyzer (Pitch, Yaw, Roll, Nod)
               ├── TemporalAnalyzer & ScoreCalculator
               └── StateMachine (NORMAL / ATTENTION / DROWSY / CRITICAL)
                              │
                              ▼
                         AlertManager
                              │
               ├── Audio (STREAM_ALARM - Xuyên chế độ im lặng)
               ├── Vibration (Haptic Feedback)
               ├── Voice (Text-to-Speech)
               └── ScreenWakeController (Bật sáng màn hình khi nguy hiểm)
```

---

# 40. Suggested Kotlin Modules

```text
motion/
    MotionDetector              // Lắng nghe ActivityTransition (IN_VEHICLE)
    SpeedTracker                // Đọc vận tốc GPS từ FusedLocationProvider
    AutoDrivingController       // Logic tự động bắt đầu / Grace Period dừng xe

service/
    DrowsinessMonitoringService // Foreground Service cốt lõi
    MonitoringNotificationManager // Sticky Notification & Full-screen Intent
    ScreenStateReceiver         // Lắng nghe SCREEN_ON / SCREEN_OFF

power/
    PowerAdaptiveManager        // Điều khiển chiến lược FPS thích ứng
    WakeLockManager             // Quản lý PARTIAL_WAKE_LOCK an toàn

camera/
    CameraManager               // Quản lý CameraX & vòng đời cảm biến
    FrameAnalyzer               // ImageAnalysis & Frame Throttling Gate
    HeadlessPipeline            // Pipeline xử lý buffer trực tiếp không Preview

vision/
    FaceLandmarkAnalyzer        // MediaPipe Face Landmarker on-device
    EyeAnalyzer                 // Tính toán EAR & phát hiện nhắm mắt
    MouthAnalyzer               // MAR & ngáp
    HeadPoseAnalyzer            // Hướng quay & gật đầu

drowsiness/
    DrowsinessEngine            // Bộ não trung tâm tổng hợp chỉ số
    DrowsinessScore             // Trọng số & thuật toán tính điểm
    DrowsinessState             // State Machine (NORMAL, ATTENTION, DROWSY, CRITICAL)
    TemporalAnalyzer            // Time-series & Sliding window (PERCLOS)
    CalibrationManager          // Hồ sơ hiệu chuẩn người lái

alert/
    AlertManager                // Điều phối cảnh báo
    AudioAlert                  // STREAM_ALARM audio player
    VibrationAlert              // Haptic feedback controller
    VoiceAlert                  // Text-to-Speech
    ScreenWakeController        // Full-screen Intent & bật sáng màn hình khóa

session/
    DrivingSession              // Thông tin phiên lái xe
    SessionRepository           // Lưu trữ lịch sử cục bộ Room/DataStore

ui/
    MainScreen                  // Màn hình chính & cấu hình nhanh
    CalibrationScreen           // Màn hình hiệu chuẩn ban đầu
    MonitoringScreen            // Màn hình giám sát khi mở máy
    HistoryScreen               // Lịch sử các chuyến đi
```

---

# 41. Data Models

Example:

```kotlin
data class FaceMetrics(
    val faceConfidence: Float,
    val leftEar: Float,
    val rightEar: Float,
    val averageEar: Float,
    val mar: Float,
    val pitch: Float,
    val yaw: Float,
    val roll: Float
)
```

Drowsiness:

```kotlin
data class DrowsinessMetrics(
    val eyeScore: Float,
    val perclosScore: Float,
    val blinkScore: Float,
    val yawnScore: Float,
    val headScore: Float,
    val totalScore: Float
)
```

State:

```kotlin
enum class DrowsinessState {
    NORMAL,
    ATTENTION,
    DROWSY,
    CRITICAL
}
```

Motion & Driving Detection:

```kotlin
enum class MotionActivity {
    UNKNOWN,
    STILL,
    WALKING,
    IN_VEHICLE
}

data class DrivingMotionInfo(
    val activity: MotionActivity,
    val speedKmh: Float,
    val isVehicleConfirmed: Boolean,
    val isAutoStarted: Boolean
)
```

Power & FPS Strategy:

```kotlin
enum class PowerMode {
    FOREGROUND_PREVIEW,          // Màn hình bật: 5–8 FPS, có Preview
    BACKGROUND_POWERSAVE,        // Màn hình tắt: 2–3 FPS, headless không render
    BACKGROUND_BURST_EVALUATION  // Màn hình tắt: 8–10 FPS burst xác minh trong 1–2s
}

data class FpsStrategy(
    val targetFps: Int,
    val minIntervalMs: Long,
    val isHeadless: Boolean
)
```

---

# 42. Calibration Data

Ví dụ:

```kotlin
data class CalibrationProfile(
    val baselineEar: Float,
    val eyeClosedThreshold: Float,
    val baselineMar: Float,
    val baselinePitch: Float,
    val baselineYaw: Float
)
```

Calibration profile chỉ chứa numerical parameters.

Không chứa ảnh.

---

# 43. Drowsiness Engine

Input:

```text
FaceMetrics
+
CalibrationProfile
+
TemporalHistory
```

Output:

```text
DrowsinessMetrics
+
DrowsinessState
+
Confidence
```

Ví dụ:

```text
FaceMetrics
      ↓
Feature Extraction
      ↓
Temporal Analysis
      ↓
Score Calculation
      ↓
State Machine
      ↓
AlertManager
```

---

# 44. Threshold Configuration

Không hard-code toàn bộ threshold.

Tạo configuration:

```kotlin
data class DrowsinessConfig(
    val eyeClosedDurationMs: Long,
    val criticalEyeClosedDurationMs: Long,
    val perclosAttentionThreshold: Float,
    val perclosDrowsyThreshold: Float,
    val perclosCriticalThreshold: Float,
    val yawnThreshold: Float,
    val headNodThreshold: Float,
    val alertCooldownMs: Long,
    // Tự động phát hiện di chuyển tốc độ cao
    val autoStartSpeedThresholdKmh: Float = 25.0f,
    val autoStartConfirmDurationSec: Int = 20,
    val autoStopGracePeriodMs: Long = 300_000L, // 5 phút dừng xe tạm thời
    // Tối ưu hóa pin & FPS thích ứng
    val foregroundFps: Int = 6,                  // 5–8 FPS khi mở UI
    val backgroundNormalFps: Int = 3,            // 2–3 FPS khi an toàn
    val backgroundBurstFps: Int = 8,             // 8–10 FPS khi chớm nghi ngờ
    val burstEvaluationDurationMs: Long = 2_000L
)
```

Các giá trị phải dễ thay đổi trong quá trình testing.

---

# 45. Hysteresis

Ví dụ:

```text
DROWSY threshold = X

Recovery threshold = Y

Y < X
```

Nhờ đó trạng thái không bị:

```text
DROWSY
NORMAL
DROWSY
NORMAL
```

liên tục.

---

# 46. Multi-signal Confirmation

Đối với cảnh báo mạnh, ưu tiên yêu cầu nhiều tín hiệu.

Ví dụ:

```text
PERCLOS HIGH
+
LONG EYE CLOSURE
```

hoặc:

```text
PERCLOS HIGH
+
HEAD NOD
```

hoặc:

```text
LONG EYE CLOSURE
+
HEAD NOD
+
YAWN
```

→ tăng confidence.

---

# 47. Error Handling

Các trường hợp cần xử lý:

## Camera permission denied

Hiển thị hướng dẫn cấp quyền.

## Camera unavailable

Hiển thị:

```text
Camera unavailable
```

## Face not detected

```text
Please keep your face visible.
```

## Multiple faces

MVP:

```text
Only one driver should be visible.
```

## Low confidence

Tạm dừng tính toán hoặc giảm trọng số.

## Camera stopped

Dừng monitoring an toàn.

---

# 48. App Screens

## Screen 1 — Home

```text
Driver Safety

Monitor your alertness while driving.

[ Start Monitoring ]

[ History ]

[ Settings ]
```

## Screen 2 — Calibration

```text
Get Ready

Keep your face visible
and look naturally ahead.

Calibrating...

██████████░░░░

10 seconds
```

## Screen 3 — Monitoring

```text
Monitoring

[ Camera Preview ]

Status
NORMAL

Alertness
Good

[ Stop ]
```

## Screen 4 — Drowsy

```text
⚠ Stay Alert

You may be getting sleepy.

Please consider taking a break.
```

## Screen 5 — Critical

```text
⚠ WAKE UP

You appear to be very drowsy.

PLEASE STOP AND TAKE A BREAK
```

## Screen 6 — Sticky Background Notification (Màn hình khóa / Thông báo nền)

```text
┌─────────────────────────────────────────────────────────────┐
│ 🚗 Giám sát an toàn lái xe                         [ ĐANG CHẠY ]│
│ Đang bảo vệ hành trình • Trạng thái: Tỉnh táo               │
│ Tốc độ: 58 km/h • Chế độ siêu tiết kiệm pin (3 FPS)         │
│ [ Mở ứng dụng ]                     [ Dừng giám sát ]       │
└─────────────────────────────────────────────────────────────┘
```

## Screen 7 — Lock Screen Alert (Full-Screen Intent khi tắt màn hình)

```text
┌─────────────────────────────────────────────────────────────┐
│                      ⚠ CẢNH BÁO NGUY HIỂM                    │
│                                                             │
│                    BẠN ĐANG CÓ DẤU HIỆU                     │
│                         BUỒN NGỦ!                           │
│                                                             │
│             HÃY TẤP VÀO LỀ VÀ NGHỈ NGƠI NGAY LẬP TỨC        │
│                                                             │
│                      [ TÔI ĐÃ TỈNH TÁO ]                    │
└─────────────────────────────────────────────────────────────┘
```

---

# 49. Settings

Settings gồm:

```text
- Tự động kích hoạt khi lái xe (Auto-detect Driving): Bật / Tắt
- Ngưỡng tốc độ kích hoạt: 20 km/h | 25 km/h | 30 km/h
- Giám sát khi tắt màn hình (Screen-off Monitoring): Bật / Tắt
- Chế độ siêu tiết kiệm pin (Adaptive FPS & Headless): Bật / Tắt
- Thời gian chờ khi dừng xe (Grace Period): 3 phút | 5 phút | 10 phút
- Âm lượng cảnh báo (Alert Volume)
- Rung xúc giác (Vibration)
- Cảnh báo giọng nói (Voice alerts - TTS)
- Độ nhạy nhận diện (Sensitivity: Low / Normal / High)
- Hiệu chuẩn lại (Recalibration)
- Giữ sáng màn hình (Keep screen awake): Tùy chọn nếu đặt trên giá đỡ
- Chế độ ban đêm (Dark mode)
```

Không để các setting phức tạp làm người dùng phân tâm khi đang lái.

---

# 50. Testing

Cần test các trường hợp:

## Normal

- Nhìn thẳng.
- Chớp mắt.
- Nói chuyện.
- Cười.
- Quay đầu.

## Drowsiness simulation

- Nhắm mắt 1–2 giây.
- Nhắm mắt lâu.
- Ngáp nhiều lần.
- Gật đầu.
- Kết hợp mắt nhắm + gật đầu.

## Environmental

- Ban ngày.
- Ban đêm.
- Ánh sáng yếu.
- Ngược sáng.
- Đeo kính.
- Không đeo kính.
- Khuôn mặt hơi quay sang bên.

---

# 51. Important Safety Rule

Không được coi:

```text
one frame
```

là bằng chứng buồn ngủ.

Luôn sử dụng:

```text
Temporal analysis
+
Confidence
+
Multiple signals
+
State machine
```

để quyết định cảnh báo.

---

# 52. MVP Scope

Phiên bản MVP bao gồm:

1. CameraX (Chế độ kép: Preview UI và Headless ImageAnalysis)
2. Tự động phát hiện di chuyển tốc độ cao (Activity Recognition + GPS Speed)
3. Giám sát dưới nền bằng Foreground Service ngay cả khi tắt màn hình
4. Quản lý Partial WakeLock an toàn khi khóa máy
5. Chiến lược hạ FPS thích ứng (2–3 FPS khi bình thường, 8–10 FPS burst khi nghi ngờ)
6. Zero UI Rendering (Hoàn toàn không render khi màn hình tắt để tiết kiệm pin tối đa)
7. Face Landmarks on-device (MediaPipe)
8. EAR (Eye Aspect Ratio)
9. PERCLOS (Sliding Window)
10. Eye Closure Duration
11. MAR / Yawning Detection
12. Head Pose & Head Nodding
13. Feature Fusion & Drowsiness Score
14. State Machine (NORMAL, ATTENTION, DROWSY, CRITICAL)
15. Audio Alert (STREAM_ALARM xuyên chế độ im lặng), Vibration, TTS
16. Full-screen Intent & Đánh thức màn hình khẩn cấp khi có nguy cơ buồn ngủ cao
17. Calibration (Hồ sơ hiệu chuẩn cục bộ)
18. Auto-stop & Grace Period khi dừng xe (đèn đỏ / kẹt xe)
19. Local Session History (Lịch sử chuyến đi lưu trên máy)
20. Privacy-first local processing (100% xử lý trên thiết bị, không upload video/ảnh)

Không cần trong MVP:

- Cloud server
- User account
- Facial recognition
- Cloud AI
- Remote monitoring
- Automatic emergency calling
- Driver identification
- Large custom AI model

---

# 53. Future Version

Sau khi MVP hoạt động ổn định, có thể bổ sung:

## Machine Learning

Thu thập feature sequence:

```text
EAR(t)
PERCLOS(t)
MAR(t)
Pitch(t)
Yaw(t)
Blink(t)
```

Sau đó dùng:

```text
1D CNN
LSTM
TCN
Transformer
```

để phân loại:

```text
Alert
Slightly Drowsy
Drowsy
Severely Drowsy
```

Model vẫn ưu tiên chạy on-device.

---

# 54. Development Priority

AI coding agent phải triển khai theo thứ tự:

### Phase 1

Camera preview & Headless capture pipeline.

### Phase 2

Face landmark detection on-device.

### Phase 3

EAR (Eye Aspect Ratio).

### Phase 4

Eye closure duration.

### Phase 5

PERCLOS (Sliding Window).

### Phase 6

MAR / yawning detection.

### Phase 7

Head pose & nodding detection.

### Phase 8

Temporal analyzer.

### Phase 9

Drowsiness score calculation.

### Phase 10

State machine & hysteresis.

### Phase 11

Audio (STREAM_ALARM) / vibration / TTS / Full-screen Intent.

### Phase 12

Calibration profile manager.

### Phase 13

Foreground Service & Screen-off monitoring với Partial WakeLock.

### Phase 14

Adaptive FPS & Zero UI Rendering (Ultra-low battery optimization).

### Phase 15

High-speed motion detection (Activity Recognition + GPS speed tracker & Grace period).

### Phase 16

Session repository & Local history.

### Phase 17

Real-world road testing & thermal/battery profiling.

---

# 55. Core Principle

Ứng dụng không được xây dựng theo kiểu:

```text
Eyes closed = Drowsy
```

mà phải theo:

```text
Face
 ↓
Eyes
 ↓
EAR
 ↓
Temporal Eye Analysis
 ↓
PERCLOS
 ↓
Mouth Analysis
 ↓
Head Pose
 ↓
Multi-signal Fusion
 ↓
Drowsiness Score
 ↓
State Machine
 ↓
Alert
```

Mục tiêu cuối cùng là tạo một hệ thống **phát hiện xu hướng buồn ngủ theo thời gian**, giảm cảnh báo giả và đưa ra cảnh báo đủ sớm để người lái có thể chủ động nghỉ ngơi.
