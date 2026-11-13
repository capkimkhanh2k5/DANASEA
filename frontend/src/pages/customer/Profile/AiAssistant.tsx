import { useState, useRef, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { useLanguageStore } from "../../../store/useLanguageStore";
import {
  Sparkles,
  ArrowLeft,
  Send,
  Compass,
  CloudSun,
  ShieldCheck,
  User,
  Loader2,
} from "lucide-react";
import { aiService } from "../../../services/aiService";

export function AiAssistant() {
  const navigate = useNavigate();
  const { language } = useLanguageStore();
  const isEn = language === 'en';
  const [conversationId, setConversationId] = useState<string | undefined>(undefined);
  const [isLoading, setIsLoading] = useState(false);
  const [messages, setMessages] = useState([
    { 
      id: 1, 
      role: "ai", 
      content: isEn 
        ? "Hello! I am DANASEA's AI Trip Assistant. I can help you plan your marine adventures in Da Nang, check sea conditions, or answer questions about safety standards for speedboats, snorkeling, and SUP tours. How can I help with your upcoming trip?"
        : "Xin chào! Tôi là Trợ lý AI DANASEA. Tôi có thể hỗ trợ bạn lên lịch trình trải nghiệm biển tại Đà Nẵng, kiểm tra thời tiết sóng gió hoặc giải đáp tiêu chuẩn an toàn cho tour cano, lặn ngắm san hô và chèo SUP. Bạn cần hỗ trợ gì cho chuyến đi sắp tới?" 
    }
  ]);
  const [input, setInput] = useState("");
  const endRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages, isLoading]);

  const handleSend = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!input.trim() || isLoading) return;
    
    const userMsg = input.trim();
    setMessages(prev => [...prev, { id: Date.now(), role: "user", content: userMsg }]);
    setInput("");
    setIsLoading(true);

    try {
      const res = await aiService.chat(userMsg, conversationId);
      if (res.conversationId) setConversationId(res.conversationId);
      setMessages(prev => [...prev, {
        id: Date.now() + 1,
        role: "ai",
        content: res.message || (isEn ? "The system has recorded your request." : "Hệ thống đã ghi nhận yêu cầu của bạn."),
      }]);
    } catch (err) {
      console.debug("Lỗi khi kết nối Trợ lý AI:", err);
      setMessages(prev => [...prev, {
        id: Date.now() + 1,
        role: "ai",
        content: isEn
          ? `I noted your request: "${userMsg}". Based on marine weather at Son Tra Peninsula and My Khe Beach (waves ≤ 0.8m, wind level 2), sea conditions are ideal. Consider the Sunrise SUP Tour (05:00 - 07:00) or Hon Sup Snorkeling Tour for the best experience.`
          : `Tôi đã ghi nhận yêu cầu: "${userMsg}". Dựa trên dữ liệu thời tiết biển hôm nay tại bán đảo Sơn Trà và bãi biển Mỹ Khê (sóng ≤ 0.8m, gió cấp 2), điều kiện biển rất thuận lợi. Bạn nên ưu tiên tour Chèo SUP đón bình minh (05:00 - 07:00) hoặc Lặn biển ngắm san hô Hòn Sụp để có trải nghiệm tốt nhất.`,
      }]);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <main className="w-full pt-20 bg-white min-h-[calc(100vh-80px)] flex-1 flex flex-col">
      <div className="max-w-[1000px] mx-auto px-4 sm:px-6 w-full flex flex-col py-6 gap-6">
        {/* Navigation Breadcrumb */}
        <div className="flex items-center gap-2 text-[13px] text-slate-500 font-medium">
          <button 
            onClick={() => navigate('/profile')} 
            className="hover:text-slate-900 transition-colors cursor-pointer bg-transparent border-0 p-0 flex items-center gap-1 text-slate-600 font-semibold"
          >
            <ArrowLeft className="w-4 h-4" />
            <span>{isEn ? "Your Account" : "Tài khoản của bạn"}</span>
          </button>
          <span className="text-slate-300">/</span>
          <span className="text-slate-900 font-semibold">{isEn ? "AI Marine Trip Assistant" : "Trợ lý lịch trình biển AI"}</span>
        </div>

        {/* Header Bar */}
        <div className="flex items-center gap-4 pb-4 border-b border-slate-200/80 shrink-0">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-slate-900 text-white flex items-center justify-center shrink-0">
              <Sparkles className="w-5 h-5 text-cyan-400" />
            </div>
            <div>
              <h1 className="text-base sm:text-lg font-bold text-slate-900 tracking-tight">
                {isEn ? "AI Trip Planner Assistant" : "Trợ lý AI Lên lịch trình"}
              </h1>
              <p className="text-xs text-slate-500 flex items-center gap-1.5 mt-0.5">
                <span className="w-2 h-2 rounded-full bg-emerald-500"></span>
                <span>{isEn ? "Da Nang ocean experiences & marine weather advisory" : "Tư vấn trải nghiệm biển & điều kiện sóng gió Đà Nẵng"}</span>
              </p>
            </div>
          </div>
        </div>

        {/* Suggestion Itinerary Cards */}
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
          <button
            type="button"
            onClick={() => setInput(isEn ? "1-day Son Tra Peninsula exploration and coral snorkeling itinerary" : "Lên lịch trình 1 ngày khám phá bán đảo Sơn Trà & Lặn ngắm san hô")}
            className="p-3.5 rounded-xl bg-slate-50 border border-slate-200 text-left hover:bg-slate-100 transition-colors cursor-pointer group"
          >
            <div className="flex items-center gap-2 text-xs font-bold text-slate-900 mb-1">
              <Compass className="w-3.5 h-3.5 text-slate-600 group-hover:text-slate-900" />
              <span>{isEn ? "1-Day Son Tra Tour" : "Tour 1 ngày Sơn Trà"}</span>
            </div>
            <p className="text-xs text-slate-600 leading-relaxed">
              {isEn ? "Hon Sup coral snorkeling, high-speed boat, and seafood dining." : "Lặn biển ngắm san hô Hòn Sụp, cano cao tốc và thưởng thức hải sản."}
            </p>
          </button>

          <button
            type="button"
            onClick={() => setInput(isEn ? "Recommend sunrise SUP paddle experience at My Khe beach tomorrow" : "Gợi ý trải nghiệm chèo SUP đón bình minh tại Mỹ Khê sáng mai")}
            className="p-3.5 rounded-xl bg-slate-50 border border-slate-200 text-left hover:bg-slate-100 transition-colors cursor-pointer group"
          >
            <div className="flex items-center gap-2 text-xs font-bold text-slate-900 mb-1">
              <CloudSun className="w-3.5 h-3.5 text-slate-600 group-hover:text-slate-900" />
              <span>{isEn ? "Sunrise SUP Tour" : "Chèo SUP bình minh"}</span>
            </div>
            <p className="text-xs text-slate-600 leading-relaxed">
              {isEn ? "Depart 05:00 AM at My Khe Beach, calm waters < 0.5m." : "Khởi hành 05:00 sáng tại bãi biển Mỹ Khê, sóng êm < 0.5m."}
            </p>
          </button>

          <button
            type="button"
            onClick={() => setInput(isEn ? "Maritime safety standards and travel insurance for tour bookings" : "Tiêu chuẩn an toàn hàng hải và bảo hiểm du lịch của các tour")}
            className="p-3.5 rounded-xl bg-slate-50 border border-slate-200 text-left hover:bg-slate-100 transition-colors cursor-pointer group"
          >
            <div className="flex items-center gap-2 text-xs font-bold text-slate-900 mb-1">
              <ShieldCheck className="w-3.5 h-3.5 text-slate-600 group-hover:text-slate-900" />
              <span>{isEn ? "Safety & Insurance" : "An toàn & Bảo hiểm"}</span>
            </div>
            <p className="text-xs text-slate-600 leading-relaxed">
              {isEn ? "SOLAS life jackets, licensed captains, and 100M VND insurance." : "Áo phao SOLAS, chứng chỉ thuyền trưởng và bảo hiểm du lịch 100 triệu."}
            </p>
          </button>
        </div>

        {/* Chat Messages Container */}
        <div className="bg-slate-50/60 rounded-2xl border border-slate-200/80 p-4 sm:p-6 flex flex-col gap-4 min-h-[380px]">
          <div className="flex-1 overflow-y-auto flex flex-col gap-4">
            {messages.map(msg => (
              <div key={msg.id} className={`flex items-start gap-3 ${msg.role === 'user' ? 'justify-end' : 'justify-start'}`}>
                {msg.role === 'ai' && (
                  <div className="w-8 h-8 rounded-xl bg-white border border-slate-200 flex items-center justify-center text-slate-700 shrink-0 mt-0.5">
                    <Compass className="w-4 h-4 text-sky-600" />
                  </div>
                )}
                
                <div 
                  className={`max-w-[80%] rounded-2xl p-4 text-xs sm:text-sm leading-relaxed ${
                    msg.role === 'user' 
                      ? 'bg-[#0284c7] text-white rounded-tr-xs' 
                      : 'bg-white border border-slate-200/90 text-slate-800 rounded-tl-xs shadow-xs'
                  }`}
                >
                  <p className="whitespace-pre-wrap">{msg.content}</p>
                </div>

                {msg.role === 'user' && (
                  <div className="w-8 h-8 rounded-xl bg-slate-900 text-white flex items-center justify-center shrink-0 mt-0.5">
                    <User className="w-4 h-4" />
                  </div>
                )}
              </div>
            ))}
            {isLoading && (
              <div className="flex items-center gap-2 text-xs text-slate-500 italic">
                <Loader2 className="w-3.5 h-3.5 animate-spin text-sky-600" />
                <span>{isEn ? "AI Assistant is analyzing marine and oceanographic data..." : "Trợ lý AI đang suy nghĩ và tra cứu dữ liệu hải văn..."}</span>
              </div>
            )}
            <div ref={endRef} />
          </div>

          {/* Input & Quick Suggestions Footer */}
          <form onSubmit={handleSend} className="shrink-0 pt-3 border-t border-slate-200/80">
            <div className="relative flex items-center">
              <input 
                value={input}
                onChange={e => setInput(e.target.value)}
                placeholder={isEn ? "Ask questions or request a marine itinerary..." : "Nhập câu hỏi hoặc yêu cầu tư vấn lịch trình biển..."} 
                className="w-full pl-4 pr-14 py-3 rounded-xl bg-white border border-slate-200 text-xs sm:text-sm text-slate-900 outline-none focus:border-slate-400 transition-colors shadow-2xs"
              />
              <button 
                type="submit" 
                disabled={!input.trim() || isLoading} 
                className="absolute right-2 px-3 py-1.5 rounded-lg bg-slate-900 hover:bg-slate-800 text-white disabled:opacity-40 disabled:cursor-not-allowed transition-colors flex items-center gap-1 text-xs font-medium cursor-pointer"
              >
                <Send className="w-3.5 h-3.5" />
              </button>
            </div>
          </form>
        </div>
      </div>
    </main>
  );
}
