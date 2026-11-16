import React from 'react';

export interface BlockCode {
  type: 'code';
  lang: string;
  code: string;
}

export interface BlockHeading {
  type: 'h1' | 'h2' | 'h3';
  content: string;
}

export interface BlockBulletList {
  type: 'bullet-list';
  items: string[];
}

export interface BlockNumberedList {
  type: 'numbered-list';
  items: Array<{ num: string; content: string }>;
}

export interface BlockParagraph {
  type: 'p';
  content: string;
}

export type MarkdownBlock =
  | BlockCode
  | BlockHeading
  | BlockBulletList
  | BlockNumberedList
  | BlockParagraph;

export const INLINE_REGEX = /(\[[^\]]+\]\([^)]+\)|\*\*[^*]+\*\*|`[^`]+`|\*[^*]+\*)/g;

export function renderInline(text: string, tone: 'default' | 'invert'): React.ReactNode[] {
  if (!text) return [];

  const parts = text.split(INLINE_REGEX);

  return parts.filter(Boolean).map((part, idx) => {
    // 1. Link [text](url)
    if (part.startsWith('[') && part.includes('](') && part.endsWith(')')) {
      const match = part.match(/^\[([^\]]+)\]\(([^)]+)\)$/);
      if (match) {
        const [, label, url] = match;
        const linkClass =
          tone === 'invert'
            ? 'text-sky-200 hover:text-white underline font-medium'
            : 'text-sky-600 hover:text-sky-800 underline font-medium';
        return (
          <a
            key={idx}
            href={url}
            target="_blank"
            rel="noopener noreferrer"
            className={linkClass}
          >
            {label}
          </a>
        );
      }
    }

    // 2. Bold **text**
    if (part.startsWith('**') && part.endsWith('**') && part.length >= 4) {
      const boldClass =
        tone === 'invert' ? 'font-semibold text-white' : 'font-semibold text-slate-900';
      return (
        <strong key={idx} className={boldClass}>
          {part.slice(2, -2)}
        </strong>
      );
    }

    // 3. Inline code `text`
    if (part.startsWith('`') && part.endsWith('`') && part.length >= 2) {
      const codeClass =
        tone === 'invert'
          ? 'px-1.5 py-0.5 rounded bg-white/20 text-white font-mono text-[11px]'
          : 'px-1.5 py-0.5 rounded bg-slate-100 text-slate-800 font-mono text-[11px] border border-slate-200/60';
      return (
        <code key={idx} className={codeClass}>
          {part.slice(1, -1)}
        </code>
      );
    }

    // 4. Italic *text*
    if (part.startsWith('*') && part.endsWith('*') && part.length >= 2 && !part.startsWith('**')) {
      return (
        <em key={idx} className="italic">
          {part.slice(1, -1)}
        </em>
      );
    }

    // Normal text
    return <React.Fragment key={idx}>{part}</React.Fragment>;
  });
}

export function parseBlocks(raw: string): MarkdownBlock[] {
  if (!raw) return [];

  // 1. Separate fenced code blocks first
  const codeBlockRegex = /(```[\s\S]*?```)/g;
  const sections = raw.split(codeBlockRegex);

  const blocks: MarkdownBlock[] = [];

  sections.forEach((section) => {
    if (!section) return;

    if (section.startsWith('```') && section.endsWith('```')) {
      const inner = section.slice(3, -3);
      const newlineIndex = inner.indexOf('\n');
      const lang = newlineIndex !== -1 ? inner.slice(0, newlineIndex).trim() : '';
      const code = newlineIndex !== -1 ? inner.slice(newlineIndex + 1) : inner;
      blocks.push({ type: 'code', lang, code: code.trim() });
      return;
    }

    const lines = section.split('\n');
    let currentList: BlockBulletList | BlockNumberedList | null = null;

    lines.forEach((line) => {
      const trimmed = line.trim();

      if (!trimmed) {
        if (currentList) {
          blocks.push(currentList);
          currentList = null;
        }
        return;
      }

      // Bullet item: "- " or "* " followed by text
      if (/^[-*]\s+/.test(trimmed)) {
        const itemContent = trimmed.replace(/^[-*]\s+/, '');
        if (!currentList || currentList.type !== 'bullet-list') {
          if (currentList) blocks.push(currentList);
          currentList = { type: 'bullet-list', items: [] };
        }
        currentList.items.push(itemContent);
        return;
      }

      // Numbered item: "1. " followed by text
      if (/^\d+\.\s+/.test(trimmed)) {
        const match = trimmed.match(/^(\d+)\.\s+(.*)$/);
        if (match) {
          if (!currentList || currentList.type !== 'numbered-list') {
            if (currentList) blocks.push(currentList);
            currentList = { type: 'numbered-list', items: [] };
          }
          currentList.items.push({ num: match[1], content: match[2] });
          return;
        }
      }

      if (currentList) {
        blocks.push(currentList);
        currentList = null;
      }

      if (trimmed.startsWith('### ')) {
        blocks.push({ type: 'h3', content: trimmed.replace(/^###\s+/, '') });
      } else if (trimmed.startsWith('## ')) {
        blocks.push({ type: 'h2', content: trimmed.replace(/^##\s+/, '') });
      } else if (trimmed.startsWith('# ')) {
        blocks.push({ type: 'h1', content: trimmed.replace(/^#\s+/, '') });
      } else {
        blocks.push({ type: 'p', content: line });
      }
    });

    if (currentList) {
      blocks.push(currentList);
    }
  });

  return blocks;
}
