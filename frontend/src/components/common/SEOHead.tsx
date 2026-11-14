import { useEffect } from 'react';
import { useLanguageStore } from '../../store/useLanguageStore';
import { getSiteOrigin } from '../../utils/seo';

export interface SEOHeadProps {
  title?: string;
  description?: string;
  keywords?: string;
  canonicalUrl?: string;
  ogImage?: string;
  ogType?: 'website' | 'article' | 'product';
  noIndex?: boolean;
  jsonLd?: Record<string, unknown> | Array<Record<string, unknown>>;
}

const DEFAULT_DESCRIPTION_VI =
  'Khám phá và đặt vé các hoạt động thể thao biển, lặn ngắm san hô, chèo SUP, cano dù bay tại Đà Nẵng cùng DANASEA. Giá minh bạch, cảnh báo thời tiết AI và bảo hiểm an toàn.';
const DEFAULT_DESCRIPTION_EN =
  'Discover and book marine sports, coral diving, SUP paddleboarding, and parasailing in Da Nang with DANASEA. Transparent pricing, AI weather safety alerts, and full insurance.';

function setMetaTag(selector: string, attribute: string, attrName: string, value: string) {
  let element = document.querySelector(selector);
  if (!element) {
    element = document.createElement('meta');
    element.setAttribute(attribute, attrName);
    document.head.appendChild(element);
  }
  element.setAttribute('content', value);
}

function setCanonical(url: string) {
  let link = document.querySelector('link[rel="canonical"]') as HTMLLinkElement | null;
  if (!link) {
    link = document.createElement('link');
    link.rel = 'canonical';
    document.head.appendChild(link);
  }
  link.href = url;
}

export function SEOHead({
  title,
  description,
  keywords,
  canonicalUrl,
  ogImage,
  ogType = 'website',
  noIndex = false,
  jsonLd,
}: SEOHeadProps) {
  const { language } = useLanguageStore();

  useEffect(() => {
    const isEnglish = language === 'en';
    const baseUrl = getSiteOrigin();
    const defaultImage = `${baseUrl}/logo192.png`;
    const defaultDescription = isEnglish ? DEFAULT_DESCRIPTION_EN : DEFAULT_DESCRIPTION_VI;

    // 1. Title (Cố định tên tab là DANASEA theo yêu cầu)
    const formattedTitle = 'DANASEA';
    document.title = formattedTitle;

    // 2. Language attribute on <html>
    document.documentElement.lang = isEnglish ? 'en' : 'vi';

    // 3. Meta Description & Keywords
    const metaDescription = description || defaultDescription;
    setMetaTag('meta[name="description"]', 'name', 'description', metaDescription);

    if (keywords) {
      setMetaTag('meta[name="keywords"]', 'name', 'keywords', keywords);
    }

    // 4. Robots
    const robots = noIndex ? 'noindex, nofollow' : 'index, follow';
    setMetaTag('meta[name="robots"]', 'name', 'robots', robots);

    // 5. Canonical URL
    const canonical = canonicalUrl
      ? canonicalUrl.startsWith('http')
        ? canonicalUrl
        : `${baseUrl}${canonicalUrl.startsWith('/') ? canonicalUrl : `/${canonicalUrl}`}`
      : window.location.href;
    setCanonical(canonical);

    // 6. Open Graph Tags
    setMetaTag('meta[property="og:title"]', 'property', 'og:title', formattedTitle);
    setMetaTag('meta[property="og:description"]', 'property', 'og:description', metaDescription);
    setMetaTag('meta[property="og:url"]', 'property', 'og:url', canonical);
    setMetaTag('meta[property="og:type"]', 'property', 'og:type', ogType);
    setMetaTag('meta[property="og:image"]', 'property', 'og:image', ogImage || defaultImage);
    setMetaTag('meta[property="og:locale"]', 'property', 'og:locale', isEnglish ? 'en_US' : 'vi_VN');

    // 7. Twitter Card
    setMetaTag('meta[name="twitter:card"]', 'name', 'twitter:card', 'summary_large_image');
    setMetaTag('meta[name="twitter:title"]', 'name', 'twitter:title', formattedTitle);
    setMetaTag('meta[name="twitter:description"]', 'name', 'twitter:description', metaDescription);
    setMetaTag('meta[name="twitter:image"]', 'name', 'twitter:image', ogImage || defaultImage);

    // 8. JSON-LD Structured Data
    const existingScript = document.getElementById('danasea-page-jsonld');
    if (existingScript) {
      existingScript.remove();
    }

    if (jsonLd) {
      const script = document.createElement('script');
      script.id = 'danasea-page-jsonld';
      script.type = 'application/ld+json';
      script.textContent = JSON.stringify(jsonLd);
      document.head.appendChild(script);
    }

    return () => {
      const script = document.getElementById('danasea-page-jsonld');
      if (script) {
        script.remove();
      }
    };
  }, [title, description, keywords, canonicalUrl, ogImage, ogType, noIndex, jsonLd, language]);

  return null;
}
