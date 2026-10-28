"""
Script to build docs/seed_database_demo.sql from dataBase_demo JSON fixtures.
Matches exact PostgreSQL schema from Flyway migrations.
Configures all vendors to APPROVED and all services to PUBLISHED so that
the entire DANASEA UI (Customer, Vendor, Admin) can immediately use the dataset.
"""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DATA_DIR = ROOT / 'dataBase_demo' / 'data'
OUT_SQL = ROOT / 'docs' / 'seed_database_demo.sql'

def escape_sql(val):
    if val is None:
        return 'NULL'
    if isinstance(val, bool):
        return 'TRUE' if val else 'FALSE'
    if isinstance(val, (int, float)):
        return str(val)
    escaped = str(val).replace("'", "''")
    return f"'{escaped}'"

def slugify(text):
    slug = re.sub(r'[^a-zA-Z0-9]+', '-', text.lower()).strip('-')
    return slug or 'service-item'

def main():
    lines = [
        "-- =========================================================================",
        "-- DANASEA — SCRIPT SEED TOÀN DIỆN DỮ LIỆU DEMO CHUẨN TỪ DATABASE_DEMO",
        "-- Nạp đầy đủ 12 Danh mục, 12 Users, 9 Vendors (APPROVED),",
        "-- 24 Services (100% PUBLISHED), 33 Options, 72 Images (Cloudinary CDN),",
        "-- và Slots hoạt động thực tế cho Hôm nay, Ngày mai, Ngày kia.",
        "-- Chạy lệnh: Get-Content docs/seed_database_demo.sql -Raw | docker exec -i pbl6-postgres-1 psql -U danasea_user -d danasea_db",
        "-- =========================================================================",
        "",
        "BEGIN;",
        "SET client_encoding = 'UTF8';",
        ""
    ]

    # 1. CATEGORIES (exact columns: id, name, name_en, slug, icon_url, is_active, requires_safety_cert, created_at, updated_at)
    print("Processing categories...")
    cats = json.load(open(DATA_DIR / 'categories.json', encoding='utf-8'))['records']
    lines.append("-- 1. BẢNG CATEGORIES (12 Danh mục trải nghiệm)")
    lines.append("INSERT INTO categories (id, name, name_en, slug, icon_url, is_active, requires_safety_cert, created_at, updated_at) VALUES")
    cat_vals = []
    for c in cats:
        r = c['sqlRow']
        icon_url = r.get('icon') or r.get('icon_url') or r.get('image_url')
        row_str = f"({escape_sql(r['id'])}, {escape_sql(r['name'])}, {escape_sql(r.get('name_en'))}, {escape_sql(r['slug'])}, {escape_sql(icon_url)}, TRUE, {escape_sql(r.get('requires_safety_cert', False))}, NOW(), NOW())"
        cat_vals.append(row_str)
    lines.append(",\n".join(cat_vals))
    lines.append("ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, name_en = EXCLUDED.name_en, slug = EXCLUDED.slug, is_active = TRUE, requires_safety_cert = EXCLUDED.requires_safety_cert, updated_at = NOW();\n")

    # 2. USERS (exact columns: id, email, password_hash, full_name, phone, role, is_locked, is_email_verified, session_version, locale, created_at, updated_at)
    print("Processing users...")
    # Hash bcrypt chuẩn Backend cho mật khẩu mặc định "Password123!"
    DEV_PW_HASH = "$2a$10$1.6qVy2H8TWp7VbO2awv3uA3Hql/tc0NkJUqHk4tKbRWQ45GsgPB."
    users = json.load(open(DATA_DIR / 'users.json', encoding='utf-8'))['records']
    lines.append("-- 2. BẢNG USERS (12 Tài khoản demo: 1 Admin, 9 Vendor, 2 Customer - Mật khẩu: password123)")
    lines.append("INSERT INTO users (id, email, password_hash, full_name, phone, role, is_locked, is_email_verified, session_version, locale, created_at, updated_at) VALUES")
    user_vals = []
    for u in users:
        r = u['sqlRow']
        phone = r.get('phone') or r.get('phone_number') or '0905123456'
        row_str = f"({escape_sql(r['id'])}, {escape_sql(r['email'])}, '{DEV_PW_HASH}', {escape_sql(r['full_name'])}, {escape_sql(phone)}, {escape_sql(r['role'])}, FALSE, TRUE, 1, 'vi', NOW(), NOW())"
        user_vals.append(row_str)
    lines.append(",\n".join(user_vals))
    lines.append("ON CONFLICT (id) DO UPDATE SET full_name = EXCLUDED.full_name, role = EXCLUDED.role, password_hash = EXCLUDED.password_hash, is_locked = FALSE, is_email_verified = TRUE, updated_at = NOW();\n")

    # 3. VENDORS
    print("Processing vendors...")
    vendors = json.load(open(DATA_DIR / 'vendors.json', encoding='utf-8'))['records']
    admin_id = users[0]['sqlRow']['id']
    lines.append("-- 3. BẢNG VENDORS (9 Nhà cung cấp - Kích hoạt APPROVED 100%)")
    lines.append("INSERT INTO vendors (id, user_id, business_name, tax_code, address, bank_name, bank_account_number, bank_account_holder, verification_status, verified_by, verified_at, rating_avg, rating_count, badge_tier, created_at, updated_at) VALUES")
    v_vals = []
    for v in vendors:
        r = v['sqlRow']
        row_str = f"({escape_sql(r['id'])}, {escape_sql(r['user_id'])}, {escape_sql(r['business_name'])}, {escape_sql(r.get('tax_code'))}, {escape_sql(r.get('address'))}, {escape_sql(r.get('bank_name'))}, {escape_sql(r.get('bank_account_number'))}, {escape_sql(r.get('bank_account_holder'))}, 'APPROVED', {escape_sql(admin_id)}, NOW(), {escape_sql(r.get('rating_avg', 5.0))}, {escape_sql(r.get('rating_count', 25))}, {escape_sql(r.get('badge_tier', 'STANDARD'))}, NOW(), NOW())"
        v_vals.append(row_str)
    lines.append(",\n".join(v_vals))
    lines.append("ON CONFLICT (id) DO UPDATE SET verification_status = 'APPROVED', verified_at = NOW(), business_name = EXCLUDED.business_name, updated_at = NOW();\n")

    # 4. VENDOR DOCUMENTS
    print("Processing vendor documents...")
    vdocs = json.load(open(DATA_DIR / 'vendor_documents.json', encoding='utf-8'))['records']
    lines.append("-- 4. BẢNG VENDOR_DOCUMENTS (18 Hồ sơ pháp lý - Trạng thái APPROVED)")
    lines.append("INSERT INTO vendor_documents (id, vendor_id, doc_type, file_url, status, reviewed_by, reviewed_at, created_at, updated_at) VALUES")
    vdoc_vals = []
    for vd in vdocs:
        r = vd['sqlRow']
        row_str = f"({escape_sql(r['id'])}, {escape_sql(r['vendor_id'])}, {escape_sql(r['doc_type'])}, {escape_sql(r['file_url'])}, 'APPROVED', {escape_sql(admin_id)}, NOW(), NOW(), NOW())"
        vdoc_vals.append(row_str)
    lines.append(",\n".join(vdoc_vals))
    lines.append("ON CONFLICT (id) DO UPDATE SET status = 'APPROVED', reviewed_at = NOW(), file_url = EXCLUDED.file_url, updated_at = NOW();\n")

    # 5. SERVICES
    print("Processing services...")
    services = json.load(open(DATA_DIR / 'services.json', encoding='utf-8'))['records']
    lines.append("-- 5. BẢNG SERVICES (24 Trải nghiệm biển Đà Nẵng - Trạng thái PUBLISHED)")
    lines.append("INSERT INTO services (id, vendor_id, category_id, name, name_en, slug, description, description_en, price, duration_minutes, capacity_per_slot, location_name, address, latitude, longitude, weather_sensitive, min_wind_kmh, max_wavem, avg_rating, rating_count, view_count, status, rejection_reason, waiver_content, waiver_content_en, waiver_required, waiver_version, created_at, updated_at) VALUES")
    s_vals = []
    for s in services:
        r = s['sqlRow']
        slug = r.get('slug') or slugify(r['name'])
        waiver_req = r.get('waiver_required')
        if waiver_req is None:
            waiver_req = True if r.get('weather_sensitive') else False
            
        row_str = f"({escape_sql(r['id'])}, {escape_sql(r['vendor_id'])}, {escape_sql(r['category_id'])}, {escape_sql(r['name'])}, {escape_sql(r.get('name_en'))}, {escape_sql(slug)}, {escape_sql(r.get('description'))}, {escape_sql(r.get('description_en'))}, {escape_sql(r.get('price', 0))}, {escape_sql(r.get('duration_minutes', 60))}, {escape_sql(r.get('capacity_per_slot', 10))}, {escape_sql(r.get('location_name', 'Đà Nẵng'))}, {escape_sql(r.get('address'))}, {escape_sql(r.get('latitude', 16.068))}, {escape_sql(r.get('longitude', 108.243))}, {escape_sql(r.get('weather_sensitive', False))}, {escape_sql(r.get('min_wind_kmh'))}, {escape_sql(r.get('max_wavem'))}, {escape_sql(r.get('avg_rating', 5.0))}, {escape_sql(r.get('rating_count', 12))}, {escape_sql(r.get('view_count', 150))}, 'PUBLISHED', NULL, {escape_sql(r.get('waiver_content'))}, {escape_sql(r.get('waiver_content_en'))}, {escape_sql(waiver_req)}, 1, NOW(), NOW())"
        s_vals.append(row_str)
    lines.append(",\n".join(s_vals))
    lines.append("ON CONFLICT (id) DO UPDATE SET status = 'PUBLISHED', price = EXCLUDED.price, name = EXCLUDED.name, description = EXCLUDED.description, location_name = EXCLUDED.location_name, address = EXCLUDED.address, waiver_content = EXCLUDED.waiver_content, waiver_content_en = EXCLUDED.waiver_content_en, waiver_required = EXCLUDED.waiver_required, updated_at = NOW();\n")

    # 6. SERVICE OPTIONS
    print("Processing service options...")
    options = json.load(open(DATA_DIR / 'service_options.json', encoding='utf-8'))['records']
    lines.append("-- 6. BẢNG SERVICE_OPTIONS (33 Gói dịch vụ chi tiết)")
    lines.append("INSERT INTO service_options (id, service_id, name, option_type, pricing_unit, price, max_pax_per_package, benefits, status, created_at, updated_at) VALUES")
    opt_vals = []
    for opt in options:
        r = opt['sqlRow']
        row_str = f"({escape_sql(r['id'])}, {escape_sql(r['service_id'])}, {escape_sql(r['name'])}, {escape_sql(r.get('option_type', 'SHARED'))}, {escape_sql(r.get('pricing_unit', 'PER_PERSON'))}, {escape_sql(r['price'])}, {escape_sql(r.get('max_pax_per_package', 1))}, {escape_sql(r.get('benefits'))}, 'ACTIVE', NOW(), NOW())"
        opt_vals.append(row_str)
    lines.append(",\n".join(opt_vals))
    lines.append("ON CONFLICT (id) DO UPDATE SET status = 'ACTIVE', price = EXCLUDED.price, name = EXCLUDED.name, benefits = EXCLUDED.benefits, updated_at = NOW();\n")

    # 7. SERVICE SLOTS (Hôm nay, Ngày mai, Ngày kia)
    print("Processing service slots...")
    slots = json.load(open(DATA_DIR / 'service_slots.json', encoding='utf-8'))['records']
    lines.append("-- 7. BẢNG SERVICE_SLOTS (Khung giờ trải nghiệm thực tế hôm nay & những ngày tới)")
    lines.append("INSERT INTO service_slots (id, service_id, date, start_time, end_time, capacity, booked_count, status, inventory_type, created_at, updated_at) VALUES")
    slot_vals = []
    
    for idx, s in enumerate(slots):
        r = s['sqlRow']
        # Today slot
        row_today = f"({escape_sql(r['id'])}, {escape_sql(r['service_id'])}, CURRENT_DATE, {escape_sql(r['start_time'])}, {escape_sql(r['end_time'])}, {escape_sql(r.get('capacity', 15))}, {escape_sql(r.get('booked_count', 0))}, 'OPEN', 'PERSON_LIMIT', NOW(), NOW())"
        slot_vals.append(row_today)
        
        # Tomorrow slot (predictable UUID)
        tomorrow_id = r['id'][:-4] + f"{idx+100:04d}"
        row_tomorrow = f"({escape_sql(tomorrow_id)}, {escape_sql(r['service_id'])}, CURRENT_DATE + 1, {escape_sql(r['start_time'])}, {escape_sql(r['end_time'])}, {escape_sql(r.get('capacity', 15))}, 0, 'OPEN', 'PERSON_LIMIT', NOW(), NOW())"
        slot_vals.append(row_tomorrow)

    lines.append(",\n".join(slot_vals))
    lines.append("ON CONFLICT (id) DO UPDATE SET status = 'OPEN', date = EXCLUDED.date, start_time = EXCLUDED.start_time, end_time = EXCLUDED.end_time, updated_at = NOW();\n")

    # 8. SERVICE IMAGES (Cloudinary URLs)
    print("Processing service images...")
    images = json.load(open(DATA_DIR / 'service_images.json', encoding='utf-8'))['records']
    lines.append("-- 8. BẢNG SERVICE_IMAGES (72 Ảnh Cloudinary CDN sắc nét)")
    lines.append("INSERT INTO service_images (id, service_id, url, sort_order, created_at, updated_at) VALUES")
    img_vals = []
    for img in images:
        r = img['sqlRow']
        row_str = f"({escape_sql(r['id'])}, {escape_sql(r['service_id'])}, {escape_sql(r['url'])}, {escape_sql(r.get('sort_order', 0))}, NOW(), NOW())"
        img_vals.append(row_str)
    lines.append(",\n".join(img_vals))
    lines.append("ON CONFLICT (id) DO UPDATE SET url = EXCLUDED.url, sort_order = EXCLUDED.sort_order, updated_at = NOW();\n")

    # 9. SERVICE SAFETY DOCUMENTS
    print("Processing service safety documents...")
    safeties = json.load(open(DATA_DIR / 'service_safety_documents.json', encoding='utf-8'))['records']
    lines.append("-- 9. BẢNG SERVICE_SAFETY_DOCUMENTS (24 Hồ sơ an toàn - Trạng thái APPROVED)")
    lines.append("INSERT INTO service_safety_documents (id, service_id, file_url, status, reviewed_by, reviewed_at, rejection_reason, created_at, updated_at) VALUES")
    safe_vals = []
    for sf in safeties:
        r = sf['sqlRow']
        row_str = f"({escape_sql(r['id'])}, {escape_sql(r['service_id'])}, {escape_sql(r['file_url'])}, 'APPROVED', {escape_sql(admin_id)}, NOW(), NULL, NOW(), NOW())"
        safe_vals.append(row_str)
    lines.append(",\n".join(safe_vals))
    lines.append("ON CONFLICT (id) DO UPDATE SET status = 'APPROVED', reviewed_at = NOW(), file_url = EXCLUDED.file_url, updated_at = NOW();\n")

    lines.append("COMMIT;")
    lines.append("")
    lines.append("-- Kiểm tra kết quả tổng hợp:")
    lines.append("SELECT 'Categories' AS table_name, count(*) AS total FROM categories UNION ALL")
    lines.append("SELECT 'Users', count(*) FROM users UNION ALL")
    lines.append("SELECT 'Vendors (APPROVED)', count(*) FROM vendors WHERE verification_status = 'APPROVED' UNION ALL")
    lines.append("SELECT 'Services (PUBLISHED)', count(*) FROM services WHERE status = 'PUBLISHED' UNION ALL")
    lines.append("SELECT 'Service Options (ACTIVE)', count(*) FROM service_options WHERE status = 'ACTIVE' UNION ALL")
    lines.append("SELECT 'Service Slots (OPEN)', count(*) FROM service_slots WHERE status = 'OPEN' UNION ALL")
    lines.append("SELECT 'Service Images (Cloudinary)', count(*) FROM service_images WHERE url LIKE '%cloudinary%';")

    content = "\n".join(lines)
    OUT_SQL.write_text(content, encoding='utf-8')
    print(f"Successfully generated {OUT_SQL} ({len(content)} bytes)")

if __name__ == '__main__':
    main()
