import React from 'react';
import { Printer, X, ShieldCheck } from 'lucide-react';
import type { SettlementItem } from './Revenue';
import type { VendorProfileDto } from '../../../services/vendorProfileService';

function formatVndWords(n: number): string {
  if (!n || n <= 0) return 'Không đồng';
  const digits = ['không', 'một', 'hai', 'ba', 'bốn', 'năm', 'sáu', 'bảy', 'tám', 'chín'];
  function read3(val: number, needHundred: boolean): string {
    const h = Math.floor(val / 100);
    const t = Math.floor((val % 100) / 10);
    const u = val % 10;
    let s = '';
    if (h > 0 || needHundred) s += digits[h] + ' trăm ';
    if (t > 1) {
      s += digits[t] + ' mươi ';
      if (u === 1) s += 'mốt';
      else if (u === 5) s += 'lăm';
      else if (u > 0) s += digits[u];
    } else if (t === 1) {
      s += 'mười ';
      if (u === 5) s += 'lăm';
      else if (u > 0) s += digits[u];
    } else if (needHundred && t === 0 && u > 0) {
      s += 'lẻ ' + digits[u];
    } else if (u > 0) {
      s += digits[u];
    }
    return s.trim();
  }

  const scales = ['', 'nghìn', 'triệu', 'tỷ'];
  const parts: number[] = [];
  let temp = Math.round(n);
  while (temp > 0) {
    parts.push(temp % 1000);
    temp = Math.floor(temp / 1000);
  }
  let words = '';
  for (let i = parts.length - 1; i >= 0; i--) {
    const val = parts[i];
    if (val > 0) {
      const isTop = i === parts.length - 1;
      const text = read3(val, !isTop);
      words += text + ' ' + scales[i] + ' ';
    }
  }
  words = words.trim() + ' đồng';
  return words.charAt(0).toUpperCase() + words.slice(1);
}

interface SettlementStatementDocumentProps {
  cycle: {
    code: string;
    dateRange: string;
    grossAmount: number;
    commissionAmount: number;
    netAmount: number;
    status: 'PAID' | 'PENDING';
    itemsCount: number;
  };
  items: SettlementItem[];
  vendorProfile: VendorProfileDto | null;
  bankInfo: { bankName?: string; accountNumber?: string };
  isModal?: boolean;
  onClose?: () => void;
  onPrint?: () => void;
}

export const SettlementStatementDocument: React.FC<SettlementStatementDocumentProps> = ({
  cycle,
  items,
  vendorProfile,
  bankInfo,
  isModal = false,
  onClose,
  onPrint,
}) => {
  const today = new Date();
  const dayStr = today.getDate().toString().padStart(2, '0');
  const monthStr = (today.getMonth() + 1).toString().padStart(2, '0');
  const yearStr = today.getFullYear().toString();

  const cleanCycleCode = (cycle.code || 'SET-UNKNOWN').replace('#', '');
  const partnerName = vendorProfile?.businessName || vendorProfile?.brandName || 'Danabeach Club (Điểm dịch vụ thể thao biển)';
  const partnerTaxCode = vendorProfile?.taxCode || 'TEST-DS-001';
  const partnerAddress = vendorProfile?.address || vendorProfile?.businessAddress || '51 Võ Nguyên Giáp, P. Mỹ An, Q. Ngũ Hành Sơn, TP. Đà Nẵng';
  const partnerEmail = vendorProfile?.contactEmail || 'vender_1@danasea.test';
  const partnerBank = bankInfo.bankName || vendorProfile?.bankName || 'VPBank';
  const partnerAccountNum = bankInfo.accountNumber || vendorProfile?.bankAccountNumber || '6969696163';
  const partnerAccountHolder = vendorProfile?.bankAccountHolder || 'DANASEA DEMO VENDOR 01';

  return (
    <div className={isModal ? "p-6 sm:p-10 bg-white max-w-4xl mx-auto text-slate-900 font-sans shadow-lg rounded-xl border border-slate-200" : "bg-white text-slate-900 font-sans p-6 sm:p-10 w-full"}>
      {/* Modal Toolbar (hidden when printing) */}
      {isModal && (
        <div className="flex items-center justify-between pb-6 mb-6 border-b border-slate-200 print:hidden">
          <div className="flex items-center gap-2">
            <span className="w-2 h-2 rounded-full bg-emerald-500"></span>
            <span className="text-xs font-bold text-slate-700 uppercase tracking-wider">Xem trước biên bản đối soát tài chính A4</span>
          </div>
          <div className="flex items-center gap-2.5">
            <button
              type="button"
              onClick={onClose}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-slate-200 text-slate-600 text-xs font-semibold hover:bg-slate-50 transition-colors cursor-pointer"
            >
              <X className="w-4 h-4" />
              <span>Đóng</span>
            </button>
            <button
              type="button"
              onClick={onPrint || (() => window.print())}
              className="inline-flex items-center gap-1.5 px-4 py-1.5 rounded-lg bg-sky-600 text-white text-xs font-bold hover:bg-sky-700 transition-colors shadow-xs cursor-pointer"
            >
              <Printer className="w-4 h-4" />
              <span>Xác nhận In / Lưu PDF</span>
            </button>
          </div>
        </div>
      )}

      {/* DOCUMENT BODY FOR PRINTING */}
      <div className="statement-sheet space-y-6">
        {/* Header Quốc hiệu / Tiêu ngữ */}
        <div className="grid grid-cols-2 gap-4 pb-4 border-b border-slate-300 text-xs">
          <div className="space-y-0.5">
            <p className="font-bold uppercase tracking-wider text-slate-800 text-[11px]">UBND THÀNH PHỐ ĐÀ NẴNG</p>
            <p className="font-bold uppercase tracking-wider text-slate-900 text-xs">DANASEA MARINE PLATFORM</p>
            <p className="text-slate-500 text-[10px]">Cảng dịch vụ thể thao biển Đà Nẵng</p>
            <p className="text-slate-600 font-mono text-[11px] pt-1 font-semibold">
              Số: {cleanCycleCode}/BB-DSQT/DANASEA
            </p>
          </div>

          <div className="text-right space-y-0.5">
            <p className="font-bold uppercase tracking-wider text-slate-900 text-[11px]">CỘNG HÒA XÃ HỘI CHỦ NGHĨA VIỆT NAM</p>
            <p className="font-semibold text-slate-800 text-xs">Độc lập – Tự do – Hạnh phúc</p>
            <p className="text-slate-400 text-[10px]">---o0o---</p>
            <p className="text-slate-600 italic text-[11px] pt-1">
              Đà Nẵng, ngày {dayStr} tháng {monthStr} năm {yearStr}
            </p>
          </div>
        </div>

        {/* Tiêu đề văn bản */}
        <div className="text-center space-y-1.5 py-2">
          <h1 className="text-lg sm:text-xl font-black text-slate-950 uppercase tracking-tight">
            BIÊN BẢN ĐỐI SOÁT &amp; QUYẾT TOÁN DOANH THU ĐỐI TÁC
          </h1>
          <p className="text-xs text-slate-600">
            Kỳ hạch toán: <strong className="text-slate-900 font-bold">{cycle.dateRange}</strong> • Mã đối soát: <strong className="font-mono text-slate-900 font-bold">#{cleanCycleCode}</strong>
          </p>
          <div className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-md font-mono tabular-nums bg-slate-100 border border-slate-200 text-[11px] font-semibold text-slate-700">
            <span>Trạng thái:</span>
            {cycle.status === 'PAID' ? (
              <span className="text-emerald-700 font-bold">Đã thanh toán ngân hàng</span>
            ) : (
              <span className="text-amber-700 font-bold">Đang hoàn tất đối soát định kỳ</span>
            )}
          </div>
        </div>

        {/* Căn cứ pháp lý */}
        <p className="text-[11px] text-slate-600 italic leading-relaxed text-justify">
          Căn cứ Hợp đồng hợp tác cung ứng dịch vụ thể thao &amp; trải nghiệm biển trên Nền tảng DANASea; 
          Hôm nay, ngày {dayStr}/{monthStr}/{yearStr}, tại Văn phòng Điều hành Nền tảng DANASea, hai bên tiến hành lập Biên bản đối soát doanh thu chi tiết như sau:
        </p>

        {/* Thông tin Bên A & Bên B */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4 bg-slate-50 p-3.5 rounded-xl border border-slate-200 text-xs">
          <div className="space-y-1">
            <p className="font-bold text-slate-900 uppercase text-[11px] border-b border-slate-200 pb-1">
              BÊN A: NỀN TẢNG QUẢN TRỊ DANASEA
            </p>
            <p><span className="text-slate-500">Đơn vị:</span> <strong className="font-semibold text-slate-800">CÔNG TY CỔ PHẦN CÔNG NGHỆ &amp; DU LỊCH BIỂN DANASEA</strong></p>
            <p><span className="text-slate-500">Đại diện:</span> Ban Điều phối Vận hành &amp; Quyết toán</p>
            <p><span className="text-slate-500">Địa chỉ:</span> 51 Võ Nguyên Giáp, P. Mỹ An, Q. Ngũ Hành Sơn, TP. Đà Nẵng</p>
            <p><span className="text-slate-500">Hotline:</span> 0905 112 233 • MST: 0402198888</p>
          </div>

          <div className="space-y-1">
            <p className="font-bold text-slate-900 uppercase text-[11px] border-b border-slate-200 pb-1">
              BÊN B: ĐƠN VỊ ĐỐI TÁC CUNG ỨNG DỊCH VỤ
            </p>
            <p><span className="text-slate-500">Đơn vị:</span> <strong className="font-semibold text-slate-800">{partnerName}</strong></p>
            <p><span className="text-slate-500">Mã số thuế:</span> <span className="font-mono">{partnerTaxCode}</span> • Email: {partnerEmail}</p>
            <p><span className="text-slate-500">Địa chỉ:</span> {partnerAddress}</p>
            <p><span className="text-slate-500">TK thụ hưởng:</span> <strong className="font-mono text-slate-900">{partnerBank} – {partnerAccountNum}</strong> ({partnerAccountHolder})</p>
          </div>
        </div>

        {/* Bảng kê chi tiết đơn hàng */}
        <div className="space-y-2">
          <p className="text-xs font-bold text-slate-900 uppercase tracking-tight">
            I. BẢNG KÊ CHI TIẾT CÁC GIAO DỊCH HOÀN TẤT TRONG KỲ ({items.length} GIAO DỊCH)
          </p>
          <div className="overflow-x-auto border border-slate-300 rounded-lg">
            <table className="w-full text-left border-collapse text-xs">
              <thead>
                <tr className="bg-slate-100 text-slate-900 font-bold border-b border-slate-300">
                  <th className="py-2 px-2.5 text-center border-r border-slate-300 w-10">STT</th>
                  <th className="py-2 px-2.5 border-r border-slate-300">Mã SubOrder</th>
                  <th className="py-2 px-2.5 border-r border-slate-300">Dịch vụ trải nghiệm</th>
                  <th className="py-2 px-2.5 border-r border-slate-300">Thời gian ca</th>
                  <th className="py-2 px-2 text-center border-r border-slate-300 w-12">SL</th>
                  <th className="py-2 px-2.5 text-right border-r border-slate-300">Doanh thu gộp</th>
                  <th className="py-2 px-2.5 text-right border-r border-slate-300">Khấu trừ sàn</th>
                  <th className="py-2 px-2.5 text-right">Thực nhận</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-200 text-slate-800">
                {items.length === 0 ? (
                  <tr>
                    <td colSpan={8} className="py-4 text-center text-slate-500 italic">
                      Kỳ này chưa có giao dịch nào được hoàn tất.
                    </td>
                  </tr>
                ) : (
                  items.map((item, idx) => (
                    <tr key={item.id} className="hover:bg-slate-50">
                      <td className="py-2 px-2.5 text-center font-medium border-r border-slate-200">{idx + 1}</td>
                      <td className="py-2 px-2.5 font-mono font-semibold text-slate-900 border-r border-slate-200">{item.subOrderCode}</td>
                      <td className="py-2 px-2.5 font-medium text-slate-800 border-r border-slate-200">{item.serviceName}</td>
                      <td className="py-2 px-2.5 text-slate-600 border-r border-slate-200">{item.slotTime}</td>
                      <td className="py-2 px-2 text-center font-bold border-r border-slate-200">{item.quantity}</td>
                      <td className="py-2 px-2.5 text-right font-mono border-r border-slate-200">{item.gross.toLocaleString('vi-VN')} đ</td>
                      <td className="py-2 px-2.5 text-right font-mono text-amber-700 border-r border-slate-200">-{item.commission.toLocaleString('vi-VN')} đ</td>
                      <td className="py-2 px-2.5 text-right font-mono font-bold text-sky-800">{item.net.toLocaleString('vi-VN')} đ</td>
                    </tr>
                  ))
                )}
              </tbody>
              <tfoot>
                <tr className="bg-slate-100 font-bold border-t-2 border-slate-300 text-slate-950">
                  <td colSpan={5} className="py-2.5 px-3 text-right uppercase border-r border-slate-300">
                    TỔNG CỘNG HẠCH TOÁN ({items.length} đơn):
                  </td>
                  <td className="py-2.5 px-2.5 text-right font-mono border-r border-slate-300">
                    {cycle.grossAmount.toLocaleString('vi-VN')} đ
                  </td>
                  <td className="py-2.5 px-2.5 text-right font-mono text-amber-700 border-r border-slate-300">
                    -{cycle.commissionAmount.toLocaleString('vi-VN')} đ
                  </td>
                  <td className="py-2.5 px-2.5 text-right font-mono text-sky-900 font-extrabold">
                    {cycle.netAmount.toLocaleString('vi-VN')} đ
                  </td>
                </tr>
              </tfoot>
            </table>
          </div>
        </div>

        {/* Tổng kết tài chính bằng chữ */}
        <div className="p-3.5 bg-slate-50 border border-slate-200 rounded-xl space-y-1.5 text-xs">
          <p className="font-bold uppercase text-slate-900 text-[11px]">II. TỔNG KẾT TÀI CHÍNH &amp; PHƯƠNG THỨC THANH TOÁN</p>
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-2 py-1">
            <div className="p-2 bg-white rounded-lg border border-slate-200">
              <span className="text-slate-500 text-[10px] block">Doanh thu gộp (Gross)</span>
              <span className="font-mono font-bold text-slate-900 text-sm">{cycle.grossAmount.toLocaleString('vi-VN')} VNĐ</span>
            </div>
            <div className="p-2 bg-white rounded-lg border border-slate-200">
              <span className="text-slate-500 text-[10px] block">Phí sàn &amp; cảng vụ</span>
              <span className="font-mono font-bold text-amber-700 text-sm">-{cycle.commissionAmount.toLocaleString('vi-VN')} VNĐ</span>
            </div>
            <div className="p-2 bg-white rounded-lg border border-sky-200 bg-sky-50/50">
              <span className="text-sky-700 text-[10px] block font-semibold">Số tiền thực nhận đối tác (Net)</span>
              <span className="font-mono font-extrabold text-sky-900 text-sm">{cycle.netAmount.toLocaleString('vi-VN')} VNĐ</span>
            </div>
          </div>
          <p className="text-xs text-slate-800">
            Số tiền bằng chữ: <strong className="italic text-slate-950 font-bold">{formatVndWords(cycle.netAmount)}</strong>.
          </p>
          <p className="text-[11px] text-slate-600">
            Hình thức quyết toán: <strong>Chuyển khoản điện tử tự động qua ngân hàng</strong>. Khoản chi trả được thanh toán vào số tài khoản <span className="font-mono font-bold text-slate-900">{partnerAccountNum}</span> mở tại ngân hàng <span className="font-bold text-slate-900">{partnerBank}</span> của Đối tác.
          </p>
        </div>

        {/* Xác nhận ký tên 2 bên */}
        <div className="pt-4 grid grid-cols-2 gap-8 text-center text-xs">
          <div className="space-y-16">
            <div>
              <p className="font-bold uppercase text-slate-900">ĐẠI DIỆN ĐỐI TÁC (BÊN B)</p>
              <p className="text-[11px] text-slate-500 italic">(Ký, ghi rõ họ tên và đóng dấu nếu có)</p>
            </div>
            <p className="font-bold text-slate-800 text-xs">{partnerAccountHolder}</p>
          </div>

          <div className="space-y-16">
            <div>
              <p className="font-bold uppercase text-slate-900">ĐẠI DIỆN BAN QUẢN TRỊ DANASEA (BÊN A)</p>
              <p className="text-[11px] text-slate-500 italic">(Ký, đóng dấu xác nhận)</p>
            </div>
            <div className="flex flex-col items-center">
              <div className="inline-flex items-center gap-1 px-2.5 py-1 rounded border border-emerald-500 text-emerald-700 text-[10px] font-bold uppercase tracking-wider bg-emerald-50 shadow-2xs">
                <ShieldCheck className="w-3.5 h-3.5 text-emerald-600" />
                <span>ĐÃ XÁC THỰC SỐ QUA HỆ THỐNG DANASEA CORE</span>
              </div>
              <p className="font-bold text-slate-900 text-xs mt-1.5">BAN ĐIỀU PHỐI VẬN HÀNH &amp; TÀI CHÍNH</p>
            </div>
          </div>
        </div>

        {/* Footer ghi chú */}
        <div className="pt-6 border-t border-slate-200 text-center text-[10px] text-slate-500 italic">
          Biên bản này được lập thành 02 bản có giá trị pháp lý như nhau, mỗi bên giữ 01 bản để làm căn cứ hạch toán kế toán và nghĩa vụ thuế.
        </div>
      </div>
    </div>
  );
};
