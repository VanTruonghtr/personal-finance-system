import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Wallet, Mail, Lock, User, ArrowRight, AlertCircle, Loader2 } from 'lucide-react';
import axiosClient from '../api/axiosClient';

const AuthPage: React.FC = () => {
  const [isLogin, setIsLogin] = useState(true);
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  const switchMode = (loginMode: boolean) => {
    setIsLogin(loginMode);
    setError('');
    setFullName('');
    setEmail('');
    setPassword('');
  };

  const validateForm = (): string | null => {
    const trimmedEmail = email.trim();
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

    if (!isLogin && !fullName.trim()) {
      return 'Vui lòng nhập họ và tên của bạn.';
    }
    if (!trimmedEmail) {
      return 'Vui lòng nhập địa chỉ email.';
    }
    if (!emailRegex.test(trimmedEmail)) {
      return 'Định dạng email không hợp lệ (ví dụ: name@gmail.com).';
    }
    if (!password) {
      return 'Vui lòng nhập mật khẩu.';
    }
    if (!isLogin && password.length < 6) {
      return 'Mật khẩu phải có ít nhất 6 ký tự.';
    }
    return null;
  };

  const extractErrorMessage = (err: any): string => {
    if (!err.response) {
      return 'Không thể kết nối máy chủ. Nếu web vừa mở lại, vui lòng đợi 30 giây rồi bấm thử lại.';
    }

    const status = err.response.status;
    const data = err.response.data;
    const serverMsg =
      typeof data === 'string'
        ? data
        : data?.message || data?.error || '';
    const lowerMsg = serverMsg.toLowerCase();

    if (!isLogin) {
      if (
        status === 409 ||
        lowerMsg.includes('exist') ||
        lowerMsg.includes('duplicate') ||
        lowerMsg.includes('already') ||
        lowerMsg.includes('tồn tại') ||
        lowerMsg.includes('trùng')
      ) {
        return 'Email này đã được đăng ký. Vui lòng dùng email khác hoặc chuyển sang Đăng nhập.';
      }
      return serverMsg || 'Đăng ký thất bại. Vui lòng kiểm tra lại thông tin.';
    } else {
      if (status === 401 || status === 403 || status === 404 || lowerMsg.includes('bad credentials') || lowerMsg.includes('invalid')) {
        return 'Email hoặc mật khẩu không chính xác.';
      }
      return serverMsg || 'Đăng nhập thất bại. Vui lòng thử lại.';
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    const validationError = validateForm();
    if (validationError) {
      setError(validationError);
      return;
    }

    setLoading(true);
    try {
      const endpoint = isLogin ? '/auth/login' : '/auth/register';
      const payload = isLogin
        ? { email: email.trim(), password }
        : { fullName: fullName.trim(), name: fullName.trim(), email: email.trim(), password };

      const response = await axiosClient.post(endpoint, payload);
      const token = response.data?.token || response.data?.accessToken;

      if (token) {
        localStorage.setItem('token', token);
        if (response.data?.user) {
          localStorage.setItem('user', JSON.stringify(response.data.user));
        } else {
          localStorage.setItem('user', JSON.stringify({ email: email.trim(), fullName: fullName.trim() }));
        }
        navigate('/dashboard');
      } else if (!isLogin) {
        switchMode(true);
        setError('');
        alert('Đăng ký thành công! Vui lòng đăng nhập.');
      }
    } catch (err: any) {
      setError(extractErrorMessage(err));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-gradient-to-br from-emerald-50 via-white to-teal-50 flex items-center justify-center p-4">
      <div className="w-full max-w-md bg-white rounded-2xl shadow-xl border border-slate-100 p-8">
        <div className="flex items-center gap-3 mb-6">
          <div className="w-11 h-11 rounded-xl bg-emerald-600 flex items-center justify-center text-white shadow-md shadow-emerald-200">
            <Wallet className="w-6 h-6" />
          </div>
          <div>
            <h1 className="text-xl font-bold text-slate-900">HTR FinMind</h1>
            <p className="text-xs text-slate-500">Hệ thống quản lý tài chính cá nhân</p>
          </div>
        </div>

        <h2 className="text-2xl font-bold text-slate-900 mb-1">
          {isLogin ? 'Chào mừng trở lại' : 'Tạo tài khoản mới'}
        </h2>
        <p className="text-sm text-slate-500 mb-6">
          {isLogin
            ? 'Đăng nhập để theo dõi thu chi và ngân sách của bạn'
            : 'Bắt đầu kiểm soát tài chính cá nhân thông minh'}
        </p>

        {error && (
          <div className="mb-5 p-3.5 rounded-xl bg-red-50 border border-red-200 flex items-start gap-2.5 text-red-700 text-sm">
            <AlertCircle className="w-4 h-4 mt-0.5 shrink-0" />
            <span>{error}</span>
          </div>
        )}

        <form onSubmit={handleSubmit} autoComplete="off" className="space-y-4">
          {!isLogin && (
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1.5">Họ và tên</label>
              <div className="relative">
                <User className="w-4 h-4 text-slate-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
                <input
                  type="text"
                  name="register_fullname"
                  autoComplete="off"
                  value={fullName}
                  onChange={(e) => setFullName(e.target.value)}
                  placeholder="Nguyễn Văn A"
                  className="w-full pl-10 pr-4 py-2.5 rounded-xl border border-slate-200 focus:outline-none focus:ring-2 focus:ring-emerald-500 focus:border-transparent text-sm"
                />
              </div>
            </div>
          )}

          <div>
            <label className="block text-sm font-medium text-slate-700 mb-1.5">Email</label>
            <div className="relative">
              <Mail className="w-4 h-4 text-slate-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
              <input
                type="email"
                name={isLogin ? 'login_email' : 'register_email'}
                autoComplete="off"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="example@gmail.com"
                className="w-full pl-10 pr-4 py-2.5 rounded-xl border border-slate-200 focus:outline-none focus:ring-2 focus:ring-emerald-500 focus:border-transparent text-sm"
              />
            </div>
          </div>

          <div>
            <label className="block text-sm font-medium text-slate-700 mb-1.5">Mật khẩu</label>
            <div className="relative">
              <Lock className="w-4 h-4 text-slate-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
              <input
                type="password"
                name={isLogin ? 'login_password' : 'register_password'}
                autoComplete="new-password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="••••••••"
                className="w-full pl-10 pr-4 py-2.5 rounded-xl border border-slate-200 focus:outline-none focus:ring-2 focus:ring-emerald-500 focus:border-transparent text-sm"
              />
            </div>
          </div>

          <button
            type="submit"
            disabled={loading}
            className="w-full py-2.5 px-4 bg-emerald-600 hover:bg-emerald-700 disabled:opacity-60 text-white font-medium rounded-xl transition-colors flex items-center justify-center gap-2 shadow-md shadow-emerald-200 cursor-pointer"
          >
            {loading ? (
              <Loader2 className="w-4 h-4 animate-spin" />
            ) : (
              <>
                <span>{isLogin ? 'Đăng nhập' : 'Đăng ký ngay'}</span>
                <ArrowRight className="w-4 h-4" />
              </>
            )}
          </button>
        </form>

        <div className="mt-6 text-center text-sm text-slate-600">
          {isLogin ? (
            <>
              Chưa có tài khoản?{' '}
              <button
                type="button"
                onClick={() => switchMode(false)}
                className="text-emerald-600 font-semibold hover:underline cursor-pointer"
              >
                Đăng ký miễn phí
              </button>
            </>
          ) : (
            <>
              Đã có tài khoản?{' '}
              <button
                type="button"
                onClick={() => switchMode(true)}
                className="text-emerald-600 font-semibold hover:underline cursor-pointer"
              >
                Đăng nhập
              </button>
            </>
          )}
        </div>
      </div>
    </div>
  );
};

export default AuthPage;