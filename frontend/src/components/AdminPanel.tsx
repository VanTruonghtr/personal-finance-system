import React, { useEffect, useState } from 'react';
import axiosClient from '../api/axiosClient';
import { Shield, Server, Users, Tags, Plus, Trash2, Lock } from 'lucide-react';

export default function AdminPanel() {
  const [stats, setStats] = useState<any>(null);
  const [defaultCats, setDefaultCats] = useState<any[]>([]);
  const [name, setName] = useState('');
  const [type, setType] = useState<'EXPENSE' | 'INCOME'>('EXPENSE');
  const [color, setColor] = useState('#0071e3');
  const [error, setError] = useState('');

  const loadData = async () => {
    try {
      const [sRes, cRes] = await Promise.all([
        axiosClient.get('/admin/stats'),
        axiosClient.get('/admin/default-categories'),
      ]);
      setStats(sRes.data);
      setDefaultCats(cRes.data || []);
      setError('');
    } catch (err: any) {
      setError(err?.response?.data?.message || 'Không có quyền truy cập trang Quản trị viên');
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  const handleAdd = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim()) return;
    try {
      await axiosClient.post('/admin/default-categories', { name: name.trim(), type, color });
      setName('');
      await loadData();
    } catch (err: any) {
      setError(err?.response?.data?.message || 'Không thể thêm danh mục mặc định');
    }
  };

  const handleDelete = async (id: number) => {
    try {
      await axiosClient.delete(`/admin/default-categories/${id}`);
      await loadData();
    } catch {
      return;
    }
  };

  if (error) {
    return (
      <div className="bg-white p-8 rounded-2xl border border-slate-100 text-center text-sm text-[#d70015] font-medium">
        {error}
      </div>
    );
  }

  return (
    <div className="space-y-6 apple-tab-enter">
      <div className="p-4 rounded-2xl bg-[#0071e3]/[0.08] border border-[#0071e3]/20 flex items-center gap-3 text-xs text-[#0071e3]">
        <Lock className="w-4 h-4 shrink-0" />
        <span>
          <strong>Chế độ Bảo mật Quyền riêng tư:</strong> Quản trị viên chỉ theo dõi chỉ số vận hành tổng hợp và danh mục mặc định; tuyệt đối không có quyền xem giao dịch chi tiết của người dùng cá nhân.
        </span>
      </div>

      {stats && (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 apple-stagger">
          <div className="bg-white p-5 rounded-2xl border border-slate-100">
            <div className="flex items-center justify-between text-xs text-[#6e6e73]">
              <span>Trạng thái hệ thống</span>
              <Server className="w-4 h-4 text-[#248a3d]" />
            </div>
            <div className="text-2xl font-bold text-[#248a3d] mt-2">{stats.status}</div>
            <div className="text-[11px] text-[#86868b] mt-1">Uptime: {Math.floor((stats.uptimeSeconds || 0) / 60)} phút</div>
          </div>
          <div className="bg-white p-5 rounded-2xl border border-slate-100">
            <div className="flex items-center justify-between text-xs text-[#6e6e73]">
              <span>Tổng người dùng</span>
              <Users className="w-4 h-4 text-[#0071e3]" />
            </div>
            <div className="text-2xl font-bold text-[#1d1d1f] mt-2">{stats.totalUsers}</div>
            <div className="text-[11px] text-[#86868b] mt-1">{stats.totalWallets} ví đang quản lý</div>
          </div>
          <div className="bg-white p-5 rounded-2xl border border-slate-100">
            <div className="flex items-center justify-between text-xs text-[#6e6e73]">
              <span>Tổng giao dịch (Ẩn danh)</span>
              <Shield className="w-4 h-4 text-[#5856d6]" />
            </div>
            <div className="text-2xl font-bold text-[#1d1d1f] mt-2">{stats.totalTransactionsCount}</div>
            <div className="text-[11px] text-[#86868b] mt-1">Không hiển thị nội dung chi tiết</div>
          </div>
          <div className="bg-white p-5 rounded-2xl border border-slate-100">
            <div className="flex items-center justify-between text-xs text-[#6e6e73]">
              <span>Bộ nhớ JVM</span>
              <Tags className="w-4 h-4 text-[#ff9500]" />
            </div>
            <div className="text-2xl font-bold text-[#1d1d1f] mt-2">{stats.memoryUsedMb} / {stats.memoryMaxMb} MB</div>
            <div className="text-[11px] text-[#86868b] mt-1">{stats.defaultCategoriesCount} danh mục mặc định</div>
          </div>
        </div>
      )}

      <div className="bg-white p-6 rounded-2xl border border-slate-100 space-y-4">
        <h3 className="text-base font-bold text-[#1d1d1f]">Quản lý Danh mục mặc định hệ thống</h3>
        <form onSubmit={handleAdd} className="flex flex-col sm:flex-row gap-2.5">
          <input
            type="text"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="Tên danh mục mặc định mới..."
            required
            className="flex-1 px-4 py-2.5 bg-[#f2f2f7] focus:bg-white rounded-full text-sm border border-transparent"
          />
          <select value={type} onChange={(e) => setType(e.target.value as any)} className="px-4 py-2.5 rounded-full border border-black/[0.1] bg-white text-sm">
            <option value="EXPENSE">Khoản Chi</option>
            <option value="INCOME">Khoản Thu</option>
          </select>
          <input type="color" value={color} onChange={(e) => setColor(e.target.value)} className="w-11 h-10 rounded-full border border-black/[0.1] cursor-pointer" />
          <button type="submit" className="px-5 py-2.5 rounded-full bg-[#1d1d1f] hover:bg-black text-white text-sm font-medium inline-flex items-center justify-center gap-1.5 cursor-pointer">
            <Plus className="w-4 h-4" /> Thêm mới
          </button>
        </form>

        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2.5">
          {defaultCats.map((c) => (
            <div key={c.id} className="flex items-center justify-between gap-2 px-4 py-3 rounded-2xl bg-[#f5f5f7]">
              <div className="flex items-center gap-2 min-w-0">
                <span className="w-3 h-3 rounded-full shrink-0" style={{ backgroundColor: c.color || '#64748b' }} />
                <div className="min-w-0">
                  <div className="text-sm font-semibold text-[#1d1d1f] truncate">{c.name}</div>
                  <div className="text-[11px] text-[#6e6e73]">{c.type === 'INCOME' ? 'Khoản Thu' : 'Khoản Chi'}</div>
                </div>
              </div>
              <button onClick={() => handleDelete(c.id)} className="p-1.5 text-[#86868b] hover:text-[#d70015] rounded-full cursor-pointer">
                <Trash2 className="w-4 h-4" />
              </button>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
