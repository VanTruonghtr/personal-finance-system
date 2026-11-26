import React, { useEffect, useRef, useState } from 'react';
import axiosClient from '../api/axiosClient';
import { Wallet, Lock, Mail, User, ArrowRight, KeyRound, ShieldCheck, ArrowLeft } from 'lucide-react';

declare global {
  interface Window {
    google?: any;
  }
}

interface AuthPageProps {
  onLoginSuccess: (user: any, token: string) => void;
}

type AuthMode = 'login' | 'register' | 'register_otp' | 'forgot' | 'forgot_otp';

export default function AuthPage({ onLoginSuccess }: AuthPageProps) {
  const [mode, setMode] = useState<AuthMode>('login');
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [otp, setOtp] = useState('');
  const [error, setError] = useState('');
  const [successMsg, setSuccessMsg] = useState('');
  const [loading, setLoading] = useState(false);

  const googleBtnRef = useRef<HTMLDivElement | null>(null);
  const googleClientId = import.meta.env.VITE_GOOGLE_CLIENT_ID || '';

  const finishAuth = (data: any, fallbackEmail: string, fallbackName?: string) => {
    const token = data.token || data.accessToken;
    if (!token || typeof token !== 'string') {
      throw new Error('Máy chủ chưa cấp mã xác thực hợp lệ. Vui lòng thử lại!');
    }
    const { token: _t, accessToken: _at, ...rawUser } = data;
    const userObj = rawUser.user ? rawUser.user : rawUser;
    const finalUser = {
      ...userObj,
      fullName: userObj.fullName || fallbackName || fallbackEmail.split('@')[0],
      email: userObj.email || fallbackEmail,
    };
    localStorage.setItem('token', token);
    localStorage.setItem('user', JSON.stringify(finalUser));
    onLoginSuccess(finalUser, token);
  };

  const extractError = (err: any, fallback: string) => {
    const serverMsg =
      err?.response?.data?.message ||
      err?.response?.data?.error ||
      (typeof err?.response?.data === 'string' && err.response.data ? err.response.data : '');
    if (serverMsg) return serverMsg;
    if (!err?.response) return 'Máy chủ đang khởi động hoặc mất kết nối (vui lòng đợi 30 giây rồi thử lại).';
    return err.message || fallback;
  };

  // Khởi tạo nút Google Sign-In chính thức
  useEffect(() => {
    if (!googleClientId || (mode !== 'login' && mode !== 'register')) return;

    const handleGoogleCallback = async (response: any) => {
      if (!response?.credential) return;
      setError('');
      setSuccessMsg('');
      setLoading(true);
      try {
        const res = await axiosClient.post('/auth/google', { credential: response.credential });
        finishAuth(res.data || {}, res.data?.email || '');
      } catch (err: any) {
        setError(extractError(err, 'Đăng nhập Google thất bại'));
      } finally {
        setLoading(false);
      }
    };

    const initGoogle = () => {
      if (!window.google?.accounts?.id || !googleBtnRef.current) return false;
      googleBtnRef.current.innerHTML = '';
      window.google.accounts.id.initialize({
        client_id: googleClientId,
        callback: handleGoogleCallback,
      });
      window.google.accounts.id.renderButton(googleBtnRef.current, {
        theme: 'outline',
        size: 'large',
        width: 360,
        text: mode === 'login' ? 'signin_with' : 'signup_with',
        shape: 'rectangular',
        locale: 'vi',
      });
      return true;
    };

    if (initGoogle()) return;
    const timer = window.setInterval(() => {
      if (initGoogle()) window.clearInterval(timer);
    }, 300);

    return () => window.clearInterval(timer);
  }, [googleClientId, mode]);

  const switchMode = (nextMode: AuthMode) => {
    setMode(nextMode);
    setError('');
    setSuccessMsg('');
    setOtp('');
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setSuccessMsg('');
    setLoading(true);

    const cleanEmail = email.trim();

    try {
      if (mode === 'login') {
        const res = await axiosClient.post('/auth/login', { email: cleanEmail, password });
        finishAuth(res.data || {}, cleanEmail);
      } else if (mode === 'register') {
        if (password.length < 6) {
          setError('Mật khẩu phải có ít nhất 6 ký tự');
          setLoading(false);
          return;
        }
        if (password !== confirmPassword) {
          setError('Mật khẩu xác nhận không khớp');
          setLoading(false);
          return;
        }
        const res = await axiosClient.post('/auth/register/send-otp', {
          fullName: fullName.trim(),
          email: cleanEmail,
          password,
        });
        setSuccessMsg(res.data?.message || 'Đã gửi mã xác nhận tới email của bạn.');
        setMode('register_otp');
      } else if (mode === 'register_otp') {
        const res = await axiosClient.post('/auth/register/verify', {
          fullName: fullName.trim(),
          email: cleanEmail,
          password,
          otp: otp.trim(),
        });
        finishAuth(res.data || {}, cleanEmail, fullName.trim());
      } else if (mode === 'forgot') {
        const res = await axiosClient.post('/auth/forgot-password', { email: cleanEmail });
        setSuccessMsg(res.data?.message || 'Đã gửi mã đặt lại mật khẩu tới email của bạn.');
        setPassword('');
        setConfirmPassword('');
        setMode('forgot_otp');
      } else if (mode === 'forgot_otp') {
        if (password.length < 6) {
          setError('Mật khẩu mới phải có ít nhất 6 ký tự');
          setLoading(false);
          return;
        }
        if (password !== confirmPassword) {
          setError('Mật khẩu xác nhận không khớp');
          setLoading(false);
          return;
        }
        const res = await axiosClient.post('/auth/reset-password', {
          email: cleanEmail,
          otp: otp.trim(),
          newPassword: password,
        });
        setPassword('');
        setConfirmPassword('');
        setOtp('');
        setMode('login');
        setSuccessMsg(res.data?.message || 'Đặt lại mật khẩu thành công! Vui lòng đăng nhập lại.');
      }
    } catch (err: any) {
      setError(extractError(err, 'Có lỗi xảy ra, vui lòng thử lại!'));
    } finally {
      setLoading(false);
    }
  };

  const handleResendOtp = async () => {
    setError('');
    setSuccessMsg('');
    setLoading(true);
    try {
      const cleanEmail = email.trim();
      const url = mode === 'register_otp' ? '/auth/register/send-otp' : '/auth/forgot-password';
      const payload = mode === 'register_otp'
        ? { fullName: fullName.trim(), email: cleanEmail, password }
        : { email: cleanEmail };
      const res = await axiosClient.post(url, payload);
      setSuccessMsg(res.data?.message || 'Đã gửi lại mã xác nhận mới!');
    } catch (err: any) {
      setError(extractError(err, 'Không thể gửi lại mã'));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-emerald-50 via-slate-50 to-teal-50 p-4">
      <div className="w-full max-w-md bg-white rounded-2xl shadow-xl border border-slate-100 p-6 sm:p-8">
        <div className="flex items-center gap-3 mb-6">
          <div className="w-11 h-11 rounded-xl bg-emerald-600 text-white flex items-center justify-center shadow-md shadow-emerald-200 shrink-0">
            <Wallet className="w-6 h-6" />
          </div>
          <div>
            <h1 className="text-xl font-bold text-slate-900">HTR FinMind</h1>
            <p className="text-xs text-slate-500">Hệ thống quản lý tài chính cá nhân</p>
          </div>
        </div>

        {(mode === 'forgot' || mode === 'forgot_otp' || mode === 'register_otp') && (
          <button
            type="button"
            onClick={() => switchMode(mode === 'register_otp' ? 'register' : 'login')}
            className="mb-3 inline-flex items-center gap-1.5 text-xs font-medium text-slate-500 hover:text-emerald-600 cursor-pointer"
          >
            <ArrowLeft className="w-3.5 h-3.5" /> Quay lại
          </button>
        )}

        <h2 className="text-2xl font-bold text-slate-800 mb-1">
          {mode === 'login' && 'Chào mừng trở lại'}
          {mode === 'register' && 'Tạo tài khoản mới'}
          {mode === 'register_otp' && 'Xác nhận địa chỉ Email'}
          {mode === 'forgot' && 'Quên mật khẩu'}
          {mode === 'forgot_otp' && 'Đặt lại mật khẩu mới'}
        </h2>
        <p className="text-sm text-slate-500 mb-5">
          {mode === 'login' && 'Đăng nhập để theo dõi thu chi và ngân sách của bạn'}
          {mode === 'register' && 'Đăng ký tài khoản và xác thực nhanh qua mã OTP email'}
          {mode === 'register_otp' && `Nhập mã 6 số đã gửi về ${email}`}
          {mode === 'forgot' && 'Nhập email đã đăng ký để nhận mã xác nhận khôi phục mật khẩu'}
          {mode === 'forgot_otp' && `Nhập mã OTP gửi về ${email} và thiết lập mật khẩu mới`}
        </p>

        {error && (
          <div className="mb-4 p-3 rounded-xl bg-rose-50 border border-rose-200 text-rose-600 text-sm">
            {error}
          </div>
        )}

        {successMsg && (
          <div className="mb-4 p-3 rounded-xl bg-emerald-50 border border-emerald-200 text-emerald-700 text-sm">
            {successMsg}
          </div>
        )}

        <form autoComplete="off" onSubmit={handleSubmit} className="space-y-4">
          {mode === 'register' && (
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

          {(mode === 'login' || mode === 'register' || mode === 'forgot') && (
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
          )}

          {(mode === 'register_otp' || mode === 'forgot_otp') && (
            <div>
              <div className="flex items-center justify-between mb-1">
                <label className="block text-sm font-medium text-slate-700">Mã xác nhận (6 số)</label>
                <button
                  type="button"
                  disabled={loading}
                  onClick={handleResendOtp}
                  className="text-xs font-semibold text-emerald-600 hover:underline cursor-pointer disabled:opacity-50"
                >
                  Gửi lại mã
                </button>
              </div>
              <div className="relative">
                <ShieldCheck className="w-4 h-4 text-slate-400 absolute left-3.5 top-3" />
                <input
                  type="text"
                  inputMode="numeric"
                  maxLength={6}
                  required
                  value={otp}
                  onChange={(e) => setOtp(e.target.value.replace(/\D/g, ''))}
                  placeholder="123456"
                  className="w-full pl-10 pr-4 py-2.5 border border-slate-200 rounded-xl text-sm font-bold tracking-[0.35em] focus:outline-none focus:ring-2 focus:ring-emerald-500"
                />
              </div>
            </div>
          )}

          {(mode === 'login' || mode === 'register' || mode === 'forgot_otp') && (
            <div>
              <div className="flex items-center justify-between mb-1">
                <label className="block text-sm font-medium text-slate-700">
                  {mode === 'forgot_otp' ? 'Mật khẩu mới' : 'Mật khẩu'}
                </label>
                {mode === 'login' && (
                  <button
                    type="button"
                    onClick={() => switchMode('forgot')}
                    className="text-xs font-semibold text-emerald-600 hover:underline cursor-pointer"
                  >
                    Quên mật khẩu?
                  </button>
                )}
              </div>
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
          )}

          {(mode === 'register' || mode === 'forgot_otp') && (
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1">Nhập lại mật khẩu</label>
              <div className="relative">
                <KeyRound className="w-4 h-4 text-slate-400 absolute left-3.5 top-3" />
                <input
                  type="password"
                  required
                  minLength={6}
                  value={confirmPassword}
                  onChange={(e) => setConfirmPassword(e.target.value)}
                  placeholder="••••••••"
                  className="w-full pl-10 pr-4 py-2.5 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-emerald-500"
                />
              </div>
            </div>
          )}

          <button
            type="submit"
            disabled={loading}
            className="w-full py-2.5 px-4 bg-emerald-600 hover:bg-emerald-700 text-white font-medium rounded-xl text-sm flex items-center justify-center gap-2 transition cursor-pointer disabled:opacity-60"
          >
            {loading
              ? 'Đang xử lý...'
              : mode === 'login'
              ? 'Đăng nhập'
              : mode === 'register'
              ? 'Gửi mã xác nhận qua Email'
              : mode === 'register_otp'
              ? 'Xác nhận & Hoàn tất đăng ký'
              : mode === 'forgot'
              ? 'Gửi mã khôi phục'
              : 'Đặt lại mật khẩu'}
            <ArrowRight className="w-4 h-4" />
          </button>
        </form>

        {(mode === 'login' || mode === 'register') && (
          <>
            <div className="relative my-5">
              <div className="absolute inset-0 flex items-center">
                <div className="w-full border-t border-slate-200"></div>
              </div>
              <div className="relative flex justify-center text-xs">
                <span className="bg-white px-3 text-slate-400">hoặc tiếp tục với</span>
              </div>
            </div>

            {googleClientId ? (
              <div className="flex justify-center">
                <div ref={googleBtnRef} />
              </div>
            ) : (
              <div className="p-2.5 rounded-xl bg-slate-50 border border-dashed border-slate-200 text-center text-xs text-slate-500">
                Thêm <code className="font-semibold text-slate-700">VITE_GOOGLE_CLIENT_ID</code> vào file <code className="font-semibold">.env</code> để bật nút đăng nhập Google
              </div>
            )}
          </>
        )}

        {(mode === 'login' || mode === 'register') && (
          <div className="mt-6 text-center text-sm text-slate-600">
            {mode === 'login' ? 'Chưa có tài khoản?' : 'Đã có tài khoản?'}{' '}
            <button
              type="button"
              onClick={() => switchMode(mode === 'login' ? 'register' : 'login')}
              className="text-emerald-600 font-semibold hover:underline cursor-pointer"
            >
              {mode === 'login' ? 'Đăng ký miễn phí' : 'Đăng nhập'}
            </button>
          </div>
        )}
      </div>
    </div>
  );
}