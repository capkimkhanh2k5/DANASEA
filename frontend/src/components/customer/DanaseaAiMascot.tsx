import React from 'react';

interface DanaseaAiMascotProps {
  className?: string;
  size?: number;
}

export const DanaseaAiMascot: React.FC<DanaseaAiMascotProps> = ({
  className = '',
  size = 56,
}) => {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 100 100"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      className={`select-none shrink-0 ${className}`}
      aria-hidden="true"
    >
      <defs>
        {/* Soft shadow for depth */}
        <filter id="danasea-mascot-shadow" x="-10%" y="-10%" width="125%" height="130%" filterUnits="userSpaceOnUse">
          <feDropShadow dx="0" dy="3" stdDeviation="3.5" floodColor="#0c4a6e" floodOpacity="0.22" />
        </filter>

        {/* Ocean Body Gradient (DANASEA Brand Cyan & Sky) */}
        <linearGradient id="dolphin-body" x1="20" y1="15" x2="80" y2="85" gradientUnits="userSpaceOnUse">
          <stop offset="0%" stopColor="#38BDF8" />
          <stop offset="65%" stopColor="#0284C7" />
          <stop offset="100%" stopColor="#0369A1" />
        </linearGradient>

        {/* Soft Belly Gradient */}
        <linearGradient id="dolphin-belly" x1="45" y1="50" x2="65" y2="90" gradientUnits="userSpaceOnUse">
          <stop offset="0%" stopColor="#FFFFFF" />
          <stop offset="100%" stopColor="#E0F2FE" />
        </linearGradient>

        {/* Sailor Cap Brim Gradient */}
        <linearGradient id="cap-brim" x1="25" y1="28" x2="65" y2="28" gradientUnits="userSpaceOnUse">
          <stop offset="0%" stopColor="#0F172A" />
          <stop offset="100%" stopColor="#1E293B" />
        </linearGradient>
      </defs>

      <g filter="url(#danasea-mascot-shadow)">
        {/* Dorsal Fin / Cute top fin peeking behind sailor cap */}
        <path
          d="M 52 14 C 48 8, 40 4, 34 6 C 35 12, 41 18, 48 20 Z"
          fill="#0284C7"
        />

        {/* Main Chubby Baby Dolphin Head & Body */}
        <path
          d="M 22 55 C 20 38, 33 22, 54 22 C 75 22, 88 36, 88 56 C 88 74, 76 88, 54 88 C 34 88, 24 74, 22 55 Z"
          fill="url(#dolphin-body)"
        />

        {/* Dolphin Snout (Cá heo mũi tròn phúng phính đặc trưng) */}
        <path
          d="M 22 54 C 13 54, 8 60, 11 67 C 14 74, 24 73, 31 71 C 28 66, 25 60, 22 54 Z"
          fill="#38BDF8"
        />

        {/* Soft Creamy Belly / Cheek Patch */}
        <path
          d="M 28 68 C 22 71, 15 73, 20 76 C 26 79, 36 84, 52 84 C 66 84, 74 78, 70 70 C 64 64, 46 63, 38 64 C 33 65, 30 66, 28 68 Z"
          fill="url(#dolphin-belly)"
        />

        {/* Waving Sailor Flipper (Vây cá heo vẫy chào bên phải) */}
        <path
          d="M 82 58 C 88 56, 96 61, 95 69 C 94 77, 85 78, 80 73 Z"
          fill="#0284C7"
        />

        {/* Sailor / Marine Captain Cap (Mũ thủy thủ DANASEA) */}
        {/* Cap Crown (White fabric) */}
        <path
          d="M 36 24 C 36 15, 62 13, 68 20 C 72 24, 69 28, 64 29 C 55 30, 42 29, 36 24 Z"
          fill="#FFFFFF"
        />
        {/* Cap Ribbon / Brim (Navy) */}
        <path
          d="M 33 27 C 44 23, 62 25, 71 30 C 70 33, 65 34, 53 33 C 41 32, 34 30, 33 27 Z"
          fill="url(#cap-brim)"
        />
        {/* Little Anchor / Ocean Wave Emblem on Cap */}
        <circle cx="52" cy="24" r="3" fill="#38BDF8" />
        <path d="M 50 24 Q 52 26 54 24" stroke="#FFFFFF" strokeWidth="1" strokeLinecap="round" fill="none" />

        {/* Sparkly Happy Eyes */}
        {/* Left Eye */}
        <ellipse cx="40" cy="50" rx="4.5" ry="5.5" fill="#0F172A" />
        <circle cx="38.5" cy="48" r="1.8" fill="#FFFFFF" />
        <circle cx="42" cy="52.5" r="0.9" fill="#FFFFFF" />

        {/* Right Eye (Playful happy wink arc or cheerful sparkly eye) */}
        <ellipse cx="64" cy="50" rx="4.5" ry="5.5" fill="#0F172A" />
        <circle cx="62.5" cy="48" r="1.8" fill="#FFFFFF" />
        <circle cx="66" cy="52.5" r="0.9" fill="#FFFFFF" />

        {/* Cute Blushing Cheeks (Coral Pink) */}
        <ellipse cx="32" cy="58" rx="4.5" ry="2.8" fill="#FB7185" opacity="0.8" />
        <ellipse cx="72" cy="58" rx="4.5" ry="2.8" fill="#FB7185" opacity="0.8" />

        {/* Cheerful Dolphin Smile */}
        <path
          d="M 45 61 Q 52 68 59 61"
          stroke="#0F172A"
          strokeWidth="2"
          strokeLinecap="round"
          fill="none"
        />
        {/* Cute open mouth tongue patch */}
        <path
          d="M 48 64 Q 52 69 56 64 Z"
          fill="#F43F5E"
        />

        {/* Light Water Shimmer Sparkle (Top-left forehead highlight) */}
        <circle cx="42" cy="34" r="2.2" fill="#FFFFFF" opacity="0.75" />
        <circle cx="36" cy="38" r="1.3" fill="#FFFFFF" opacity="0.5" />
      </g>
    </svg>
  );
};

export default DanaseaAiMascot;
