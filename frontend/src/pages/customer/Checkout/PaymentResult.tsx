import { useNavigate } from "react-router-dom";
import { useState } from "react";
import { createPortal } from "react-dom";

export function PaymentResult() {
  const navigate = useNavigate();
  const [scenario, setScenario] = useState<'success' | 'pending' | 'failed'>('success');
  const [copied, setCopied] = useState(false);
  const [isDisputeModalOpen, setIsDisputeModalOpen] = useState(false);
  const [disputeBank, setDisputeBank] = useState("Vietcombank");
  const [disputeAccount, setDisputeAccount] = useState("");
  const [disputeNote, setDisputeNote] = useState("");
  const [disputeSubmitted, setDisputeSubmitted] = useState(false);

  const handleCopy = () => {
    navigator.clipboard.writeText('DNS-89412');
    setCopied(true);
    setTimeout(() => setCopied(false), 2500);
  };

  return (
    <>
      <main className="w-full pt-20 bg-surface flex-1"><div className="flex flex-col w-full">
{/* Sub-Header Progress Stepper Bar (Synchronized with Step 1 & 2) */}
<section className="w-full bg-surface-container-lowest/80 backdrop-blur-md shadow-sm border-b border-slate-100">
  <div className="max-w-[1280px] mx-auto px-margin-mobile md:px-margin-desktop py-space-md">
    <div className="flex items-center justify-between gap-space-md flex-wrap mb-space-sm">
      <div className="flex items-center gap-space-xs font-label-md text-xs text-slate-500">
        <span className="hover:text-cyan-700 cursor-pointer transition-colors" onClick={() => navigate('/')}>DANASEA</span>
        <span className="material-symbols-outlined text-[14px]">chevron_right</span>
        <span className="hover:text-cyan-700 cursor-pointer transition-colors" onClick={() => navigate('/cart')}>Giỏ trải nghiệm biển</span>
        <span className="material-symbols-outlined text-[14px]">chevron_right</span>
        <span className="text-cyan-700 font-bold">Nhận vé điện tử QR</span>
      </div>
    </div>

    <div className="grid grid-cols-1 md:grid-cols-3 gap-space-sm items-center">
      <div className="flex items-center gap-space-sm rounded-xl bg-slate-50 border border-slate-100 p-2">
        <div className="w-8 h-8 rounded-full bg-emerald-600 text-white flex items-center justify-center flex-shrink-0 font-bold shadow-sm">
          <span className="material-symbols-outlined text-[18px]">check</span>
        </div>
        <div className="min-w-0">
          <p className="font-label-sm text-[11px] text-slate-400 uppercase tracking-wider">Bước 1</p>
          <p className="font-headline-sm text-xs font-bold text-slate-800 truncate">Giỏ trải nghiệm</p>
        </div>
      </div>

      <div className="flex items-center gap-space-sm rounded-xl bg-slate-50 border border-slate-100 p-2">
        <div className="w-8 h-8 rounded-full bg-emerald-600 text-white flex items-center justify-center flex-shrink-0 font-bold shadow-sm">
          <span className="material-symbols-outlined text-[18px]">check</span>
        </div>
        <div className="min-w-0">
          <p className="font-label-sm text-[11px] text-slate-400 uppercase tracking-wider">Bước 2</p>
          <p className="font-headline-sm text-xs font-bold text-slate-800 truncate">Thông tin &amp; Thanh toán</p>
        </div>
      </div>

      <div className="flex items-center gap-space-sm rounded-xl bg-cyan-50/80 border border-cyan-200 shadow-sm p-2">
        <div className="w-8 h-8 rounded-full bg-cyan-600 text-white flex items-center justify-center flex-shrink-0 font-bold shadow-sm">
          <span>3</span>
        </div>
        <div className="min-w-0">
          <p className="font-label-sm text-[11px] text-cyan-600 font-bold uppercase tracking-wider">Bước 3 • Hoàn tất</p>
          <p className="font-headline-sm text-xs font-bold text-cyan-900 truncate">Nhận vé điện tử QR</p>
        </div>
      </div>
    </div>
  </div>
</section>
{/* Main Content Body */}
<div className="max-w-[1280px] mx-auto px-margin-mobile md:px-margin-desktop py-space-xl sm:py-space-2xl w-full">
{/* Simulation Scenario Pills */}
<div className="flex items-center justify-between flex-wrap gap-space-sm mb-space-xl p-space-sm bg-surface-container rounded-lg">
<div className="flex items-center gap-space-xs text-on-surface-variant font-label-sm text-label-sm">
<span className="material-symbols-outlined text-[18px] text-tertiary">tune</span>
<span className="">Mô phỏng kịch bản hệ thống:</span>
</div>
<div className="flex items-center gap-space-xs overflow-x-auto pb-1 sm:pb-0">
<button className={`px-space-md py-1.5 rounded-full font-label-sm text-label-sm shadow-sm transition-all flex items-center gap-1 ${scenario === 'success' ? 'bg-secondary text-on-secondary font-bold' : 'bg-surface-container-lowest text-on-surface-variant hover:text-on-surface'}`} onClick={() => setScenario('success')}>
<span className="material-symbols-outlined text-[14px]">check_circle</span>
          Thành công (Hiện tại)
        </button>
<button className={`px-space-md py-1.5 rounded-full font-label-sm text-label-sm shadow-sm transition-all flex items-center gap-1 ${scenario === 'pending' ? 'bg-secondary text-on-secondary font-bold' : 'bg-surface-container-lowest text-on-surface-variant hover:text-on-surface'}`} onClick={() => setScenario('pending')}>
<span className="material-symbols-outlined text-[14px]">timelapse</span>
          Đang xác nhận ngân hàng
        </button>
<button className={`px-space-md py-1.5 rounded-full font-label-sm text-label-sm shadow-sm transition-all flex items-center gap-1 ${scenario === 'failed' ? 'bg-secondary text-on-secondary font-bold' : 'bg-surface-container-lowest text-on-surface-variant hover:text-on-surface'}`} onClick={() => setScenario('failed')}>
<span className="material-symbols-outlined text-[14px]">error_outline</span>
          Thanh toán lỗi / Hết chỗ
        </button>
</div>
</div>
{/* STATE: PENDING NOTIFICATION */}
{scenario === 'pending' && (
<div className="mb-space-2xl p-space-lg rounded-lg bg-surface-container-high shadow-md animate-fade-in-up" id="state-pending-box">
<div className="flex items-start gap-space-md">
<div className="w-12 h-12 rounded-full bg-tertiary-fixed flex items-center justify-center flex-shrink-0 text-on-tertiary-fixed">
<span className="material-symbols-outlined text-[28px] animate-spin">refresh</span>
</div>
<div className="flex-1">
<span className="inline-block px-space-sm py-0.5 rounded-full bg-tertiary-fixed-dim text-on-tertiary-fixed-variant font-label-sm text-label-sm mb-1">Đang đồng bộ giao dịch</span>
<h2 className="font-headline-sm text-headline-sm text-on-surface">Đang xử lý kết nối từ cổng thanh toán...</h2>
<p className="font-body-md text-body-md text-on-surface-variant mt-1">Giao dịch của bạn đã ghi nhận mã đối soát <strong>#DNS-84291</strong>. Hệ thống tự động cập nhật ngay khi ngân hàng phản hồi mã ủy quyền (khoảng 30-60 giây). Vui lòng không đóng tab.</p>
<div className="mt-space-sm flex items-center gap-space-sm">
<button onClick={() => setScenario('success')} className="px-space-md py-1 rounded-full bg-secondary text-on-secondary font-label-sm text-label-sm">Làm mới trạng thái</button>
<span className="font-body-sm text-body-sm text-outline">Thời gian giữ chỗ còn lại: 14 phút 20 giây</span>
</div>
</div>
</div>
</div>
)}
{/* STATE: FAILED NOTIFICATION */}
{scenario === 'failed' && (
<div className="mb-space-2xl p-space-lg rounded-lg bg-surface-container-highest shadow-md animate-fade-in-up" id="state-failed-box">
<div className="flex items-start gap-space-md">
<div className="w-12 h-12 rounded-full bg-error text-on-error flex items-center justify-center flex-shrink-0 shadow-lg">
<span className="material-symbols-outlined text-[28px]">credit_card_off</span>
</div>
<div className="flex-1">
<div className="flex items-center gap-space-xs">
<span className="px-space-sm py-0.5 rounded-full bg-error-container text-on-error-container font-label-sm text-label-sm">Thanh toán chưa hoàn tất</span>
<span className="font-label-sm text-label-sm text-outline">Mã lỗi: GATEWAY_TIMEOUT_402</span>
</div>
<h2 className="font-headline-sm text-headline-sm text-on-surface mt-1">Rất tiếc! Đã hết thời gian giữ suất trải nghiệm</h2>
<p className="font-body-md text-body-md text-on-surface-variant mt-1">Giao dịch bị gián đoạn do mã OTP ngân hàng quá hạn. Nếu tài khoản đã trừ tiền, hệ thống DANASEA cam kết tự động đối soát và hoàn tiền 100% trong 24 giờ làm việc.</p>
<div className="mt-space-md flex flex-wrap items-center gap-space-sm">
<button onClick={() => navigate('/checkout')} className="px-space-lg py-space-xs rounded-full bg-primary text-on-primary font-label-md text-label-md shadow-md">Thử lại giao dịch</button>
<button onClick={() => setIsDisputeModalOpen(true)} className="px-space-md py-space-xs rounded-full bg-surface text-on-surface font-label-md text-label-md hover:bg-surface-container cursor-pointer transition-colors">Gửi yêu cầu hoàn tiền tự động</button>
<span className="font-body-sm text-body-sm text-secondary font-semibold flex items-center gap-1"><span className="material-symbols-outlined text-[16px]">call</span>Hotline xử lý gấp: 1900 8468</span>
</div>
</div>
</div>
</div>
)}
{/* STATE: SUCCESS CONFIRMATION HERO */}
<div className={`flex flex-col items-center text-center mb-space-2xl ${scenario === 'failed' ? 'opacity-30 pointer-events-none' : ''}`} id="state-success-box">
<div className="relative mb-space-md">
<div className="w-20 h-20 rounded-full bg-secondary-container flex items-center justify-center shadow-lg">
<span className="material-symbols-outlined text-on-secondary-container text-[44px]">verified</span>
</div>
<div className="absolute -bottom-1 -right-1 w-7 h-7 rounded-full bg-primary-container text-on-primary flex items-center justify-center shadow-md">
<span className="material-symbols-outlined text-[18px]">waves</span>
</div>
</div>
<div className="flex items-center gap-space-xs mb-space-2xs">
<span className="px-space-md py-1 rounded-full bg-secondary/10 text-secondary font-label-sm text-label-sm font-bold tracking-wider uppercase">Thanh toán hoàn tất</span>
<span className="w-1.5 h-1.5 rounded-full bg-outline"></span>
<span className="font-label-md text-label-md text-on-surface-variant">Hóa đơn điện tử hợp lệ</span>
</div>
<h1 className="font-headline-lg text-headline-lg text-on-surface tracking-tight max-w-2xl">
        Thanh toán &amp; Xác nhận chỗ thành công!
      </h1>
{/* Meta order badges */}
<div className="flex flex-wrap items-center justify-center gap-space-sm mt-space-sm"><div className="flex items-center gap-1 px-space-md py-1.5 rounded-full bg-surface-container font-label-md text-label-md text-on-surface"><span className="text-tertiary">Mã đơn hàng:</span><span className="font-bold text-secondary">#DNS-89412</span><button className="hover:text-primary transition-colors ml-1" onClick={handleCopy} title="Sao chép mã đơn"><span className="material-symbols-outlined text-[16px]">{copied ? 'check' : 'content_copy'}</span></button>{copied && <span className="text-[11px] text-emerald-600 font-bold ml-1">Đã sao chép!</span>}</div><div className="flex items-center gap-1 px-space-md py-1.5 rounded-full bg-surface-container font-label-md text-label-md text-on-surface-variant"><span className="material-symbols-outlined text-[16px] text-tertiary">schedule</span><span className="">08:35 • 27/10/2024</span></div><div className="flex items-center gap-1 px-space-md py-1.5 rounded-full bg-surface-container font-label-md text-label-md text-on-surface-variant"><span className="material-symbols-outlined text-[16px] text-secondary">account_balance</span><span className="">Đã thanh toán (SEPAY)</span></div></div>
{/* Sent info notice */}
<div className="mt-space-md p-space-sm px-space-lg rounded-full bg-surface-container-low text-on-surface flex items-center gap-space-xs text-center max-w-xl">
<span className="material-symbols-outlined text-secondary text-[20px] flex-shrink-0">mark_email_read</span>
<p className="font-body-sm text-body-sm text-on-surface-variant text-left">
          Vé điện tử QR và tài liệu hướng dẫn an toàn đã gửi qua <strong>Zalo</strong> &amp; Email <span className="text-secondary font-bold underline">an.nguyen@danangtraveler.vn</span>.
        </p>
</div>
</div>
{/* MAIN GRID: TICKETS & REAL-TIME ADVISORY */}
<div className="grid grid-cols-1 lg:grid-cols-12 gap-space-xl items-start">
{/* TICKETS COLUMN (8 cols) */}
<div className="lg:col-span-8 flex flex-col gap-space-xl">
<div className="flex items-center justify-between"><div><span className="font-label-sm text-label-sm text-tertiary uppercase tracking-wider">Danh sách dịch vụ đơn con (Sub Orders)</span><h3 className="font-headline-md text-headline-md text-on-surface">Dịch Vụ Đã Đăng Ký (02 dịch vụ)</h3></div><button className="flex items-center gap-1 px-space-md py-1.5 rounded-full bg-surface-container-low text-secondary hover:bg-surface-container font-label-sm text-label-sm transition-colors"><span className="material-symbols-outlined text-[16px]">file_download</span>Tải tất cả vé (PDF)</button></div>
{/* TICKET 1: SUP SUNRISE MY KHE */}
<article className="bg-surface-container-lowest rounded-xl shadow-lg overflow-hidden flex flex-col transition-all hover:shadow-xl">{/* Header Sub Order 1 */}
<div className="bg-surface-container-low px-space-lg py-space-sm flex flex-wrap items-center justify-between gap-space-xs">
<div className="flex items-center gap-space-xs">
<span className="w-2.5 h-2.5 rounded-full bg-secondary animate-pulse"></span>
<span className="font-label-sm text-label-sm text-secondary uppercase font-bold tracking-wider">CONFIRMED • ĐÃ XÁC NHẬN</span>
<span className="px-space-xs py-0.5 rounded-full bg-surface-container text-on-surface-variant font-label-sm text-[11px]">Chưa check-in</span>
</div>
<div className="flex items-center gap-space-xs font-label-sm text-label-sm text-on-surface-variant">
<span className="">Mã đơn con:</span>
<span className="font-bold text-on-surface">#SUB-89412-01</span>
</div>
</div>
<div className="p-space-lg grid grid-cols-1 md:grid-cols-12 gap-space-lg items-center">
{/* Thông tin dịch vụ */}
<div className="md:col-span-7 flex flex-col gap-space-md">
<div className="flex items-start gap-space-sm">
<div className="w-16 h-16 rounded-lg overflow-hidden flex-shrink-0 bg-surface-container shadow-inner">
<img alt="Chèo SUP đón bình minh Mỹ Khê" className="w-full h-full object-cover" src="https://lh3.googleusercontent.com/aida-public/AB6AXuDjpYftyDUQHzx6oL8seYsJeWLSPERdd3MhLRyhN5EkSGGImN1mqxfafUm7aa0EqtWyI7kPzbB4IU_hmSTBnmIwgJvmFReNwLtkRdsu7tNEJ8zDN-VqMbAsIx4Kk70nOiZPyG29S5pnCnYYdejrXi-t29t3W3zlsLxL4L0sc-w7QfwWeerDFn5AtwIUQwAmSRX_KV7XHYWwRWyBkC7zLGYF_MUtY05wNwFVkd82aKi2BAiPL4G9JRVhdA"/>
</div>
<div>
<span className="px-space-xs py-0.5 rounded-full bg-primary-container/15 text-primary font-label-sm text-[11px] font-bold">Hoạt động thể thao biển</span>
<h4 className="font-headline-sm text-headline-sm text-on-surface mt-0.5">Chèo SUP đón bình minh Mỹ Khê</h4>
<p className="font-body-sm text-body-sm text-on-surface-variant mt-0.5">Nhà cung cấp: <strong className="text-secondary">Danang Ocean Club</strong></p>
</div>
</div>
<div className="grid grid-cols-2 gap-space-sm p-space-sm rounded-lg bg-surface-container-low">
<div>
<span className="font-label-sm text-[11px] text-tertiary block">Thời gian sử dụng:</span>
<span className="font-label-md text-label-md text-on-surface flex items-center gap-1 mt-0.5">
<span className="material-symbols-outlined text-[16px] text-secondary">alarm</span>
05:00 - 07:00
</span>
<span className="font-body-sm text-[12px] text-on-surface-variant">Thứ 2, 28/10/2024</span>
</div>
<div>
<span className="font-label-sm text-[11px] text-tertiary block">Số lượng &amp; Đơn giá:</span>
<span className="font-label-md text-label-md text-on-surface flex items-center gap-1 mt-0.5">
<span className="material-symbols-outlined text-[16px] text-secondary">group</span>
2 vé người lớn
</span>
<span className="font-label-sm text-[11px] text-primary font-bold">280.000 đ × 2 = 560.000 đ</span>
</div>
</div>
<div className="flex items-start gap-space-xs text-on-surface-variant font-body-sm text-body-sm">
<span className="material-symbols-outlined text-secondary text-[18px] flex-shrink-0 mt-0.5">pin_drop</span>
<div className="">
<strong className="text-on-surface">Điểm tập kết:</strong> Bãi tắm Phạm Văn Đồng (Công viên Biển Đông), Q. Sơn Trà, Đà Nẵng.
<span className="block text-primary font-medium text-[12px] mt-0.5">Nhận diện: Trạm điều hành Danang Ocean Club tại Water Hub #02.</span>
</div>
</div>
<div className="flex flex-wrap items-center gap-space-xs pt-space-xs">
<button className="flex items-center gap-1 px-space-sm py-1 rounded-full bg-surface-container text-on-surface hover:bg-surface-container-high font-label-sm text-label-sm transition-colors">
<span className="material-symbols-outlined text-[16px] text-secondary">map</span>
Bản đồ điểm đón
</button>
<button className="flex items-center gap-1 px-space-sm py-1 rounded-full bg-surface-container text-on-surface hover:bg-surface-container-high font-label-sm text-label-sm transition-colors">
<span className="material-symbols-outlined text-[16px] text-tertiary">download</span>
Tải vé PDF
</button>
<button className="flex items-center gap-1 px-space-sm py-1 rounded-full bg-surface-container text-on-surface hover:bg-surface-container-high font-label-sm text-label-sm transition-colors">
<span className="material-symbols-outlined text-[16px] text-secondary">wallet</span>
Thêm Apple Wallet
</button>
</div>
</div>
{/* Mã QR Check-in bảo mật */}
<div className="md:col-span-5 flex flex-col items-center justify-center p-space-md rounded-xl bg-surface-container-low text-center">
<div className="p-space-sm bg-surface-container-lowest rounded-xl shadow-md flex flex-col items-center">
<svg className="w-36 h-36 text-on-surface" fill="currentColor" viewBox="0 0 100 100">
<rect fill="none" height="24" rx="4" stroke="currentColor" strokeWidth="4" width="24" x="10" y="10"></rect>
<rect fill="currentColor" height="12" rx="2" width="12" x="16" y="16"></rect>
<rect fill="none" height="24" rx="4" stroke="currentColor" strokeWidth="4" width="24" x="66" y="10"></rect>
<rect fill="currentColor" height="12" rx="2" width="12" x="72" y="16"></rect>
<rect fill="none" height="24" rx="4" stroke="currentColor" strokeWidth="4" width="24" x="10" y="66"></rect>
<rect fill="currentColor" height="12" rx="2" width="12" x="16" y="72"></rect>
<rect height="6" rx="1" width="6" x="42" y="12"></rect>
<rect height="6" rx="1" width="6" x="52" y="12"></rect>
<rect height="10" rx="1" width="6" x="42" y="24"></rect>
<rect height="6" rx="1" width="8" x="12" y="42"></rect>
<rect height="6" rx="1" width="12" x="24" y="42"></rect>
<rect fill="#006874" height="16" rx="2" width="16" x="42" y="42"></rect>
<rect height="6" rx="1" width="10" x="64" y="42"></rect>
<rect height="6" rx="1" width="12" x="78" y="42"></rect>
<rect height="12" rx="1" width="6" x="42" y="64"></rect>
<rect height="6" rx="1" width="12" x="52" y="70"></rect>
<rect height="6" rx="1" width="20" x="70" y="66"></rect>
<rect height="12" rx="1" width="8" x="70" y="78"></rect>
<rect height="12" rx="1" width="8" x="82" y="78"></rect>
</svg>
<div className="mt-2 text-center">
<span className="font-headline-sm text-headline-sm tracking-wider text-secondary">#SUB-89412-01</span>
<span className="block font-label-sm text-[10px] text-tertiary">MÃ VÉ ĐIỆN TỬ CHECK-IN</span>
</div>
</div>
<p className="font-body-sm text-[12px] text-on-surface-variant mt-space-xs max-w-[200px]">Xuất trình mã QR tại điểm đón để nhân viên đối soát vào cổng.</p>
</div>
</div></article>
{/* TICKET 2: SNORKELING SON TRA */}
<article className="bg-surface-container-lowest rounded-xl shadow-lg overflow-hidden flex flex-col transition-all hover:shadow-xl">{/* Header Sub Order 2 */}
<div className="bg-surface-container-low px-space-lg py-space-sm flex flex-wrap items-center justify-between gap-space-xs">
<div className="flex items-center gap-space-xs">
<span className="w-2.5 h-2.5 rounded-full bg-tertiary"></span>
<span className="font-label-sm text-label-sm text-tertiary uppercase font-bold tracking-wider">PENDING • ĐANG CHỜ XÁC NHẬN</span>
</div>
<div className="flex items-center gap-space-xs font-label-sm text-label-sm text-on-surface-variant">
<span className="">Mã đơn con:</span>
<span className="font-bold text-on-surface">#SUB-89412-02</span>
</div>
</div>
<div className="p-space-lg grid grid-cols-1 md:grid-cols-12 gap-space-lg items-center">
{/* Thông tin chi tiết dịch vụ */}
<div className="md:col-span-7 flex flex-col gap-space-md">
<div className="flex items-start gap-space-sm">
<div className="w-16 h-16 rounded-lg overflow-hidden flex-shrink-0 bg-surface-container shadow-inner">
<img alt="Lặn biển ngắm rạn san hô Bãi Bụt" className="w-full h-full object-cover" src="https://lh3.googleusercontent.com/aida-public/AB6AXuBfgeVL6Vx2d90HAtHOa8oYDyRArqJUbzmmZ9MtsiRcqWM6lAQreDI1n7Q9PiNqboULdkP9xDfkZk2iA3xuRjiFys38yqRb947PPE2_nDzmJbpl5cAQ-PldkcX1uB_UtWnzB986-PoV4vhOI1LyvlmW4Ay0cS2BvejZtfg6QByIRgjotHOPjrS6fjHRnaTltcr4uW6j9xiBMTtS_ERp4LFWAWuALOnDQ8_mbaG78K1MBRebf3yZDbc8QQ"/>
</div>
<div>
<span className="px-space-xs py-0.5 rounded-full bg-secondary-container text-on-secondary-container font-label-sm text-[11px] font-bold">Khám phá sinh thái biển</span>
<h4 className="font-headline-sm text-headline-sm text-on-surface mt-0.5">Lặn biển ngắm rạn san hô Bãi Bụt</h4>
<p className="font-body-sm text-body-sm text-on-surface-variant mt-0.5">Nhà cung cấp: <strong className="text-secondary">Son Tra Marine Diving</strong></p>
</div>
</div>
<div className="grid grid-cols-2 gap-space-sm p-space-sm rounded-lg bg-surface-container-low">
<div>
<span className="font-label-sm text-[11px] text-tertiary block">Thời gian dự kiến:</span>
<span className="font-label-md text-label-md text-on-surface flex items-center gap-1 mt-0.5">
<span className="material-symbols-outlined text-[16px] text-secondary">alarm</span>
08:30 - 11:00
</span>
<span className="font-body-sm text-[12px] text-on-surface-variant">Thứ 3, 29/10/2024</span>
</div>
<div>
<span className="font-label-sm text-[11px] text-tertiary block">Số lượng &amp; Đơn giá:</span>
<span className="font-label-md text-label-md text-on-surface flex items-center gap-1 mt-0.5">
<span className="material-symbols-outlined text-[16px] text-secondary">group</span>
1 vé người lớn
</span>
<span className="font-label-sm text-[11px] text-primary font-bold">520.000 đ × 1 = 520.000 đ</span>
</div>
</div>
<div className="flex items-start gap-space-xs text-on-surface-variant font-body-sm text-body-sm">
<span className="material-symbols-outlined text-secondary text-[18px] flex-shrink-0 mt-0.5">directions_boat</span>
<div className="">
<strong className="text-on-surface">Điểm đón &amp; xuất phát:</strong> Bến cano Bãi Bụt, Bán đảo Sơn Trà, TP. Đà Nẵng.
<span className="block text-tertiary font-medium text-[12px] mt-0.5">Đội ngũ Son Tra Marine Diving tiếp đón trực tiếp tại khu vực cầu tàu.</span>
</div>
</div>
<div className="flex flex-wrap items-center gap-space-xs pt-space-xs">
<button className="flex items-center gap-1 px-space-sm py-1 rounded-full bg-surface-container text-on-surface hover:bg-surface-container-high font-label-sm text-label-sm transition-colors">
<span className="material-symbols-outlined text-[16px] text-secondary">pin_drop</span>
Vị trí điểm đón
</button>
<button className="flex items-center gap-1 px-space-sm py-1 rounded-full bg-surface-container text-on-surface hover:bg-surface-container-high font-label-sm text-label-sm transition-colors">
<span className="material-symbols-outlined text-[16px] text-tertiary">call</span>
Liên hệ NCC
</button>
</div>
</div>
{/* Trạng thái chờ phê duyệt từ NCC */}
<div className="md:col-span-5 flex flex-col items-center justify-center p-space-md rounded-xl bg-surface-container-low text-center min-h-[220px]">
<div className="p-space-lg bg-surface-container-lowest rounded-xl shadow-md flex flex-col items-center max-w-[240px]">
<div className="w-12 h-12 rounded-full bg-surface-container flex items-center justify-center text-tertiary mb-2">
<span className="material-symbols-outlined text-[28px] animate-spin">sync</span>
</div>
<span className="font-label-md text-label-md text-on-surface font-bold">Đang chờ nhà cung cấp xác nhận</span>
<p className="font-body-sm text-[12px] text-on-surface-variant mt-1.5 leading-relaxed">Son Tra Marine Diving đang rà soát ca thuyền lặn. Mã QR vé sẽ kích hoạt tự động ngay khi được duyệt (tối đa 15 phút).</p>
</div>
</div>
</div></article>
{/* Weather & Ocean Safety Advisory Widget (Inline) */}
<div className="p-space-lg rounded-xl bg-surface-container-low shadow-md">
<div className="flex items-center justify-between flex-wrap gap-space-sm mb-space-sm">
<div className="flex items-center gap-space-xs">
<span className="material-symbols-outlined text-secondary text-[24px]">airwave</span>
<h4 className="font-headline-sm text-headline-sm text-on-surface">Dự báo điều kiện biển Đà Nẵng (24 giờ tới)</h4>
</div>
<span className="px-space-sm py-0.5 rounded-full bg-secondary text-on-secondary font-label-sm text-label-sm font-bold">Rất lý tưởng cho SUP &amp; Lặn</span>
</div>
<div className="grid grid-cols-2 sm:grid-cols-4 gap-space-sm mt-space-sm">
<div className="p-space-sm rounded-lg bg-surface-container-lowest flex flex-col">
<span className="font-label-sm text-[11px] text-tertiary">Độ cao sóng (Swell)</span>
<span className="font-headline-sm text-headline-sm text-secondary mt-1">0.4 - 0.6 m</span>
<span className="font-body-sm text-[11px] text-on-surface-variant">Sóng êm, nước trong</span>
</div>
<div className="p-space-sm rounded-lg bg-surface-container-lowest flex flex-col">
<span className="font-label-sm text-[11px] text-tertiary">Thủy triều (Tide)</span>
<span className="font-headline-sm text-headline-sm text-on-surface mt-1">Cực đại 06:15</span>
<span className="font-body-sm text-[11px] text-on-surface-variant">+1.2m mực nước tĩnh</span>
</div>
<div className="p-space-sm rounded-lg bg-surface-container-lowest flex flex-col">
<span className="font-label-sm text-[11px] text-tertiary">Tầm nhìn dưới nước</span>
<span className="font-headline-sm text-headline-sm text-secondary mt-1">&gt; 12 mét</span>
<span className="font-body-sm text-[11px] text-on-surface-variant">Rạn san hô rõ nét</span>
</div>
<div className="p-space-sm rounded-lg bg-surface-container-lowest flex flex-col">
<span className="font-label-sm text-[11px] text-tertiary">Gió biển &amp; Nhiệt độ</span>
<span className="font-headline-sm text-headline-sm text-on-surface mt-1">6 knot • 26°C</span>
<span className="font-body-sm text-[11px] text-on-surface-variant">Nắng dịu sáng sớm</span>
</div>
</div>
<div className="mt-space-md p-space-sm rounded-lg bg-surface-container flex items-center justify-between flex-wrap gap-space-sm">
<div className="flex items-center gap-space-xs text-on-surface font-body-sm text-body-sm">
<span className="material-symbols-outlined text-primary text-[20px]">health_and_safety</span>
<span className=""><strong>Cam kết thời tiết Danasea:</strong> Nếu mưa giông hoặc biển động bất thường, bạn được đổi ngày miễn phí hoặc hoàn tiền 100% trong 2h.</span>
</div>
<a className="flex items-center gap-1 font-label-md text-label-md text-secondary hover:underline" href="tel:19008468">
<span className="material-symbols-outlined text-[16px]">support_agent</span>
              Hotline hỗ trợ: 1900 8468
            </a>
</div>
</div>
</div>
{/* SIDEBAR SUMMARY & ACTIONS (4 cols) */}
<div className="lg:col-span-4 flex flex-col gap-space-xl">
{/* Order Financial Summary Card */}
<div className="bg-surface-container-lowest p-space-lg rounded-xl shadow-lg flex flex-col gap-space-md"><div className="flex items-center justify-between pb-space-xs"><span className="font-headline-sm text-headline-sm text-on-surface">Chi tiết thanh toán</span><span className="px-space-xs py-0.5 rounded-full bg-secondary-container text-on-secondary-container font-label-sm text-[11px] font-bold">Đã thanh toán</span></div><div className="flex flex-col gap-space-xs font-body-sm text-body-sm"><div className="flex justify-between text-on-surface-variant"><span className="">#SUB-89412-01: SUP Mỹ Khê (2 vé × 280k):</span><span className="font-bold text-on-surface">560.000 đ</span></div><div className="flex justify-between text-on-surface-variant"><span className="">#SUB-89412-02: Lặn Bãi Bụt (1 vé × 520k):</span><span className="font-bold text-on-surface">520.000 đ</span></div><div className="flex justify-between text-primary font-body-sm"><span className="">Voucher khuyến mại DANASEA:</span><span className="">- 50.000 đ</span></div></div><div className="h-0.5 w-full bg-surface-container-high rounded-full my-space-2xs"></div><div className="flex items-baseline justify-between"><div><span className="font-label-sm text-label-sm text-tertiary block">Tổng tiền thanh toán:</span><span className="font-body-sm text-[12px] text-on-surface-variant">Cổng SEPAY • Đã bao gồm thuế GTGT</span></div><span className="font-headline-lg text-headline-lg text-primary font-bold">1.030.000 đ</span></div><div className="p-space-xs px-space-sm rounded-lg bg-surface-container-low flex items-center gap-space-xs text-on-surface-variant font-label-sm text-label-sm"><span className="material-symbols-outlined text-[16px] text-secondary">verified_user</span><span className="">Đã khớp lệnh thành công qua SEPAY</span></div></div>
{/* Navigation Buttons Card */}
<div className="bg-surface-container-lowest p-space-lg rounded-xl shadow-lg flex flex-col gap-space-sm">
<span className="font-label-sm text-label-sm text-tertiary uppercase tracking-wider">Hành động tiếp theo</span>
<a className="w-full py-space-sm px-space-md rounded-full bg-primary-container text-on-primary font-label-lg text-label-lg text-center shadow-md hover:scale-[1.01] transition-transform flex items-center justify-center gap-space-xs" href="/">
<span className="material-symbols-outlined text-[20px]">assignment</span>
            Xem &amp; quản lý đơn hàng của tôi
          </a>
<a className="w-full py-space-sm px-space-md rounded-full bg-surface-container text-on-surface hover:bg-surface-container-high font-label-lg text-label-lg text-center transition-colors flex items-center justify-center gap-space-xs" href="/">
<span className="material-symbols-outlined text-[20px] text-secondary">explore</span>
            Tiếp tục khám phá trải nghiệm khác
          </a>
<button className="w-full py-space-sm px-space-md rounded-full bg-surface-container-lowest text-on-surface-variant hover:text-on-surface font-label-sm text-label-sm text-center transition-colors flex items-center justify-center gap-space-xs border border-surface-container"><span className="material-symbols-outlined text-[16px] text-secondary">receipt_long</span><span className="">Tải hóa đơn VAT (PDF)</span><span className="px-space-xs py-0.5 rounded-full bg-surface-container text-tertiary font-label-sm text-[10px]">Đang chuẩn bị phát hành</span></button>
</div>
{/* Preparation Checklist Card */}
<div className="bg-surface-container-lowest p-space-lg rounded-xl shadow-lg flex flex-col gap-space-sm">
<div className="flex items-center gap-space-xs text-on-surface">
<span className="material-symbols-outlined text-secondary text-[22px]">backpack</span>
<h4 className="font-headline-sm text-headline-sm">Cần chuẩn bị khi tham gia</h4>
</div>
<ul className="flex flex-col gap-space-xs font-body-sm text-body-sm text-on-surface-variant">
<li className="flex items-start gap-2">
<span className="material-symbols-outlined text-secondary text-[16px] mt-0.5">check</span>
<span className="">Đến trước giờ khởi hành tối thiểu <strong>15 phút</strong> để làm thủ tục áo phao.</span>
</li>
<li className="flex items-start gap-2">
<span className="material-symbols-outlined text-secondary text-[16px] mt-0.5">check</span>
<span className="">Trang phục bơi co giãn, đồ khô thay thế và khăn bông cá nhân.</span>
</li>
<li className="flex items-start gap-2">
<span className="material-symbols-outlined text-secondary text-[16px] mt-0.5">check</span>
<span className="">Kem chống nắng an toàn với rạn san hô (Reef-safe Sunscreen).</span>
</li>
<li className="flex items-start gap-2">
<span className="material-symbols-outlined text-secondary text-[16px] mt-0.5">check</span>
<span className="">Túi chống nước điện thoại (Danasea có tặng miễn phí 1 chiếc tại lều).</span>
</li>
</ul>
<div className="mt-space-xs pt-space-xs border-t border-surface-container flex items-center justify-between text-[12px] text-on-surface-variant"><span className="">Cần hỗ trợ về đơn hàng?</span><a className="text-primary font-bold hover:underline flex items-center gap-0.5" href="tel:19008468"><span className="material-symbols-outlined text-[14px]">call</span>Tổng đài DANASEA: 1900 8468</a></div>
</div>
{/* AI Journey Assistant Card */}
<div className="bg-gradient-to-br from-surface-container-low via-surface-container to-secondary-container/20 p-space-lg rounded-xl shadow-md flex flex-col gap-space-xs">
<div className="flex items-center gap-space-xs text-secondary">
<span className="material-symbols-outlined text-[20px]">smart_toy</span>
<span className="font-label-sm text-label-sm font-bold uppercase tracking-wide">DANASEA AI TRIP CONCIERGE</span>
</div>
<h5 className="font-headline-sm text-headline-sm text-on-surface">Lên lịch ăn sáng sau buổi SUP?</h5>
<p className="font-body-sm text-body-sm text-on-surface-variant">
            Trợ lý AI đã tìm sẵn 3 quán mì Quảng &amp; bún chả cá ngon chuẩn vị gần bãi tắm Mỹ Khê mở cửa từ 6h sáng.
          </p>
<button className="mt-space-xs px-space-md py-1.5 rounded-full bg-secondary text-on-secondary font-label-sm text-label-sm self-start shadow-sm hover:opacity-90 transition-opacity">
            Xem gợi ý ẩm thực quanh điểm đến
          </button>
</div>
</div>
</div>
</div>
</div>
</main>

      {/* Portaled Dispute / Refund Request Modal */}
      {isDisputeModalOpen &&
        createPortal(
          <div className="fixed inset-0 z-[9999] flex items-center justify-center bg-black/70 backdrop-blur-sm p-4 animate-scale-in">
            <div className="bg-surface-container-lowest border border-outline-variant/30 rounded-3xl p-6 max-w-md w-full shadow-2xl relative">
              <button
                onClick={() => {
                  setIsDisputeModalOpen(false);
                  setDisputeSubmitted(false);
                }}
                className="absolute top-4 right-4 p-1.5 rounded-full text-on-surface-variant hover:bg-surface-container cursor-pointer transition-colors"
              >
                <span className="material-symbols-outlined">close</span>
              </button>

              {disputeSubmitted ? (
                <div className="text-center py-6">
                  <div className="w-14 h-14 rounded-full bg-secondary-container text-on-secondary-container flex items-center justify-center mx-auto mb-3">
                    <span className="material-symbols-outlined text-[32px]">check_circle</span>
                  </div>
                  <h3 className="font-headline-sm text-headline-sm text-on-surface font-bold">
                    Đã tiếp nhận yêu cầu hoàn tiền!
                  </h3>
                  <p className="font-body-sm text-body-sm text-on-surface-variant mt-2 max-w-sm mx-auto">
                    Mã đối soát <strong className="text-secondary">#DISP-89412</strong>. Đội ngũ Kế toán DANASEA sẽ xử lý và tiền hoàn sẽ về tài khoản của bạn trong 24 giờ.
                  </p>
                  <button
                    onClick={() => {
                      setIsDisputeModalOpen(false);
                      setDisputeSubmitted(false);
                    }}
                    className="mt-6 px-6 py-2.5 rounded-full bg-secondary text-on-secondary font-label-md font-semibold hover:opacity-90 transition-opacity cursor-pointer"
                  >
                    Xác nhận hoàn tất
                  </button>
                </div>
              ) : (
                <form
                  onSubmit={(e) => {
                    e.preventDefault();
                    setDisputeSubmitted(true);
                  }}
                  className="space-y-4"
                >
                  <div className="flex items-center gap-3 mb-2">
                    <div className="w-10 h-10 rounded-xl bg-error/10 text-error flex items-center justify-center">
                      <span className="material-symbols-outlined text-[24px]">currency_exchange</span>
                    </div>
                    <div>
                      <h3 className="font-headline-sm text-headline-sm text-on-surface font-bold">
                        Yêu cầu hoàn tiền tự động
                      </h3>
                      <p className="font-label-sm text-label-sm text-on-surface-variant">
                        Đơn hàng #DNS-89412
                      </p>
                    </div>
                  </div>

                  <div>
                    <label className="font-label-md text-label-md text-on-surface font-semibold block mb-1">
                      Ngân hàng nhận tiền
                    </label>
                    <select
                      value={disputeBank}
                      onChange={(e) => setDisputeBank(e.target.value)}
                      className="w-full px-3 py-2 rounded-xl bg-surface-container-low border border-outline-variant/30 font-body-md"
                    >
                      <option value="Vietcombank">Vietcombank (Ngoại thương)</option>
                      <option value="Techcombank">Techcombank (Kỹ thương)</option>
                      <option value="MBBank">MBBank (Quân đội)</option>
                      <option value="VietinBank">VietinBank (Công thương)</option>
                      <option value="BIDV">BIDV (Đầu tư & Phát triển)</option>
                    </select>
                  </div>

                  <div>
                    <label className="font-label-md text-label-md text-on-surface font-semibold block mb-1">
                      Số tài khoản ngân hàng
                    </label>
                    <input
                      type="text"
                      required
                      placeholder="Nhập số tài khoản chính chủ"
                      value={disputeAccount}
                      onChange={(e) => setDisputeAccount(e.target.value)}
                      className="w-full px-3 py-2 rounded-xl bg-surface-container-low border border-outline-variant/30 font-body-md"
                    />
                  </div>

                  <div>
                    <label className="font-label-md text-label-md text-on-surface font-semibold block mb-1">
                      Ghi chú / Mã tham chiếu giao dịch (nếu có)
                    </label>
                    <textarea
                      rows={2}
                      value={disputeNote}
                      onChange={(e) => setDisputeNote(e.target.value)}
                      placeholder="Ví dụ: Đã bị trừ 1.250.000đ từ app ngân hàng lúc 08:32"
                      className="w-full px-3 py-2 rounded-xl bg-surface-container-low border border-outline-variant/30 font-body-md resize-none"
                    ></textarea>
                  </div>

                  <div className="pt-2 flex items-center justify-end gap-3">
                    <button
                      type="button"
                      onClick={() => setIsDisputeModalOpen(false)}
                      className="px-4 py-2 rounded-xl bg-surface-container text-on-surface font-label-md cursor-pointer hover:bg-surface-container-high transition-colors"
                    >
                      Hủy bỏ
                    </button>
                    <button
                      type="submit"
                      className="px-5 py-2 rounded-xl bg-primary text-on-primary font-label-md font-semibold cursor-pointer hover:opacity-90 transition-opacity shadow-md"
                    >
                      Gửi yêu cầu hoàn tiền
                    </button>
                  </div>
                </form>
              )}
            </div>
          </div>,
          document.body
        )}
    </>
  );
}
