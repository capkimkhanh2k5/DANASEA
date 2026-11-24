import React, { useState, useRef, useEffect } from 'react';
import {
  Paperclip,
  Image as ImageIcon,
  FileText,
  Send,
  X,
  Loader2,
} from 'lucide-react';
import { useVendorCopilotStore } from '../../../store/useVendorCopilotStore';
import { disputeService } from '../../../services/disputeService';
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
    attachedMedia,
    setAttachedContext,
    clearAttachedContext,
    addAttachedMedia,
    removeAttachedMedia,
    sendMessage,
  } = useVendorCopilotStore();

  const [input, setInput] = useState('');
  const [showAttachMenu, setShowAttachMenu] = useState(false);
  const [showPinModal, setShowPinModal] = useState(false);
  const [isUploading, setIsUploading] = useState(false);

  const menuRef = useRef<HTMLDivElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const docInputRef = useRef<HTMLInputElement>(null);
  const textInputRef = useRef<HTMLInputElement>(null);

  // Click outside to close attachment menu
  useEffect(() => {
    const handleDocClick = (e: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) {
        setShowAttachMenu(false);
      }
    };
    if (showAttachMenu) {
      document.addEventListener('mousedown', handleDocClick);
    }
    return () => {
      document.removeEventListener('mousedown', handleDocClick);
    };
  }, [showAttachMenu]);

  const handleFileUpload = async (e: React.ChangeEvent<HTMLInputElement>, isDoc = false) => {
    const files = e.target.files;
    if (!files || files.length === 0) return;
    const file = files[0];
    e.target.value = '';

    setIsUploading(true);
    setShowAttachMenu(false);
    try {
      const res = await disputeService.uploadEvidence(file);
      addAttachedMedia({
        id: 'media-' + Date.now(),
        type: isDoc ? 'file' : 'image',
        name: file.name,
        url: res.fileUrl,
        size: file.size,
        previewUrl: isDoc ? undefined : URL.createObjectURL(file),
      });
    } catch {
      addAttachedMedia({
        id: 'media-' + Date.now(),
        type: isDoc ? 'file' : 'image',
        name: file.name,
        size: file.size,
        previewUrl: isDoc ? undefined : URL.createObjectURL(file),
      });
    } finally {
      setIsUploading(false);
    }
  };

  const handleSubmit = (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    const clean = input.trim();
    if (!clean && attachedMedia.length === 0) return;
    if (isLoading || disabled) return;

    setInput('');
    if (onSend) {
      onSend(clean || 'Xin hãy phân tích và tư vấn cho các tệp đính kèm.');
    } else {
      sendMessage(clean || 'Xin hãy phân tích và tư vấn cho các tệp đính kèm.');
    }
  };

  return (
    <div className="w-full relative flex flex-col gap-2">
      {/* Hidden File Inputs */}
      <input
        ref={fileInputRef}
        type="file"
        accept="image/jpeg,image/png,image/webp"
        className="hidden"
        onChange={(e) => handleFileUpload(e, false)}
      />
      <input
        ref={docInputRef}
        type="file"
        accept="application/pdf,.doc,.docx"
        className="hidden"
        onChange={(e) => handleFileUpload(e, true)}
      />

      {/* Floating Light Attachment Menu (Paperclip Popover) */}
      {showAttachMenu && (
        <div
          ref={menuRef}
          className="absolute bottom-full mb-2 left-0 z-50 bg-white text-slate-800 rounded-xl shadow-xl border border-slate-200/90 p-1 w-48 animate-in fade-in slide-in-from-bottom-2 duration-150"
        >
          <button
            type="button"
            onClick={() => {
              setShowAttachMenu(false);
              fileInputRef.current?.click();
            }}
            className="w-full flex items-center gap-2.5 px-3 py-2 rounded-lg text-left text-xs text-slate-700 hover:bg-slate-100 hover:text-slate-900 transition-colors cursor-pointer"
          >
            <ImageIcon className="w-4 h-4 text-slate-500 shrink-0" />
            <span className="font-medium">Tải ảnh lên</span>
          </button>

          <button
            type="button"
            onClick={() => {
              setShowAttachMenu(false);
              docInputRef.current?.click();
            }}
            className="w-full flex items-center gap-2.5 px-3 py-2 rounded-lg text-left text-xs text-slate-700 hover:bg-slate-100 hover:text-slate-900 transition-colors cursor-pointer"
          >
            <FileText className="w-4 h-4 text-slate-500 shrink-0" />
            <span className="font-medium">Tệp tài liệu (PDF)</span>
          </button>
        </div>
      )}

      {/* Modal: Ghim ngữ cảnh (8 loại đối tượng nghiệp vụ với danh sách chọn trực quan) */}
      <VendorContextPinModal
        isOpen={showPinModal}
        onClose={() => setShowPinModal(false)}
        onSelectContext={(ctx) => {
          setAttachedContext(ctx);
          textInputRef.current?.focus();
        }}
      />

      {/* Previews & Chips Deck (Attached Context & Media) */}
      {(attachedContext || attachedMedia.length > 0) && (
        <div className="flex flex-wrap items-center gap-2 px-1">
          {/* Pinned Context Chip */}
          {attachedContext && (
            <div className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg bg-sky-50 border border-sky-200/80 text-sky-800 text-xs font-medium shadow-2xs">
              <Paperclip className="w-3 h-3 text-sky-600 shrink-0" />
              <span className="truncate max-w-[200px]">{attachedContext.label}</span>
              <button
                type="button"
                onClick={clearAttachedContext}
                className="text-sky-400 hover:text-sky-700 p-0.5 rounded transition-colors cursor-pointer ml-0.5"
                title="Gỡ ngữ cảnh"
              >
                <X className="w-3 h-3" />
              </button>
            </div>
          )}

          {/* Attached Media Chips */}
          {attachedMedia.map((m) => (
            <div
              key={m.id}
              className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg bg-slate-100 border border-slate-200 text-slate-700 text-xs shadow-2xs group"
            >
              {m.type === 'image' && m.previewUrl ? (
                <img src={m.previewUrl} alt={m.name} className="w-4 h-4 rounded object-cover shrink-0" />
              ) : (
                <FileText className="w-3 h-3 text-slate-500 shrink-0" />
              )}
              <span className="truncate max-w-[150px] font-mono text-[11px]">{m.name}</span>
              <button
                type="button"
                onClick={() => removeAttachedMedia(m.id)}
                className="text-slate-400 hover:text-rose-600 p-0.5 rounded transition-colors cursor-pointer"
                title="Gỡ tệp"
              >
                <X className="w-3 h-3" />
              </button>
            </div>
          ))}

          {isUploading && (
            <div className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg bg-slate-50 border border-slate-200 text-slate-500 text-xs">
              <Loader2 className="w-3 h-3 animate-spin text-sky-600" />
              <span className="text-[11px]">Đang tải tệp lên...</span>
            </div>
          )}
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

        {/* Đính kèm hình ảnh hoặc tài liệu bằng chứng */}
        <button
          type="button"
          onClick={() => setShowAttachMenu((prev) => !prev)}
          className={`p-2.5 rounded-xl border transition-colors cursor-pointer shrink-0 shadow-2xs ${
            showAttachMenu
              ? 'bg-slate-100 border-slate-300 text-slate-900'
              : 'bg-white border-slate-200/80 text-slate-500 hover:text-slate-800 hover:bg-slate-100'
          }`}
          title="Đính kèm hình ảnh hoặc tệp tài liệu"
        >
          <ImageIcon className="w-4 h-4" />
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
          disabled={(!input.trim() && attachedMedia.length === 0) || isLoading || disabled}
          className="p-2.5 rounded-xl bg-slate-900 text-white hover:bg-slate-800 disabled:opacity-40 disabled:cursor-not-allowed transition-colors cursor-pointer shrink-0 shadow-xs"
          title="Gửi câu hỏi"
        >
          {isLoading ? <Loader2 className="w-4 h-4 animate-spin text-white" /> : <Send className="w-4 h-4" />}
        </button>
      </form>
    </div>
  );
};
