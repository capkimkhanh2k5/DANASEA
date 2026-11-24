import React from 'react';
import { parseBlocks, renderInline } from './markdownParser';
import { formatTextWithDisplayCodes } from '../../utils/displayCode';

interface MarkdownTextProps {
  content?: string | null;
  className?: string;
  tone?: 'default' | 'invert';
}

export const MarkdownText: React.FC<MarkdownTextProps> = ({
  content,
  className = '',
  tone = 'default',
}) => {
  if (!content) return null;

  const formattedContent = formatTextWithDisplayCodes(content);
  const blocks = parseBlocks(formattedContent);
  const textClass = tone === 'invert' ? 'text-white/95' : 'text-slate-700';

  return (
    <div className={`space-y-2 text-xs leading-relaxed ${textClass} ${className}`}>
      {blocks.map((block, idx) => {
        if (block.type === 'code') {
          return (
            <pre
              key={idx}
              className="p-3 my-2 rounded-xl bg-slate-900 text-slate-100 font-mono text-[11px] overflow-x-auto shadow-2xs"
            >
              <code>{block.code}</code>
            </pre>
          );
        }

        if (block.type === 'h1' || block.type === 'h2' || block.type === 'h3') {
          const headingClass =
            tone === 'invert'
              ? 'font-bold text-white tracking-tight'
              : 'font-bold text-slate-900 tracking-tight';
          const sizeClass =
            block.type === 'h1' ? 'text-sm' : block.type === 'h2' ? 'text-[13px]' : 'text-xs';
          return (
            <div key={idx} className={`${headingClass} ${sizeClass} pt-1`}>
              {renderInline(block.content, tone)}
            </div>
          );
        }

        if (block.type === 'bullet-list') {
          const dotClass = tone === 'invert' ? 'bg-white/80' : 'bg-sky-600';
          return (
            <ul key={idx} className="space-y-1.5 my-1.5 pl-0 list-none">
              {block.items.map((item, itemIdx) => (
                <li key={itemIdx} className="flex items-start gap-2">
                  <span className={`w-1.5 h-1.5 rounded-full shrink-0 mt-1.5 ${dotClass}`} />
                  <span className="flex-1 min-w-0">{renderInline(item, tone)}</span>
                </li>
              ))}
            </ul>
          );
        }

        if (block.type === 'numbered-list') {
          const numClass =
            tone === 'invert'
              ? 'font-mono text-[11px] font-bold text-sky-200'
              : 'font-mono text-[11px] font-bold text-sky-700';
          return (
            <ol key={idx} className="space-y-1.5 my-1.5 pl-0 list-none">
              {block.items.map((item, itemIdx) => (
                <li key={itemIdx} className="flex items-start gap-2">
                  <span className={`shrink-0 mt-0.5 ${numClass}`}>{item.num}.</span>
                  <span className="flex-1 min-w-0">{renderInline(item.content, tone)}</span>
                </li>
              ))}
            </ol>
          );
        }

        // Paragraph
        return (
          <p key={idx} className="my-1">
            {renderInline(block.content, tone)}
          </p>
        );
      })}
    </div>
  );
};

export default MarkdownText;
