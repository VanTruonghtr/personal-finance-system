import React, { useEffect, useState } from 'react';
import axiosClient from '../api/axiosClient';
import { User as UserIcon, X } from 'lucide-react';

interface ProfileModalProps {
  isOpen: boolean;
  onClose: () => void;
  user: any;
  onUpdated: (nextUser: any) => void;
}

const formatInputMoney = (val: string | number) => {
  const digits = String(val).replace(/\D/g, '');
  if (!digits) return '';
  return digits.replace(/\B(?=(\d{3})+(?!\d))/g, '.');
};

const parseInputMoney = (val: string) => {
  const digits = String(val).replace(/\D/g, '');
  return digits ? Number(digits) : 0;
};

export default function ProfileModal({ isOpen, onClose, user, onUpdated }: ProfileModalProps) {
  const [fullName, setFullName] = useState(user?.fullName || '');
  const [monthlyBudget, setMonthlyBudget] = useState(user?.monthlyBudgetLimit ? formatInputMoney(user.monthlyBudgetLimit) : '');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  useEffect(() => {
    if (!isOpen) return;
    setError('');
    setSuccess('');
    axiosClient.get('/auth/me').then((res) => {
      if (res.data) {
        setFullName(res.data.fullName || '');
        setMonthlyBudget(res.data.monthlyBudgetLimit > 0 ? formatInputMoney(res.data.monthlyBudgetLimit) : '');
      }
    }).catch(() => {
      setFullName(user?.fullName || '');
    });
  }, [isOpen, user]);

  if (!isOpen) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError('');
    setSuccess('');
    try {
      const res = await axiosClient.put('/auth/profile', {
        fullName: fullName.trim(),
        monthlyBudgetLimit: parseInputMoney(monthlyBudget),
      });
      const updated = {
        ...user,
        fullName: res.data.fullName,
        role: res.data.role,
        monthlyBudgetLimit: res.data.monthlyBudgetLimit,
      };
      localStorage.setItem('user', JSON.stringify(updated));
      onUpdated(updated);
      setSuccess('Đã lưu hồ sơ và ngân sách tổng tháng!');
      window.setTimeout(() => onClose(), 900);
    } catch (err: any) {
      setError(err?.response?.data?.message || 'Cập nhật hồ sơ thất bại');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center p-4 z-50">
      <div className="bg-white rounded-[28px] max-w-md w-full p-6 shadow-xl">
        <div className="flex items-center justify-between mb-4">
          <h3 className="text-lg font-bold text-[#1d1d1f] flex items-center gap-2">
            <UserIcon className="w-5 h-5 text-[#0071e3]" /> Hồ sơ & Ngân sách tổng
          </h3>
          <button type="button" onClick={onClose} className="p-1.5 text-[#86868b] hover:text-[#1d1d1f] hover:bg-black/[0.05] rounded-full cursor-pointer">
            <X className="w-5 h-5" />
          </button>
        </div>

        {error && <div className="mb-3 p-3 rounded-2xl bg-[#ff3b30]/10 text-[#d70015] text-xs font-medium">{error}</div>}
        {success && <div className="mb-3 p-3 rounded-2xl bg-[#34c759]/12 text-[#248a3d] text-xs font-medium">{success}</div>}

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-[#1d1d1f] mb-1">Email tài khoản</label>
            <input type="text" value={user?.email || ''} disabled className="w-full px-3.5 py-2.5 rounded-2xl bg-[#f2f2f7] text-sm text-[#6e6e73]" />
          </div>
          <div>
            <label className="block text-sm font-medium text-[#1d1d1f] mb-1">Họ và tên hiển thị</label>
            <input
              type="text"
              required
              value={fullName}
              onChange={(e) => setFullName(e.target.value)}
              className="w-full px-3.5 py-2.5 border border-black/[0.1] rounded-2xl text-sm"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-[#1d1d1f] mb-1">Ngân sách chi tiêu tổng/tháng (VNĐ)</label>
            <input
              type="text"
              inputMode="numeric"
              value={monthlyBudget}
              onChange={(e) => setMonthlyBudget(formatInputMoney(e.target.value))}
              placeholder="VD: 10.000.000 (Để trống = cộng hạn mức từng danh mục)"
              className="w-full px-3.5 py-2.5 border border-black/[0.1] rounded-2xl text-sm font-semibold"
            />
            <p className="text-[11px] text-[#6e6e73] mt-1">
              Dùng để tính chỉ số "Hạn mức chi tiêu an toàn/ngày" và cảnh báo tốc độ tiêu dùng (Burn-rate).
            </p>
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <button type="button" onClick={onClose} className="px-4 py-2 text-sm text-[#6e6e73] hover:bg-black/[0.05] rounded-full cursor-pointer">Hủy</button>
            <button type="submit" disabled={loading} className="px-5 py-2 text-sm bg-[#1d1d1f] hover:bg-black text-white rounded-full font-medium cursor-pointer disabled:opacity-50">
              {loading ? 'Đang lưu...' : 'Lưu thay đổi'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
