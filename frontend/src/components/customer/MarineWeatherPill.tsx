import React, { useEffect, useState } from 'react';
import { useLanguageStore } from '../../store/useLanguageStore';
import { weatherService } from '../../services/weatherService';
import type { WeatherInfoDto, MarineSafetySummary } from '../../types/weather';
import { Thermometer, Waves, Wind } from 'lucide-react';

interface MarineWeatherPillProps {
  className?: string;
}

export const MarineWeatherPill: React.FC<MarineWeatherPillProps> = ({ className = '' }) => {
  const { language } = useLanguageStore();
  const [data, setData] = useState<WeatherInfoDto | null>(null);
  const [safety, setSafety] = useState<MarineSafetySummary | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [hasError, setHasError] = useState<boolean>(false);

  useEffect(() => {
    let isMounted = true;

    const fetchWeather = async () => {
      try {
        setIsLoading(true);
        setHasError(false);
        const result = await weatherService.getCurrentWeather();
        if (isMounted) {
          setData(result);
          setSafety(weatherService.evaluateSafety(result));
        }
      } catch (err) {
        if (isMounted) {
          console.warn('[MarineWeatherPill] Không thể nạp dữ liệu thời tiết biển, ẩn an toàn:', err);
          setHasError(true);
        }
      } finally {
        if (isMounted) {
          setIsLoading(false);
        }
      }
    };

    fetchWeather();

    return () => {
      isMounted = false;
    };
  }, []);

  // Gracefully hide if error occurs to avoid breaking layout
  if (hasError) {
    return null;
  }

  // Loading Skeleton Pill
  if (isLoading || !data) {
    return (
      <div
        className={`inline-flex items-center gap-3 px-3.5 py-1.5 sm:px-4 sm:py-2 rounded-xl bg-white border border-slate-200/80 ${className}`}
      >
        <div className="w-2.5 h-2.5 rounded-full bg-slate-300" />
        <span className="w-px h-3 bg-slate-200" />
        <div className="w-10 h-3 rounded bg-slate-200" />
        <span className="w-px h-3 bg-slate-200" />
        <div className="w-10 h-3 rounded bg-slate-200" />
        <span className="w-px h-3 bg-slate-200" />
        <div className="w-12 h-3 rounded bg-slate-200" />
      </div>
    );
  }

  const temp = Math.round(data.weather?.temperature ?? 0);
  const wave = (data.marine?.waveHeight ?? 0).toFixed(1);
  const wind = (data.weather?.windSpeed ?? 0).toFixed(1);

  // Status dot styling based on safety level
  const statusColor =
    safety?.level === 'RED'
      ? 'bg-rose-500'
      : safety?.level === 'YELLOW'
      ? 'bg-amber-500'
      : 'bg-emerald-500';

  const statusLabel = language === 'en' ? safety?.statusLabelEn : safety?.statusLabel;

  return (
    <div
      className={`inline-flex items-center flex-wrap sm:flex-nowrap justify-center gap-2.5 sm:gap-3 px-3.5 sm:px-4 py-1.5 sm:py-2 rounded-xl bg-white border border-slate-200/90 text-[13px] text-slate-800 transition-all select-none cursor-default ${className}`}
    >
      {/* 1. Status Indicator Dot */}
      <span
        title={statusLabel}
        className={`w-2.5 h-2.5 rounded-full ${statusColor} shrink-0`}
      />

      <span className="w-px h-3 bg-slate-200" />

      {/* 2. Temperature */}
      <div className="flex items-center gap-1.5 text-slate-700 font-medium">
        <Thermometer className="w-3.5 h-3.5 text-amber-500 shrink-0" />
        <span>{temp}°C</span>
      </div>

      <span className="w-px h-3 bg-slate-200" />

      {/* 3. Wave Height */}
      <div className="flex items-center gap-1.5 text-slate-700 font-medium">
        <Waves className="w-3.5 h-3.5 text-cyan-500 shrink-0" />
        <span>{wave} m</span>
      </div>

      <span className="w-px h-3 bg-slate-200" />

      {/* 4. Wind Speed */}
      <div className="flex items-center gap-1.5 text-slate-700 font-medium">
        <Wind className="w-3.5 h-3.5 text-teal-500 shrink-0" />
        <span>{wind} km/h</span>
      </div>
    </div>
  );
};
