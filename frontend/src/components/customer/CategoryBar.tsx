import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useLanguageStore } from '../../store/useLanguageStore';
import { categoryService } from '../../services/categoryService';
import type { CategoryDto } from '../../types/category';

interface CategoryBarProps {
  className?: string;
}

// Icon mapping fallback for Material Symbols
const ICON_MAP: Record<string, string> = {
  'cheo-sup-kayak': 'surfing',
  'lan-ngam-san-ho': 'scuba_diving',
  'cano-du-bay': 'speed',
  'mo-to-nuoc-jetski': 'waves',
  'truot-phao-chuoi': 'sports_kabaddi',
  'du-thuyen-ngam-hoang-hon': 'sailing',
};

export const CategoryBar: React.FC<CategoryBarProps> = ({ className = '' }) => {
  const { language } = useLanguageStore();
  const navigate = useNavigate();
  const [categories, setCategories] = useState<CategoryDto[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(true);

  useEffect(() => {
    let isMounted = true;

    const fetchCategories = async () => {
      try {
        setIsLoading(true);
        const data = await categoryService.getCategories();
        if (isMounted) {
          setCategories(data);
        }
      } catch (err) {
        console.warn('[CategoryBar] Không thể nạp danh mục:', err);
      } finally {
        if (isMounted) {
          setIsLoading(false);
        }
      }
    };

    fetchCategories();

    return () => {
      isMounted = false;
    };
  }, [language]);

  const handleCategoryClick = (slug: string) => {
    navigate(`/search?category=${encodeURIComponent(slug)}`);
  };

  // Skeleton loading state
  if (isLoading) {
    return (
      <div className={`flex items-center justify-center gap-2 sm:gap-3 overflow-x-auto no-scrollbar py-2 ${className}`}>
        {Array.from({ length: 6 }).map((_, idx) => (
          <div
            key={`cat-skel-${idx}`}
            className="flex items-center gap-2 px-3.5 py-2 rounded-full bg-white/60 dark:bg-slate-800/60 border border-slate-200/50 animate-pulse shrink-0"
          >
            <div className="w-4 h-4 rounded-full bg-slate-300 dark:bg-slate-700" />
            <div className="w-16 sm:w-20 h-3.5 rounded bg-slate-300 dark:bg-slate-700" />
          </div>
        ))}
      </div>
    );
  }

  if (categories.length === 0) {
    return null;
  }

  return (
    <div className={`flex items-center justify-start sm:justify-center gap-2 sm:gap-3 overflow-x-auto no-scrollbar py-2 px-2 select-none ${className}`}>
      {categories.map((cat) => {
        const iconName = cat.iconUrl || ICON_MAP[cat.slug] || 'waves';
        const label = language === 'en' ? (cat.nameEn || cat.name) : cat.name;

        return (
          <button
            key={cat.id}
            type="button"
            onClick={() => handleCategoryClick(cat.slug)}
            className="group flex items-center gap-2 px-3.5 sm:px-4 py-2 rounded-full bg-white/90 dark:bg-slate-900/80 backdrop-blur-md border border-slate-200/80 dark:border-slate-700 shadow-xs hover:shadow-md hover:border-cyan-500 hover:text-cyan-700 dark:hover:text-cyan-400 hover:scale-[1.02] active:scale-95 transition-all text-xs sm:text-sm font-medium text-slate-700 dark:text-slate-200 shrink-0 cursor-pointer"
          >
            <span className="material-symbols-outlined text-[18px] text-cyan-600 dark:text-cyan-400 group-hover:rotate-6 transition-transform">
              {iconName}
            </span>
            <span className="whitespace-nowrap">{label}</span>
          </button>
        );
      })}
    </div>
  );
};
