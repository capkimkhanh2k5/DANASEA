/**
 * Image Optimizer Utility for DANASEA
 * Specializing in high-fidelity Cloudinary dynamic transformations, AI smart cropping,
 * upscaling, and safe fallbacks for full-width Hero sections and catalog cards.
 */

export interface CloudinaryTransformOptions {
  width?: number;
  height?: number;
  aspectRatio?: string; // e.g. '16:9', '4:3', '1:1'
  crop?: 'fill' | 'limit' | 'fit' | 'thumb' | 'scale';
  gravity?: 'auto' | 'face' | 'center';
  quality?: 'auto' | 'auto:best' | 'auto:good' | 'auto:eco';
  format?: 'auto' | 'webp' | 'avif';
  upscale?: boolean;
  sharpen?: boolean | number;
}

// Local high-res scenic ocean fallbacks (stored directly in /public/images/scenes)
export const FALLBACK_HERO_IMAGE = '/images/scenes/brandon-mcdonald-_oU62drqdho-unsplash.jpg';
export const FALLBACK_CARD_IMAGE = '/images/scenes/sean-oulashin-KMn4VEeEPR8-unsplash.jpg';

/**
 * Transforms a raw Cloudinary URL with dynamic parameters for optimal sharpness and performance.
 * Non-Cloudinary URLs are preserved safely without alteration.
 */
export function optimizeCloudinaryUrl(
  url?: string | null,
  options: CloudinaryTransformOptions = {}
): string {
  if (!url || typeof url !== 'string' || !url.trim()) {
    return FALLBACK_HERO_IMAGE;
  }

  const cleanUrl = url.trim();

  // If not a Cloudinary upload URL, return cleanUrl as-is
  if (!cleanUrl.includes('res.cloudinary.com') || !cleanUrl.includes('/upload/')) {
    return cleanUrl;
  }

  // Build transformation chain
  const transforms: string[] = [];

  // 1. Auto format (modern AVIF/WebP) & Auto Quality
  transforms.push(`f_${options.format || 'auto'}`);
  transforms.push(`q_${options.quality || 'auto:best'}`);

  // 2. Aspect ratio & Crop mode
  if (options.aspectRatio) {
    transforms.push(`ar_${options.aspectRatio}`);
  }
  if (options.crop) {
    transforms.push(`c_${options.crop}`);
  } else if (options.width || options.height) {
    transforms.push('c_fill');
  }

  // 3. AI Smart Gravity (centers on action / humans / water craft)
  if (options.gravity) {
    transforms.push(`g_${options.gravity}`);
  } else if (options.crop === 'fill' || (!options.crop && (options.width || options.height))) {
    transforms.push('g_auto');
  }

  // 4. Geometry dimensions
  if (options.width) {
    transforms.push(`w_${options.width}`);
  }
  if (options.height) {
    transforms.push(`h_${options.height}`);
  }

  // 5. AI Super-Resolution Upscaling (for smaller source images)
  if (options.upscale) {
    transforms.push('e_upscale');
  }

  // 6. Edge Sharpening (enhances water ripples, foam, and spray detail)
  if (options.sharpen) {
    const sharpenAmount = typeof options.sharpen === 'number' ? options.sharpen : 60;
    transforms.push(`e_sharpen:${sharpenAmount}`);
  }

  const transformString = transforms.join(',');

  // Prevent double-injecting transformations if already present
  // Regex looks for /upload/ followed optionally by existing transformations
  const uploadToken = '/upload/';
  const uploadIndex = cleanUrl.indexOf(uploadToken);
  if (uploadIndex === -1) return cleanUrl;

  const prefix = cleanUrl.substring(0, uploadIndex + uploadToken.length);
  const rest = cleanUrl.substring(uploadIndex + uploadToken.length);

  // If URL already has existing transformations right after /upload/, replace or append
  const match = rest.match(/^(?:(?:[a-z]_[a-zA-Z0-9_:-]+,?)+\/)?(.*)$/);
  const remainingPath = match && match[1] ? match[1] : rest;

  return `${prefix}${transformString}/${remainingPath}`;
}

/**
 * Preset for Large Hero Banners (Full-width, 16:9, Ultra-sharp 1920px)
 */
export function getHeroImageUrl(url?: string | null, fallback = FALLBACK_HERO_IMAGE): string {
  if (!url || !url.trim()) return fallback;
  return optimizeCloudinaryUrl(url, {
    width: 1920,
    height: 1080,
    aspectRatio: '16:9',
    crop: 'fill',
    gravity: 'auto',
    quality: 'auto:best',
    sharpen: 50,
  });
}

/**
 * Preset for Product / Experience Activity Cards (Compact 600x400 with smart crop)
 */
export function getCardThumbnailUrl(url?: string | null, fallback = FALLBACK_CARD_IMAGE): string {
  if (!url || !url.trim()) return fallback;
  return optimizeCloudinaryUrl(url, {
    width: 600,
    height: 400,
    aspectRatio: '3:2',
    crop: 'fill',
    gravity: 'auto',
    quality: 'auto:good',
  });
}
