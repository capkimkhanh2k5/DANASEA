import { useMemo } from 'react';
import { useLocation, useParams } from 'react-router-dom';
import { useLanguageStore } from '../../store/useLanguageStore';

export type PolicyTab = 
  | 'terms'
  | 'privacy'
  | 'refund'
  | 'safety'
  | 'conservation'
  | 'booking-guide'
  | 'about';

interface PolicyMeta {
  id: PolicyTab;
  labelVi: string;
  labelEn: string;
  titleVi: string;
  titleEn: string;
  updatedVi: string;
  updatedEn: string;
}

const POLICY_NAV: PolicyMeta[] = [
  {
    id: 'terms',
    labelVi: 'Điều khoản dịch vụ',
    labelEn: 'Terms & Conditions',
    titleVi: 'ĐIỀU KHOẢN VÀ ĐIỀU KIỆN SỬ DỤNG SÀN THƯƠNG MẠI ĐIỆN TỬ DANASEA',
    titleEn: 'DANASEA PLATFORM TERMS AND CONDITIONS OF USE',
    updatedVi: 'Được chỉnh sửa lần cuối vào: Ngày 15 tháng 11 năm 2026',
    updatedEn: 'Last modified on: November 15, 2026',
  },
  {
    id: 'privacy',
    labelVi: 'Chính sách quyền riêng tư',
    labelEn: 'Privacy Notice',
    titleVi: 'CHÍNH SÁCH QUYỀN RIÊNG TƯ VÀ BẢO VỆ DỮ LIỆU CÁ NHÂN CỦA DANASEA',
    titleEn: 'DANASEA PRIVACY POLICY & PERSONAL DATA PROTECTION NOTICE',
    updatedVi: 'Được chỉnh sửa lần cuối vào: Ngày 15 tháng 11 năm 2026',
    updatedEn: 'Last modified on: November 15, 2026',
  },
  {
    id: 'refund',
    labelVi: 'Chính sách hoàn tiền',
    labelEn: 'Refund Policy',
    titleVi: 'CHÍNH SÁCH HỦY TOUR, ĐIỀU CHỈNH CHUYẾN ĐI VÀ HOÀN TIỀN THEO THỜI TIẾT',
    titleEn: 'CANCELLATION, WEATHER ESCALATION AND REFUND POLICY',
    updatedVi: 'Được chỉnh sửa lần cuối vào: Ngày 15 tháng 11 năm 2026',
    updatedEn: 'Last modified on: November 15, 2026',
  },
  {
    id: 'safety',
    labelVi: 'An toàn hàng hải',
    labelEn: 'Maritime Safety',
    titleVi: 'QUY CHUẨN AN TOÀN HÀNG HẢI, CỨU SINH VÀ ĐIỀU KIỆN VẬN HÀNH THỂ THAO BIỂN',
    titleEn: 'MARITIME SAFETY, RESCUE STANDARDS & OPERATIONAL WEATHER THRESHOLDS',
    updatedVi: 'Được chỉnh sửa lần cuối vào: Ngày 12 tháng 11 năm 2026',
    updatedEn: 'Last modified on: November 12, 2026',
  },
  {
    id: 'conservation',
    labelVi: 'Bảo tồn san hô',
    labelEn: 'Coral Conservation',
    titleVi: 'QUY ĐỊNH BẢO TỒN NGUYÊN TRẠNG RẠN SAN HÔ VÀ MÔI TRƯỜNG BIỂN SƠN TRÀ',
    titleEn: 'SON TRA PENINSULA CORAL REEF CONSERVATION & MARINE PROTECTION PLEDGE',
    updatedVi: 'Được chỉnh sửa lần cuối vào: Ngày 10 tháng 11 năm 2026',
    updatedEn: 'Last modified on: November 10, 2026',
  },
  {
    id: 'booking-guide',
    labelVi: 'Hướng dẫn đặt tour',
    labelEn: 'Booking Guide',
    titleVi: 'QUY TRÌNH ĐẶT VÉ ĐIỆN TỬ, CAM KẾT AN TOÀN VÀ QUÉT MÃ QR XUẤT BẾN',
    titleEn: 'ELECTRONIC BOOKING, SAFETY WAIVER ACCEPTANCE & PORT CHECK-IN WORKFLOW',
    updatedVi: 'Được chỉnh sửa lần cuối vào: Ngày 10 tháng 11 năm 2026',
    updatedEn: 'Last modified on: November 10, 2026',
  },
  {
    id: 'about',
    labelVi: 'Pháp nhân & Giấy phép',
    labelEn: 'Legal Profile',
    titleVi: 'HỒ SƠ PHÁP NHÂN DOANH NGHIỆP, GIẤY PHÉP LỮ HÀNH VÀ BẢO HIỂM DU LỊCH',
    titleEn: 'CORPORATE PROFILE, TOURISM BUSINESS LICENSING & PASSENGER INSURANCE',
    updatedVi: 'Được chỉnh sửa lần cuối vào: Ngày 15 tháng 11 năm 2026',
    updatedEn: 'Last modified on: November 15, 2026',
  },
];

export function LegalPolicyPage() {
  const { tab } = useParams<{ tab?: string }>();
  const location = useLocation();
  const { language } = useLanguageStore();
  const isEn = language === 'en';

  const activeTab: PolicyTab = useMemo(() => {
    if (location.pathname === '/terms') return 'terms';
    if (location.pathname === '/privacy') return 'privacy';
    if (tab && POLICY_NAV.some(t => t.id === tab)) return tab as PolicyTab;
    return 'refund';
  }, [location.pathname, tab]);

  const currentMeta = useMemo(() => {
    return POLICY_NAV.find(t => t.id === activeTab) || POLICY_NAV[0];
  }, [activeTab]);

  return (
    <div className="w-full min-h-screen bg-white text-slate-800 pt-16 sm:pt-[68px]">
      {/* Main Document Body: Pure, Clean Legal Reading Container */}
      <main className="max-w-[920px] mx-auto px-4 sm:px-6 py-10 lg:py-14 leading-relaxed font-normal text-slate-800">
        
        {/* Document Header */}
        <header className="mb-10 space-y-2.5">
          <h1 className="text-2xl sm:text-3xl font-extrabold text-slate-900 tracking-tight leading-snug uppercase">
            {isEn ? currentMeta.titleEn : currentMeta.titleVi}
          </h1>
          <p className="text-xs sm:text-sm text-slate-500">
            {isEn ? currentMeta.updatedEn : currentMeta.updatedVi}
          </p>
        </header>

        {/* Document Clauses */}
        <div className="text-sm sm:text-[15px] space-y-9">
          {activeTab === 'terms' && <TermsDocument isEn={isEn} />}
          {activeTab === 'privacy' && <PrivacyDocument isEn={isEn} />}
          {activeTab === 'refund' && <RefundDocument isEn={isEn} />}
          {activeTab === 'safety' && <SafetyDocument isEn={isEn} />}
          {activeTab === 'conservation' && <ConservationDocument isEn={isEn} />}
          {activeTab === 'booking-guide' && <BookingGuideDocument isEn={isEn} />}
          {activeTab === 'about' && <AboutDocument isEn={isEn} />}
        </div>

        {/* Document End Sign-off */}
        <div className="mt-16 pt-8 border-t border-slate-200 text-xs text-slate-500 space-y-2">
          <p>
            {isEn
              ? 'These terms are governed by and construed in accordance with the laws of the Socialist Republic of Vietnam. Any questions or compliance requests can be addressed to hotro@danasea.vn or via our 24/7 hotline at 1900 8468.'
              : 'Các điều khoản trong văn bản này được điều chỉnh và giải thích theo quy định của pháp luật Nước Cộng hòa Xã hội Chủ nghĩa Việt Nam. Mọi thắc mắc hoặc yêu cầu làm rõ về mặt pháp lý, quý khách vui lòng liên hệ email hotro@danasea.vn hoặc tổng đài hỗ trợ 24/7 theo số 1900 8468.'}
          </p>
          <p className="font-mono text-slate-400">
            CÔNG TY CỔ PHẦN CÔNG NGHỆ VÀ DU LỊCH BIỂN DANASEA • MST: 0402188468 • SỞ DU LỊCH ĐÀ NẴNG CẤP GP LHND: 48-0024/2026/SDL-GP LHND
          </p>
        </div>

      </main>

    </div>
  );
}

// -------------------------------------------------------------------------------------------------
// 1. TERMS DOCUMENT (Extensive & Complete Legal Prose - Traveloka Grade for DANASEA)
// -------------------------------------------------------------------------------------------------
function TermsDocument({ isEn }: { isEn: boolean }) {
  if (isEn) {
    return (
      <div className="space-y-8 text-slate-700 leading-relaxed">
        <p>
          Welcome to the DANASEA ocean experiences reservation platform. The Platform at www.danasea.vn and associated mobile applications are managed and operated by DANASEA Ocean Technology and Tourism Joint Stock Company.
        </p>
        <p>
          By creating an account, accessing, browsing, reserving, or making payments for any maritime recreational service on the Platform, you confirm that you have read, understood, and agreed to these Terms and Conditions of Use as well as all incorporated operating policies, safety standards, and refund rules. If you do not agree with any part of these Terms, please discontinue using the Platform immediately.
        </p>

        <section className="space-y-3">
          <h2 className="text-base font-bold text-slate-900 uppercase">1. Scope of Agreement and Acceptance</h2>
          <p>
            1.1 These Terms constitute a legally binding agreement between you as an individual or legal entity and DANASEA Ocean Technology and Tourism Joint Stock Company. They govern your access to and utilization of the website, mobile applications, APIs, and automated customer services.
          </p>
          <p>
            1.2 DANASEA reserves the right to amend, update, or replace any portion of these Terms to comply with statutory legal requirements, maritime safety notices, or platform operational evolution. Revisions take immediate legal effect upon publication on the Platform. Your continued use of the Platform following published modifications constitutes full acceptance of the revised Terms.
          </p>
        </section>

        <section className="space-y-3">
          <h2 className="text-base font-bold text-slate-900 uppercase">2. Definitions and Legal Interpretations</h2>
          <p>2.1 <strong>Platform:</strong> The digital marketplace website at www.danasea.vn, iOS/Android mobile applications, administrative portals, APIs, and cloud booking engines developed and maintained by DANASEA.</p>
          <p>2.2 <strong>Vendor / Partner:</strong> Commercial boat operators, water sports clubs, certified scuba diving academies, and coastal hospitality service providers verified and authorized by competent maritime authorities and onboarded by DANASEA.</p>
          <p>2.3 <strong>Ocean Experience / Service:</strong> Any recreational activity listed on the Platform, encompassing Stand-Up Paddleboarding (SUP), sea kayaking, coral reef snorkeling, certified scuba diving, seawalker underwater walks, cano-towed parasailing flights, speedboat tours, jetskis (personal watercraft), banana boat towing, and sunset catamaran cruises.</p>
          <p>2.4 <strong>Operational Slot:</strong> A specific scheduled departure shift (such as Sunrise Shift 05:00 - 07:00, Morning Shift 08:00 - 10:00, or Sunset Shift 16:00 - 18:00) with capped passenger quotas, registered craft capacity, and assigned 1:1 certified instructors.</p>
          <p>2.5 <strong>Escrow Mechanism:</strong> The secure custodial holding mechanism whereby 100% of customer booking funds are deposited into an independent custodial escrow account and released to operating Vendors only after trip completion without unresolved customer disputes within 24 hours.</p>
          <p>2.6 <strong>Inventory Hold Lock:</strong> The temporary 15-minute programmatic reservation mechanism locking equipment inventory and passenger capacity upon initiating checkout, preventing duplicate bookings.</p>
          <p>2.7 <strong>Guard R9 (Mandatory Safety Waiver):</strong> The programmatic barrier preventing the generation of payment requests until all travelers complete the digital safety waiver and health declaration for high-risk water sports.</p>
        </section>

        <section className="space-y-3">
          <h2 className="text-base font-bold text-slate-900 uppercase">3. Platform Services and Third-Party Supplier Relationships</h2>
          <p>
            3.1 DANASEA operates as a specialized intermediary e-commerce marketplace platform connecting consumers with licensed marine recreational operators in Da Nang. Unless explicitly stated that a service is operated directly by DANASEA, all maritime services are delivered by third-party Vendors.
          </p>
          <p>
            3.2 Each operating Vendor maintains legal responsibility for vessel seaworthiness, periodic registry certifications, navigation safety equipment, captain licensing, and strict adherence to designated maritime channels established by the Da Nang Port Authority and Coastal Border Guards.
          </p>
          <p>
            3.3 DANASEA verifies business licenses, passenger transportation permits, and instructor credentials prior to vendor listing, and conducts automated real-time weather monitoring to supervise overall operational safety.
          </p>
        </section>

        <section className="space-y-3">
          <h2 className="text-base font-bold text-slate-900 uppercase">4. User Account Registration, Verification and Security</h2>
          <p>
            4.1 Users must provide complete, authentic, and current legal information when creating an account, including full legal birth name, primary telephone number, and verified email address. Registering under fictitious identities or burner phone numbers constitutes a material breach of these Terms.
          </p>
          <p>
            4.2 You are strictly responsible for maintaining credential secrecy, including passwords and two-factor authentication codes. DANASEA shall not be held liable for losses or damages resulting from unauthorized third-party access caused by your failure to protect your login information.
          </p>
          <p>
            4.3 DANASEA reserves the right to suspend, freeze, or permanently terminate accounts without prior notice if we detect suspicious payment transactions, fraudulent chargebacks, speculative booking bots, or intentional violations of maritime safety regulations.
          </p>
        </section>

        <section className="space-y-3">
          <h2 className="text-base font-bold text-slate-900 uppercase">5. 15-Minute Inventory Hold Lock and Electronic Contract Formation</h2>
          <p>
            5.1 When you initiate a reservation, the booking engine executes an Inventory Hold Lock for a period of fifteen (15) minutes. During this holding window, capacity and instructor quotas are reserved exclusively for you. If checkout is not completed within 15 minutes, inventory automatically releases back to the general pool.
          </p>
          <p>
            5.2 A legally binding electronic service contract between you and the operating Vendor is formed upon successful payment authorization by our gateways and the issuance of an encrypted electronic QR boarding pass.
          </p>
          <p>
            5.3 The electronic boarding pass displays the encrypted QR code, booking code, lead passenger name, reserved shift, departure harbor, and emergency contacts. Each QR code is single-use and tamper-proof.
          </p>
        </section>

        <section className="space-y-3">
          <h2 className="text-base font-bold text-slate-900 uppercase">6. Maritime Border Guard Manifest Compliance</h2>
          <p>
            6.1 Under Vietnam Inland Waterway Navigation Regulations and border port security decrees, all passengers embarking on motorized vessels or traveling beyond near-shore recreation zones must provide valid identification details (Citizen Identification Card / CCCD or Passport).
          </p>
          <p>
            6.2 DANASEA automatically compiles and transmits passenger manifests to the Coastal Border Guard Station and Da Nang Port Authority prior to vessel departure clearance. Refusal to provide identification or discrepancies upon pier inspection will result in boarding denial without refund.
          </p>
        </section>

        <section className="space-y-3">
          <h2 className="text-base font-bold text-slate-900 uppercase">7. Escrow Custody Protection and Vendor Payout Settlement</h2>
          <p>
            7.1 100% of customer booking payments are held securely in an independent custodial escrow account. Operating Vendors do not receive disbursements prior to trip execution, safeguarding travelers against unexpected cancellations or equipment unavailability.
          </p>
          <p>
            7.2 Following successful QR check-in and trip completion, a 24-hour dispute observation window is initiated. If no customer claim is registered, net earnings are released into the Vendor balance after platform commission deduction (12% to 18% as established in commercial agreements).
          </p>
        </section>

        <section className="space-y-3">
          <h2 className="text-base font-bold text-slate-900 uppercase">8. Listed Pricing, Value Added Tax (VAT) and Invoicing</h2>
          <p>
            8.1 All listed prices on DANASEA are expressed in Vietnamese Dong (VND) and are inclusive of statutory Value Added Tax (VAT) and mandatory passenger accident insurance underwritten by Bao Viet Insurance Corporation (maximum liability 100,000,000 VND / person / occurrence).
          </p>
          <p>
            8.2 Corporate electronic VAT invoices may be requested through your account within forty-eight (48) hours of trip completion, issued according to the regulations of the General Department of Taxation.
          </p>
        </section>

        <section className="space-y-3">
          <h2 className="text-base font-bold text-slate-900 uppercase">9. Promotional Codes, Vouchers and Platform Credits</h2>
          <p>
            9.1 Promotional coupons apply strictly as immediate checkout discounts against eligible orders. Coupons possess no cash surrender value and are non-refundable upon voluntary customer cancellations.
          </p>
          <p>
            9.2 DANASEA reserves the right to void any discount code applied through illegitimate mechanisms, account duplication, or promotional exploits.
          </p>
        </section>

        <section className="space-y-3">
          <h2 className="text-base font-bold text-slate-900 uppercase">10. Digital Safety Waiver Declaration and Captain’s Right of Refusal</h2>
          <p>
            10.1 High-thrill water activities (parasailing, jetskis, scuba diving, seawalker, speedboats) require every participant to review and execute a digital Safety Waiver and Medical Declaration prior to payment.
          </p>
          <p>
            10.2 Vessel captains and pier safety coordinators maintain the unappealable legal authority to deny boarding to any individual displaying alcohol intoxication, narcotic impairment, aggressive behavior, or non-compliance with life jacket instructions.
          </p>
        </section>

        <section className="space-y-3">
          <h2 className="text-base font-bold text-slate-900 uppercase">11. Intellectual Property and Prohibited Activities</h2>
          <p>
            11.1 All software, UI graphics, trademarks, logos, photography, video assets, and text content on DANASEA are the exclusive intellectual property of DANASEA or its licensors.
          </p>
          <p>
            11.2 Users are strictly prohibited from: utilizing web-scraping bots, reverse engineering platform code, injecting malicious payloads, submitting fraudulent chargebacks, or engaging in coordinated review manipulation.
          </p>
        </section>

        <section className="space-y-3">
          <h2 className="text-base font-bold text-slate-900 uppercase">12. Verified Customer Reviews and Content Integrity</h2>
          <p>
            12.1 Only customers who have completed a verified trip through QR check-in are eligible to submit activity reviews and ratings. Fabricated, defamatory, or incentivized reviews are strictly purged.
          </p>
        </section>

        <section className="space-y-3">
          <h2 className="text-base font-bold text-slate-900 uppercase">13. Limitation of Liability and Marine Weather Force Majeure</h2>
          <p>
            13.1 Marine recreation is subject to natural meteorological conditions. Whenever wave heights exceed 1.2 meters, wind gusts surpass 35 km/h, or port authorities issue departure prohibitions, operations are suspended under Force Majeure.
          </p>
          <p>
            13.2 In all weather-related cancellations, customers receive a 100% refund of the paid order amount without penalty deductions.
          </p>
        </section>

        <section className="space-y-3">
          <h2 className="text-base font-bold text-slate-900 uppercase">14. Dispute Resolution, Arbitration and Governing Law</h2>
          <p>
            14.1 Customers may lodge formal disputes within twenty-four (24) hours of trip completion. The DANASEA Arbitration Council reviews sensor logs, GPS breadcrumbs, and CCTV evidence to render a binding resolution within 48 business hours.
          </p>
          <p>
            14.2 These Terms are governed by the laws of the Socialist Republic of Vietnam. Any unresolved dispute shall be submitted to the exclusive jurisdiction of the competent People’s Court of Da Nang City.
          </p>
        </section>
      </div>
    );
  }

  return (
    <div className="space-y-8 text-slate-700 leading-relaxed">
      <p>
        Chào mừng quý khách đến với sàn giao dịch thương mại điện tử trải nghiệm biển DANASEA. Nền tảng tại địa chỉ www.danasea.vn và ứng dụng di động liên kết được sở hữu, quản lý và vận hành bởi Công ty Cổ phần Công nghệ và Du lịch Biển DANASEA.
      </p>
      <p>
        Bằng việc đăng ký tài khoản, truy cập, tìm kiếm, đặt chỗ hoặc thực hiện thanh toán cho bất kỳ dịch vụ trải nghiệm thể thao biển nào trên hệ thống, quý khách xác nhận đã đọc, hiểu rõ và đồng ý với toàn bộ Điều khoản và Điều kiện sử dụng này cũng như các chính sách pháp lý đi kèm. Nếu quý khách không đồng ý với bất kỳ nội dung nào, vui lòng ngừng sử dụng nền tảng ngay lập tức.
      </p>

      <section className="space-y-3">
        <h2 className="text-base font-bold text-slate-900 uppercase">1. Phạm Vi Áp Dụng Và Chấp Thuận Điều Khoản</h2>
        <p>
          1.1 Bản Điều khoản và Điều kiện sử dụng này cấu thành một thỏa thuận pháp lý ràng buộc giữa người dùng (bao gồm cá nhân hoặc tổ chức sử dụng dịch vụ) và Công ty Cổ phần Công nghệ và Du lịch Biển DANASEA. Thỏa thuận này điều chỉnh toàn bộ các hoạt động truy cập, tra cứu thông tin, giao dịch thanh toán và sử dụng dịch vụ thông qua website, ứng dụng di động và các kênh kết nối điện tử của DANASEA.
        </p>
        <p>
          1.2 DANASEA có quyền điều chỉnh, bổ sung hoặc sửa đổi các điều khoản bất kỳ lúc nào nhằm đáp ứng thay đổi của pháp luật hoặc nâng cấp mô hình vận hành. Bản cập nhật mới nhất có hiệu lực thi hành kể từ thời điểm công bố công khai trên website và ứng dụng. Việc quý khách tiếp tục sử dụng dịch vụ sau khi các sửa đổi được đăng tải đồng nghĩa với việc quý khách chấp thuận vô điều kiện các nội dung sửa đổi đó.
        </p>
      </section>

      <section className="space-y-3">
        <h2 className="text-base font-bold text-slate-900 uppercase">2. Giải Thích Từ Ngữ Và Định Nghĩa Pháp Lý</h2>
        <p>2.1 <strong>Sàn giao dịch DANASEA:</strong> Hệ sinh thái công nghệ gồm website tại địa chỉ www.danasea.vn, ứng dụng di động trên iOS/Android, giao diện lập trình kết nối (API) và hệ thống máy chủ đặt chỗ trung gian do DANASEA phát triển và sở hữu hợp pháp.</p>
        <p>2.2 <strong>Đối tác vận hành (Vendor):</strong> Các đơn vị kinh doanh vận tải thủy nội địa, câu lạc bộ thể thao dưới nước, trung tâm lặn biển đã được cơ quan nhà nước có thẩm quyền cấp phép hoạt động tại vùng biển Đà Nẵng và được DANASEA thẩm định hồ sơ năng lực.</p>
        <p>2.3 <strong>Trải nghiệm biển (Dịch vụ):</strong> Toàn bộ các hoạt động thể thao và du lịch biển trên nền tảng, bao gồm chèo ván đứng SUP, chèo thuyền Kayak, lặn ngắm san hô (Snorkeling và Scuba Diving), đi bộ dưới đáy biển Seawalker, cano kéo dù bay Parasailing, cano cao tốc lướt sóng, mô tô nước Jetski, kéo phao chuối và du thuyền Catamaran ngắm hoàng hôn vịnh Đà Nẵng.</p>
        <p>2.4 <strong>Ca vận hành (Service Slot):</strong> Khung giờ khởi hành cụ thể trong ngày (ví dụ ca bình minh 05:00 - 07:00, ca sáng 08:00 - 10:00, ca hoàng hôn 16:00 - 18:00) được kiểm soát nghiêm ngặt về số lượng khách, số phương tiện và định biên hướng dẫn viên an toàn.</p>
        <p>2.5 <strong>Cơ chế bảo chứng (Escrow):</strong> Quy trình quản lý dòng tiền trung gian bảo đảm quyền lợi du khách: 100% tiền thanh toán của du khách được khóa giữ an toàn tại tài khoản bảo chứng và chỉ giải ngân cho đối tác sau khi chuyến đi hoàn tất trọn vẹn mà không có tranh chấp trong vòng 24 giờ.</p>
        <p>2.6 <strong>Khóa giữ chỗ (Inventory HOLD Lock 15 Phút):</strong> Cơ chế giữ chỗ tạm thời trong 15 phút kể từ lúc bắt đầu thanh toán để bảo đảm không xảy ra tình trạng trùng lặp chỗ (double booking).</p>
        <p>2.7 <strong>Rào chắn an toàn (Guard R9):</strong> Cơ chế tự động khóa quy trình thanh toán trên hệ thống nếu đơn hàng có dịch vụ thể thao biển mạo hiểm nhưng khách hàng chưa hoàn tất việc đọc và bấm xác nhận Bản cam kết an toàn &amp; Khai báo sức khỏe.</p>
      </section>

      <section className="space-y-3">
        <h2 className="text-base font-bold text-slate-900 uppercase">3. Dịch Vụ Nền Tảng Và Trách Nhiệm Của Đối Tác Cung Ứng</h2>
        <p>
          3.1 DANASEA vận hành sàn giao dịch điện tử đóng vai trò cầu nối công nghệ, hỗ trợ duyệt tìm, đặt chỗ trước, thanh toán an toàn và giám sát điều kiện an toàn thời tiết hàng hải. Trừ khi được nêu rõ dịch vụ do DANASEA trực tiếp sở hữu, các trải nghiệm trên biển được cung cấp bởi các đối tác vận tải và câu lạc bộ được cấp phép.
        </p>
        <p>
          3.2 Đối tác vận hành chịu hoàn toàn trách nhiệm trước pháp luật về tính an toàn kỹ thuật của phương tiện thủy, hạn đăng kiểm, chứng chỉ chuyên môn của thuyền viên và việc tuân thủ luồng tuyến hàng hải theo quy định của Cảng vụ Hàng hải Đà Nẵng.
        </p>
        <p>
          3.3 DANASEA thực hiện quy trình kiểm duyệt định kỳ giấy phép kinh doanh, hợp đồng bảo hiểm và bằng cấp huấn luyện viên của đối tác trước khi kích hoạt hiển thị dịch vụ trên sàn.
        </p>
      </section>

      <section className="space-y-3">
        <h2 className="text-base font-bold text-slate-900 uppercase">4. Đăng Ký Tài Khoản, Xác Thực Danh Tính Và Bảo Mật</h2>
        <p>
          4.1 Quý khách phải cung cấp đầy đủ, chính xác họ tên khai sinh, số điện thoại chính chủ và email khi đăng ký tài khoản. Việc sử dụng thông tin giả mạo hoặc số điện thoại ảo là hành vi vi phạm nghiêm trọng điều khoản sử dụng.
        </p>
        <p>
          4.2 Quý khách chịu trách nhiệm tự quản lý và bảo mật mật khẩu, mã OTP và thiết bị đăng nhập. DANASEA không chịu trách nhiệm đối với bất kỳ tổn thất nào phát sinh do quý khách để lộ thông tin xác thực cho bên thứ ba.
        </p>
        <p>
          4.3 DANASEA có quyền đình chỉ, đóng băng hoặc xóa vĩnh viễn tài khoản mà không cần báo trước nếu phát hiện hành vi gian lận thanh toán, cố tình đặt đơn ảo gây tắc nghẽn chỗ, hoặc vi phạm nghiêm trọng quy chuẩn an toàn hàng hải.
        </p>
      </section>

      <section className="space-y-3">
        <h2 className="text-base font-bold text-slate-900 uppercase">5. Quy Trình Khóa Giữ Chỗ 15 Phút Và Xác Lập Hợp Đồng Điện Tử</h2>
        <p>
          5.1 Khi quý khách bấm chuyển sang màn hình thanh toán, hệ thống sẽ thực hiện lệnh khóa chỗ tồn kho (Inventory HOLD Lock) trong vòng 15 phút. Trong thời gian này, số lượng vé và phương tiện được bảo lưu riêng cho quý khách. Nếu quá 15 phút giao dịch chưa hoàn tất, chỗ sẽ tự động được giải phóng cho du khách khác.
        </p>
        <p>
          5.2 Hợp đồng dịch vụ điện tử giữa du khách và đơn vị vận hành chính thức được xác lập ngay khi cổng thanh toán phản hồi giao dịch thành công và hệ thống phát hành Vé điện tử mã QR mã hóa gửi vào email hoặc mục Đơn hàng của tôi.
        </p>
        <p>
          5.3 Vé điện tử mã QR là chứng từ hợp lệ duy nhất để xuất bến. Mỗi mã QR chỉ có giá trị sử dụng một lần (Single-use Validation) và được bảo vệ bằng chữ ký số chống sao chép.
        </p>
      </section>

      <section className="space-y-3">
        <h2 className="text-base font-bold text-slate-900 uppercase">6. Khai Báo Danh Sách Hành Khách Xuất Bến Với Biên Phòng &amp; Cảng Vụ</h2>
        <p>
          6.1 Căn cứ Luật Giao thông đường thủy nội địa và quy định kiểm soát an ninh cảng biển, toàn bộ hành khách tham gia tour cano, lặn biển hoặc di chuyển ra vùng vịnh bắt buộc phải cung cấp số Căn cước công dân (CCCD) hoặc Hộ chiếu trước giờ khởi hành.
        </p>
        <p>
          6.2 DANASEA có trách nhiệm tổng hợp danh sách hành khách điện tử để đối tác trình Đồn Biên phòng Cửa khẩu Cảng Đà Nẵng và Cảng vụ Hàng hải kiểm tra trước khi cấp lệnh xuất bến. Du khách từ chối xuất trình giấy tờ tùy thân hợp lệ tại bến sẽ bị từ chối phục vụ và không được hoàn tiền.
        </p>
      </section>

      <section className="space-y-3">
        <h2 className="text-base font-bold text-slate-900 uppercase">7. Cơ Chế Bảo Chứng Thanh Toán (Escrow) Và Quyết Toán Dòng Tiền</h2>
        <p>
          7.1 100% số tiền thanh toán của du khách được giữ tại tài khoản trung gian bảo chứng độc lập. Tiền không được chuyển trước cho đối tác trước chuyến đi nhằm bảo vệ du khách tuyệt đối trước nguy cơ bị hủy tour đột ngột, đối tác không bố trí đủ cano, hoặc thời tiết xấu không thể ra khơi.
        </p>
        <p>
          7.2 Sau khi kết thúc ca và mã QR được quét xác nhận hoàn tất tại bến, hệ thống kích hoạt giai đoạn theo dõi 24 giờ. Nếu không có khiếu nại phát sinh từ du khách, doanh thu sẽ được giải ngân vào tài khoản của đối tác sau khi khấu trừ phí hoa hồng sàn (từ 12% đến 18% tùy thỏa thuận hợp tác).
        </p>
      </section>

      <section className="space-y-3">
        <h2 className="text-base font-bold text-slate-900 uppercase">8. Giá Cả Niêm Yết, Thuế Giá Trị Gia Tăng (VAT) Và Hóa Đơn Điện Tử</h2>
        <p>
          8.1 Toàn bộ giá dịch vụ hiển thị trên DANASEA là giá trọn gói bằng đồng Việt Nam (VND), đã bao gồm thuế GTGT theo luật định và bảo hiểm tai nạn du khách do Tổng Công ty Bảo hiểm Bảo Việt bảo trợ (hạn mức bồi thường tối đa 100.000.000 VNĐ / người / vụ).
        </p>
        <p>
          8.2 Khách hàng có nhu cầu xuất hóa đơn điện tử VAT cho tổ chức, doanh nghiệp vui lòng đăng ký thông tin xuất hóa đơn trong vòng 48 giờ kể từ khi hoàn tất chuyến đi. Hóa đơn sẽ được xuất điện tử theo đúng quy định của Tổng cục Thuế.
        </p>
      </section>

      <section className="space-y-3">
        <h2 className="text-base font-bold text-slate-900 uppercase">9. Quy Định Sử Dụng Mã Giảm Giá, Voucher Và Điểm Tích Lũy</h2>
        <p>
          9.1 Mã giảm giá (Voucher) chỉ có giá trị giảm trừ trực tiếp vào giá trị đơn hàng hợp lệ khi thanh toán trên hệ thống, không có giá trị quy đổi thành tiền mặt và không được chuyển nhượng cho tài khoản khác.
        </p>
        <p>
          9.2 Trường hợp khách hàng chủ động hủy đơn tour vì lý do cá nhân, giá trị mã giảm giá đã sử dụng sẽ không được hoàn lại hay phục hồi hạn dùng.
        </p>
      </section>

      <section className="space-y-3">
        <h2 className="text-base font-bold text-slate-900 uppercase">10. Bản Cam Kết An Toàn Biển Và Quyền Từ Chối Phục Vụ Của Thuyền Trưởng</h2>
        <p>
          10.1 Do tính chất đặc thù của các môn thể thao mạo hiểm dưới nước, du khách bắt buộc phải hoàn thành việc ký điện tử Bản cam kết an toàn &amp; Khai báo tình trạng sức khỏe trước khi được cấp mã thanh toán.
        </p>
        <p>
          10.2 Thuyền trưởng và điều phối viên an toàn bến bãi có toàn quyền từ chối phục vụ ngay lập tức đối với bất kỳ du khách nào có biểu hiện say xỉn, sử dụng chất kích thích, hoặc không tuân thủ nghiêm ngặt hiệu lệnh mặc áo phao cứu sinh của thuyền viên.
        </p>
      </section>

      <section className="space-y-3">
        <h2 className="text-base font-bold text-slate-900 uppercase">11. Quyền Sở Hữu Trí Tuệ Và Các Hành Vi Nghiêm Cấm</h2>
        <p>
          11.1 Toàn bộ mã nguồn, thiết kế giao diện, logo thương hiệu, hình ảnh bản quyền và nội dung văn bản trên hệ thống DANASEA là tài sản sở hữu trí tuệ duy nhất của DANASEA hoặc đối tác cấp quyền hợp pháp.
        </p>
        <p>
          11.2 Nghiêm cấm mọi hành vi: sao chép dữ liệu bằng công cụ tự động (scraping/crawling), can thiệp đảo ngược mã nguồn, tấn công từ chối dịch vụ (DDoS), hoặc đăng tải nội dung xúc phạm, đe dọa các thành viên trên nền tảng.
        </p>
      </section>

      <section className="space-y-3">
        <h2 className="text-base font-bold text-slate-900 uppercase">12. Đánh Giá Của Người Dùng Và Tính Trung Thực Của Bình Luận</h2>
        <p>
          12.1 Chỉ những du khách đã thực sự hoàn thành trải nghiệm và quét mã QR xác nhận xuất bến tại cảng mới có quyền gửi đánh giá sao và bình luận nhận xét về dịch vụ.
        </p>
        <p>
          12.2 DANASEA nghiêm cấm tuyệt đối hành vi tạo đánh giá giả mạo, đánh giá công kích đối thủ hoặc nhận tiền để đăng tải nhận xét sai lệch sự thật. Các đánh giá vi phạm sẽ bị gỡ bỏ vĩnh viễn.
        </p>
      </section>

      <section className="space-y-3">
        <h2 className="text-base font-bold text-slate-900 uppercase">13. Giới Hạn Trách Nhiệm Và Sự Kiện Bất Khả Kháng Do Thời Tiết Hải Văn</h2>
        <p>
          13.1 Các hoạt động du lịch biển chịu ảnh hưởng trực tiếp của yếu tố tự nhiên. Trường hợp trạm hải văn ghi nhận chiều cao sóng Hs &gt; 1.2 mét, gió giật &gt; 35 km/h hoặc Cảng vụ ban hành lệnh cấm tàu xuất bến, đây được coi là Sự kiện bất khả kháng.
        </p>
        <p>
          13.2 Trong các trường hợp bất khả kháng do thời tiết nguy hiểm, DANASEA kích hoạt lệnh hủy ca tự động và hoàn trả 100% số tiền du khách đã thanh toán mà không áp dụng bất kỳ khoản phạt nào.
        </p>
      </section>

      <section className="space-y-3">
        <h2 className="text-base font-bold text-slate-900 uppercase">14. Khiếu Nại, Trọng Tài Tranh Chấp Và Tòa Án Có Thẩm Quyền</h2>
        <p>
          14.1 Du khách có quyền gửi khiếu nại qua hệ thống trong vòng 24 giờ kể từ khi kết thúc ca vận hành. Hội đồng Trọng tài Cảng vụ DANASEA sẽ tiến hành trích xuất dữ liệu cảm biến, camera và định vị GPS để đưa ra phán quyết bồi thường thỏa đáng trong vòng 48 giờ làm việc.
        </p>
        <p>
          14.2 Bản Điều khoản này được giải thích và điều chỉnh theo pháp luật Nước Cộng hòa Xã hội Chủ nghĩa Việt Nam. Mọi tranh chấp không thể hòa giải sẽ được giải quyết tại Tòa án nhân dân có thẩm quyền tại Thành phố Đà Nẵng.
        </p>
      </section>
    </div>
  );
}

// -------------------------------------------------------------------------------------------------
// 2. PRIVACY DOCUMENT (Exhaustive & Authoritative - Decree 13/2023/ND-CP & Traveloka Grade)
// -------------------------------------------------------------------------------------------------
function PrivacyDocument({ isEn }: { isEn: boolean }) {
  if (isEn) {
    return (
      <div className="space-y-8 text-slate-700 leading-relaxed">
        <p>
          DANASEA is deeply committed to upholding the privacy and safeguarding the personal data of all travelers, boat operators, and platform users. This Privacy Notice is formulated in strict compliance with the Personal Data Protection Decree No. 13/2023/ND-CP of the Government of Vietnam, national cybersecurity legislation, and international payment data protection standards.
        </p>
        <p>
          This document describes exhaustively how DANASEA collects, records, stores, utilizes, safeguards, transfers, and purges your personal data when you interact with our digital marketplace, reserve marine adventures, or complete check-in procedures across Da Nang harbors.
        </p>

        <section className="space-y-3">
          <h2 className="text-base font-bold text-slate-900 uppercase">1. Categories of Personal Data Collected</h2>
          <p>1.1 <strong>Personal Identity Information:</strong> Full legal birth name, date of birth, gender, nationality, contact phone number, and verified email address.</p>
          <p>1.2 <strong>Maritime Border Manifest Data:</strong> National Citizen Identification Card (CCCD) numbers, passport numbers, date and place of issuance, and legal guardian relationships for minors. Under Vietnam Maritime Administration and Coastal Border Guard regulations, this information is strictly mandatory for registering passenger manifests prior to harbor clearance.</p>
          <p>1.3 <strong>Financial and Payment Metadata:</strong> Order reference numbers, transaction token identifiers, payment channel codes (VNPAY, MoMo, Visa/Mastercard), and masked account identifiers. In strict compliance with PCI-DSS Level 1 mandates, DANASEA does not store 16-digit credit card numbers, CVV security codes, or banking PINs on its application servers.</p>
          <p>1.4 <strong>Location and GPS Geodata:</strong> Exact geographic coordinates captured upon scanning QR boarding passes at harbor gates or beach staging stations, utilized strictly for safety monitoring and passenger accountability.</p>
          <p>1.5 <strong>Technical Telemetry and Session Cookies:</strong> IP address, operating system, browser user-agent, session activity logs, referring URLs, and essential security cookies protecting against Cross-Site Request Forgery (CSRF).</p>
          <p>1.6 <strong>Harbor Video and Check-in Records:</strong> CCTV safety feeds monitored at Tho Quang Harbor and Man Thai staging zones for maritime security and dispute resolution.</p>
        </section>

        <section className="space-y-3">
          <h2 className="text-base font-bold text-slate-900 uppercase">2. Purposes and Legal Bases for Data Processing</h2>
          <p>2.1 <strong>Contract Execution:</strong> Provisioning user accounts, executing 15-minute inventory hold locks, processing payments, and issuing tamper-proof electronic QR boarding passes.</p>
          <p>2.2 <strong>Statutory Maritime Compliance:</strong> Submitting authenticated passenger manifests to the Da Nang Maritime Administration and Coastal Border Guards before vessel clearance.</p>
          <p>2.3 <strong>Passenger Insurance Underwriting:</strong> Enrolling travelers under master tourist civil liability insurance policy No. BV-DANASEA-2026 underwritten by Bao Viet Insurance Corporation.</p>
          <p>2.4 <strong>Safety Monitoring and Rescue Operations:</strong> Transmitting automated weather alerts, tidal surge warnings, and coordinating emergency response with the Maritime Rescue Coordination Center Region II (Danang MRCC).</p>
          <p>2.5 <strong>Escrow Financial Auditing &amp; Fraud Prevention:</strong> Operating payment custody accounts, preventing fraudulent chargebacks, and resolving post-trip claims.</p>
        </section>

        <section className="space-y-3">
          <h2 className="text-base font-bold text-slate-900 uppercase">3. Information Security Standards &amp; Payment Gateway PCI-DSS</h2>
          <p>
            3.1 All payment flows are processed through licensed payment intermediaries regulated by the State Bank of Vietnam (VNPAY, MoMo, ZaloPay, PayPal, Visa/Mastercard networks).
          </p>
          <p>
            3.2 All data in transit is encrypted using modern Transport Layer Security (TLS 1.3) with 256-bit AES encryption. Our payment partners maintain certified compliance with Payment Card Industry Data Security Standards (PCI-DSS Level 1).
          </p>
          <p>
            3.3 Internal access to passenger manifests and customer records is governed strictly through Role-Based Access Control (RBAC), requiring multi-factor authentication for administrative personnel.
          </p>
        </section>

        <section className="space-y-3">
          <h2 className="text-base font-bold text-slate-900 uppercase">4. Third-Party Sharing Restrictions &amp; Disclosures</h2>
          <p>
            4.1 DANASEA maintains an absolute zero-tolerance policy against selling, leasing, or commercializing personal data to advertising networks or unauthorized third parties.
          </p>
          <p>
            4.2 Data sharing occurs strictly under legitimate operational necessities with: (a) Operating boat operators for safety coordination; (b) Bao Viet Insurance for passenger claims; (c) Border Guards and Port Authorities under maritime laws; (d) Competent judicial authorities upon presentation of official legal warrants.
          </p>
        </section>

        <section className="space-y-3">
          <h2 className="text-base font-bold text-slate-900 uppercase">5. Cookie Policy and Session Tracking Technologies</h2>
          <p>
            5.1 We deploy essential security cookies and session tokens to preserve authentication sessions, maintain shopping cart contents, and protect against Cross-Site Request Forgery (CSRF).
          </p>
          <p>
            5.2 Users may manage or disable cookies via their browser preferences; however, disabling essential cookies may degrade booking and payment functionalities.
          </p>
        </section>

        <section className="space-y-3">
          <h2 className="text-base font-bold text-slate-900 uppercase">6. Data Subject Rights Under Decree 13/2023/ND-CP</h2>
          <p>Under Vietnamese Personal Data Protection regulations, you are entitled to:</p>
          <p>6.1 <strong>Right to be informed and consent:</strong> Receive clear notice regarding what data is collected and provide explicit opt-in consent.</p>
          <p>6.2 <strong>Right of access and rectification:</strong> Access and update your personal profile directly within your account settings.</p>
          <p>6.3 <strong>Right to erasure:</strong> Request deletion or anonymization of your account profile once all completed orders and statutory tax obligations are fulfilled.</p>
          <p>6.4 <strong>Right to restrict and object:</strong> Restrict non-statutory data processing or revoke consent for automated promotional communications.</p>
          <p>6.5 <strong>Right to file complaints:</strong> Submit formal complaints regarding data handling to our Data Protection Officer or competent state agencies.</p>
        </section>

        <section className="space-y-3">
          <h2 className="text-base font-bold text-slate-900 uppercase">7. Data Retention Period</h2>
          <p>
            7.1 Accounting, payment, and passenger manifest records are retained securely for a statutory minimum of five (5) years in compliance with the Law on Accounting and Tourism Regulations of Vietnam.
          </p>
          <p>
            7.2 Telemetry logs and transient operational data are purged automatically after ninety (90) days.
          </p>
        </section>

        <section className="space-y-3">
          <h2 className="text-base font-bold text-slate-900 uppercase">8. Data Protection Officer (DPO) Contact Details</h2>
          <p>
            For any inquiries, requests to exercise data subject rights, or security disclosures, please contact our Data Protection Office at:
          </p>
          <p className="font-mono text-xs sm:text-sm bg-slate-50 p-3 rounded-lg border border-slate-200">
            Email: dpo@danasea.vn • Hotline: 1900 8468<br />
            Address: Tho Quang Harbor &amp; Man Thai Beach, Hoang Sa Road, Son Tra District, Da Nang City.
          </p>
        </section>
      </div>
    );
  }

  return (
    <div className="space-y-8 text-slate-700 leading-relaxed">
      <p>
        DANASEA cam kết bảo vệ tuyệt đối quyền riêng tư và an toàn dữ liệu cá nhân của mọi du khách, đối tác bến bãi và người dùng trên nền tảng. Chính sách quyền riêng tư này được xây dựng và tuân thủ nghiêm ngặt theo quy định của <strong>Nghị định số 13/2023/NĐ-CP</strong> ngày 17/04/2023 của Chính phủ về bảo vệ dữ liệu cá nhân, Luật An ninh mạng Việt Nam và các chuẩn mực bảo mật thanh toán quốc tế.
      </p>
      <p>
        Văn bản này quy định chi tiết cách thức DANASEA thu thập, tiếp nhận, xử lý, lưu trữ, bảo mật, truyền tải và xóa dữ liệu cá nhân của quý khách khi truy cập website, cài đặt ứng dụng di động, hoàn tất thủ tục khai báo hành khách xuất bến hoặc tham gia trải nghiệm thể thao biển tại Đà Nẵng.
      </p>

      <section className="space-y-3">
        <h2 className="text-base font-bold text-slate-900 uppercase">1. Danh Mục Dữ Liệu Cá Nhân Được Thu Thập</h2>
        <p>1.1 <strong>Dữ liệu định danh cá nhân cơ bản:</strong> Họ và tên khai sinh, ngày tháng năm sinh, giới tính, quốc tịch, số điện thoại liên lạc chính và địa chỉ thư điện tử (email).</p>
        <p>1.2 <strong>Dữ liệu phục vụ thủ tục xuất bến hàng hải:</strong> Số thẻ Căn cước công dân (CCCD), mã định danh cá nhân hoặc số Hộ chiếu (đối với khách quốc tế), ngày cấp, nơi cấp và thông tin người giám hộ hợp pháp đối với trẻ em. Căn cứ Luật Giao thông đường thủy nội địa và quy định của Cục Hàng hải Việt Nam, đây là thông tin bắt buộc phải thu thập để lập Danh bạ thuyền viên và Danh sách hành khách xuất bến trình Đồn Biên phòng và Cảng vụ Hàng hải Đà Nẵng trước khi phương tiện rời âu thuyền.</p>
        <p>1.3 <strong>Dữ liệu giao dịch tài chính:</strong> Mã tham chiếu đơn hàng, mã token giao dịch, phương thức thanh toán (VNPAY, MoMo, Visa/Mastercard) và tên ngân hàng phát hành. Tuân thủ tiêu chuẩn PCI-DSS Level 1, hệ thống máy chủ của DANASEA hoàn toàn không lưu trữ số CVV/CVC, mã PIN hay 16 chữ số đầy đủ của thẻ tín dụng.</p>
        <p>1.4 <strong>Dữ liệu định vị và tọa độ bến bãi:</strong> Tọa độ địa lý GPS tại thời điểm du khách quét mã QR vé điện tử tại bến cảng hoặc trạm thể thao biển, phục vụ công tác cứu hộ và kiểm soát hành trình an toàn.</p>
        <p>1.5 <strong>Dữ liệu kỹ thuật truy cập:</strong> Địa chỉ giao thức Internet (IP), loại trình duyệt web, hệ điều hành thiết bị, thời gian truy cập, nhật ký phiên làm việc và cookie bảo mật chống tấn công giả mạo yêu cầu chéo trang (CSRF).</p>
        <p>1.6 <strong>Dữ liệu hình ảnh an ninh bến bãi:</strong> Dữ liệu camera giám sát tại âu thuyền Thọ Quang và bến Mân Thái phục vụ kiểm soát trật tự an toàn hàng hải và giải quyết khiếu nại.</p>
      </section>

      <section className="space-y-3">
        <h2 className="text-base font-bold text-slate-900 uppercase">2. Mục Đích &amp; Cơ Sở Pháp Lý Của Việc Xử Lý Dữ Liệu</h2>
        <p>2.1 <strong>Thực hiện hợp đồng dịch vụ:</strong> Khởi tạo tài khoản, thực hiện lệnh khóa giữ chỗ 15 phút (HOLD Lock), ghi nhận thanh toán và phát hành Vé điện tử mã QR mã hóa bảo mật.</p>
        <p>2.2 <strong>Tuân thủ quy chuẩn an ninh hàng hải:</strong> Khai báo danh sách hành khách hợp lệ với Ban Chỉ huy Bộ đội Biên phòng Cửa khẩu Cảng Đà Nẵng và Cảng vụ Hàng hải Đà Nẵng trước khi tàu cano xuất cảng.</p>
        <p>2.3 <strong>Đăng ký bảo hiểm du lịch:</strong> Lập danh sách thụ hưởng hợp đồng bảo hiểm tai nạn du khách với Tổng Công ty Bảo hiểm Bảo Việt theo hạn mức trách nhiệm 100.000.000 VNĐ / người / vụ.</p>
        <p>2.4 <strong>Cảnh báo khẩn cấp và Cứu hộ cứu nạn:</strong> Tự động gửi tin nhắn SMS/Email thông báo biến động thời tiết hải văn nguy hiểm, thông báo hoàn tiền 100% khi có bão, hoặc phối hợp cứu nạn cùng Trung tâm Tìm kiếm Cứu nạn Hàng hải Khu vực II.</p>
        <p>2.5 <strong>Vận hành cơ chế bảo chứng (Escrow) &amp; Đối soát:</strong> Quản lý dòng tiền tạm giữ, kiểm soát khiếu nại chất lượng dịch vụ và bảo vệ người dùng trước các rủi ro gian lận tài chính.</p>
      </section>

      <section className="space-y-3">
        <h2 className="text-base font-bold text-slate-900 uppercase">3. Tiêu Chuẩn Bảo Mật Cổng Thanh Toán PCI-DSS Level 1</h2>
        <p>
          3.1 Toàn bộ quá trình xử lý giao dịch thanh toán trực tuyến được thực hiện qua các cổng trung gian thanh toán uy tín được Ngân hàng Nhà nước Việt Nam cấp phép (VNPAY, MoMo, ZaloPay, PayPal, Tổ chức thẻ quốc tế Visa/Mastercard).
        </p>
        <p>
          3.2 Đường truyền dữ liệu thanh toán được mã hóa đa tầng bằng giao thức Transport Layer Security (TLS 1.3) với thuật toán mã hóa AES-256 bit. Mọi đối tác tài chính của chúng tôi đều đạt chứng chỉ bảo mật quốc tế PCI-DSS Level 1 cao nhất.
        </p>
        <p>
          3.3 Việc truy cập dữ liệu hành khách trong nội bộ hệ thống được phân quyền nghiêm ngặt theo vai trò (RBAC), bắt buộc xác thực hai yếu tố (2FA) đối với nhân sự điều phối cảng vụ.
        </p>
      </section>

      <section className="space-y-3">
        <h2 className="text-base font-bold text-slate-900 uppercase">4. Cam Kết Không Chia Sẻ Dữ Liệu Vì Mục Đích Thương Mại</h2>
        <p>
          4.1 DANASEA duy trì nguyên tắc cấm tuyệt đối hành vi mua bán, cho thuê, trao đổi hoặc chuyển giao dữ liệu cá nhân của người dùng cho bất kỳ bên thứ ba hay mạng lưới quảng cáo nào vì mục đích thương mại.
        </p>
        <p>
          4.2 Dữ liệu chỉ được chia sẻ trong phạm vi cần thiết phục vụ vận hành chuyến đi: (a) Chuyển thông tin hành khách cho đối tác cano để bố trí phương tiện; (b) Khai báo xuất bến với Đồn Biên phòng và Cảng vụ Hàng hải; (c) Cung cấp hồ sơ bồi thường cho Bảo hiểm Bảo Việt; (d) Cung cấp cho Cơ quan điều tra khi có văn bản yêu cầu chính thức theo quy định của pháp luật.
        </p>
      </section>

      <section className="space-y-3">
        <h2 className="text-base font-bold text-slate-900 uppercase">5. Chính Sách Cookie Và Công Nghệ Theo Dõi Phiên Làm Việc</h2>
        <p>
          5.1 Nền tảng sử dụng cookie cần thiết để duy trì trạng thái đăng nhập an toàn, lưu trữ giỏ hàng tạm thời và ngăn chặn các cuộc tấn công giả mạo yêu cầu chéo trang (CSRF).
        </p>
        <p>
          5.2 Quý khách có thể tùy chỉnh hoặc tắt tính năng tiếp nhận cookie trên trình duyệt của mình; tuy nhiên, điều này có thể làm gián đoạn một số thao tác thanh toán và xuất vé điện tử trên hệ thống.
        </p>
      </section>

      <section className="space-y-3">
        <h2 className="text-base font-bold text-slate-900 uppercase">6. Quyền Và Nghĩa Vụ Của Chủ Thể Dữ Liệu (Nghị Định 13/2023/NĐ-CP)</h2>
        <p>Theo quy định của pháp luật bảo vệ dữ liệu cá nhân Việt Nam, quý khách có các quyền hợp pháp sau:</p>
        <p>6.1 <strong>Quyền được biết và đồng ý:</strong> Được thông báo rõ ràng về các hoạt động xử lý dữ liệu và có quyền đồng ý hoặc không đồng ý với việc thu thập.</p>
        <p>6.2 <strong>Quyền truy cập và chỉnh sửa:</strong> Quý khách có thể tự kiểm tra, tra cứu và chỉnh sửa thông tin hồ sơ của mình trong phần Cài đặt tài khoản bất kỳ lúc nào.</p>
        <p>6.3 <strong>Quyền yêu cầu xóa dữ liệu:</strong> Quý khách có quyền yêu cầu xóa bỏ tài khoản hoặc ẩn danh dữ liệu cá nhân sau khi toàn bộ đơn hàng và nghĩa vụ thuế liên quan đã hoàn thành.</p>
        <p>6.4 <strong>Quyền hạn chế và phản đối xử lý dữ liệu:</strong> Quý khách có quyền yêu cầu hạn chế xử lý dữ liệu hoặc từ chối nhận các thông báo khuyến mại tự động.</p>
        <p>6.5 <strong>Quyền khiếu nại và tố cáo:</strong> Quý khách có quyền khiếu nại về hành vi vi phạm bảo vệ dữ liệu với Cán bộ bảo vệ dữ liệu của DANASEA hoặc Cơ quan chuyên trách bảo vệ dữ liệu Bộ Công an.</p>
      </section>

      <section className="space-y-3">
        <h2 className="text-base font-bold text-slate-900 uppercase">7. Thời Hạn Lưu Trữ Dữ Liệu</h2>
        <p>
          7.1 Dữ liệu hợp đồng, chứng từ thanh toán và danh sách hành khách xuất bến được lưu trữ an toàn trong thời hạn tối thiểu <strong>05 năm</strong> theo quy định của Luật Kế toán và Luật Du lịch Việt Nam.
        </p>
        <p>
          7.2 Nhật ký kỹ thuật truy cập và dữ liệu phiên tạm thời được hệ thống tự động xóa định kỳ sau 90 ngày làm việc.
        </p>
      </section>

      <section className="space-y-3">
        <h2 className="text-base font-bold text-slate-900 uppercase">8. Thông Tin Liên Hệ Cán Bộ Bảo Vệ Dữ Liệu (DPO)</h2>
        <p>
          Mọi yêu cầu thực hiện quyền của chủ thể dữ liệu hoặc báo cáo sự cố an toàn thông tin, quý khách vui lòng liên hệ trực tiếp với Bộ phận Bảo vệ dữ liệu cá nhân của DANASEA qua:
        </p>
        <p className="font-mono text-xs sm:text-sm bg-slate-50 p-3 rounded-lg border border-slate-200">
          Email chuyên trách: dpo@danasea.vn • Tổng đài bảo vệ người dùng: 1900 8468<br />
          Địa chỉ: Âu thuyền Thọ Quang &amp; Bãi biển Mân Thái, Đường Hoàng Sa, Phường Mân Thái, Quận Sơn Trà, TP. Đà Nẵng.
        </p>
      </section>
    </div>
  );
}

// -------------------------------------------------------------------------------------------------
// 3. REFUND DOCUMENT (Grounded in RefundPolicyEngine.java & Severe Marine Weather Matrix)
// -------------------------------------------------------------------------------------------------
function RefundDocument({ isEn }: { isEn: boolean }) {
  if (isEn) {
    return (
      <div className="space-y-8 text-slate-700 leading-relaxed">
        <p>
          DANASEA operates an automated refund computation system powered by the backend <code>RefundPolicyEngine</code>. This engine transparently evaluates cancellation validity windows, meteorological thresholds, and financial routing across all payment channels.
        </p>

        <section className="space-y-3">
          <h2 className="text-base font-bold text-slate-900 uppercase">1. Severe Marine Weather Force Majeure (100% Refund Guarantee)</h2>
          <p>
            1.1 Safety of human life at sea is non-negotiable. Whenever coastal telemetry stations at Son Tra and My Khe record meteorological readings surpassing maritime safety limits (significant wave height Hs &gt; 1.2m, sustained wind gusts &gt; 35km/h, squall convective clouds, dangerous rip currents, or official harbor bans by the Da Nang Port Authority), affected slots trigger an immediate automated cancellation.
          </p>
          <p>
            1.2 Under the <code>WEATHER</code> and <code>VENDOR_FAULT</code> classifications, customers receive an automatic 100% refund of the total booking value, with zero cancellation penalties, merchant fees, or payment processing deductions.
          </p>
          <p>
            1.3 In the event of a sudden mid-tour weather deterioration where the harbor master orders emergency vessel return, customers receive a prorated 50% refund or a complimentary rescheduled ticket voucher valid for twelve (12) months.
          </p>
        </section>

        <section className="space-y-3">
          <h2 className="text-base font-bold text-slate-900 uppercase">2. Comprehensive Refund Matrix (Rule-Set)</h2>
          <div className="overflow-x-auto my-4">
            <table className="w-full text-left text-xs sm:text-sm border border-slate-200">
              <thead>
                <tr className="bg-slate-50 text-slate-900 border-b border-slate-200">
                  <th className="py-3 px-3.5 font-semibold">Cancellation Category</th>
                  <th className="py-3 px-3.5 font-semibold">Request Time Window</th>
                  <th className="py-3 px-3.5 font-semibold text-center">Refund Percentage</th>
                  <th className="py-3 px-3.5 font-semibold">Disbursement Timeline</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-200 text-slate-700">
                <tr>
                  <td className="py-3 px-3.5 font-medium text-slate-900">Marine weather exceedance / Harbor departure ban</td>
                  <td className="py-3 px-3.5">Anytime prior to or during the slot</td>
                  <td className="py-3 px-3.5 text-center font-bold text-slate-900">100%</td>
                  <td className="py-3 px-3.5">Instant automated gateway refund (1 - 3 mins)</td>
                </tr>
                <tr>
                  <td className="py-3 px-3.5 font-medium text-slate-900">Vendor mechanical fault / Vessel unavailability</td>
                  <td className="py-3 px-3.5">Anytime prior to trip completion</td>
                  <td className="py-3 px-3.5 text-center font-bold text-slate-900">100%</td>
                  <td className="py-3 px-3.5">Instant automated gateway refund (1 - 3 mins)</td>
                </tr>
                <tr>
                  <td className="py-3 px-3.5 font-medium text-slate-900">Arbitrated dispute resolution / Admin ruling</td>
                  <td className="py-3 px-3.5">Within 24 hours of scheduled completion</td>
                  <td className="py-3 px-3.5 text-center font-bold text-slate-900">Custom (Up to 100%)</td>
                  <td className="py-3 px-3.5">Disbursed immediately upon council ruling</td>
                </tr>
                <tr>
                  <td className="py-3 px-3.5 font-medium text-slate-900">Customer voluntary cancellation</td>
                  <td className="py-3 px-3.5">&gt; 24 hours prior to departure time</td>
                  <td className="py-3 px-3.5 text-center font-bold text-slate-900">100%</td>
                  <td className="py-3 px-3.5">1 - 2 business banking days</td>
                </tr>
                <tr>
                  <td className="py-3 px-3.5 font-medium text-slate-900">Late cancellation or departure no-show</td>
                  <td className="py-3 px-3.5">Within ≤ 24 hours or after scheduled time</td>
                  <td className="py-3 px-3.5 text-center font-bold text-slate-400">0%</td>
                  <td className="py-3 px-3.5 text-slate-400">Non-refundable (quota &amp; crew reserved)</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>

        <section className="space-y-3">
          <h2 className="text-base font-bold text-slate-900 uppercase">3. Two-Phase Cancellation Workflow: Preview &amp; Execution</h2>
          <p>
            3.1 <strong>Phase 1 (Preview Mode):</strong> An idempotent calculation evaluating remaining minutes prior to slot departure, verifying whether the cancellation window remains valid, and returning the exact refund amount before the traveler confirms.
          </p>
          <p>
            3.2 <strong>Phase 2 (Command DB Execution):</strong> Upon customer confirmation, the database transaction updates booking status to <code>REFUNDED</code>, marks sub-orders, releases inventory slots, and dispatches refund payment commands via payment gateway APIs with Exponential Backoff retry queues.
          </p>
        </section>

        <section className="space-y-3">
          <h2 className="text-base font-bold text-slate-900 uppercase">4. Rescheduling Policy (Date &amp; Shift Modification)</h2>
          <p>
            4.1 Customers may request a one-time free departure shift change at least twelve (12) hours prior to the original departure shift, subject to remaining capacity on the target shift.
          </p>
          <p>
            4.2 In situations where weather alerts prompt voluntary rescheduling, changes are accommodated free of administrative charges.
          </p>
        </section>

        <section className="space-y-3">
          <h2 className="text-base font-bold text-slate-900 uppercase">5. Promotional Voucher Deduction Rules</h2>
          <p>
            5.1 The refunded amount is computed strictly against the net cash paid by the customer after voucher deduction (<code>finalAmount</code>).
          </p>
          <p>
            5.2 Promotional discount coupons cannot be refunded as cash and are not reinstated upon voluntary cancellations.
          </p>
        </section>
      </div>
    );
  }

  return (
    <div className="space-y-8 text-slate-700 leading-relaxed">
      <p>
        Hệ thống DANASEA ứng dụng công cụ tự động hóa tính toán hoàn tiền độc quyền (<code>RefundPolicyEngine</code>). Mọi quyết định hoàn trả, hủy tour hoặc bồi hoàn đều được xử lý minh bạch theo thuật toán được kiểm duyệt nghiêm ngặt, loại bỏ hoàn toàn sự can thiệp thủ công thiếu nhất quán.
      </p>

      <section className="space-y-3">
        <h2 className="text-base font-bold text-slate-900 uppercase">1. Điều Kiện Bất Khả Kháng Do Thời Tiết Biển (Cam Kết Hoàn Tiền 100%)</h2>
        <p>
          1.1 Tiêu chuẩn an toàn sinh mạng là bất khả thương lượng. Bất cứ khi nào dữ liệu từ trạm hải văn Sơn Trà - Mỹ Khê hoặc cảnh báo WMO ghi nhận chiều cao sóng <strong>Hs &gt; 1.2 mét</strong>, gió giật <strong>&gt; 35 km/h</strong>, xuất hiện dòng chảy xa bờ (Rip Current) nguy hiểm, mây dông đối lưu hoặc Cảng vụ Hàng hải phát lệnh cấm tàu xuất bến, hệ thống sẽ tự động kích hoạt hủy ca vận hành.
        </p>
        <p>
          1.2 Đối với nhóm lý do thời tiết (<code>WEATHER</code>) và sự cố phương tiện của nhà cung cấp (<code>VENDOR_FAULT</code>), khách hàng được bảo đảm nhận lại <strong>100% tổng số tiền thanh toán thực tế</strong> mà không phải chịu bất kỳ khoản khấu trừ nào.
        </p>
        <p>
          1.3 Trường hợp thời tiết chuyển biến xấu đột ngột khi tour đang diễn ra và Cảng vụ yêu cầu tàu quay đầu khẩn cấp để đảm bảo an toàn, du khách sẽ được hoàn tiền 50% chi phí thực tế hoặc được cấp voucher vé mới miễn phí có hạn dùng 12 tháng.
        </p>
      </section>

      <section className="space-y-3">
        <h2 className="text-base font-bold text-slate-900 uppercase">2. Bảng Ma Trận Tỷ Lệ Hoàn Tiền Toàn Diện</h2>
        <div className="overflow-x-auto my-4">
          <table className="w-full text-left text-xs sm:text-sm border border-slate-200">
            <thead>
              <tr className="bg-slate-50 text-slate-900 border-b border-slate-200">
                <th className="py-3 px-3.5 font-semibold">Nhóm lý do hủy đơn</th>
                <th className="py-3 px-3.5 font-semibold">Thời điểm gửi yêu cầu</th>
                <th className="py-3 px-3.5 font-semibold text-center">Tỷ lệ hoàn tiền</th>
                <th className="py-3 px-3.5 font-semibold">Thời gian tiền về tài khoản</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-200 text-slate-700">
              <tr>
                <td className="py-3 px-3.5 font-medium text-slate-900">Thời tiết biển vượt ngưỡng an toàn / Lệnh cấm Cảng vụ</td>
                <td className="py-3 px-3.5">Bất kỳ thời điểm nào trước/trong ca vận hành</td>
                <td className="py-3 px-3.5 text-center font-bold text-slate-900">100%</td>
                <td className="py-3 px-3.5">Tự động hoàn qua cổng thanh toán (1 - 3 phút)</td>
              </tr>
              <tr>
                <td className="py-3 px-3.5 font-medium text-slate-900">Sự cố kỹ thuật tàu cano / Lỗi từ phía đối tác</td>
                <td className="py-3 px-3.5">Bất kỳ thời điểm nào trước khi hoàn tất tour</td>
                <td className="py-3 px-3.5 text-center font-bold text-slate-900">100%</td>
                <td className="py-3 px-3.5">Tự động hoàn qua cổng thanh toán (1 - 3 phút)</td>
              </tr>
              <tr>
                <td className="py-3 px-3.5 font-medium text-slate-900">Phán quyết bồi thường khiếu nại (Disputes Arbitration)</td>
                <td className="py-3 px-3.5">Trong vòng 24 giờ kể từ khi kết thúc ca</td>
                <td className="py-3 px-3.5 text-center font-bold text-slate-900">Theo phán quyết (Lên đến 100%)</td>
                <td className="py-3 px-3.5">Giải ngân ngay sau khi Hội đồng ra quyết định</td>
              </tr>
              <tr>
                <td className="py-3 px-3.5 font-medium text-slate-900">Khách hàng chủ động hủy cá nhân (Customer Cancel)</td>
                <td className="py-3 px-3.5">Trước giờ khởi hành của ca &gt; 24 giờ</td>
                <td className="py-3 px-3.5 text-center font-bold text-slate-900">100%</td>
                <td className="py-3 px-3.5">1 - 2 ngày làm việc theo quy trình ngân hàng</td>
              </tr>
              <tr>
                <td className="py-3 px-3.5 font-medium text-slate-900">Khách hủy sát giờ hoặc vắng mặt tại bến bãi</td>
                <td className="py-3 px-3.5">Trong vòng ≤ 24 giờ hoặc sau giờ xuất bến</td>
                <td className="py-3 px-3.5 text-center font-bold text-slate-400">0%</td>
                <td className="py-3 px-3.5 text-slate-400">Không hoàn lại (đã bố trí cano và kíp trực HDV)</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <section className="space-y-3">
        <h2 className="text-base font-bold text-slate-900 uppercase">3. Quy Trình Hủy Đơn 2 Giai Đoạn (Preview &amp; Execute)</h2>
        <p>
          3.1 <strong>Giai đoạn Xem trước (Preview Mode):</strong> Khi quý khách truy cập mục chi tiết đơn hàng, hệ thống thực hiện truy vấn không ghi (Idempotent Query) để tính toán chính xác số phút còn lại trước giờ ca chạy, đối soát với chính sách và thông báo rõ số tiền sẽ được hoàn về trước khi quý khách đưa ra quyết định cuối cùng.
        </p>
        <p>
          3.2 <strong>Giai đoạn Thực thi (Execution Transaction):</strong> Khi xác nhận hủy, hệ thống cập nhật trạng thái đơn sang <code>CANCELLED/REFUNDED</code>, tự động hoàn trả tồn chỗ (capacity) cho đối tác, và kích hoạt tiến trình giải ngân tự động qua API cổng thanh toán VNPay/PayPal với cơ chế thử lại lũy thừa (Exponential Backoff) nếu mạng gặp sự cố.
        </p>
      </section>

      <section className="space-y-3">
        <h2 className="text-base font-bold text-slate-900 uppercase">4. Chính Sách Đổi Lịch &amp; Chuyển Ca Vận Hành</h2>
        <p>
          4.1 Quý khách được quyền yêu cầu đổi ca vận hành hoặc đổi ngày khởi hành miễn phí một lần nếu gửi yêu cầu trước giờ ca khởi hành ban đầu tối thiểu <strong>12 giờ</strong>, với điều kiện ca mong muốn chuyển tới còn đủ chỗ trống.
        </p>
        <p>
          4.2 Trường hợp đổi lịch vì lý do dự báo thời tiết xấu, DANASEA sẽ hỗ trợ quý khách dời ngày hoàn toàn miễn phí mà không giới hạn số lần dời.
        </p>
      </section>

      <section className="space-y-3">
        <h2 className="text-base font-bold text-slate-900 uppercase">5. Quy Định Về Khấu Trừ Voucher Ưu Đãi</h2>
        <p>
          5.1 Số tiền hoàn trả được tính toán dựa trên số tiền thực tế khách hàng đã thanh toán từ tài khoản ngân hàng hoặc ví điện tử (<code>finalAmount</code>), sau khi đã khấu trừ giá trị khuyến mãi từ voucher.
        </p>
        <p>
          5.2 Các voucher giảm giá áp dụng theo chương trình khuyến mãi sẽ không được hoàn lại bằng tiền mặt và không được phục hồi hạn sử dụng nếu khách chủ động hủy đơn vì lý do cá nhân.
        </p>
      </section>
    </div>
  );
}

// -------------------------------------------------------------------------------------------------
// 4. SAFETY DOCUMENT (SOLAS Rescue & Technical Marine Standards)
// -------------------------------------------------------------------------------------------------
function SafetyDocument({ isEn }: { isEn: boolean }) {
  if (isEn) {
    return (
      <div className="space-y-8 text-slate-700 leading-relaxed">
        <p>
          DANASEA operates under rigorous maritime safety standards overseen by the Da Nang Port Authority, Coastal Border Guards, and the Management Board of Son Tra Peninsula. Below are the operational safety standards and automated meteorological thresholds governing recreational water sports across the platform.
        </p>

        <section className="space-y-3">
          <h2 className="text-base font-bold text-slate-900 uppercase">1. SOLAS Life-Saving Equipment Mandate</h2>
          <p>
            1.1 100% of tourists, instructors, and vessel crew must wear certified SOLAS-standard life jackets equipped with high-intensity retro-reflective patches and maritime emergency whistles during the entirety of water activities.
          </p>
          <p>
            1.2 All speedboats must maintain self-inflatable life rafts, automated marine fire suppression systems, waterproof VHF communication gear operating on marine emergency Channel 16, and specialized medical trauma kits.
          </p>
          <p>
            1.3 Staging piers and floating platforms maintain automated external defibrillators (AED), spine-immobilization backboards, and emergency oxygen resuscitators with trained first-responders on constant standby.
          </p>
        </section>

        <section className="space-y-3">
          <h2 className="text-base font-bold text-slate-900 uppercase">2. Detailed Operational Guidelines for Water Sports &amp; Marine Activities</h2>
          <div className="space-y-4 text-sm">
            <p><strong>2.1 Stand-Up Paddleboarding (SUP):</strong> Staged at Bai But, Man Thai, and My Khe. Limited to calm waters with wave height &lt; 0.8m, wind &lt; 25 km/h. Mandatory safety leash attached to the paddler’s ankle at all times. Distance restricted to within 300 meters of the coastline buoy perimeter.</p>
            <p><strong>2.2 Sea Kayaking:</strong> Conducted in sheltered coastal zones around Son Tra peninsula. Double-hull sit-on-top kayaks equipped with automated scupper drainage. Strict requirement of 1 safety escort boat per 5 kayaks.</p>
            <p><strong>2.3 Coral Snorkeling:</strong> Operated at Hon Sup, Mui Nghe, and Bai Rang. Requires underwater visibility &gt; 3 meters and wave height &lt; 1.2m. Accompanied 1:1 by certified local instructors equipped with surface marker buoys (SMB) and rescue flotation rings.</p>
            <p><strong>2.4 Certified Scuba Diving:</strong> Conducted under international PADI and SSI safety standards. Dive depth capped at 12m for discover dives and 18m for open-water certified divers. Medical dive declaration mandatory; direct emergency transport link established with the Hyperbaric Chamber at Da Nang Naval Hospital 17.</p>
            <p><strong>2.5 Seawalker (Underwater Walking):</strong> Dedicated diving helmets supplied with continuous surface air compressor lines backed by dual redundant emergency air storage tanks. Restricted to shallow sandy plateaus with zero swell under constant divemaster supervision.</p>
            <p><strong>2.6 Parasailing (Cano-Towed Parachute):</strong> Operating altitudes between 50m and 80m. Strict maximum wind gust limit of 28 km/h. High-tensile synthetic winch tow lines tested to 3,000kg breaking strain. Instant cancellation triggered upon detection of convective squall clouds.</p>
            <p><strong>2.7 Coastal Speedboats:</strong> Twin-engine high-speed craft operating strictly within registered navigational channels around the Son Tra peninsula. Maximum speed capped at 25 knots. Certified Category 3 commercial skippers.</p>
            <p><strong>2.8 Jetskis (Personal Watercraft):</strong> Operated exclusively within buoy-marked recreational corridors at least 100 meters offshore, completely separated from public swimming beaches. Automatic engine cut-off lanyard attached to wrist at all times.</p>
            <p><strong>2.9 Banana Boat Towing:</strong> Maximum towing speed strictly capped at 20 knots. Mandatory impact-resistant water sports helmets and multi-chamber buoyancy vests. Dangerous high-speed inversion turns are outlawed.</p>
            <p><strong>2.10 Sunset Catamaran Cruises:</strong> Departing from DHC Marina down the Han River into Da Nang Bay. Certified captain, chief engineer, and onboard marine radar and AIS transponders.</p>
          </div>
        </section>

        <section className="space-y-3">
          <h2 className="text-base font-bold text-slate-900 uppercase">3. Automated Weather Threshold Matrix (Open-Meteo Integration)</h2>
          <div className="overflow-x-auto my-4">
            <table className="w-full text-left text-xs sm:text-sm border border-slate-200">
              <thead>
                <tr className="bg-slate-50 text-slate-900 border-b border-slate-200">
                  <th className="py-2.5 px-3 font-semibold">Activity</th>
                  <th className="py-2.5 px-3 font-semibold">Max Wave Height (Hs)</th>
                  <th className="py-2.5 px-3 font-semibold">Max Wind Gust</th>
                  <th className="py-2.5 px-3 font-semibold">Automatic Suspension Threshold</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-200 text-slate-700">
                <tr>
                  <td className="py-2.5 px-3 font-medium text-slate-900">Parasailing</td>
                  <td className="py-2.5 px-3 font-mono">&lt; 1.0 m</td>
                  <td className="py-2.5 px-3 font-mono">&lt; 28 km/h</td>
                  <td className="py-2.5 px-3">Wind ≥ 30 km/h or convective cloud</td>
                </tr>
                <tr>
                  <td className="py-2.5 px-3 font-medium text-slate-900">SUP &amp; Kayaking</td>
                  <td className="py-2.5 px-3 font-mono">&lt; 0.8 m</td>
                  <td className="py-2.5 px-3 font-mono">&lt; 25 km/h</td>
                  <td className="py-2.5 px-3">Long period swell &gt; 1.0m or rip currents</td>
                </tr>
                <tr>
                  <td className="py-2.5 px-3 font-medium text-slate-900">Coral Snorkeling &amp; Diving</td>
                  <td className="py-2.5 px-3 font-mono">&lt; 1.2 m</td>
                  <td className="py-2.5 px-3 font-mono">&lt; 30 km/h</td>
                  <td className="py-2.5 px-3">Turbid water (visibility &lt; 3m) or rough sea</td>
                </tr>
                <tr>
                  <td className="py-2.5 px-3 font-medium text-slate-900">Speedboats &amp; Jetskis</td>
                  <td className="py-2.5 px-3 font-mono">&lt; 1.5 m</td>
                  <td className="py-2.5 px-3 font-mono">&lt; 38 km/h</td>
                  <td className="py-2.5 px-3">Storm warning level 5+ or dense fog</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>

        <section className="space-y-3">
          <h2 className="text-base font-bold text-slate-900 uppercase">4. Medical Screening and High-Risk Constraints</h2>
          <p>
            4.1 Participation is strictly contraindicated for travelers with a history of acute coronary syndromes, severe hypertension, epilepsy, respiratory illnesses, asthma, or pregnant women. Captains reserve the unappealable authority to decline service if a traveler displays medical impairment or alcohol intoxication.
          </p>
          <p>
            4.2 Travelers undergoing scuba diving must adhere to strict post-dive flight restrictions: a minimum surface interval of eighteen (18) hours before boarding commercial aircraft.
          </p>
        </section>

        <section className="space-y-3">
          <h2 className="text-base font-bold text-slate-900 uppercase">5. Emergency Rescue Chain of Command</h2>
          <p>
            5.1 In the event of a maritime emergency, the platform coordinates an immediate unified rescue chain: Pier Dispatcher &rarr; Dedicated On-Water Rescue Boat &rarr; Maritime Rescue Coordination Center Region II (MRCC Danang) &rarr; Border Guard Coastal Patrol &rarr; Da Nang Emergency Center 115.
          </p>
        </section>
      </div>
    );
  }

  return (
    <div className="space-y-8 text-slate-700 leading-relaxed">
      <p>
        Toàn bộ hoạt động trên hệ sinh thái DANASEA chịu sự giám sát trực tiếp của Cảng vụ Hàng hải Đà Nẵng, Ban Chỉ huy Bộ đội Biên phòng và Ban Quản lý Bán đảo Sơn Trà. Dưới đây là các quy chuẩn kỹ thuật an toàn và ngưỡng thời tiết chi tiết áp dụng cho các hoạt động thể thao và trải nghiệm biển trên nền tảng DANASEA.
      </p>

      <section className="space-y-3">
        <h2 className="text-base font-bold text-slate-900 uppercase">1. Tiêu Chuẩn Trang Bị Cứu Sinh SOLAS Bắt Buộc</h2>
        <p>
          1.1 100% du khách, hướng dẫn viên và thuyền viên bắt buộc phải mặc áo phao cứu sinh tiêu chuẩn SOLAS có dải phản quang và còi cứu nạn trong suốt thời gian tham gia các hoạt động trên mặt nước.
        </p>
        <p>
          1.2 Tất cả các phương tiện thủy nội địa (cano cao tốc, tàu du lịch) phải được trang bị đầy đủ phao cứu sinh tự thổi, bình bọt chữa cháy, túi sơ cấp cứu chấn thương biển và thiết bị liên lạc vô tuyến VHF chống nước hoạt động trên kênh khẩn cấp 16.
        </p>
        <p>
          1.3 Trạm bến bãi và sàn nổi lặn biển duy trì thiết bị sốc tim tự động (AED), nẹp cố định cột sống cổ, bình oxy cấp cứu khẩn cấp và nhân sự có chứng chỉ sơ cấp cứu quốc tế túc trực thường trực.
        </p>
      </section>

      <section className="space-y-3">
        <h2 className="text-base font-bold text-slate-900 uppercase">2. Quy Chuẩn Vận Hành Chi Tiết Các Hoạt Động Thể Thao Biển</h2>
        <div className="space-y-4 text-sm">
          <p><strong>2.1 Chèo ván đứng (SUP):</strong> Hoạt động tại khu vực Bãi Bụt, Mân Thái, Mỹ Khê. Thường tổ chức sáng sớm (đón bình minh) hoặc chiều mát. Bắt buộc gắn dây an toàn (leash) vào cổ chân du khách; cấm ra xa quá vùng phao tiêu 300 mét. Luôn có cano cứu hộ cơ động sẵn sàng tiếp ứng trong 2 phút.</p>
          <p><strong>2.2 Chèo thuyền Kayak:</strong> Vùng vịnh lặng sóng quanh Bán đảo Sơn Trà. Thuyền đôi vật liệu composite hoặc nhựa đúc có khoang thoát nước tự động. Bắt buộc có cano cứu hộ hộ tống theo tỷ lệ 1 cano giám sát tối đa 5 thuyền kayak.</p>
          <p><strong>2.3 Lặn ngắm san hô (Snorkeling):</strong> Khu vực Hòn Sụp, Mũi Nghê, Bãi Rạng (Sơn Trà). Yêu cầu nước trong, tầm nhìn đáy biển &gt; 3 mét, dòng chảy yếu. Du khách được kèm sát theo tỷ lệ 1 hướng dẫn viên kèm 1 khách, trang bị phao tiêu nổi cảnh báo tàu bè.</p>
          <p><strong>2.4 Lặn bình khí (Scuba Diving):</strong> Vận hành theo tiêu chuẩn an toàn PADI/SSI. Độ sâu tối đa 12m đối với khách trải nghiệm và 18m đối với thợ lặn có chứng chỉ. Thiết lập quy trình cấp cứu giảm áp kết nối thẳng Buồng giảm áp Viện Y học Hải quân / Bệnh viện 17 Quân khu 5.</p>
          <p><strong>2.5 Đi bộ dưới biển (Seawalker):</strong> Du khách đội mũ dưỡng khí đặc chủng cung cấp oxy liên tục từ máy nén trên sàn nổi, có bình tích khí dự phòng khẩn cấp; chỉ tổ chức trên thềm cát san hô nông ổn định dưới sự giám sát kèm cặp 1:1 của thợ lặn an toàn.</p>
          <p><strong>2.6 Dù bay biển kéo bằng cano (Parasailing):</strong> Hoạt động ở độ cao 50m – 80m trên mặt biển; cực kỳ nhạy cảm với đối lưu gió. Tốc độ gió giật tối đa cho phép &lt; 28 km/h. Dây dù tời điện bằng sợi tổng hợp siêu bền chịu lực 3.000kg. Lập tức hạ dù khẩn cấp nếu xuất hiện mây dông đối lưu.</p>
          <p><strong>2.7 Cano cao tốc lướt sóng (Speedboat):</strong> Vận chuyển khách tham quan quanh Bán đảo Sơn Trà, Bãi Bụt, Mũi Nghê. Động cơ đôi tối thiểu 150HP – 300HP đạt kiểm định; thuyền trưởng có bằng thuyền trưởng hạng 3 trở lên, tốc độ tối đa không quá 25 hải lý/giờ.</p>
          <p><strong>2.8 Mô tô nước (Jetski):</strong> Chạy tự do trong vùng nước giới hạn phao tiêu tại Bãi Bụt, Mỹ Khê, cách bãi tắm dân sinh tối thiểu 100 mét. Bắt buộc gắn khóa ngắt động cơ tự động (kill switch) vào cổ tay người lái.</p>
          <p><strong>2.9 Kéo phao chuối cảm giác mạnh (Banana Boat):</strong> Phao bơm hơi nhiều khoang độc lập do cano kéo với tốc độ quy định không quá 20 hải lý/giờ; du khách bắt buộc đội mũ bảo hiểm thể thao nước và áo phao giảm chấn.</p>
          <p><strong>2.10 Du thuyền ngắm hoàng hôn vịnh (Catamaran / Yacht):</strong> Xuất phát từ bến du thuyền sông Hàn ra Vịnh Đà Nẵng; phục vụ ngắm cảnh với radar hàng hải và thiết bị định vị AIS giám sát hải trình liên tục.</p>
        </div>
      </section>

      <section className="space-y-3">
        <h2 className="text-base font-bold text-slate-900 uppercase">3. Ngưỡng Thời Tiết An Toàn Tự Động (Dữ Liệu Hàng Hải Open-Meteo)</h2>
        <div className="overflow-x-auto my-4">
          <table className="w-full text-left text-xs sm:text-sm border border-slate-200">
            <thead>
              <tr className="bg-slate-50 text-slate-900 border-b border-slate-200">
                <th className="py-2.5 px-3 font-semibold">Loại hình trải nghiệm biển</th>
                <th className="py-2.5 px-3 font-semibold">Chiều cao sóng tối đa (Hs)</th>
                <th className="py-2.5 px-3 font-semibold">Gió giật tối đa</th>
                <th className="py-2.5 px-3 font-semibold">Ngưỡng tự động tạm ngưng ca</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-200 text-slate-700">
              <tr>
                <td className="py-2.5 px-3 font-medium text-slate-900">Dù bay biển (Parasailing)</td>
                <td className="py-2.5 px-3 font-mono">&lt; 1.0 m</td>
                <td className="py-2.5 px-3 font-mono">&lt; 28 km/h</td>
                <td className="py-2.5 px-3">Gió giật ≥ 30 km/h hoặc mây dông đối lưu</td>
              </tr>
              <tr>
                <td className="py-2.5 px-3 font-medium text-slate-900">Chèo SUP &amp; Chèo Kayak</td>
                <td className="py-2.5 px-3 font-mono">&lt; 0.8 m</td>
                <td className="py-2.5 px-3 font-mono">&lt; 25 km/h</td>
                <td className="py-2.5 px-3">Sóng lừng &gt; 1.0m hoặc dòng chảy xiết</td>
              </tr>
              <tr>
                <td className="py-2.5 px-3 font-medium text-slate-900">Lặn ngắm san hô (Snorkeling)</td>
                <td className="py-2.5 px-3 font-mono">&lt; 1.2 m</td>
                <td className="py-2.5 px-3 font-mono">&lt; 30 km/h</td>
                <td className="py-2.5 px-3">Tầm nhìn nước đáy &lt; 3m hoặc biển động</td>
              </tr>
              <tr>
                <td className="py-2.5 px-3 font-medium text-slate-900">Cano cao tốc &amp; Jetski</td>
                <td className="py-2.5 px-3 font-mono">&lt; 1.5 m</td>
                <td className="py-2.5 px-3 font-mono">&lt; 38 km/h</td>
                <td className="py-2.5 px-3">Cảnh báo bão cấp 5+ hoặc sương mù dày</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <section className="space-y-3">
        <h2 className="text-base font-bold text-slate-900 uppercase">4. Sàng Lọc Sức Khỏe &amp; Quyền Từ Chối Phục Vụ</h2>
        <p>
          4.1 Nghiêm cấm tham gia đối với khách hàng có tiền sử bệnh tim mạch, suy tim, cao huyết áp, hen suyễn nặng, động kinh hoặc phụ nữ đang mang thai đối với các trò chơi cảm giác mạnh. Khách lặn bình khí Scuba bắt buộc phải tuân thủ thời gian giãn cách không đi máy bay tối thiểu 18 giờ sau ca lặn.
        </p>
        <p>
          4.2 Thuyền trưởng và điều phối viên bến bãi có quyền từ chối phục vụ ngay lập tức mà không bồi thường nếu du khách có nồng độ cồn, sử dụng chất kích thích hoặc có hành vi không chấp hành hướng dẫn an toàn trên boong.
        </p>
      </section>

      <section className="space-y-3">
        <h2 className="text-base font-bold text-slate-900 uppercase">5. Chuỗi Phản Ứng Cứu Hộ Khẩn Cấp Trên Biển</h2>
        <p>
          5.1 Khi xảy ra tình huống cứu nạn khẩn cấp, quy trình phản ứng nhanh được kích hoạt tức thì theo chuỗi chỉ huy: Trực ban điều phối bến &rarr; Cano cứu sinh cơ động &rarr; Trung tâm Tìm kiếm Cứu nạn Hàng hải Khu vực II (Danang MRCC) &rarr; Đồn Biên phòng Sơn Trà &rarr; Cấp cứu y tế 115 Đà Nẵng.
        </p>
      </section>
    </div>
  );
}

// -------------------------------------------------------------------------------------------------
// 5. CONSERVATION DOCUMENT (Son Tra Coral Sanctuary Protection)
// -------------------------------------------------------------------------------------------------
function ConservationDocument({ isEn }: { isEn: boolean }) {
  if (isEn) {
    return (
      <div className="space-y-8 text-slate-700 leading-relaxed">
        <p>
          The pristine coral reefs and marine flora of Son Tra Peninsula represent a legally protected national natural sanctuary encompassing over 80 hectares of coral coverage with more than 100 species of reef-building corals. DANASEA operates in strict synergy with the Management Board of Son Tra Peninsula and the Da Nang Department of Natural Resources and Environment to enforce stringent eco-preservation rules across all listed operations.
        </p>

        <section className="space-y-3">
          <h2 className="text-base font-bold text-slate-900 uppercase">1. Permanent Mooring Buoy Network &amp; Ban on Iron Anchors</h2>
          <p>
            1.1 Dropping traditional iron fluked anchors directly onto coral beds inflicts catastrophic mechanical breakage that takes decades to regenerate. Consequently, dropping anchors is strictly outlawed across all designated diving bays (Bai But, Hon Sup, Bai Rang, Mui Nghe).
          </p>
          <p>
            1.2 All partner vessels, speedboats, and floating platforms must tie exclusively to permanent marine mooring buoys pre-installed and certified by the Management Board. Any vessel found dropping anchors will face immediate platform delisting and administrative referral to the Maritime Police.
          </p>
        </section>

        <section className="space-y-3">
          <h2 className="text-base font-bold text-slate-900 uppercase">2. The 3-NO Code of Conduct for Coral Snorkeling &amp; Diving</h2>
          <p>
            2.1 <strong>NO standing, stepping, or resting on corals:</strong> Divers must maintain at least 1.5 meters vertical clearance above coral crowns, utilizing life vest buoyancy to prevent fin contact. Touching, trampling, or breaking coral polyps is strictly prohibited.
          </p>
          <p>
            2.2 <strong>NO artificial feeding or plastic littering:</strong> Introducing bread, snacks, processed bait, or single-use plastics disrupts native reef fish diets, causes algal blooms, and contaminates clear waters. All trash generated on vessels must be brought back to onshore disposal bins.
          </p>
          <p>
            2.3 <strong>NO specimen harvesting:</strong> Absolutely no removing live or dead coral fragments, giant clams, shells, starfish, sea anemones, or geological specimens. Violators will be recorded on pier surveillance and reported to the Marine Environmental Police for statutory fines up to 50,000,000 VND under the Law on Fisheries.
          </p>
        </section>

        <section className="space-y-3">
          <h2 className="text-base font-bold text-slate-900 uppercase">3. Reef-Safe Sunscreen Scientific Guidelines</h2>
          <p>
            3.1 Scientific research confirms that chemical sunscreen filters containing Oxybenzone, Octinoxate, and Octocrylene cause endocrine disruption and induce rapid bleaching in juvenile coral colonies even at parts-per-billion concentrations.
          </p>
          <p>
            3.2 DANASEA strongly recommends tourists choose certified 100% mineral-based, non-nano Zinc Oxide or Titanium Dioxide sunscreen formulas prior to embarking on snorkeling and diving adventures.
          </p>
        </section>

        <section className="space-y-3">
          <h2 className="text-base font-bold text-slate-900 uppercase">4. Environmental Monitoring and Marine Patrol</h2>
          <p>
            4.1 DANASEA participates in periodic reef health monitoring alongside local marine biologists, evaluating coral bleach indices and water quality parameters around Son Tra Peninsula.
          </p>
          <p>
            4.2 A portion of platform operational revenue is contributed toward coral restoration nurseries and community coastal clean-up initiatives organized by the Da Nang Tourism Association.
          </p>
        </section>
      </div>
    );
  }

  return (
    <div className="space-y-8 text-slate-700 leading-relaxed">
      <p>
        Hệ sinh thái rạn san hô và sinh vật biển tại Bán đảo Sơn Trà là di sản thiên nhiên vô giá của Thành phố Đà Nẵng, bao phủ trên 80 hecta thềm lục địa với hơn 100 loài san hô cứng và mềm quý hiếm. Toàn bộ đối tác vận tải và du khách tham gia tour trên sàn DANASEA cam kết tuân thủ nghiêm ngặt quy chế bảo vệ môi trường biển theo Nghị định của Chính phủ, Luật Thủy sản và Quyết định của UBND TP. Đà Nẵng.
      </p>

      <section className="space-y-3">
        <h2 className="text-base font-bold text-slate-900 uppercase">1. Lệnh Cấm Tuyệt Đối Thả Mỏ Neo Sắt &amp; Buộc Phao Cố Định</h2>
        <p>
          1.1 Việc thả mỏ neo sắt trực tiếp xuống đáy biển là nguyên nhân hàng đầu phá hủy thảm san hô non. Cành san hô bị gãy mất hàng chục năm để phục hồi nguyên trạng. DANASEA nghiêm cấm tuyệt đối mọi hành vi thả mỏ neo sắt tại các vùng vịnh lặn san hô (Hòn Sụp, Bãi Bụt, Bãi Rạng, Mũi Nghê).
        </p>
        <p>
          1.2 100% cano cao tốc và sàn phao nổi của đối tác bắt buộc phải buộc dây vào hệ thống phao neo cố định do Ban Quản lý Bán đảo Sơn Trà lắp đặt chuyên dụng. Bất kỳ phương tiện nào vi phạm sẽ bị khóa tài khoản vĩnh viễn và chuyển hồ sơ cho Cảnh sát Môi trường xử lý.
        </p>
      </section>

      <section className="space-y-3">
        <h2 className="text-base font-bold text-slate-900 uppercase">2. Quy Tắc 3 KHÔNG Khi Lặn Ngắm San Hô</h2>
        <p>
          2.1 <strong>KHÔNG dẫm đạp, bẻ cành hoặc đứng lên san hô:</strong> Du khách luôn duy trì khoảng cách bơi nổi tối thiểu 1.5 mét so với thềm san hô, sử dụng áo phao cứu sinh để nổi an toàn. Nghiêm cấm mọi hành động dùng chân vịt quạt mạnh vào rạn hoặc đứng tỳ lên vỉa san hô.
        </p>
        <p>
          2.2 <strong>KHÔNG cho sinh vật biển ăn thức ăn lạ &amp; Không xả rác:</strong> Nghiêm cấm thả bánh mì, đồ ăn chế biến sẵn hoặc xả rác thải nhựa xuống biển làm thay đổi tập tính tự nhiên của cá biển và gây ô nhiễm vùng nước. Mọi rác thải trên tàu phải được thu gom về thùng rác tại bến bờ.
        </p>
        <p>
          2.3 <strong>KHÔNG thu hái, mang mẫu vật san hô về đất liền:</strong> Mọi hành vi bẻ san hô, nhặt sò tai tượng, bắt sao biển hoặc sinh vật biển sẽ bị camera giám sát cảng ghi nhận và xử phạt hành chính lên tới 50.000.000 VNĐ theo Luật Thủy sản và Luật Bảo vệ môi trường.
        </p>
      </section>

      <section className="space-y-3">
        <h2 className="text-base font-bold text-slate-900 uppercase">3. Khuyến Nghị Kem Chống Nắng Thân Thiện San Hô (Reef-Safe)</h2>
        <p>
          3.1 Hai hóa chất Oxybenzone và Octinoxate trong các loại kem chống nắng hóa học thông thường gây biến đổi gen, suy giảm miễn dịch và tẩy trắng san hô non ngay cả ở nồng độ cực nhỏ.
        </p>
        <p>
          3.2 DANASEA khuyến nghị du khách sử dụng kem chống nắng khoáng chất vật lý (Kẽm Oxit / Titanium Dioxide không chứa hạt nano) thân thiện với rạn san hô trước khi xuống tắm và lặn biển.
        </p>
      </section>

      <section className="space-y-3">
        <h2 className="text-base font-bold text-slate-900 uppercase">4. Giám Sát Môi Trường &amp; Quỹ Phục Hồi San Hô</h2>
        <p>
          4.1 DANASEA phối hợp định kỳ với các chuyên gia sinh học biển khảo sát chỉ số tẩy trắng san hô và chất lượng vùng nước quanh bán đảo Sơn Trà.
        </p>
        <p>
          4.2 Một phần doanh thu phí dịch vụ nền tảng được trích lập trực tiếp vào Quỹ Ươm mầm San hô và phong trào làm sạch đáy biển do Hiệp hội Du lịch Đà Nẵng phát động.
        </p>
      </section>
    </div>
  );
}

// -------------------------------------------------------------------------------------------------
// 6. BOOKING GUIDE DOCUMENT (BE Booking & CheckIn workflows)
// -------------------------------------------------------------------------------------------------
function BookingGuideDocument({ isEn }: { isEn: boolean }) {
  if (isEn) {
    return (
      <div className="space-y-8 text-slate-700 leading-relaxed">
        <p>
          DANASEA coordinates live inventory reservation, electronic safety waivers, and QR check-in to provide a seamless ocean experience. Below is the step-by-step customer journey.
        </p>

        <section className="space-y-3">
          <h2 className="text-base font-bold text-slate-900 uppercase">1. Selecting Experiences and Operating Shifts</h2>
          <p>
            1.1 Choose your activity, coastal beach location (Bai But, Man Thai, or My Khe), calendar date, and departure shift. The system provides real-time remaining seat inventory updated with active marine safety levels.
          </p>
          <p>
            1.2 Available departure shifts are color-coded: Morning Shifts (05:00 - 07:00, 08:00 - 10:00) offer calm seas ideal for SUP, coral snorkeling, and parasailing; Afternoon Shifts (14:00 - 16:00, 16:00 - 18:00) are optimized for sunset catamaran cruises and jetskis.
          </p>
        </section>

        <section className="space-y-3">
          <h2 className="text-base font-bold text-slate-900 uppercase">2. 15-Minute Inventory Hold Lock</h2>
          <p>
            2.1 Upon proceeding to checkout, capacity is temporarily reserved via our Inventory Hold mechanism for exactly fifteen (15) minutes, preventing double-bookings while you complete payment.
          </p>
          <p>
            2.2 A real-time countdown timer is displayed during checkout. If the transaction is not finalized within 15 minutes, the slot is automatically released back to the marketplace.
          </p>
        </section>

        <section className="space-y-3">
          <h2 className="text-base font-bold text-slate-900 uppercase">3. Executing Safety Waiver Declaration (Guard R9)</h2>
          <p>
            3.1 For regulated water sports (parasailing, jetskis, scuba diving, seawalker), travelers must review and execute a digital Safety Waiver and medical disclosure.
          </p>
          <p>
            3.2 The backend engine programmatically enforces Guard R9 (Waiver Acceptance Required), blocking payment gateway link generation until all safety declarations are executed by the lead passenger.
          </p>
        </section>

        <section className="space-y-3">
          <h2 className="text-base font-bold text-slate-900 uppercase">4. Maritime Passenger Manifest Information</h2>
          <p>
            4.1 Prior to payment, travelers must submit complete legal names, dates of birth, nationalities, and CCCD/Passport numbers for every passenger in the booking group.
          </p>
          <p>
            4.2 This information is compiled into the official passenger manifest transmitted electronically to the Da Nang Maritime Administration and Coastal Border Guards.
          </p>
        </section>

        <section className="space-y-3">
          <h2 className="text-base font-bold text-slate-900 uppercase">5. Secure Payment via Escrow Protection</h2>
          <p>
            5.1 Choose your preferred payment method: VNPAY (QR code / domestic ATM), MoMo, ZaloPay, or international Visa/Mastercard credit cards.
          </p>
          <p>
            5.2 All funds are held in escrow custody and released to operators only after trip completion, ensuring complete financial safety.
          </p>
        </section>

        <section className="space-y-3">
          <h2 className="text-base font-bold text-slate-900 uppercase">6. Encrypted QR Ticket Issuance &amp; Port Boarding</h2>
          <p>
            6.1 Following payment completion, an encrypted electronic boarding pass containing a tamper-proof QR code is generated instantly and delivered via email and the My Orders section.
          </p>
          <p>
            6.2 Arrive at the harbor or beach station 15 minutes prior to departure. Present your mobile QR ticket to pier staff for validation via the mobile scanner app, receive certified life gear, and board safely.
          </p>
        </section>
      </div>
    );
  }

  return (
    <div className="space-y-8 text-slate-700 leading-relaxed">
      <p>
        Hệ thống đặt chỗ và xuất bến của DANASEA được thiết kế đồng bộ theo chu trình khép kín: Giữ chỗ thời gian thực, cam kết an toàn số và quét mã QR check-in tại bến cảng.
      </p>

      <section className="space-y-3">
        <h2 className="text-base font-bold text-slate-900 uppercase">1. Chọn Trải Nghiệm &amp; Khung Giờ Ca Vận Hành</h2>
        <p>
          1.1 Quý khách lựa chọn dịch vụ mong muốn, chọn bãi tập kết (Bãi Bụt, Mân Thái, Mỹ Khê), ngày khởi hành và khung giờ (đón bình minh hoặc hoàng hôn). Hệ thống hiển thị số lượng chỗ còn trống chính xác theo thời gian thực.
        </p>
        <p>
          1.2 Các ca vận hành được mã hóa màu sắc: Ca sáng sớm (05:00 - 07:00, 08:00 - 10:00) biển êm thích hợp chèo SUP, lặn ngắm san hô và dù bay; Ca chiều (14:00 - 16:00, 16:00 - 18:00) biển lộng gió thích hợp cano lướt sóng, mô tô nước và du thuyền hoàng hôn.
        </p>
      </section>

      <section className="space-y-3">
        <h2 className="text-base font-bold text-slate-900 uppercase">2. Cơ Chế Khóa Giữ Chỗ Tồn Kho (HOLD Lock 15 Phút)</h2>
        <p>
          2.1 Ngay khi quý khách tiến hành đặt chỗ, hệ thống tự động khóa giữ số lượng chỗ của quý khách trong vòng 15 phút. Trong thời gian này, không một du khách nào khác có thể đặt trùng ca của quý khách.
        </p>
        <p>
          2.2 Màn hình thanh toán hiển thị đồng hồ đếm ngược 15 phút. Nếu quý khách không hoàn tất thanh toán trước khi thời gian kết thúc, chỗ sẽ được tự động giải phóng lại cho du khách khác.
        </p>
      </section>

      <section className="space-y-3">
        <h2 className="text-base font-bold text-slate-900 uppercase">3. Đọc &amp; Ký Cam Kết An Toàn Trực Tuyến (Safety Waiver)</h2>
        <p>
          3.1 Đối với các môn thể thao mạo hiểm (mô tô nước, dù bay, lặn biển), quý khách xác nhận Bản cam kết an toàn &amp; Khai báo sức khỏe điện tử trước khi thanh toán. Đây là rào chắn an toàn bắt buộc (Guard R9) để bảo vệ quyền lợi bảo hiểm du lịch của quý khách.
        </p>
        <p>
          3.2 Hệ thống backend sẽ tự động chặn việc tạo liên kết thanh toán nếu phát hiện đơn hàng có trò chơi mạo hiểm nhưng người đặt chưa bấm xác nhận cam kết.
        </p>
      </section>

      <section className="space-y-3">
        <h2 className="text-base font-bold text-slate-900 uppercase">4. Khai Báo Thông Tin Xuất Bến Trình Biên Phòng</h2>
        <p>
          4.1 Quý khách điền đầy đủ họ tên, ngày sinh, quốc tịch và số CCCD/Hộ chiếu của từng thành viên trong đoàn.
        </p>
        <p>
          4.2 Dữ liệu này được tự động chuyển thành danh sách hành khách điện tử gửi Cảng vụ Hàng hải và Đồn Biên phòng phê duyệt lệnh rời bến.
        </p>
      </section>

      <section className="space-y-3">
        <h2 className="text-base font-bold text-slate-900 uppercase">5. Thanh Toán An Toàn Qua Cổng Bảo Chứng Escrow</h2>
        <p>
          5.1 Quý khách lựa chọn phương thức thanh toán thuận tiện: VNPAY (quét mã QR ngân hàng), MoMo, ZaloPay, hoặc thẻ quốc tế Visa/Mastercard.
        </p>
        <p>
          5.2 Toàn bộ số tiền được giữ an toàn tại tài khoản bảo chứng Escrow và chỉ giải ngân cho đối tác sau khi chuyến đi hoàn tất tốt đẹp.
        </p>
      </section>

      <section className="space-y-3">
        <h2 className="text-base font-bold text-slate-900 uppercase">6. Nhận Vé Điện Tử &amp; Quét Mã QR Xuất Bến</h2>
        <p>
          6.1 Sau khi hoàn tất thanh toán, hệ thống phát hành ngay Vé điện tử mã QR mã hóa bảo mật gửi vào email và lưu trữ trong mục Đơn hàng của tôi.
        </p>
        <p>
          6.2 Du khách có mặt tại bến trước giờ ca 15 phút, xuất trình mã QR trên điện thoại cho điều phối viên bến bãi quét xác thực bằng ứng dụng máy quét bến tàu, nhận trang bị áo phao đạt chuẩn SOLAS và xuống phương tiện an toàn.
        </p>
      </section>
    </div>
  );
}

// -------------------------------------------------------------------------------------------------
// 7. ABOUT DOCUMENT (Corporate Identity & Tourism Licenses)
// -------------------------------------------------------------------------------------------------
function AboutDocument({ isEn }: { isEn: boolean }) {
  if (isEn) {
    return (
      <div className="space-y-8 text-slate-700 leading-relaxed">
        <section className="space-y-3">
          <h2 className="text-base font-bold text-slate-900 uppercase">1. Platform Mission &amp; Overview</h2>
          <p>
            DANASEA is the premier digital marine marketplace in Da Nang, directly integrating tourists with licensed speedboat operators, certified water sports academies, and scuba diving centers. The platform monitors ocean sensor data in real time to enforce uncompromising maritime safety, eco-preservation of coral ecosystems, and seamless e-commerce transactions.
          </p>
          <p>
            Established in 2026, DANASEA connects over 20 licensed coastal operators, providing standardized safety protocols, transparent pricing, 100% escrow payment custody, and 1:1 certified local instructors.
          </p>
        </section>

        <section className="space-y-3">
          <h2 className="text-base font-bold text-slate-900 uppercase">2. Corporate Entity and Operating Licensing</h2>
          <p>
            <strong>Corporate Name:</strong> DANASEA OCEAN TECHNOLOGY AND TOURISM JOINT STOCK COMPANY<br />
            <strong>International Trading Name:</strong> DANASEA OCEAN TECHNOLOGY AND TOURISM JOINT STOCK COMPANY<br />
            <strong>Enterprise Identification / Tax Code:</strong> 0402188468 issued by the Department of Planning and Investment of Da Nang City.<br />
            <strong>Domestic Tour Operator License:</strong> No. 48-0024/2026/SDL-GP LHND issued by the Da Nang Department of Tourism.<br />
            <strong>Registered Capital:</strong> 20,000,000,000 VND (Twenty Billion Vietnamese Dong).<br />
            <strong>Operations Pier &amp; Staging Harbor:</strong> Tho Quang Harbor &amp; Man Thai Beach, Hoang Sa Road, Son Tra District, Da Nang City.<br />
            <strong>Headquarters Office:</strong> 48 Hoang Sa Road, Man Thai Ward, Son Tra District, Da Nang City.<br />
            <strong>Emergency &amp; Rescue Hotline 24/7:</strong> 1900 8468 • <strong>Official Email:</strong> hotro@danasea.vn.
          </p>
        </section>

        <section className="space-y-3">
          <h2 className="text-base font-bold text-slate-900 uppercase">3. Comprehensive Civil Liability Insurance</h2>
          <p>
            All tourists reserving experiences on the DANASEA platform are covered under master civil liability insurance policy No. BV-DANASEA-2026 underwritten by Bao Viet Insurance Corporation, featuring a maximum compensation limit of <strong>100,000,000 VND per passenger per occurrence</strong>. Coverage covers medical emergency evacuations, hospital care, and emergency salvage operations at sea.
          </p>
        </section>

        <section className="space-y-3">
          <h2 className="text-base font-bold text-slate-900 uppercase">4. Local Marine Instructor Certification Program</h2>
          <p>
            DANASEA partners with the Da Nang Tourism College and the Maritime Rescue Coordination Center Region II to operate the Local Marine Instructor Guild, training and certifying over 100 indigenous fishermen and lifeguards into professional 1:1 water sports guides.
          </p>
        </section>
      </div>
    );
  }

  return (
    <div className="space-y-8 text-slate-700 leading-relaxed">
      <section className="space-y-3">
        <h2 className="text-base font-bold text-slate-900 uppercase">1. Giới Thiệu &amp; Sứ Mệnh Nền Tảng DANASEA</h2>
        <p>
          DANASEA là sàn giao dịch thương mại điện tử chuyên biệt kết nối trực tiếp du khách với các đơn vị vận tải cano, chủ câu lạc bộ SUP, lặn biển và thể thao nước được cấp phép tại Đà Nẵng. Hệ thống tích hợp giám sát dữ liệu hải văn thời gian thực để đảm bảo mỗi chuyến đi đều an toàn tuyệt đối, minh bạch giá cả và bảo vệ nguyên trạng hệ sinh thái rạn san hô Sơn Trà.
        </p>
        <p>
          Thành lập năm 2026, DANASEA quy tụ hơn 20 hợp tác xã vận tải và câu lạc bộ thể thao biển uy tín nhất vịnh Đà Nẵng, áp dụng tiêu chuẩn an toàn cứu sinh SOLAS, thanh toán bảo chứng Escrow và đào tạo mạng lưới hướng dẫn viên bản địa 1:1 chuyên nghiệp.
        </p>
      </section>

      <section className="space-y-3">
        <h2 className="text-base font-bold text-slate-900 uppercase">2. Hồ Sơ Pháp Nhân &amp; Giấy Phép Hoạt Động</h2>
        <p>
          <strong>Tên doanh nghiệp:</strong> CÔNG TY CỔ PHẦN CÔNG NGHỆ VÀ DU LỊCH BIỂN DANASEA<br />
          <strong>Tên giao dịch quốc tế:</strong> DANASEA OCEAN TECHNOLOGY AND TOURISM JOINT STOCK COMPANY<br />
          <strong>Mã số doanh nghiệp / Mã số thuế:</strong> 0402188468 do Sở Kế hoạch và Đầu tư TP. Đà Nẵng cấp.<br />
          <strong>Vốn điều lệ:</strong> 20.000.000.000 VNĐ (Hai mươi tỷ đồng).<br />
          <strong>Giấy phép kinh doanh lữ hành nội địa:</strong> Số 48-0024/2026/SDL-GP LHND do Sở Du lịch Thành phố Đà Nẵng cấp.<br />
          <strong>Trụ sở chính:</strong> Số 48 Đường Hoàng Sa, Phường Mân Thái, Quận Sơn Trà, TP. Đà Nẵng.<br />
          <strong>Trụ sở điều hành &amp; Bến bãi:</strong> Âu thuyền Thọ Quang &amp; Bãi biển Mân Thái, Đường Hoàng Sa, Phường Mân Thái, Quận Sơn Trà, TP. Đà Nẵng.<br />
          <strong>Tổng đài hỗ trợ &amp; Cứu hộ hàng hải 24/7:</strong> 1900 8468 • <strong>Thư điện tử:</strong> hotro@danasea.vn.
        </p>
      </section>

      <section className="space-y-3">
        <h2 className="text-base font-bold text-slate-900 uppercase">3. Bảo Hiểm Du Lịch Trách Nhiệm Dân Sự</h2>
        <p>
          Toàn bộ du khách đặt dịch vụ qua hệ thống DANASEA đều được bảo hiểm du lịch trách nhiệm dân sự theo hợp đồng nguyên tắc ký kết cùng Tổng Công ty Bảo hiểm Bảo Việt, với hạn mức bảo hiểm tối đa <strong>100.000.000 VNĐ / người / vụ</strong>, chi trả toàn bộ chi phí y tế và cứu hộ phát sinh trong suốt hành trình trên biển.
        </p>
      </section>

      <section className="space-y-3">
        <h2 className="text-base font-bold text-slate-900 uppercase">4. Chương Trình Đào Tạo Hướng Dẫn Viên Bản Địa 1:1</h2>
        <p>
          DANASEA hợp tác chặt chẽ cùng Trường Cao đẳng Du lịch Đà Nẵng và Trung tâm Phối hợp Tìm kiếm Cứu nạn Hàng hải Khu vực II mở các khóa đào tạo chuẩn hóa kỹ năng cứu sinh và cấp chứng chỉ cho hơn 100 ngư dân bản địa, chuyển đổi sinh kế bền vững sang làm hướng dẫn viên thể thao nước 1:1 an toàn.
        </p>
      </section>
    </div>
  );
}
