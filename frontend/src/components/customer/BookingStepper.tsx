import React from 'react';
import { useNavigate } from 'react-router-dom';
import { Check } from 'lucide-react';
import { useLanguageStore } from '../../store/useLanguageStore';

export interface BookingStepperProps {
  currentStep: 1 | 2 | 3;
}

export const BookingStepper: React.FC<BookingStepperProps> = ({ currentStep }) => {
  const navigate = useNavigate();
  const { language } = useLanguageStore();

  const steps = [
    {
      number: 1,
      label: language === 'en' ? 'Cart' : 'Giỏ hàng',
      path: '/cart',
    },
    {
      number: 2,
      label: language === 'en' ? 'Checkout' : 'Thông tin & Thanh toán',
      path: '/checkout',
    },
    {
      number: 3,
      label: language === 'en' ? 'Done & QR Pass' : 'Nhận vé điện tử QR',
      path: '/checkout/result',
    },
  ];

  return (
    <div className="w-full max-w-xl mx-auto py-5 sm:py-6 px-4 select-none">
      <style>{`
        @keyframes stepCircleDraw {
          0% {
            stroke-dashoffset: 145;
          }
          100% {
            stroke-dashoffset: 0;
          }
        }
        @keyframes stepPulseGlow {
          0%, 100% {
            box-shadow: 0 0 0 0 rgba(2, 132, 199, 0.45);
          }
          50% {
            box-shadow: 0 0 0 8px rgba(2, 132, 199, 0);
          }
        }
        .animate-circle-draw {
          stroke-dasharray: 145;
          animation: stepCircleDraw 0.65s cubic-bezier(0.25, 1, 0.5, 1) forwards;
        }
        .active-step-glow {
          animation: stepPulseGlow 2.4s infinite cubic-bezier(0.4, 0, 0.6, 1);
        }
      `}</style>

      <div className="relative grid grid-cols-3 items-start">
        {/* Background Track Line: Exactly from center of Col 1 (16.666%) to center of Col 3 (83.333%) */}
        <div className="absolute top-5 sm:top-6 left-[16.666%] right-[16.666%] h-1 sm:h-1.5 bg-slate-200 -z-0 rounded-full" />

        {/* Animated Active Progress Fill Line */}
        <div
          className="absolute top-5 sm:top-6 left-[16.666%] right-[16.666%] h-1 sm:h-1.5 -z-0 overflow-hidden rounded-full pointer-events-none"
        >
          <div
            className="h-full bg-sky-600 rounded-full transition-all duration-700 ease-[cubic-bezier(0.25,1,0.5,1)] shadow-[0_0_10px_rgba(2,132,199,0.5)]"
            style={{
              width: currentStep === 1 ? '0%' : currentStep === 2 ? '50%' : '100%',
            }}
          />
        </div>

        {/* 3 Step Nodes */}
        {steps.map((step) => {
          const isCompleted = step.number < currentStep;
          const isActive = step.number === currentStep;

          return (
            <div
              key={step.number}
              onClick={() => {
                if (isCompleted && step.path) {
                  navigate(step.path);
                }
              }}
              className={`relative z-10 flex flex-col items-center px-1 ${
                isCompleted ? 'cursor-pointer group' : ''
              }`}
            >
              {/* Circle Node Container */}
              <div className="relative flex items-center justify-center">
                {isCompleted ? (
                  <div
                    title={language === 'en' ? `Back to ${step.label}` : `Quay lại ${step.label}`}
                    className="w-10 h-10 sm:w-12 sm:h-12 rounded-full bg-sky-600 text-white flex items-center justify-center shadow-md shadow-sky-600/30 transition-all duration-300 group-hover:scale-110 group-active:scale-95"
                  >
                    <Check className="w-5 h-5 sm:w-6 sm:h-6 stroke-[3] animate-in zoom-in-50 duration-300" />
                  </div>
                ) : isActive ? (
                  <div className="relative w-10 h-10 sm:w-12 sm:h-12 rounded-full bg-white flex items-center justify-center active-step-glow transition-all duration-300">
                    {/* Dynamic SVG Circular Stroke Drawing Animation */}
                    <svg
                      className="absolute -inset-1 w-[calc(100%+8px)] h-[calc(100%+8px)] -rotate-90 pointer-events-none"
                      viewBox="0 0 56 56"
                    >
                      <circle
                        cx="28"
                        cy="28"
                        r="23"
                        fill="none"
                        stroke="#0284c7"
                        strokeWidth="3.5"
                        strokeLinecap="round"
                        className="animate-circle-draw"
                      />
                    </svg>
                    <span className="font-mono tabular-nums text-sky-600 font-black text-base sm:text-lg">
                      {step.number}
                    </span>
                  </div>
                ) : (
                  <div className="w-10 h-10 sm:w-12 sm:h-12 rounded-full bg-slate-200 border-2 border-white text-slate-500 font-bold text-sm sm:text-base flex items-center justify-center transition-all duration-300">
                    <span className="font-mono tabular-nums">{step.number}</span>
                  </div>
                )}
              </div>

              {/* Step Label */}
              <span
                className={`mt-2.5 text-xs sm:text-sm text-center leading-tight transition-colors line-clamp-2 max-w-[140px] ${
                  isActive
                    ? 'font-bold text-slate-900 tracking-tight'
                    : isCompleted
                    ? 'font-semibold text-slate-700 group-hover:text-sky-700'
                    : 'font-medium text-slate-400'
                }`}
              >
                {step.label}
              </span>
            </div>
          );
        })}
      </div>
    </div>
  );
};

export default BookingStepper;
