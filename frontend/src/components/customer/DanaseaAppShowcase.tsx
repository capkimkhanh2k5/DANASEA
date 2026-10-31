import React, { useEffect, useRef, useState } from 'react';
import { useLanguageStore } from '../../store/useLanguageStore';

export function DanaseaAppShowcase() {
  const { language } = useLanguageStore();
  const sectionRef = useRef<HTMLElement>(null);
  const [isInView, setIsInView] = useState(false);

  useEffect(() => {
    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) {
          setIsInView(true);
        }
      },
      { threshold: 0.15 }
    );

    if (sectionRef.current) {
      observer.observe(sectionRef.current);
    }

    return () => observer.disconnect();
  }, []);

  return (
    <section
      ref={sectionRef}
      className="py-10 sm:py-14 relative overflow-hidden text-slate-900"
    >
      <div className="max-w-[1500px] mx-auto px-4 sm:px-6 lg:px-8 relative z-10">
        {/* Cohesive Card Container with Premium Soft Oceanic Gradient Surface */}
        <div className="relative rounded-3xl bg-gradient-to-br from-slate-50 via-sky-50/50 to-blue-50/30 border border-slate-200/80 p-6 sm:p-10 lg:p-14 overflow-hidden shadow-xs">
          
          {/* Subtle Ambient Decorative Circles */}
          <div className="absolute -top-24 -right-24 w-96 h-96 rounded-full bg-sky-200/20 blur-3xl pointer-events-none" />
          <div className="absolute -bottom-24 -left-24 w-96 h-96 rounded-full bg-cyan-200/20 blur-3xl pointer-events-none" />

          <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 lg:gap-12 items-center relative z-10">
            
            {/* LEFT COLUMN: Heading & Download Deck (QR Code + Store Badges) (7 cols) */}
            <div className="lg:col-span-7 flex flex-col justify-center space-y-6 order-2 lg:order-1 text-left">
              
              {/* Header info */}
              <div>
                <span className="text-xs font-bold text-sky-700 uppercase tracking-widest block mb-2">
                  {language === 'en' ? 'DANASEA MOBILE EXPERIENCE' : 'ỨNG DỤNG DI ĐỘNG DANASEA'}
                </span>
                <h2 className="text-2xl sm:text-4xl lg:text-5xl font-extrabold tracking-tight text-slate-900 leading-tight">
                  {language === 'en'
                    ? 'Experience on DANASEA Mobile App'
                    : 'Trải Nghiệm Trên Ứng Dụng DANASEA'}
                </h2>
                <p className="text-slate-600 text-sm sm:text-base mt-3 max-w-xl font-normal leading-relaxed">
                  {language === 'en'
                    ? 'Scan QR to install the app. Manage your bookings, receive tidal advisories, and get offline digital passes directly on your device.'
                    : 'Quét mã QR để tải ứng dụng. Quản lý vé điện tử, nhận thông báo thời tiết thủy triều theo thời gian thực và check-in không cần mạng tại bến tàu.'}
                </p>
              </div>

              {/* Download Deck: Large QR Code + App Store & Google Play Badges */}
              <div className="pt-2 flex flex-col sm:flex-row items-center gap-5">
                
                {/* Standalone Large QR Code */}
                <div className="bg-white p-2.5 rounded-2xl border border-slate-200/80 shadow-xs shrink-0">
                  <img
                    src="https://api.qrserver.com/v1/create-qr-code/?size=260x260&data=https://danasea.vn/app"
                    alt="QR DANASEA App"
                    className="w-28 h-28 sm:w-32 sm:h-32 object-contain block rounded-xl"
                  />
                </div>

                {/* App Store & Google Play Badges (Light style) */}
                <div className="flex flex-col gap-2.5 w-full sm:w-auto">
                  <a
                    href="#download-ios"
                    onClick={(e) => {
                      e.preventDefault();
                      alert(language === 'en' ? 'DANASEA iOS version is preparing for App Store launch.' : 'Ứng dụng DANASEA iOS đang chuẩn bị phát hành trên App Store.');
                    }}
                    className="w-full sm:w-[190px] flex items-center gap-3 px-4 py-3 rounded-xl bg-white hover:bg-slate-50 text-slate-900 border border-slate-200/80 shadow-xs transition-colors cursor-pointer"
                  >
                    <svg className="w-5 h-5 fill-slate-900 shrink-0" viewBox="0 0 24 24">
                      <path d="M18.71 19.5c-.83 1.24-1.71 2.45-3.05 2.47-1.34.03-1.77-.79-3.29-.79-1.53 0-2 .77-3.27.82-1.31.05-2.3-1.32-3.14-2.53C4.25 17 2.94 12.45 4.7 9.39c.87-1.52 2.43-2.48 4.12-2.51 1.28-.02 2.5.87 3.29.87.78 0 2.26-1.07 3.81-.91.65.03 2.47.26 3.64 1.98-.09.06-2.17 1.28-2.15 3.81.03 3.02 2.65 4.03 2.68 4.04-.03.07-.42 1.44-1.38 2.83M15.97 6.37c.62-.75 1.04-1.8 0.92-2.85-.9.04-1.99.6-2.63 1.35-.57.65-.98 1.72-.85 2.73.99.08 2.02-.48 2.56-1.23z"/>
                    </svg>
                    <div className="text-left">
                      <span className="block text-[11px] text-slate-500 leading-none">Tải trên</span>
                      <span className="block text-xs font-bold text-slate-900 leading-tight mt-0.5">App Store</span>
                    </div>
                  </a>

                  <a
                    href="#download-android"
                    onClick={(e) => {
                      e.preventDefault();
                      alert(language === 'en' ? 'DANASEA Android version is preparing for Google Play launch.' : 'Ứng dụng DANASEA Android đang chuẩn bị phát hành trên Google Play.');
                    }}
                    className="w-full sm:w-[190px] flex items-center gap-3 px-4 py-3 rounded-xl bg-white hover:bg-slate-50 text-slate-900 border border-slate-200/80 shadow-xs transition-colors cursor-pointer"
                  >
                    <svg className="w-5 h-5 shrink-0" viewBox="0 0 24 24">
                      <path fill="#0086F9" d="M3.609 1.814L13.793 12 3.61 22.186c-.23-.23-.36-.55-.36-.91V2.724c0-.36.13-.68.36-.91z"/>
                      <path fill="#00E676" d="M14.844 13.048l2.585-2.586-12.01-6.86 9.425 9.446z"/>
                      <path fill="#FF3A44" d="M14.844 14.952l-9.425 9.446 12.01-6.86-2.585-2.586z"/>
                      <path fill="#FFD900" d="M16.184 13.612l3.158 1.805c.876.5 1.417.18 1.417-.82v-3.6c0-1-.541-1.32-1.417-.82l-3.158 1.805-.62.63.62.62z"/>
                    </svg>
                    <div className="text-left">
                      <span className="block text-[11px] text-slate-500 leading-none">Tải trên</span>
                      <span className="block text-xs font-bold text-slate-900 leading-tight mt-0.5">Google Play</span>
                    </div>
                  </a>
                </div>

              </div>

            </div>

            {/* RIGHT COLUMN: Compact Mobile Phone Mockup (5 cols) */}
            <div className="lg:col-span-5 flex justify-center order-1 lg:order-2">
              
              {/* Phone Wrapper with Controlled Size to fit viewport fully */}
              <div
                style={{
                  transition: 'transform 700ms cubic-bezier(0.16, 1, 0.3, 1), opacity 700ms ease-out',
                }}
                className={`relative mx-auto w-full max-w-[240px] sm:max-w-[260px] ${
                  isInView
                    ? 'opacity-100 translate-x-0'
                    : 'opacity-0 translate-x-12'
                }`}
              >
                {/* Outer Phone Bezel (Modern Smartphone Frame) */}
                <div className="relative rounded-[38px] p-2 bg-slate-900 shadow-2xl border border-slate-800">
                  {/* Phone Screen Container */}
                  <div className="rounded-[30px] bg-slate-100 overflow-hidden flex flex-col select-none relative max-h-[500px]">
                    <img
                      src="/images/danasea-app-screen.png"
                      alt="Giao diện ứng dụng di động DANASEA"
                      className="w-full h-auto object-cover block"
                    />
                  </div>
                </div>

              </div>

            </div>

          </div>

        </div>
      </div>
    </section>
  );
}

export default DanaseaAppShowcase;
