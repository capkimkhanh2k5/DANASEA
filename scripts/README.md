# Cấu Trúc Và Hướng Dẫn Sử Dụng Scripts DANASEA

Thư mục `scripts/` chứa toàn bộ công cụ tự động hóa, kiểm thử, triển khai, quản trị dữ liệu và kiểm thử AI của dự án **DANASEA**.


```
scripts/
├── ai/                                # 🤖 NHÓM 1: AI & MÔ HÌNH HỌC MÁY
│   ├── test_interactive_ai.py        # Tương tác hội thoại với trợ lý AI
│   ├── check_ai_acceptance.py        # Kiểm tra tiêu chuẩn nghiệm thu AI Worker & Proxy
│   └── ai_evidence.py                # Thu thập & xử lý bằng chứng kiểm thử AI
│
├── ops/                               # 🚀 NHÓM 2: VẬN HÀNH & TRIỂN KHAI (OPERATIONS)
│   ├── deploy.sh                     # Triển khai hệ thống tự động
│   ├── production.py                 # Chuẩn bị & kiểm tra cấu hình môi trường production
│   ├── release.py                    # Quản lý & kích hoạt phiên bản release
│   ├── backup.py                     # Sao lưu & kiểm thử phục hồi database
│   ├── availability.py               # Kiểm tra tính sẵn sàng (Healthcheck) của các dịch vụ
│   ├── rehearse.py                   # Diễn tập phục hồi thảm họa & rollback
│   ├── rehearse_migration.py         # Diễn tập migration CSDL v45
│   └── install-production-controls.sh # Thiết lập cấu hình bảo vệ production
│
├── qa/                                # 🧪 NHÓM 3: KIỂM THỬ CHẤT LƯỢNG & TÀI CHÍNH (QA)
│   ├── acceptance.py                 # Bộ điều phối chạy toàn bộ test nghiệm thu hệ thống
│   ├── acceptance_payments.py        # Kiểm thử quy trình thanh toán sandbox (PayPal/VNPay)
│   ├── payment_session.py            # Khởi tạo & quản lý phiên thanh toán
│   ├── verify_financial_mutations.py # Kiểm định tính toàn vẹn biến động số dư tài chính
│   └── check_coverage.py             # Kiểm tra ngưỡng bao phủ code coverage
│
├── demo/                              # 📦 NHÓM 4: DỮ LIỆU MẪU & SEEDING (DEMO DATA)
│   ├── demo_data.py                  # Quản lý dữ liệu demo hệ thống
│   ├── demo_request.py               # Ký và xác thực yêu cầu nạp demo data an toàn
│   ├── demo_report.py                # Tạo báo cáo kết quả nạp demo
│   ├── demo_runtime.py               # Môi trường runtime thực thi nạp demo
│   ├── seed_database_demo_builder.py # Sinh dữ liệu mẫu (tour biển, vendor, slot)
│   └── install-demo-data-controls.sh # Cài đặt chính sách kiểm soát demo
│
├── governance/ & ci/                  # 🛡️ NHÓM 5: CI/CD & QUẢN TRỊ BẢO MẬT GITHUB
│   ├── check_repository_controls.py  # Kiểm tra thiết lập bảo vệ nhánh Git & repo
│   ├── check_dependabot.py           # Quét cảnh báo bảo mật thư viện phụ thuộc
│   ├── check_test_reports.py         # Xác thực tính hợp lệ của báo cáo kiểm thử
│   ├── ci/download_model.py          # Tải mô hình phục vụ CI
│   ├── ci/install_tools.py           # Cài đặt công cụ môi trường CI
│   ├── ci/run_proxy_acceptance.py    # Kiểm thử nghiệm thu CLI Proxy trên CI
│   └── ci/run_java_worker_acceptance.py # Kiểm thử nghiệm thu Java Worker trên CI
│
└── tests/                             # 🧪 Thư mục chứa 224 unit tests tự động cho scripts
```

Chế độ backend của `python3 scripts/manage.py ai:chat` dùng access token của tài khoản test. Đặt biến môi trường `DANASEA_ACCESS_TOKEN` trước khi chạy.
