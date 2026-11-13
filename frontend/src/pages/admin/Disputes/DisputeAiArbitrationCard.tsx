import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Scale,
  Sparkles,
  CheckCircle2,
  AlertTriangle,
  RotateCcw,
  MessageSquare,
  ShieldAlert,
  Clock,
  FileCheck,
} from 'lucide-react';
import { adminAiService, getAdminAiError } from '../../../services/adminAiService';
import type { DisputeArbitrationResult } from '../../../types/adminAi';
import { AdminButton } from '../../../components/admin/AdminWorkspace';

interface DisputeAiArbitrationCardProps {
  disputeId: string;
  onApplyDraftNote: (draftNote: string, suggestedRefundPercent?: number | null) => void;
}

export const DisputeAiArbitrationCard: React.FC<DisputeAiArbitrationCardProps> = ({
  disputeId,
  onApplyDraftNote,
}) => {
  const navigate = useNavigate();
  const [result, setResult] = useState<DisputeArbitrationResult | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [applied, setApplied] = useState(false);

  // Reset state whenever active dispute changes
  useEffect(() => {
    setResult(null);
    setError(null);
    setApplied(false);
  }, [disputeId]);

  const handleRequestArbitration = async () => {
    if (!disputeId) return;
    setLoading(true);
    setError(null);
    setApplied(false);

    try {
      const data = await adminAiService.summarizeDispute(disputeId);
      setResult(data);
    } catch (err) {
      setError(getAdminAiError(err));
    } finally {
      setLoading(false);
    }
  };

  const handleApply = () => {
    if (!result?.draftAdminNote) return;
    onApplyDraftNote(result.draftAdminNote, result.recommendedRefundPercent);
    setApplied(true);
  };

  const handleOpenCopilot = () => {
    navigate(`/admin/copilot?contextType=DISPUTE&contextId=${encodeURIComponent(disputeId)}`);
  };

  const getResolutionBadgeTone = (res?: string) => {
    switch (res) {
      case 'RESOLVED_REFUND':
        return { bg: '#ecfdf5', text: '#065f46', border: '#a7f3d0', label: 'Đề xuất hoàn 100%' };
      case 'RESOLVED_PARTIAL':
        return { bg: '#eff6ff', text: '#1e40af', border: '#bfdbfe', label: 'Đề xuất hoàn một phần' };
      case 'RESOLVED_REJECTED':
        return { bg: '#fef2f2', text: '#991b1b', border: '#fecaca', label: 'Đề xuất bác bỏ khiếu nại' };
      case 'MANUAL_REVIEW_REQUIRED':
      default:
        return { bg: '#fffbeb', text: '#92400e', border: '#fde68a', label: 'Cần thẩm định đối chiếu' };
    }
  };

  return (
    <div
      style={{
        margin: '16px 0',
        padding: '16px',
        borderRadius: '10px',
        border: '1px solid #cbd5e1',
        backgroundColor: '#ffffff',
        boxShadow: '0 1px 3px rgba(15, 23, 42, 0.05)',
      }}
      aria-label="Khối phân tích hòa giải AI"
    >
      {/* Header bar */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          flexWrap: 'wrap',
          gap: '8px',
          marginBottom: result || error || loading ? '14px' : '0',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <div
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              justifyContent: 'center',
              width: '28px',
              height: '28px',
              borderRadius: '6px',
              backgroundColor: '#e0f2fe',
              color: '#0369a1',
            }}
          >
            <Scale size={16} />
          </div>
          <div>
            <h4
              style={{
                margin: 0,
                fontSize: '13px',
                fontWeight: 700,
                color: '#0f172a',
                lineHeight: 1.3,
              }}
            >
              Trợ lý Phán quyết Tranh chấp AI
            </h4>
            <span style={{ fontSize: '11px', color: '#64748b' }}>
              Đối soát dữ liệu QR check-in, lịch trình ca và giải trình 2 bên
            </span>
          </div>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <button
            type="button"
            onClick={handleOpenCopilot}
            title="Mở hội thoại riêng về vụ này trên Admin Copilot"
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: '6px',
              padding: '6px 10px',
              fontSize: '12px',
              fontWeight: 500,
              color: '#0891b2',
              backgroundColor: '#f0fdfa',
              border: '1px solid #ccfbf1',
              borderRadius: '6px',
              cursor: 'pointer',
            }}
          >
            <MessageSquare size={13} />
            <span>Hỏi Copilot</span>
          </button>

          <AdminButton
            variant="secondary"
            onClick={handleRequestArbitration}
            disabled={loading}
          >
            {loading ? (
              <>
                <Clock size={13} className="animate-spin" />
                <span>AI đang đối soát...</span>
              </>
            ) : (
              <>
                <Sparkles size={13} />
                <span>{result ? 'Đối soát lại' : 'AI Đề xuất phán quyết'}</span>
              </>
            )}
          </AdminButton>
        </div>
      </div>

      {/* Loading state indicator */}
      {loading && (
        <div
          style={{
            padding: '16px',
            backgroundColor: '#f8fafc',
            borderRadius: '8px',
            border: '1px dashed #cbd5e1',
            display: 'flex',
            alignItems: 'center',
            gap: '10px',
            fontSize: '13px',
            color: '#475569',
          }}
        >
          <div
            style={{
              width: '18px',
              height: '18px',
              border: '2px solid #0891b2',
              borderTopColor: 'transparent',
              borderRadius: '50%',
              animation: 'spin 0.8s linear infinite',
            }}
          />
          <span>AI đang tổng hợp và đối soát dữ liệu thực tế từ máy chủ (QR check-in & giải trình)...</span>
        </div>
      )}

      {/* Transparent Error State (No fake mock hiding) */}
      {error && !loading && (
        <div
          role="alert"
          style={{
            padding: '12px 14px',
            borderRadius: '8px',
            backgroundColor: '#fef2f2',
            border: '1px solid #fecaca',
            color: '#991b1b',
            fontSize: '12px',
            display: 'flex',
            alignItems: 'flex-start',
            gap: '8px',
          }}
        >
          <AlertTriangle size={15} style={{ flexShrink: 0, marginTop: '2px' }} />
          <div style={{ flex: 1 }}>
            <div style={{ fontWeight: 600, marginBottom: '2px' }}>Không thể phân tích hồ sơ:</div>
            <div>{error}</div>
          </div>
          <button
            type="button"
            onClick={handleRequestArbitration}
            style={{
              padding: '4px 8px',
              fontSize: '11px',
              fontWeight: 600,
              color: '#991b1b',
              backgroundColor: '#fee2e2',
              border: '1px solid #fca5a5',
              borderRadius: '4px',
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              gap: '4px',
            }}
          >
            <RotateCcw size={11} /> Thử lại
          </button>
        </div>
      )}

      {/* Success Content: Fact Sheet + Recommendation */}
      {result && !loading && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '12px', fontSize: '12px', color: '#1e293b' }}>
          {/* 1. Ground Truth System Facts */}
          <div
            style={{
              padding: '10px 12px',
              backgroundColor: '#f8fafc',
              borderRadius: '6px',
              border: '1px solid #e2e8f0',
            }}
          >
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: '6px',
                fontWeight: 700,
                color: '#0f172a',
                marginBottom: '6px',
                textTransform: 'uppercase',
                letterSpacing: '0.025em',
                fontSize: '11px',
              }}
            >
              <ShieldAlert size={13} style={{ color: '#0284c7' }} />
              <span>Sự thật hệ thống (Objective Ground Truth)</span>
            </div>
            <ul style={{ margin: 0, paddingLeft: '18px', display: 'flex', flexDirection: 'column', gap: '4px' }}>
              {result.objectiveSystemFacts.map((fact, idx) => (
                <li key={idx} style={{ lineHeight: 1.5, color: '#334155' }}>
                  {fact}
                </li>
              ))}
            </ul>
          </div>

          {/* 2. Claim vs Defense Side-by-Side */}
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))',
              gap: '10px',
            }}
          >
            <div
              style={{
                padding: '10px 12px',
                backgroundColor: '#ffffff',
                borderRadius: '6px',
                border: '1px solid #e2e8f0',
              }}
            >
              <span style={{ fontWeight: 600, color: '#0f172a', display: 'block', marginBottom: '4px' }}>
                Yêu cầu từ khách hàng:
              </span>
              <p style={{ margin: 0, color: '#475569', lineHeight: 1.4 }}>
                {result.customerClaimSummary || 'Không có mô tả chi tiết'}
              </p>
            </div>

            <div
              style={{
                padding: '10px 12px',
                backgroundColor: '#ffffff',
                borderRadius: '6px',
                border: '1px solid #e2e8f0',
              }}
            >
              <span style={{ fontWeight: 600, color: '#0f172a', display: 'block', marginBottom: '4px' }}>
                Giải trình từ đối tác:
              </span>
              <p style={{ margin: 0, color: '#475569', lineHeight: 1.4 }}>
                {result.vendorDefenseSummary || 'Đối tác chưa gửi văn bản giải trình'}
              </p>
            </div>
          </div>

          {/* 3. Recommendation & Reason */}
          {(() => {
            const badge = getResolutionBadgeTone(result.recommendedResolution);
            return (
              <div
                style={{
                  padding: '10px 12px',
                  backgroundColor: badge.bg,
                  borderRadius: '6px',
                  border: `1px solid ${badge.border}`,
                  display: 'flex',
                  flexDirection: 'column',
                  gap: '6px',
                }}
              >
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '6px' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                    <span
                      style={{
                        padding: '2px 8px',
                        borderRadius: '4px',
                        fontSize: '11px',
                        fontWeight: 700,
                        backgroundColor: '#ffffff',
                        color: badge.text,
                        border: `1px solid ${badge.border}`,
                      }}
                    >
                      {badge.label}
                    </span>
                    {result.recommendedRefundPercent !== null && result.recommendedRefundPercent !== undefined && (
                      <span style={{ fontWeight: 700, color: badge.text }}>
                        Tỷ lệ hoàn: {result.recommendedRefundPercent}%
                      </span>
                    )}
                  </div>
                  <span style={{ fontSize: '10px', color: '#64748b' }}>
                    Phương pháp: {result.analysisMethod}
                  </span>
                </div>
                {result.recommendationReason && (
                  <p style={{ margin: 0, color: badge.text, lineHeight: 1.4 }}>
                    <strong>Căn cứ:</strong> {result.recommendationReason}
                  </p>
                )}
              </div>
            );
          })()}

          {/* 4. Draft Admin Note */}
          {result.draftAdminNote && (
            <div
              style={{
                padding: '12px',
                backgroundColor: '#f1f5f9',
                borderRadius: '6px',
                border: '1px solid #cbd5e1',
              }}
            >
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  marginBottom: '6px',
                }}
              >
                <span style={{ fontWeight: 700, color: '#0f172a', fontSize: '11px' }}>
                  BIÊN BẢN GIẢI QUYẾT MẪU (DRAFT NOTE):
                </span>
                <button
                  type="button"
                  onClick={handleApply}
                  style={{
                    display: 'inline-flex',
                    alignItems: 'center',
                    gap: '4px',
                    padding: '4px 8px',
                    fontSize: '11px',
                    fontWeight: 600,
                    color: applied ? '#065f46' : '#ffffff',
                    backgroundColor: applied ? '#d1fae5' : '#0e7490',
                    border: applied ? '1px solid #a7f3d0' : 'none',
                    borderRadius: '4px',
                    cursor: 'pointer',
                  }}
                >
                  {applied ? (
                    <>
                      <CheckCircle2 size={12} /> Đã áp dụng vào biên bản
                    </>
                  ) : (
                    <>
                      <FileCheck size={12} /> Áp dụng vào biên bản
                    </>
                  )}
                </button>
              </div>
              <div
                style={{
                  fontFamily: 'monospace',
                  fontSize: '12px',
                  lineHeight: 1.5,
                  color: '#334155',
                  whiteSpace: 'pre-wrap',
                }}
              >
                {result.draftAdminNote}
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
};
