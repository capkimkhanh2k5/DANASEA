import React, { useState, useRef } from 'react';
import {
  Paperclip,
  Send,
  X,
  Loader2,
} from 'lucide-react';
import { useVendorCopilotStore } from '../../../store/useVendorCopilotStore';
import { VendorContextPinModal } from './VendorContextPinModal';

interface ActionDeckProps {
  onSend?: (text: string) => void;
  placeholder?: string;
  disabled?: boolean;
  isCompact?: boolean;
}

export const VendorCopilotActionDeck: React.FC<ActionDeckProps> = ({
  onSend,
  placeholder,
  disabled = false,
}) => {
  const {
    isLoading,
    attachedContext,
    setAttachedContext,
    clearAttachedContext,
    sendMessage,
  } = useVendorCopilotStore();

  const [input, setInput] = useState('');
  const [showPinModal, setShowPinModal] = useState(false);
  const textInputRef = useRef<HTMLInputElement>(null);

  const handleSubmit = (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    const clean = input.trim();
    if (!clean) return;
    if (isLoading || disabled) return;

    setInput('');
    if (onSend) {
      onSend(clean);
    } else {
      sendMessage(clean);
    }
  };

  return (
    <div className="w-full relative flex flex-col gap-2">
      {/* Modal: Ghim ngữ cảnh (8 loại đối tượng nghiệp vụ với danh sách chọn trực quan) */}
      <VendorContextPinModal
        isOpen={showPinModal}
        onClose={() => setShowPinModal(false)}
        onSelectContext={(ctx) => {
          setAttachedContext(ctx);
          textInputRef.current?.focus();
        }}
      />

      {/* Pinned Context Chip */}
      {attachedContext && (
        <div className="flex flex-wrap items-center gap-2 px-1">
          <div className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg bg-sky-50 border border-sky-200/80 text-sky-800 text-xs font-medium shadow-2xs">
            <Paperclip className="w-3 h-3 text-sky-600 shrink-0" />
            <span className="truncate max-w-[240px]">{attachedContext.label}</span>
            <button
              type="button"
              onClick={clearAttachedContext}
              className="text-sky-400 hover:text-sky-700 p-0.5 rounded transition-colors cursor-pointer ml-0.5"
              title="Gỡ ngữ cảnh"
            >
              <X className="w-3 h-3" />
            </button>
          </div>
        </div>
      )}

      {/* Main Input Row (Clean, Professional & Native to Danasea Design) */}
      <form onSubmit={handleSubmit} className="flex items-center gap-2 w-full">
        {/* Kẹp ghim (Paperclip) - Ghim đối tượng / ngữ cảnh 8 phân hệ */}
        <button
          type="button"
          onClick={() => setShowPinModal(true)}
          className={`p-2.5 rounded-xl border transition-colors cursor-pointer shrink-0 shadow-2xs ${
            attachedContext
              ? 'bg-sky-50 border-sky-300 text-sky-700'
              : 'bg-white border-slate-200/80 text-slate-500 hover:text-slate-800 hover:bg-slate-100'
          }`}
          title="Ghim ngữ cảnh đối tượng (Đơn hàng, dịch vụ, đánh giá...)"
        >
          <Paperclip className={`w-4 h-4 ${attachedContext ? 'text-sky-600' : 'text-slate-500'}`} />
        </button>

        {/* Text Input */}
        <input
          ref={textInputRef}
          type="text"
          value={input}
          onChange={(e) => setInput(e.target.value)}
          placeholder={
            placeholder ||
            (attachedContext
              ? `Hỏi Copilot về ${attachedContext.label}...`
              : 'Hỏi doanh thu, đơn hàng hôm nay, ca tour vắng...')
          }
          disabled={isLoading || disabled}
          className="flex-1 text-xs bg-slate-50 border border-slate-200/90 rounded-xl px-4 py-2.5 text-slate-900 focus:outline-none focus:ring-1 focus:ring-sky-500 focus:border-sky-500 placeholder-slate-400 transition-all shadow-2xs"
        />

        {/* Send Button */}
        <button
          type="submit"
          disabled={!input.trim() || isLoading || disabled}
          className="p-2.5 rounded-xl bg-slate-900 text-white hover:bg-slate-800 disabled:opacity-40 disabled:cursor-not-allowed transition-colors cursor-pointer shrink-0 shadow-xs"
          title="Gửi câu hỏi"
        >
          {isLoading ? <Loader2 className="w-4 h-4 animate-spin text-white" /> : <Send className="w-4 h-4" />}
        </button>
      </form>
    </div>
  );
};
