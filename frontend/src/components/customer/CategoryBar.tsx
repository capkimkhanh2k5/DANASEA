import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Waves, Anchor, Ship, Zap, LifeBuoy, Sunset } from 'lucide-react';
import { useLanguageStore } from '../../store/useLanguageStore';
import { categoryService } from '../../services/categoryService';
import type { CategoryDto } from '../../types/category';

interface CategoryBarProps {
  className?: string;
}

// Icon mapping fallback for Lucide icons
const renderCategoryIcon = (slug: string) => {
  switch (slug) {
    case 'cheo-sup-kayak':
      return <Waves className="w-4 h-4 text-cyan-600 shrink-0" />;
    case 'lan-ngam-san-ho':
      return <Anchor className="w-4 h-4 text-cyan-600 shrink-0" />;
    case 'cano-du-bay':
      return <Ship className="w-4 h-4 text-cyan-600 shrink-0" />;
    case 'mo-to-nuoc-jetski':
      return <Zap className="w-4 h-4 text-cyan-600 shrink-0" />;
    case 'truot-phao-chuoi':
      return <LifeBuoy className="w-4 h-4 text-cyan-600 shrink-0" />;
    case 'du-thuyen-ngam-hoang-hon':
      return <Sunset className="w-4 h-4 text-cyan-600 shrink-0" />;
    default:
      return <Waves className="w-4 h-4 text-cyan-600 shrink-0" />;
  }
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
      <div data-ui="category-bar" className={`flex items-center justify-center gap-2 sm:gap-3 overflow-x-auto no-scrollbar py-2 ${className}`}>
        {Array.from({ length: 6 }).map((_, idx) => (
          <div
            key={`cat-skel-${idx}`}
            className="flex items-center gap-2 px-3.5 py-2 rounded-xl bg-white border border-slate-200/80 animate-pulse shrink-0"
          >
            <div className="w-4 h-4 rounded-md bg-slate-200" />
            <div className="w-16 sm:w-20 h-3.5 rounded bg-slate-200" />
          </div>
        ))}
      </div>
    );
  }

  if (categories.length === 0) {
    return null;
  }

  return (
    <div data-ui="category-bar" className={`flex items-center justify-start sm:justify-center gap-2 sm:gap-3 overflow-x-auto no-scrollbar py-2 px-2 select-none ${className}`}>
      {categories.map((cat) => {
        const label = language === 'en' ? (cat.nameEn || cat.name) : cat.name;

        return (
          <button
            key={cat.id}
            type="button"
            onClick={() => handleCategoryClick(cat.slug)}
            className="group flex items-center gap-2 px-3.5 sm:px-4 py-2 rounded-xl bg-white border border-slate-200/90 hover:border-cyan-500 hover:text-cyan-700 hover:-translate-y-0.5 active:translate-y-0 transition-all text-[13px] sm:text-sm font-medium text-slate-800 shrink-0 cursor-pointer"
          >
            {renderCategoryIcon(cat.slug)}
            <span className="whitespace-nowrap">{label}</span>
          </button>
        );
      })}
    </div>
  );
};
export default CategoryBar;
