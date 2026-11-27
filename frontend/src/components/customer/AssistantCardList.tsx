import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import { CheckCircle2, AlertTriangle, RefreshCw, XCircle } from 'lucide-react';
import {
  aiService,
  type AssistantAction,
  type AssistantCard,
  type BookingConfirmOutcome,
} from '../../services/aiService';
import { summarizeAssistantCard } from './assistantCardSummary';

interface AssistantCardListProps {
  cards?: AssistantCard[];
  conversationId?: string;
  language: 'vi' | 'en';
  onQuickPrompt?: (prompt: string) => void;
}

interface CardOutcomeState {
  busy: boolean;
  bookingOutcome?: BookingConfirmOutcome;
  proposalStatus?: 'ACCEPTED' | 'REJECTED';
  errorMessage?: string;
}

const formatVnd = (amount: number | undefined, language: 'vi' | 'en') =>
  typeof amount === 'number' && Number.isFinite(amount)
    ? new Intl.NumberFormat(language === 'vi' ? 'vi-VN' : 'en', {
        style: 'currency',
        currency: 'VND',
      }).format(amount)
    : undefined;

const extractApiError = (err: unknown, language: 'vi' | 'en') => {
  const e = err as { response?: { status?: number; data?: { message?: string; code?: string } } };
  if (e.response?.status === 409) {
    return language === 'en'
      ? 'Version conflict — please reload the latest itinerary before trying again.'
      : 'Xung đột phiên bản — vui lòng tải lại lịch trình mới nhất trước khi thử lại.';
  }
  return (
    e.response?.data?.message ||
    (language === 'en' ? 'Action failed. Please try again.' : 'Thao tác chưa hoàn tất. Vui lòng thử lại.')
  );
};

export const AssistantCardList: React.FC<AssistantCardListProps> = ({
  cards,
  conversationId,
  language,
  onQuickPrompt,
}) => {
  const [outcomes, setOutcomes] = useState<Record<string, CardOutcomeState>>({});
  const isEn = language === 'en';

  if (!cards?.length) return null;

  const updateOutcome = (cardId: string, patch: Partial<CardOutcomeState>) => {
    setOutcomes((prev) => ({
      ...prev,
      [cardId]: { ...(prev[cardId] || { busy: false }), ...patch },
    }));
  };

  const handleConfirmBooking = async (cardKey: string, targetCardId: string) => {
    if (!conversationId || outcomes[cardKey]?.busy) return;
    updateOutcome(cardKey, { busy: true, errorMessage: undefined });
    try {
      const outcome = await aiService.confirmBooking(conversationId, targetCardId);
      updateOutcome(cardKey, { busy: false, bookingOutcome: outcome });
    } catch (err) {
      updateOutcome(cardKey, { busy: false, errorMessage: extractApiError(err, language) });
    }
  };

  const handleProposalAction = async (cardKey: string, action: AssistantAction) => {
    if (outcomes[cardKey]?.busy) return;
    const match = action.path.match(/\/api\/ai\/itineraries\/([^/]+)\/proposals\/([^/]+)\/(accept|reject)/);
    if (!match) return;
    const [, itineraryId, proposalId, verb] = match;
    const expectedVersion = Number(action.parameters?.expectedVersion ?? 0);
    updateOutcome(cardKey, { busy: true, errorMessage: undefined });
    try {
      if (verb === 'accept') {
        await aiService.acceptProposal(itineraryId, proposalId, expectedVersion);
        updateOutcome(cardKey, { busy: false, proposalStatus: 'ACCEPTED' });
      } else {
        await aiService.rejectProposal(itineraryId, proposalId, expectedVersion);
        updateOutcome(cardKey, { busy: false, proposalStatus: 'REJECTED' });
      }
    } catch (err) {
      updateOutcome(cardKey, { busy: false, errorMessage: extractApiError(err, language) });
    }
  };

  return (
    <>
      {cards.map((card) => {
        const summary = summarizeAssistantCard(card, language);
        const state = outcomes[card.id];
        const confirmAction = card.actions?.find((a) => a.type === 'CONFIRM_BOOKING');
        const acceptAction = card.actions?.find((a) => a.type === 'ACCEPT_PROPOSAL');
        const rejectAction = card.actions?.find((a) => a.type === 'REJECT_PROPOSAL');
        const itineraryItems =
          card.type === 'ITINERARY_LIST' && Array.isArray(card.facts?.itineraries)
            ? (card.facts.itineraries as Array<{ index?: number; id?: string }>)
            : [];
        const orderItems =
          card.type === 'ORDER_LIST' && Array.isArray(card.facts?.orders)
            ? (card.facts.orders as Array<{ index?: number; id?: string; orderCode?: string }>)
            : [];

        if (!summary && !confirmAction && !acceptAction && !rejectAction) return null;

        const outcome = state?.bookingOutcome;
        const replacedCardId = outcome?.status === 'alternative_needed' ? outcome.newCard?.id : undefined;

        return (
          <section key={card.id} className="mt-2.5 border-t border-slate-200 pt-2.5 space-y-2 text-xs">
            {summary && (
              <>
                <p className="font-semibold text-slate-900">{summary.title}</p>
                <dl className="space-y-1">
                  {summary.rows.map((row, index) => (
                    <div key={index} className="flex flex-wrap items-baseline justify-between gap-2">
                      <dt className="text-slate-600">
                        {row.href ? (
                          <Link className="text-sky-800 underline font-medium" to={row.href}>
                            {row.label}
                          </Link>
                        ) : (
                          row.label
                        )}
                      </dt>
                      {row.value ? <dd className="font-medium text-slate-900 text-right">{row.value}</dd> : null}
                    </div>
                  ))}
                </dl>
                {summary.note ? <p className="text-[11px] text-slate-600">{summary.note}</p> : null}
              </>
            )}

            {/* U35 / U36 / U37 Interactive Booking Confirmation Flow */}
            {confirmAction && conversationId && (!outcome || outcome.status === 'error') && (
              <button
                type="button"
                disabled={Boolean(state?.busy)}
                onClick={() =>
                  handleConfirmBooking(
                    card.id,
                    String(confirmAction.parameters?.cardId || card.facts?.cardId || '')
                  )
                }
                className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-sky-700 hover:bg-sky-800 text-white font-semibold text-[11px] disabled:opacity-50 cursor-pointer"
              >
                <CheckCircle2 className="w-3.5 h-3.5" />
                <span>
                  {state?.busy
                    ? isEn
                      ? 'Checking live availability...'
                      : 'Đang kiểm tra giá & chỗ...'
                    : isEn
                    ? 'Confirm Booking Hold'
                    : 'Xác nhận giữ chỗ'}
                </span>
              </button>
            )}

            {outcome?.status === 'alternative_needed' && (
              <div className="p-2.5 rounded-lg bg-amber-50 border border-amber-200 text-amber-950 space-y-1.5">
                <div className="flex items-center gap-1.5 font-semibold">
                  <AlertTriangle className="w-3.5 h-3.5 text-amber-700 shrink-0" />
                  <span>
                    {outcome.reason === 'PRICE_CHANGED'
                      ? isEn
                        ? 'Live price updated — explicit re-confirmation required'
                        : 'Giá vừa thay đổi — cần bạn xác nhận lại giá mới'
                      : isEn
                      ? 'Selected departure unavailable — replacement option available'
                      : 'Ca chọn vừa hết — đã tạo thẻ phương án thay thế'}
                  </span>
                </div>
                {outcome.message && <p className="text-[11px] text-amber-900">{outcome.message}</p>}
                {(outcome.previousTotalAmount !== undefined || outcome.newTotalAmount !== undefined) && (
                  <p className="text-[11px] font-mono">
                    {isEn ? 'Previous' : 'Giá cũ'}: {formatVnd(outcome.previousTotalAmount, language) || '—'} →{' '}
                    <strong>{isEn ? 'Updated' : 'Giá mới'}: {formatVnd(outcome.newTotalAmount, language) || '—'}</strong>
                  </p>
                )}
                {outcome.newCard?.date && outcome.slotChanged && (
                  <p className="text-[11px]">
                    {isEn ? 'New departure' : 'Ca thay thế'}: <strong>{outcome.newCard.date.replace('T', ' ')}</strong>
                  </p>
                )}
                {replacedCardId && conversationId && (
                  <button
                    type="button"
                    disabled={Boolean(state?.busy)}
                    onClick={() => handleConfirmBooking(card.id, replacedCardId)}
                    className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-amber-700 hover:bg-amber-800 text-white font-semibold text-[11px] disabled:opacity-50 cursor-pointer"
                  >
                    <RefreshCw className="w-3.5 h-3.5" />
                    <span>{isEn ? 'Confirm updated option' : 'Xác nhận phương án mới'}</span>
                  </button>
                )}
              </div>
            )}

            {outcome?.status === 'out_of_stock' && (
              <div className="p-2 rounded-lg bg-rose-50 border border-rose-200 text-rose-800 flex items-center gap-1.5 text-[11px]">
                <XCircle className="w-3.5 h-3.5 shrink-0" />
                <span>{outcome.message || (isEn ? 'Sold out — no booking hold was created.' : 'Đã hết chỗ — không giữ chỗ hay overbook.')}</span>
              </div>
            )}

            {outcome?.status === 'success' && (
              <div className="p-2.5 rounded-lg bg-emerald-50 border border-emerald-200 text-emerald-900 space-y-1 text-[11px]">
                <div className="flex items-center gap-1.5 font-semibold">
                  <CheckCircle2 className="w-3.5 h-3.5 text-emerald-700 shrink-0" />
                  <span>{isEn ? 'Booking hold created' : 'Đã giữ chỗ thành công'}</span>
                </div>
                {outcome.message && <p>{outcome.message}</p>}
                {outcome.holdId && (
                  <p className="font-mono text-[10px]">
                    Hold: {outcome.holdId}
                  </p>
                )}
              </div>
            )}

            {/* U29 / U30 / U31 Itinerary Proposal Accept / Reject */}
            {(acceptAction || rejectAction) && !state?.proposalStatus && (
              <div className="flex flex-wrap gap-2 pt-1">
                {acceptAction && (
                  <button
                    type="button"
                    disabled={Boolean(state?.busy)}
                    onClick={() => handleProposalAction(card.id, acceptAction)}
                    className="inline-flex items-center gap-1 px-2.5 py-1.5 rounded-lg bg-emerald-700 hover:bg-emerald-800 text-white font-semibold text-[11px] disabled:opacity-50 cursor-pointer"
                  >
                    <CheckCircle2 className="w-3 h-3" />
                    <span>{isEn ? 'Accept proposal' : 'Chấp nhận đề xuất'}</span>
                  </button>
                )}
                {rejectAction && (
                  <button
                    type="button"
                    disabled={Boolean(state?.busy)}
                    onClick={() => handleProposalAction(card.id, rejectAction)}
                    className="inline-flex items-center gap-1 px-2.5 py-1.5 rounded-lg bg-slate-200 hover:bg-slate-300 text-slate-800 font-semibold text-[11px] disabled:opacity-50 cursor-pointer"
                  >
                    <XCircle className="w-3 h-3" />
                    <span>{isEn ? 'Reject' : 'Từ chối'}</span>
                  </button>
                )}
              </div>
            )}

            {state?.proposalStatus && (
              <div className="p-2 rounded-lg bg-slate-100 border border-slate-200 text-slate-800 text-[11px] font-medium">
                {state.proposalStatus === 'ACCEPTED'
                  ? isEn
                    ? 'Proposal accepted and saved to your itinerary.'
                    : 'Đã chấp nhận đề xuất và cập nhật phiên bản lịch trình mới.'
                  : isEn
                  ? 'Proposal rejected. Your original itinerary remains unchanged.'
                  : 'Đã từ chối đề xuất. Lịch trình gốc được giữ nguyên.'}
              </div>
            )}

            {/* Quick selection buttons for numbered itinerary list (#1, #2...) */}
            {onQuickPrompt && itineraryItems.length > 0 && (
              <div className="flex flex-wrap gap-1.5 pt-1">
                {itineraryItems.slice(0, 4).map((item, idx) => {
                  const num = item.index ?? idx + 1;
                  return (
                    <button
                      key={item.id || num}
                      type="button"
                      onClick={() =>
                        onQuickPrompt(isEn ? `Inspect itinerary #${num}` : `Xem chi tiết lịch trình số ${num}`)
                      }
                      className="px-2.5 py-1 rounded-lg bg-slate-100 hover:bg-slate-200 text-slate-800 font-medium text-[11px] cursor-pointer"
                    >
                      {isEn ? `Select #${num}` : `Chọn lịch trình #${num}`}
                    </button>
                  );
                })}
              </div>
            )}

            {/* Quick selection buttons for numbered order list (#ORD-...) */}
            {onQuickPrompt && orderItems.length > 0 && (
              <div className="flex flex-wrap gap-1.5 pt-1">
                {orderItems.slice(0, 5).map((item, idx) => {
                  const code = item.orderCode ? `#${item.orderCode}` : `#${item.index ?? idx + 1}`;
                  return (
                    <button
                      key={item.id || code}
                      type="button"
                      onClick={() =>
                        onQuickPrompt(isEn ? `Check order ${code}` : `Kiểm tra đơn ${code}`)
                      }
                      className="px-2.5 py-1 rounded-lg bg-slate-100 hover:bg-slate-200 text-slate-800 font-medium text-[11px] cursor-pointer"
                    >
                      {code}
                    </button>
                  );
                })}
              </div>
            )}

            {state?.errorMessage && (
              <p className="text-[11px] text-rose-700 bg-rose-50 border border-rose-200 rounded-md p-2">
                {state.errorMessage}
              </p>
            )}
          </section>
        );
      })}
    </>
  );
};
