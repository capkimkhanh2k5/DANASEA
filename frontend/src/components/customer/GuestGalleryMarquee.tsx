import React, { useState, useEffect } from 'react';
import { GUEST_GALLERY_ROW_1, GUEST_GALLERY_ROW_2 } from '../../data/customerHomeData';
import { useLanguageStore } from '../../store/useLanguageStore';
import { apiClient } from '../../services/apiClient';

interface GuestCard {
  imageUrl: string;
  guestName: string;
  guestLocation: string;
  activity: string;
  activityEn: string;
  quote: string;
  quoteEn: string;
  rating: number;
  isReal?: boolean;
}

const GUEST_CARDS_ROW_1: GuestCard[] = [
  {
    imageUrl: GUEST_GALLERY_ROW_1[0],
    guestName: 'Nguyễn Thảo My',
    guestLocation: 'Hà Nội',
    activity: 'SUP Bình Minh Mỹ Khê',
    activityEn: 'Sunrise SUP My Khe',
    quote: 'Nước biển trong vắt, anh hướng dẫn chụp ảnh có tâm đến từng góc máy!',
    quoteEn: 'Crystal waters and the guide took breathtaking photos for us!',
    rating: 5,
  },
  {
    imageUrl: GUEST_GALLERY_ROW_1[1],
    guestName: 'David & Sarah',
    guestLocation: 'Australia',
    activity: 'Lặn Scuba Sơn Trà',
    activityEn: 'Son Tra Scuba Dive',
    quote: 'Rạn san hô tại bán đảo Sơn Trà tuyệt đẹp. Trang thiết bị lặn chuyên nghiệp và hướng dẫn viên nhiệt tình!',
    quoteEn: 'The coral reef in Da Nang surprised us completely. Professional diving gear and certified safety guidance!',
    rating: 5,
  },
  {
    imageUrl: GUEST_GALLERY_ROW_1[2],
    guestName: 'Trần Minh Quân',
    guestLocation: 'TP. Hồ Chí Minh',
    activity: 'Cano & Lặn Bãi Rạng',
    activityEn: 'Speedboat & Snorkel',
    quote: 'Chuyến đi đáng giá từng xu! Cả nhà đều hào hứng lướt sóng và lặn ngắm san hô.',
    quoteEn: 'Worth every penny! My whole family had a blast riding waves and snorkeling.',
    rating: 5,
  },
  {
    imageUrl: GUEST_GALLERY_ROW_1[3],
    guestName: 'Lê Hoàng Phong',
    guestLocation: 'Đà Nẵng',
    activity: 'Bay Dù Lượn Vịnh Biển',
    activityEn: 'Coastal Parasailing',
    quote: 'Cảm giác lơ lửng ngắm toàn cảnh thành phố từ trên không trung cực kỳ đã.',
    quoteEn: 'Floating high above the bay with panoramic views was sensational.',
    rating: 5,
  },
  {
    imageUrl: GUEST_GALLERY_ROW_1[4],
    guestName: 'Kim Min-seo',
    guestLocation: 'South Korea',
    activity: 'Kayak Hoàng Hôn Mũi Nghê',
    activityEn: 'Sunset Kayak Mui Nghe',
    quote: '다낭 바다 노을이 너무 아름다웠어요. DANASEA 최고입니다!',
    quoteEn: 'Da Nang sunset was so breathtaking. Highly recommended experience!',
    rating: 5,
  },
];

const GUEST_CARDS_ROW_2: GuestCard[] = [
  {
    imageUrl: GUEST_GALLERY_ROW_2[0],
    guestName: 'Phạm Nhật Anh',
    guestLocation: 'Hải Phòng',
    activity: 'Jet Ski Cao Tốc 1800cc',
    activityEn: 'High-speed Jet Ski',
    quote: 'Tay ga bốc, sóng văng tung bọt sảng khoái không tả nổi!',
    quoteEn: 'Pure throttle power, slicing through ocean waves was pure euphoria!',
    rating: 5,
  },
  {
    imageUrl: GUEST_GALLERY_ROW_2[1],
    guestName: 'Emma Watson',
    guestLocation: 'United Kingdom',
    activity: 'Sunset Luxury Yacht',
    activityEn: 'Sunset Luxury Yacht',
    quote: 'Champagne on the deck while sunset painted the bay in pink. 10/10.',
    quoteEn: 'Champagne on the deck while sunset painted the bay in pink. 10/10.',
    rating: 5,
  },
  {
    imageUrl: GUEST_GALLERY_ROW_2[2],
    guestName: 'Vũ Đăng Khoa',
    guestLocation: 'Cần Thơ',
    activity: 'Chèo SUP & Snorkeling',
    activityEn: 'SUP & Snorkeling Combo',
    quote: 'Combo hoàn hảo, vừa rèn luyện thể lực vừa ngắm trọn hệ sinh thái san hô.',
    quoteEn: 'Perfect combination, rewarding exercise while exploring living corals.',
    rating: 5,
  },
  {
    imageUrl: GUEST_GALLERY_ROW_2[3],
    guestName: 'Sato Kenji',
    guestLocation: 'Japan',
    activity: 'Phao Chuối Cảm Giác Mạnh',
    activityEn: 'Banana Boat Adventure',
    quote: '友達と最高の思い出ができました。海の青さが忘れられません。',
    quoteEn: 'Made unforgettable memories with friends. The ocean blue is unforgettable.',
    rating: 5,
  },
  {
    imageUrl: GUEST_GALLERY_ROW_2[4],
    guestName: 'Hoàng Thị Mai',
    guestLocation: 'Quảng Ninh',
    activity: 'Cano Tham Quan Đảo Khỉ',
    activityEn: 'Monkey Island Speedboat',
    quote: 'Cano chạy êm, áo phao tiêu chuẩn mới tinh, gia đình rất an tâm.',
    quoteEn: 'Smooth speedboat ride, brand new SOLAS life vests, our family felt completely safe.',
    rating: 5,
  },
];

export const GuestGalleryMarquee: React.FC = () => {
  const { language } = useLanguageStore();
  const [row1, setRow1] = useState<GuestCard[]>(GUEST_CARDS_ROW_1);
  const [row2, setRow2] = useState<GuestCard[]>(GUEST_CARDS_ROW_2);

  useEffect(() => {
    let isMounted = true;
    async function loadRealReviews() {
      try {
        const res = await apiClient.get('/services', { params: { size: 10 } });
        const services = res.data?.data?.content || res.data?.content || [];
        if (!Array.isArray(services) || services.length === 0) return;

        const realReviews: GuestCard[] = [];
        for (const s of services) {
          try {
            const revRes = await apiClient.get(`/services/${s.id}/reviews`, { params: { size: 5 } });
            const reviews = revRes.data?.data?.content || revRes.data?.content || [];
            if (Array.isArray(reviews)) {
              for (const r of reviews) {
                if (r.isVisible !== false && r.comment) {
                  realReviews.push({
                    imageUrl: r.customerAvatar || s.primaryImageUrl || GUEST_GALLERY_ROW_1[realReviews.length % GUEST_GALLERY_ROW_1.length],
                    guestName: r.customerName || 'Du khách DANASEA',
                    guestLocation: language === 'en' ? 'Verified Booking' : 'Đã trải nghiệm',
                    activity: s.name,
                    activityEn: s.name,
                    quote: r.comment,
                    quoteEn: r.comment,
                    rating: r.rating || 5,
                    isReal: true,
                  });
                }
              }
            }
          } catch {
            // Ignore individual service review error
          }
        }

        if (isMounted && realReviews.length > 0) {
          // Distribute real reviews across Row 1 & Row 2
          const newRow1: GuestCard[] = [];
          const newRow2: GuestCard[] = [];

          realReviews.forEach((rev, idx) => {
            if (idx % 2 === 0) newRow1.push(rev);
            else newRow2.push(rev);
          });

          // Fill remaining slots up to at least 5 per row with fallback cards to keep smooth infinite loop
          GUEST_CARDS_ROW_1.forEach((fb) => {
            if (newRow1.length < 5) newRow1.push(fb);
          });
          GUEST_CARDS_ROW_2.forEach((fb) => {
            if (newRow2.length < 5) newRow2.push(fb);
          });

          setRow1(newRow1);
          setRow2(newRow2);
        }
      } catch (err) {
        console.warn('[GuestGalleryMarquee] Dùng fallback mock reviews:', err);
      }
    }

    loadRealReviews();
    return () => {
      isMounted = false;
    };
  }, [language]);

  return (
    <div className="py-8 sm:py-12 overflow-hidden bg-transparent">
      <style>{`
        @keyframes marquee-left {
          0% { transform: translate3d(0, 0, 0); }
          100% { transform: translate3d(-50%, 0, 0); }
        }
        @keyframes marquee-right {
          0% { transform: translate3d(-50%, 0, 0); }
          100% { transform: translate3d(0, 0, 0); }
        }
        .animate-marquee-left {
          display: flex;
          width: max-content;
          will-change: transform;
          animation: marquee-left 45s linear infinite;
        }
        .animate-marquee-right {
          display: flex;
          width: max-content;
          will-change: transform;
          animation: marquee-right 45s linear infinite;
        }
        .animate-marquee-left:hover,
        .animate-marquee-right:hover {
          animation-play-state: paused;
        }
      `}</style>

      {/* Header */}
      <div className="text-center max-w-2xl mx-auto mb-8 px-4">
        <h2 className="text-2xl sm:text-3xl font-extrabold tracking-tight text-slate-900">
          {language === 'en' ? 'Authentic Guest Memories' : 'Khoảnh Khắc Cùng Du Khách'}
        </h2>
        <p className="text-base text-slate-600 mt-2 font-medium">
          {language === 'en'
            ? 'Real moments captured by travelers across Da Nang coastal bays'
            : 'Những trải nghiệm biển thực tế từ cộng đồng du khách khắp mọi miền'}
        </p>
      </div>

      {/* Infinite Dual Marquee Track */}
      <div
        className="space-y-4 overflow-hidden"
        style={{
          maskImage: 'linear-gradient(to right, transparent 0%, black 8%, black 92%, transparent 100%)',
          WebkitMaskImage: 'linear-gradient(to right, transparent 0%, black 8%, black 92%, transparent 100%)',
        }}
      >
        {/* Marquee Row 1 (Moving Left) */}
        <div className="overflow-hidden">
          <div className="flex animate-marquee-left gap-4">
            {[...row1, ...row1].map((card, idx) => (
              <div
                key={`row1-${idx}`}
                data-ui="marquee-card"
                className="w-80 sm:w-96 flex-shrink-0 bg-white border border-slate-200/90 rounded-2xl p-4 flex gap-3.5 hover:border-sky-400 hover:shadow-md transition-all"
              >
                <img
                  src={card.imageUrl}
                  alt={card.guestName}
                  className="w-20 h-28 rounded-xl object-cover flex-shrink-0 bg-slate-100"
                  loading="lazy"
                />
                <div className="flex flex-col justify-between overflow-hidden">
                  <div>
                    <div className="flex items-center gap-0.5 text-amber-400 mb-1">
                      {Array.from({ length: card.rating }).map((_, i) => (
                        <svg key={i} className="w-3.5 h-3.5 fill-current text-amber-400" viewBox="0 0 20 20">
                          <path d="M9.049 2.927c.3-.921 1.603-.921 1.902 0l1.07 3.292a1 1 0 00.95.69h3.462c.969 0 1.371 1.24.588 1.81l-2.8 2.034a1 1 0 00-.364 1.118l1.07 3.292c.3.921-.755 1.688-1.54 1.118l-2.8-2.034a1 1 0 00-1.175 0l-2.8 2.034c-.784.57-1.838-.197-1.539-1.118l1.07-3.292a1 1 0 00-.364-1.118L2.98 8.72c-.783-.57-.38-1.81.588-1.81h3.461a1 1 0 00.951-.69l1.07-3.292z" />
                        </svg>
                      ))}
                      {card.isReal && (
                        <span className="inline-flex items-center text-[10px] font-semibold text-emerald-700 bg-emerald-50 px-1.5 py-0.5 rounded ml-1.5 border border-emerald-200">
                          ✓ {language === 'en' ? 'Verified' : 'Đã trải nghiệm'}
                        </span>
                      )}
                    </div>
                    <p className="text-[13px] text-slate-700 italic line-clamp-3 mb-2 leading-relaxed">
                      "{language === 'en' ? card.quoteEn : card.quote}"
                    </p>
                  </div>
                  <div>
                    <div className="text-[13px] font-bold text-slate-900 truncate">
                      {card.guestName} <span className="text-slate-500 font-normal">({card.guestLocation})</span>
                    </div>
                    <div className="text-[13px] text-sky-700 font-semibold truncate">
                      {language === 'en' ? card.activityEn : card.activity}
                    </div>
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* Marquee Row 2 (Moving Right) */}
        <div className="overflow-hidden">
          <div className="flex animate-marquee-right gap-4">
            {[...row2, ...row2].map((card, idx) => (
              <div
                key={`row2-${idx}`}
                data-ui="marquee-card"
                className="w-80 sm:w-96 flex-shrink-0 bg-white border border-slate-200/90 rounded-2xl p-4 flex gap-3.5 hover:border-sky-400 hover:shadow-md transition-all"
              >
                <img
                  src={card.imageUrl}
                  alt={card.guestName}
                  className="w-20 h-28 rounded-xl object-cover flex-shrink-0 bg-slate-100"
                  loading="lazy"
                />
                <div className="flex flex-col justify-between overflow-hidden">
                  <div>
                    <div className="flex items-center gap-0.5 text-amber-400 mb-1">
                      {Array.from({ length: card.rating }).map((_, i) => (
                        <svg key={i} className="w-3.5 h-3.5 fill-current text-amber-400" viewBox="0 0 20 20">
                          <path d="M9.049 2.927c.3-.921 1.603-.921 1.902 0l1.07 3.292a1 1 0 00.95.69h3.462c.969 0 1.371 1.24.588 1.81l-2.8 2.034a1 1 0 00-.364 1.118l1.07 3.292c.3.921-.755 1.688-1.54 1.118l-2.8-2.034a1 1 0 00-1.175 0l-2.8 2.034c-.784.57-1.838-.197-1.539-1.118l1.07-3.292a1 1 0 00-.364-1.118L2.98 8.72c-.783-.57-.38-1.81.588-1.81h3.461a1 1 0 00.951-.69l1.07-3.292z" />
                        </svg>
                      ))}
                      {card.isReal && (
                        <span className="inline-flex items-center text-[10px] font-semibold text-emerald-700 bg-emerald-50 px-1.5 py-0.5 rounded ml-1.5 border border-emerald-200">
                          ✓ {language === 'en' ? 'Verified' : 'Đã trải nghiệm'}
                        </span>
                      )}
                    </div>
                    <p className="text-[13px] text-slate-700 italic line-clamp-3 mb-2 leading-relaxed">
                      "{language === 'en' ? card.quoteEn : card.quote}"
                    </p>
                  </div>
                  <div>
                    <div className="text-[13px] font-bold text-slate-900 truncate">
                      {card.guestName} <span className="text-slate-500 font-normal">({card.guestLocation})</span>
                    </div>
                    <div className="text-[13px] text-sky-700 font-semibold truncate">
                      {language === 'en' ? card.activityEn : card.activity}
                    </div>
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
};

export default GuestGalleryMarquee;
