# Tọa độ vùng hoạt động của danh mục dịch vụ

Ngày đối chiếu: 20/11/2026. Hệ tọa độ: WGS84 / EPSG:4326.

24 dịch vụ trong `DB_Demo/base/services.json` được gán vào 9 vùng nước theo địa điểm, mô tả hiện có và các nguồn công khai. Đây là điểm tham chiếu vùng hoạt động để truy vấn thời tiết; không phải tọa độ văn phòng, bãi đỗ xe hoặc tọa độ tâm đảo. Không gom danh mục về Mân Thái.

**Giới hạn xác minh:** nguồn công khai xác nhận khu vực hoạt động ở nhiều dịch vụ, nhưng không công bố đầy đủ điểm vận hành GPS. Các điểm nước được chọn bằng đối chiếu địa danh và đường bờ OpenStreetMap. Chưa có điểm nào được nhà cung cấp xác nhận GPS trực tiếp. Không được đổi `operator_confirmed_exact_point` thành `true` chỉ vì API trả dữ liệu hoặc điểm nằm ngoài đất liền.

## Đối chiếu địa lý

- Nguồn có cấu trúc, cơ sở chọn điểm và thông tin cũ: [`service_coordinates.json`](../../DB_Demo/geography/service_coordinates.json).
- Đường bờ loại trừ đất liền: [`land_mask.geojson`](../../DB_Demo/geography/land_mask.geojson). Phạm vi hiệu lực được ghi rõ trong `coverage_bounds`; không dùng mask này để xác minh địa điểm ngoài phạm vi.
- Tám way OpenStreetMap được tải qua API ngày 20/11/2026, nối theo đường bờ và đóng polygon về phía đất liền. Polygon phía tây được sửa tính hợp lệ và cắt trong vùng đối chiếu. Các đoạn đóng polygon nhân tạo không được dùng tính khoảng cách đến bờ.
- Các điểm được chọn cách đường bờ được ánh xạ khoảng 137 m đến 10,7 km. Khoảng cách là xấp xỉ hình học, không chứng minh độ sâu, rạn san hô chính xác hoặc vùng hoạt động được cấp phép.
- Dữ liệu đường bờ: © OpenStreetMap contributors, [ODbL 1.0](https://www.openstreetmap.org/copyright). Khi phân phối dữ liệu dẫn xuất, giữ ghi nhận nguồn và điều kiện giấy phép.

## Các vùng tham chiếu

| Vùng nước | Vĩ độ | Kinh độ | Số dịch vụ |
|---|---:|---:|---:|
| Vùng nước ngoài bãi Danabeach – Mỹ An | 16.048901 | 108.251890 | 3 |
| Vùng nước Mỹ An trước Holiday Beach – Surf Shack | 16.050466 | 108.251286 | 4 |
| Vùng nước ven bãi Mỹ Khê | 16.060159 | 108.249054 | 9 |
| Vùng nước ven bãi Rạng – Sơn Trà | 16.104181 | 108.292191 | 1 |
| Vùng nước ven bãi Nam – Sơn Trà | 16.102846 | 108.298691 | 1 |
| Vùng nước ven bãi Mân Thái | 16.088058 | 108.251497 | 1 |
| Vùng nước phía đông Sơn Trà | 16.112000 | 108.326000 | 3 |
| Vùng nước phía tây Sơn Trà – ngoài khu vực Tiên Sa/Thọ Quang | 16.120000 | 108.204500 | 1 |
| Vùng biển giữa Sơn Trà và hướng Cù Lao Chàm | 16.040000 | 108.390000 | 1 |

## Ánh xạ từng dịch vụ

| Dịch vụ | Vùng tham chiếu | Phạm vi đánh giá |
|---|---|---|
| Mô tô nước – Danabeach | Vùng nước ngoài bãi Danabeach – Mỹ An | `SINGLE_ACTIVITY_AREA_REFERENCE` |
| Thuê ván SUP – Surf Shack | Vùng nước Mỹ An trước Holiday Beach – Surf Shack | `SINGLE_ACTIVITY_AREA_REFERENCE` |
| Lớp học lướt sóng Mỹ Khê – DNOA | Vùng nước ven bãi Mỹ Khê | `SINGLE_ACTIVITY_AREA_REFERENCE` |
| Mô tô nước – Kajin Vina | Vùng nước ven bãi Mỹ Khê | `SINGLE_ACTIVITY_AREA_REFERENCE` |
| Dù bay trên biển – Danabeach | Vùng nước ngoài bãi Danabeach – Mỹ An | `SINGLE_ACTIVITY_AREA_REFERENCE` |
| Dù bay trên biển – Kajin Vina | Vùng nước ven bãi Mỹ Khê | `SINGLE_ACTIVITY_AREA_REFERENCE` |
| Thuê ván lướt sóng – Surf Shack | Vùng nước Mỹ An trước Holiday Beach – Surf Shack | `SINGLE_ACTIVITY_AREA_REFERENCE` |
| Thuê ván lướt sóng – DNOA | Vùng nước ven bãi Mỹ Khê | `SINGLE_ACTIVITY_AREA_REFERENCE` |
| Câu cá chuyên nghiệp Sơn Trà – DaNaFi | Vùng nước phía đông Sơn Trà | `VARIABLE_OFFSHORE_AREA_REFERENCE` |
| Lặn ngắm san hô Sơn Trà – Loca Travel | Vùng nước ven bãi Rạng – Sơn Trà | `SINGLE_ACTIVITY_AREA_REFERENCE` |
| Học lướt sóng – Surf Shack | Vùng nước Mỹ An trước Holiday Beach – Surf Shack | `SINGLE_ACTIVITY_AREA_REFERENCE` |
| Kayak kết hợp trải nghiệm biển – DNOA | Vùng nước ven bãi Mỹ Khê | `ROUTE_REFERENCE_ONLY` |
| Phao chuối – Danabeach | Vùng nước ngoài bãi Danabeach – Mỹ An | `SINGLE_ACTIVITY_AREA_REFERENCE` |
| Câu cá biển bằng cano riêng – D2Tour | Vùng biển giữa Sơn Trà và hướng Cù Lao Chàm | `ROUTE_REFERENCE_ONLY` |
| Câu mực đêm – Khuê Nguyễn | Vùng nước phía tây Sơn Trà – ngoài khu vực Tiên Sa/Thọ Quang | `VARIABLE_OFFSHORE_AREA_REFERENCE` |
| Học kayak và trò chơi Splash & Dash – DNOA | Vùng nước ven bãi Mỹ Khê | `SINGLE_ACTIVITY_AREA_REFERENCE` |
| Surf tour tìm điểm sóng – Surf Shack | Vùng nước Mỹ An trước Holiday Beach – Surf Shack | `ROUTE_REFERENCE_ONLY` |
| Câu cá biển Sơn Trà – DaNaFi | Vùng nước phía đông Sơn Trà | `VARIABLE_OFFSHORE_AREA_REFERENCE` |
| Thuê kayak đơn hoặc đôi – DNOA | Vùng nước ven bãi Mỹ Khê | `SINGLE_ACTIVITY_AREA_REFERENCE` |
| Dù bay trên biển – DNOA | Vùng nước ven bãi Mỹ Khê | `SINGLE_ACTIVITY_AREA_REFERENCE` |
| Chèo SUP Mân Thái – Go N Chill | Vùng nước ven bãi Mân Thái | `NEARSHORE_REFERENCE_NOT_FULL_MORNING_ROUTE` |
| Lặn ngắm san hô Bãi Nam – Loca Travel | Vùng nước ven bãi Nam – Sơn Trà | `SINGLE_ACTIVITY_AREA_REFERENCE` |
| Câu mực đêm có bữa tối – DaNaFi | Vùng nước phía đông Sơn Trà | `VARIABLE_OFFSHORE_AREA_REFERENCE` |
| Phao chuối – Kajin Vina | Vùng nước ven bãi Mỹ Khê | `SINGLE_ACTIVITY_AREA_REFERENCE` |

## Hai địa điểm được sửa để khớp nguồn đang công bố

- Lặn ngắm san hô Sơn Trà – Loca: đổi địa chỉ Bãi Cát Vàng sang Bãi Rạng theo [vé Loca hiện hành](https://locatravel.vn/ve-lan-ngam-san-ho-ban-dao-son-tra). Giữ ghi nhận hồ sơ Bãi Cát Vàng trong mô tả và provenance; không tự coi hai gói tương đương về giá hoặc thời lượng.
- Kayak kết hợp trải nghiệm biển – DNOA: đổi địa chỉ cố định Bãi Rạng sang tuyến Mỹ Khê – vùng biển ven Sơn Trà theo [lịch trình DNOA](https://danangadventures.com/product/kayk-snorkel-tour/). Điểm thời tiết hiện chỉ đại diện vùng nước ở đầu tuyến, không bao phủ rạn ngoài khơi hoặc toàn tuyến.

## Những trường hợp vẫn cần xác nhận vận hành

- Hai dịch vụ dù bay/phao chuối Kajin chưa có bãi hoạt động công khai độc lập: khu vực Mỹ Khê suy ra từ dịch vụ mô tô nước của cùng nhà cung cấp trong danh mục. Đây là suy luận từ dữ liệu hiện có, không phải địa điểm Kajin đã xác nhận.
- Các điểm câu cá/câu mực DaNaFi, Khuê Nguyễn và D2Tour là điểm vùng biển tham chiếu. Nguồn chưa xác nhận tọa độ ngư trường cụ thể; việc tìm thấy một điểm nước không xác nhận tình trạng khai thác thương mại hiện tại.
- Bãi Nam là khu vực phù hợp mô tả lặn ven bãi, nhưng chưa xác minh được điểm GPS vận hành cụ thể của Loca tại đây.
- Surf tour Sơn Trà – Hội An, kayak Mỹ Khê – Sơn Trà, cano Sơn Trà – Cù Lao Chàm và SUP sáng ra Hòn Sụp phải đánh giá nhiều điểm hoặc theo ca/tuyến trước khi dùng như đánh giá an toàn toàn hành trình. Một cặp latitude/longitude trong schema hiện tại chưa biểu diễn được toàn tuyến.

## Kiểm tra nhà cung cấp thời tiết

Ngày 20/11/2026, cả 9 điểm trả dữ liệu sóng khi gọi Open-Meteo Marine với `cell_selection=sea`. Snapshot, tọa độ ô lưới và khoảng cách nằm trong provenance. Tám điểm ven bờ được ánh xạ vào cùng một ô lưới; khoảng cách tới ô lưới được trả về khoảng 7,3–18,2 km. Vì vậy, việc dịch vụ ở các bãi khác nhau có cùng dữ liệu sóng có thể đến từ độ phân giải mô hình, không nhất thiết là lỗi cache.

Không dùng việc Open-Meteo có dữ liệu làm bằng chứng điểm yêu cầu nằm trên biển. Kiểm tra đường bờ và kiểm tra phủ dữ liệu là hai bước độc lập. Snapshot này không được dùng làm forecast đang còn hiệu lực.

## Kiểm tra và áp dụng

```sh
python3 -m unittest discover -s DB_Demo/tests -v
```

Bộ kiểm tra yêu cầu mọi dịch vụ nhạy cảm thời tiết có tọa độ hữu hạn, đúng khoảng, khác `(0,0)`; đối chiếu từng service ID với provenance và kiểm tra điểm ngoài polygon đất liền trong vùng được phủ. Manifest bảo vệ cả JSON nguồn và GeoJSON đường bờ.

Các thay đổi này nằm trên nhánh `code/service-weather-coordinates`; chưa được áp dụng lên database production. Để cập nhật production hiện hữu, dùng cập nhật có điều kiện theo service ID, sao lưu giá trị cũ và kiểm tra lại kết quả; không chạy workflow thay thế toàn bộ dữ liệu chỉ để bổ sung tọa độ. Các sửa lỗi thời gian 404 và ánh xạ trạng thái cố vấn thuộc phần thay đổi backend/frontend riêng.
