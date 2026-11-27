import React, { useEffect, useState } from 'react';
import axiosClient from '../api/axiosClient';
import { Sparkles, Plus, Trash2 } from 'lucide-react';

interface KeywordRulesPanelProps {
  categories: any[];
}

export default function KeywordRulesPanel({ categories }: KeywordRulesPanelProps) {
  const [rules, setRules] = useState<any[]>([]);
  const [keyword, setKeyword] = useState('');
  const [categoryId, setCategoryId] = useState<number>(categories[0]?.id || 0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const fetchRules = async () => {
    try {
      const res = await axiosClient.get('/rules');
      setRules(res.data || []);
    } catch {
      setRules([]);
    }
  };

  useEffect(() => {
    fetchRules();
  }, []);

  useEffect(() => {
    if (!categoryId && categories.length > 0) setCategoryId(categories[0].id);
  }, [categories, categoryId]);

  const handleAdd = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!keyword.trim() || !categoryId) return;
    setLoading(true);
    setError('');
    try {
      await axiosClient.post('/rules', { keyword: keyword.trim(), categoryId, priority: 10 });
      setKeyword('');
      await fetchRules();
    } catch (err: any) {
      setError(err?.response?.data?.message || 'Không thể thêm quy tắc từ khóa');
    } finally {
      setLoading(false);
    }
  };

  const handleDelete = async (id: number) => {
    try {
      await axiosClient.delete(`/rules/${id}`);
      await fetchRules();
    } catch {
      return;
    }
  };

  return (
    <div className="bg-white rounded-2xl border border-slate-100 p-5 sm:p-6 space-y-5">
      <div>
        <h3 className="text-base font-bold text-[#1d1d1f] flex items-center gap-2">
          <Sparkles className="w-4 h-4 text-[#0071e3]" />
          Quy tắc tự động phân loại theo từ khóa
        </h3>
        <p className="text-xs text-[#6e6e73] mt-1">
          Gắn từ khóa quen thuộc (VD: "Grab", "Xăng", "Highlands", "Shopee") vào danh mục để hệ thống tự nhận diện khi Nhập nhanh 1 dòng và Import sao kê CSV.
        </p>
      </div>

      {error && (
        <div className="p-3 rounded-2xl bg-[#ff3b30]/10 text-[#d70015] text-xs font-medium">{error}</div>
      )}

      <form onSubmit={handleAdd} className="flex flex-col sm:flex-row gap-2.5">
        <input
          type="text"
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          placeholder='Từ khóa (VD: Grab, Xăng, Trà sữa...)'
          required
          className="flex-1 px-4 py-2.5 bg-[#f2f2f7] focus:bg-white border border-transparent rounded-full text-sm"
        />
        <select
          value={categoryId}
          onChange={(e) => setCategoryId(Number(e.target.value))}
          className="px-4 py-2.5 border border-black/[0.09] rounded-full text-sm bg-white sm:w-56"
        >
          {categories.map((c) => (
            <option key={c.id} value={c.id}>{c.name} ({c.type === 'INCOME' ? 'Thu' : 'Chi'})</option>
          ))}
        </select>
        <button
          type="submit"
          disabled={loading}
          className="px-5 py-2.5 rounded-full bg-[#1d1d1f] hover:bg-black text-white text-sm font-medium inline-flex items-center justify-center gap-1.5 cursor-pointer shrink-0"
        >
          <Plus className="w-4 h-4" /> Thêm quy tắc
        </button>
      </form>

      {rules.length === 0 ? (
        <div className="py-8 text-center text-xs text-[#86868b] bg-[#f5f5f7]/60 rounded-2xl border border-dashed border-black/[0.08]">
          Chưa có quy tắc cá nhân nào. Hệ thống vẫn đang dùng bộ từ khóa tiếng Việt mặc định.
        </div>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2.5">
          {rules.map((r) => (
            <div key={r.id} className="flex items-center justify-between gap-2 px-4 py-3 rounded-2xl bg-[#f5f5f7]/80 border border-black/[0.05]">
              <div className="min-w-0">
                <div className="text-sm font-semibold text-[#1d1d1f] truncate">“{r.keyword}”</div>
                <div className="flex items-center gap-1.5 text-xs text-[#6e6e73] mt-0.5">
                  <span className="w-2 h-2 rounded-full shrink-0" style={{ backgroundColor: r.category?.color || '#64748b' }} />
                  <span className="truncate">{r.category?.name}</span>
                </div>
              </div>
              <button
                type="button"
                onClick={() => handleDelete(r.id)}
                className="p-1.5 text-[#86868b] hover:text-[#d70015] hover:bg-[#ff3b30]/10 rounded-full cursor-pointer shrink-0"
                title="Xóa quy tắc"
              >
                <Trash2 className="w-4 h-4" />
              </button>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
