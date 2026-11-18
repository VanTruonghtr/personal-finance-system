import React, { useState } from 'react';
import axiosClient from '../api/axiosClient';
import { Wallet, Lock, Mail, User, ArrowRight } from 'lucide-react';

interface AuthPageProps {
  onLoginSuccess: (user: any, token: string) => void;
}

export default function AuthPage({ onLoginSuccess }: AuthPageProps) {
  const [isLogin, setIsLogin] = useState(true);
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      const endpoint = isLogin ? '/auth/login' : '/auth/register';
      const payload = isLogin ? { email, password } : { fullName, email, password };
      const res = await axiosClient.post(endpoint, payload);
      const { token, ...user } = res.data;
      localStorage.setItem('token', token);
      localStorage.setItem('user', JSON.stringify(user));
      onLoginSuccess(user, token);
    } catch (err: any) {
      setError(err.response?.data?.message || '${err?.response?.data?.message || err?.response?.data?.error || (typeof err?.response?.data === "string" ? err.response.data : "Email/mật khẩu không đúng hoặc email đã tồn tại!")}');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-emerald-50 via-slate-50 to-teal-50 p-4">
      <div className="w-full max-w-md bg-white rounded-2xl shadow-xl border border-slate-100 p-8">
        <div className="flex items-center gap-3 mb-6">
          <div className="w-11 h-11 rounded-xl bg-emerald-600 text-white flex items-center justify-center shadow-md shadow-emerald-200">
            <Wallet className="w-6 h-6" />
          </div>
          <div>
            <h1 className="text-xl font-bold text-slate-900">HTR FinMind</h1>
            <p className="text-xs text-slate-500">Hệ thống quản lý tài chính cá nhân</p>
          </div>
        </div>

        <h2 className="text-2xl font-bold text-slate-800 mb-1">
          {isLogin ? 'Chào mừng trở lại' : 'Tạo tài khoản mới'}
        </h2>
        <p className="text-sm text-slate-500 mb-6">
          {isLogin ? 'Đăng nhập để theo dõi thu chi và ngân sách của bạn' : 'Bắt đầu kiểm soát tài chính cá nhân thông minh'}
        </p>

        {error && (
          <div className="mb-4 p-3 rounded-lg bg-rose-50 border border-rose-200 text-rose-600 text-sm">
            {error}
          </div>
        )}

        <form autoComplete="off" onSubmit={handleSubmit} className="space-y-4">
          {!isLogin && (
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1">Họ và tên</label>
              <div className="relative">
                <User className="w-4 h-4 text-slate-400 absolute left-3.5 top-3" />
                <input
                  type="text"
                  required
                  value={fullName}
                  onChange={(e) => setFullName(e.target.value)}
                  placeholder="Nguyễn Văn A"
                  className="w-full pl-10 pr-4 py-2.5 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-emerald-500"
                />
              </div>
            </div>
          )}

          <div>
            <label className="block text-sm font-medium text-slate-700 mb-1">Email</label>
            <div className="relative">
              <Mail className="w-4 h-4 text-slate-400 absolute left-3.5 top-3" />
              <input
                type="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="example@gmail.com"
                className="w-full pl-10 pr-4 py-2.5 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-emerald-500"
              />
            </div>
          </div>

          <div>
            <label className="block text-sm font-medium text-slate-700 mb-1">Mật khẩu</label>
            <div className="relative">
              <Lock className="w-4 h-4 text-slate-400 absolute left-3.5 top-3" />
              <input
                type="password"
                required
                minLength={6}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="••••••••"
                className="w-full pl-10 pr-4 py-2.5 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-emerald-500"
              />
            </div>
          </div>

          <button
            type="submit"
            disabled={loading}
            className="w-full py-2.5 px-4 bg-emerald-600 hover:bg-emerald-700 text-white font-medium rounded-xl text-sm flex items-center justify-center gap-2 transition cursor-pointer disabled:opacity-60"
          >
            {loading ? 'Đang xử lý...' : isLogin ? 'Đăng nhập' : 'Đăng ký ngay'}
            <ArrowRight className="w-4 h-4" />
          </button>
        </form>

        <div className="mt-6 text-center text-sm text-slate-600">
          {isLogin ? 'Chưa có tài khoản?' : 'Đã có tài khoản?'}{' '}
          <button
            type="button"
            onClick={() => { setIsLogin(!isLogin); setError(''); }}
            className="text-emerald-600 font-semibold hover:underline cursor-pointer"
          >
            {isLogin ? 'Đăng ký miễn phí' : 'Đăng nhập'}
          </button>
        </div>
      </div>
    </div>
  );
}