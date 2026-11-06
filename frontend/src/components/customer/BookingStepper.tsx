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
    <div className="w-full max-w-2xl mx-auto py-5 sm:py-6 px-4 select-none">
      <div className="relative flex items-start justify-between">
        {/* Background track line behind circles */}
        <div className="absolute top-5 sm:top-6 left-6 right-6 h-1 bg-slate-200 -z-0 rounded-full" />

        {/* Animated Active Progress Fill Line */}
        <div
          className="absolute top-5 sm:top-6 left-6 h-1 bg-sky-600 transition-all duration-700 ease-[cubic-bezier(0.25,1,0.5,1)] -z-0 rounded-full"
          style={{
            width: currentStep === 1 ? '0%' : currentStep === 2 ? 'calc(50% - 12px)' : 'calc(100% - 12px)',
          }}
        />

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
              className={`relative z-10 flex flex-col items-center flex-1 max-w-[180px] ${
                isCompleted ? 'cursor-pointer group' : ''
              }`}
            >
              {/* Circle Node */}
              {isCompleted ? (
                <div
                  title={language === 'en' ? `Back to ${step.label}` : `Quay lại ${step.label}`}
                  className="w-10 h-10 sm:w-12 sm:h-12 rounded-full bg-sky-600 text-white flex items-center justify-center shadow-md shadow-sky-600/30 transition-all duration-300 group-hover:scale-110 group-active:scale-95"
                >
                  <Check className="w-5 h-5 sm:w-6 sm:h-6 stroke-[3]" />
                </div>
              ) : isActive ? (
                <div className="w-10 h-10 sm:w-12 sm:h-12 rounded-full bg-white border-[3.5px] border-sky-600 text-sky-600 font-extrabold text-base sm:text-lg flex items-center justify-center shadow-lg shadow-sky-600/20 ring-4 ring-sky-500/20 scale-105 transition-all duration-300">
                  <span className="font-mono tabular-nums">{step.number}</span>
                </div>
              ) : (
                <div className="w-10 h-10 sm:w-12 sm:h-12 rounded-full bg-slate-200 border-2 border-white text-slate-500 font-bold text-sm sm:text-base flex items-center justify-center transition-all duration-300">
                  <span className="font-mono tabular-nums">{step.number}</span>
                </div>
              )}

              {/* Step Label */}
              <span
                className={`mt-2.5 text-xs sm:text-sm text-center leading-tight transition-colors ${
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
