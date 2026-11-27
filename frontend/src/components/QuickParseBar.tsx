import React, { useEffect, useState } from 'react';
import axiosClient from '../api/axiosClient';
import { Zap, Check, Sparkles, Wallet as WalletIcon, Tag, ArrowRight } from 'lucide-react';
import type { Wallet } from '../types/finance';

interface QuickParseBarProps {
  wallets: Wallet[];
  categories: any[];
  onCreated: () => Promise<void> | void;
}

interface ParsedPreview {
  valid: boolean;
  error?: string;
  type?: string;
  amount?: number;
  description?: string;
  walletId?: number;
  walletName?: string;
  categoryId?: number;
  categoryName?: string;
  categoryColor?: string;
  matchedRule?: string;
}

const formatVND = (n: number) =>
  new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(n || 0);

const SAMPLE_PROMPTS = [
  'Ăn trưa 45k ví Momo',
  'Đổ xăng 80k tiền mặt',
  'Lương tháng này 15tr VCB',
  'Cafe Highlands 55k',
];

export default function QuickParseBar({ wallets, categories, onCreated }: QuickParseBarProps) {
  const [text, setText] = useState('');
  const [preview, setPreview] = useState<ParsedPreview | null>(null);
  const [overrideWalletId, setOverrideWalletId] = useState<number | ''>('');
  const [overrideCategoryId, setOverrideCategoryId] = useState<number | ''>('');
  const [submitting, setSubmitting] = useState(false);
  const [feedback, setFeedback] = useState<{ ok: boolean; msg: string } | null>(null);

  useEffect(() => {
    const trimmed = text.trim();
    if (!trimmed) {
      setPreview(null);
      setOverrideWalletId('');
      setOverrideCategoryId('');
      return;
    }
    const timer = window.setTimeout(async () => {
      try {
        const res = await axiosClient.post('/transactions/quick-parse/preview', { text: trimmed });
        setPreview(res.data);
      } catch {
        setPreview(null);
      }
    }, 240);
    return () => window.clearTimeout(timer);
  }, [text]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!text.trim() || submitting) return;
    setSubmitting(true);
    setFeedback(null);
    try {
      const payload: any = { text: text.trim() };
      if (overrideWalletId) payload.walletId = Number(overrideWalletId);
      if (overrideCategoryId) payload.categoryId = Number(overrideCategoryId);
      const res = await axiosClient.post('/transactions/quick-parse', payload);
      const parsed = res.data?.parsed;
      setFeedback({
        ok: true,
        msg: `Đã ghi ${parsed?.type === 'INCOME' ? 'thu' : 'chi'} ${formatVND(parsed?.amount || 0)} (${parsed?.categoryName || 'Chưa phân loại'})`,
      });
      setText('');
      setPreview(null);
      setOverrideWalletId('');
      setOverrideCategoryId('');
      await onCreated();
      window.setTimeout(() => setFeedback(null), 3500);
    } catch (err: any) {
      setFeedback({
        ok: false,
        msg: err?.response?.data?.message || 'Không thể phân tích cú pháp giao dịch',
      });
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="apple-glass-strong rounded-[26px] border border-white/80 shadow-[0_6px_26px_-8px_rgba(0,0,0,0.08)] p-4 sm:p-5">
      <div className="flex flex-wrap items-center justify-between gap-2 mb-3">
        <div className="flex items-center gap-2">
          <div className="w-7 h-7 rounded-full bg-[#1d1d1f] text-white flex items-center justify-center shrink-0">
            <Zap className="w-3.5 h-3.5" />
          </div>
          <div>
            <div className="text-sm font-bold text-[#1d1d1f] leading-tight">Nhập nhanh một dòng (Quick-Parse)</div>
            <div className="text-[11px] text-[#6e6e73]">Gõ tự nhiên số tiền, mô tả và tên ví — tự động bóc tách trong 3 giây</div>
          </div>
        </div>
        <div className="hidden md:flex items-center gap-1.5 flex-wrap">
          {SAMPLE_PROMPTS.map((sample) => (
            <button
              key={sample}
              type="button"
              onClick={() => setText(sample)}
              className="px-2.5 py-1 rounded-full bg-black/[0.04] hover:bg-black/[0.08] text-[11px] font-medium text-[#424245] cursor-pointer"
            >
              {sample}
            </button>
          ))}
        </div>
      </div>

      <form onSubmit={handleSubmit} className="flex flex-col sm:flex-row items-stretch sm:items-center gap-2">
        <div className="relative flex-1 min-w-0">
          <Sparkles className="w-4 h-4 text-[#0071e3] absolute left-4 top-1/2 -translate-y-1/2 pointer-events-none" />
          <input
            type="text"
            value={text}
            onChange={(e) => setText(e.target.value)}
            placeholder='VD: "Ăn trưa 45k ví Momo", "Lương 15tr VCB", "Đổ xăng 120k"...'
            className="w-full pl-10 pr-4 py-2.5 bg-white/95 border border-black/[0.08] rounded-full text-sm text-[#1d1d1f] placeholder:text-[#86868b] shadow-2xs"
          />
        </div>
        <button
          type="submit"
          disabled={submitting || !text.trim() || (preview !== null && !preview.valid)}
          className="px-5 py-2.5 rounded-full bg-[#1d1d1f] hover:bg-black disabled:opacity-40 text-white text-sm font-medium flex items-center justify-center gap-1.5 shrink-0 cursor-pointer shadow-sm"
        >
          {submitting ? 'Đang lưu...' : 'Ghi nhanh'}
          <ArrowRight className="w-4 h-4" />
        </button>
      </form>

      {preview && (
        <div className="mt-3 pt-3 border-t border-black/[0.06] flex flex-wrap items-center gap-2 text-xs">
          {!preview.valid ? (
            <span className="text-[#d70015] font-medium">{preview.error}</span>
          ) : (
            <>
              <span className={'px-2.5 py-1 rounded-full font-semibold ' + (preview.type === 'INCOME' ? 'bg-[#34c759]/15 text-[#248a3d]' : 'bg-[#ff3b30]/12 text-[#d70015]')}>
                {preview.type === 'INCOME' ? 'Thu nhập' : 'Chi tiêu'}
              </span>
              <span className="px-2.5 py-1 rounded-full bg-[#1d1d1f] text-white font-bold tabular-nums">
                {preview.type === 'INCOME' ? '+' : '-'}{formatVND(preview.amount || 0)}
              </span>
              <span className="px-2.5 py-1 rounded-full bg-black/[0.05] text-[#1d1d1f] font-medium max-w-[220px] truncate">
                {preview.description}
              </span>

              <div className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full bg-white border border-black/[0.08]">
                <WalletIcon className="w-3 h-3 text-[#6e6e73]" />
                <select
                  value={overrideWalletId || preview.walletId || ''}
                  onChange={(e) => setOverrideWalletId(Number(e.target.value))}
                  className="bg-transparent text-xs font-medium text-[#1d1d1f] focus:outline-none cursor-pointer"
                >
                  {wallets.map((w) => (
                    <option key={w.id} value={w.id}>{w.name}</option>
                  ))}
                </select>
              </div>

              <div className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full bg-white border border-black/[0.08]">
                <Tag className="w-3 h-3 text-[#6e6e73]" />
                <select
                  value={overrideCategoryId || preview.categoryId || ''}
                  onChange={(e) => setOverrideCategoryId(Number(e.target.value))}
                  className="bg-transparent text-xs font-medium text-[#1d1d1f] focus:outline-none cursor-pointer"
                >
                  <option value="">Chưa phân loại</option>
                  {categories.map((c) => (
                    <option key={c.id} value={c.id}>{c.name}</option>
                  ))}
                </select>
              </div>

              {preview.matchedRule && (
                <span className="text-[11px] text-[#0071e3] font-medium">
                  ({preview.matchedRule})
                </span>
              )}
            </>
          )}
        </div>
      )}

      {feedback && (
        <div className={'mt-2.5 px-3.5 py-2 rounded-2xl text-xs font-medium flex items-center gap-2 ' + (feedback.ok ? 'bg-[#34c759]/12 text-[#248a3d]' : 'bg-[#ff3b30]/10 text-[#d70015]')}>
          {feedback.ok && <Check className="w-3.5 h-3.5 shrink-0" />}
          <span>{feedback.msg}</span>
        </div>
      )}
    </div>
  );
}
