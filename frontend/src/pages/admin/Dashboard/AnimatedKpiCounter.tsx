import { useEffect, useRef, useState } from 'react';

export function AnimatedKpiCounter({
  value,
  formatter,
  unavailable,
}: {
  value: number;
  formatter: (n: number) => string;
  unavailable: boolean;
}) {
  const [displayValue, setDisplayValue] = useState(0);
  const prevValueRef = useRef<number>(0);
  const rafRef = useRef<number | null>(null);

  useEffect(() => {
    if (unavailable) {
      prevValueRef.current = 0;
      return;
    }

    if (typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      prevValueRef.current = value;
      return;
    }

    const startValue = prevValueRef.current;
    const endValue = value;
    if (startValue === endValue && displayValue === endValue) return;

    const duration = 1000; // 1000ms for clearly visible and deliberate count-up
    const startTime = performance.now();

    const animate = (currentTime: number) => {
      const elapsed = currentTime - startTime;
      const progress = Math.min(elapsed / duration, 1);
      // easeOutExpo curve
      const eased = progress === 1 ? 1 : 1 - Math.pow(2, -10 * progress);
      const current = Math.round(startValue + (endValue - startValue) * eased);
      setDisplayValue(current);

      if (progress < 1) {
        rafRef.current = requestAnimationFrame(animate);
      } else {
        prevValueRef.current = endValue;
        setDisplayValue(endValue);
      }
    };

    rafRef.current = requestAnimationFrame(animate);
    return () => {
      if (rafRef.current) {
        cancelAnimationFrame(rafRef.current);
      }
    };
  }, [value, unavailable]); // eslint-disable-line react-hooks/exhaustive-deps

  if (unavailable) {
    return <>—</>;
  }

  if (typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    return <>{formatter(value)}</>;
  }

  return <>{formatter(displayValue)}</>;
}
