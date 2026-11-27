import React, { useEffect, useState, useMemo, useRef } from 'react';
import axiosClient from './api/axiosClient';
import AuthPage from './pages/AuthPage';
import ChangePasswordModal from './components/ChangePasswordModal';
import QuickParseBar from './components/QuickParseBar';
import CsvImportModal from './components/CsvImportModal';
import KeywordRulesPanel from './components/KeywordRulesPanel';
import AnalyticsWidgets from './components/AnalyticsWidgets';
import ProfileModal from './components/ProfileModal';
import AdminPanel from './components/AdminPanel';
import type { Wallet, TransactionItem, UserProfile } from './types/finance';
import {
  LayoutDashboard, Wallet as WalletIcon, ArrowLeftRight, LogOut,
  Plus, TrendingUp, TrendingDown, Trash2, Search, CreditCard, Banknote, Smartphone,
  Pencil, Download, Calendar, ArrowUpDown, Tags, AlertTriangle, Target, PiggyBank, Repeat, Play, Eye, EyeOff, Sparkles, Send, Bot, CheckCircle2, ShieldAlert,
  Bell, Menu, X, PanelLeftClose, PanelLeftOpen, KeyRound, Upload, Shield, User as UserIcon
} from 'lucide-react';
import {
  BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, PieChart, Pie, Cell
} from 'recharts';

type TabType =
  | 'dashboard'
  | 'wallets'
  | 'transactions'
  | 'categories'
  | 'goals'
  | 'recurring'
  | 'ai'
  | 'admin';

const formatVND = (amount: number) =>
  new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(amount || 0);

const formatInputMoney = (val: string | number) => {
  const digits = String(val).replace(/\D/g, '');
  if (!digits) return '';
  return digits.replace(/\B(?=(\d{3})+(?!\d))/g, '.');
};

const parseInputMoney = (val: string) => {
  const digits = String(val).replace(/\D/g, '');
  return digits ? Number(digits) : 0;
};

const PRESET_COLORS = [
  '#f43f5e', '#f97316', '#eab308', '#10b981', '#06b6d4', '#3b82f6', '#6366f1', '#8b5cf6', '#ec4899', '#64748b'
];

function CustomDateSelect({ value, onChange, allowClear = false }: { value: string; onChange: (val: string) => void; allowClear?: boolean }) {
  const parts = value ? value.split('-') : [];
  const y = parts[0] || '';
  const m = parts[1] ? String(Number(parts[1])) : '';
  const d = parts[2] ? String(Number(parts[2])) : '';

  const updateDate = (newY: string, newM: string, newD: string) => {
    if (!newY && !newM && !newD) { onChange(''); return; }
    const now = new Date();
    const fy = newY || String(now.getFullYear());
    const fm = (newM || String(now.getMonth() + 1)).padStart(2, '0');
    const fd = (newD || String(now.getDate())).padStart(2, '0');
    onChange(`${fy}-${fm}-${fd}`);
  };

  return (
    <div className="space-y-2">
      <div className="grid grid-cols-3 gap-2">
        <select
          value={d}
          onChange={(e) => updateDate(y, m, e.target.value)}
          className="px-3 py-2 border border-slate-200 rounded-xl text-sm bg-white text-slate-700 focus:outline-none focus:border-emerald-500 cursor-pointer">
          <option value="">Ngày</option>
          {Array.from({ length: 31 }, (_, i) => i + 1).map((day) => (
            <option key={day} value={day}>Ngày {day}</option>
          ))}
        </select>

        <select
          value={m}
          onChange={(e) => updateDate(y, e.target.value, d)}
          className="px-3 py-2 border border-slate-200 rounded-xl text-sm bg-white text-slate-700 focus:outline-none focus:border-emerald-500 cursor-pointer">
          <option value="">Tháng</option>
          {Array.from({ length: 12 }, (_, i) => i + 1).map((month) => (
            <option key={month} value={month}>Tháng {month}</option>
          ))}
        </select>

        <select
          value={y}
          onChange={(e) => updateDate(e.target.value, m, d)}
          className="px-3 py-2 border border-slate-200 rounded-xl text-sm bg-white text-slate-700 focus:outline-none focus:border-emerald-500 cursor-pointer">
          <option value="">Năm</option>
          {Array.from({ length: 10 }, (_, i) => new Date().getFullYear() + i).map((year) => (
            <option key={year} value={year}>{year}</option>
          ))}
        </select>
      </div>
      {allowClear && value && (
        <div className="text-right">
          <button type="button" onClick={() => onChange('')} className="text-xs text-rose-500 hover:underline cursor-pointer">
            Xóa ngày đã chọn
          </button>
        </div>
      )}
    </div>
  );
}

function renderFormattedMessage(text: string) {
  if (!text) return null;
  const lines = String(text ?? '').split('\n');
  return lines.map((line, lineIdx) => {
    const cleanLine = line.replace(/^\s*[\*\-]\s+/, '• ');
    const parts = cleanLine.split(/(\*\*[^*]+\*\*)/g);
    return (
      <div key={lineIdx} className={line.trim() === '' ? 'h-1.5' : ''}>
        {parts.map((part, pIdx) => {
          if (part.startsWith('**') && part.endsWith('**') && part.length > 4) {
            return <strong key={pIdx} className="font-bold text-slate-900">{part.slice(2, -2)}</strong>;
          }
          return <span key={pIdx}>{part}</span>;
        })}
      </div>
    );
  });
}

function getTabFromPathname(pathname: string): TabType {
  const p = (pathname || '').toLowerCase();
  if (p.includes('/wallet')) return 'wallets';
  if (p.includes('/transaction')) return 'transactions';
  if (p.includes('/categor') || p.includes('/budget')) return 'categories';
  if (p.includes('/goal')) return 'goals';
  if (p.includes('/recurring')) return 'recurring';
  if (p.includes('/ai')) return 'ai';
  if (p.includes('/admin')) return 'admin';
  return 'dashboard';
}

function tabToPath(tab: TabType): string {
  if (tab === 'dashboard') return '/dashboard';
  if (tab === 'ai') return '/ai-advisor';
  return `/${tab}`;
}

export default function App() {
  const [user, setUser] = useState<UserProfile | null>(() => {
    const saved = localStorage.getItem('user');
    const token = localStorage.getItem('token');
    if (!saved || !token || token === 'undefined' || token === 'null') {
      localStorage.removeItem('user');
      localStorage.removeItem('token');
      return null;
    }
    try {
      return JSON.parse(saved);
    } catch {
      return null;
    }
  });
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);

  const [activeTab, setActiveTabState] = useState<TabType>(() => getTabFromPathname(window.location.pathname));

  const setActiveTab = (tab: TabType) => {
    setActiveTabState(tab);
    setMobileMenuOpen(false);
    const nextPath = tabToPath(tab);
    if (window.location.pathname !== nextPath) {
      window.history.pushState({ tab }, '', nextPath);
    }
  };

  useEffect(() => {
    const handlePopState = () => {
      setActiveTabState(getTabFromPathname(window.location.pathname));
    };
    window.addEventListener('popstate', handlePopState);
    return () => window.removeEventListener('popstate', handlePopState);
  }, []);

  const [wallets, setWallets] = useState<Wallet[]>([]);
  const [categories, setCategories] = useState<any[]>([]);
  const [transactions, setTransactions] = useState<TransactionItem[]>([]);
  const [goals, setGoals] = useState<any[]>([]);
  const [recurrings, setRecurrings] = useState<any[]>([]);

  const [showNotifDropdown, setShowNotifDropdown] = useState(false);

  const dueRecurrings = useMemo(() => {
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const limitDate = new Date(today);
    limitDate.setDate(limitDate.getDate() + 3);

    return (recurrings || []).filter((r: any) => {
      if (r.status && r.status !== 'ACTIVE') return false;
      const rawDate = r.nextDueDate || r.nextExecutionDate || r.nextDate || r.startDate;
      if (!rawDate) return false;
      const d = new Date(rawDate);
      d.setHours(0, 0, 0, 0);
      return d <= limitDate;
    });
  }, [recurrings]);

  const sixMonthCashflow = useMemo(() => {
    const months: { key: string; label: string; income: number; expense: number; net: number }[] = [];
    const now = new Date();
    for (let i = 5; i >= 0; i--) {
      const d = new Date(now.getFullYear(), now.getMonth() - i, 1);
      const yyyy = d.getFullYear();
      const mm = String(d.getMonth() + 1).padStart(2, '0');
      months.push({
        key: `${yyyy}-${mm}`,
        label: `T${d.getMonth() + 1}/${String(yyyy).slice(-2)}`,
        income: 0,
        expense: 0,
        net: 0
      });
    }

    (transactions || []).forEach((t: any) => {
      if (!t.transactionDate) return;
      const ym = String(t.transactionDate).slice(0, 7);
      const found = months.find((m) => m.key === ym);
      if (found) {
        const amt = Number(t.amount || 0);
        if (t.type === 'INCOME') found.income += amt;
        else if (t.type === 'EXPENSE') found.expense += amt;
      }
    });

    months.forEach((m) => {
      m.net = m.income - m.expense;
    });

    const maxVal = Math.max(1, ...months.map((m) => Math.max(m.income, m.expense)));
    return { months, maxVal };
  }, [transactions]);

  const [selectedMonth, setSelectedMonth] = useState<string>('ALL');
  const [hideBalance, setHideBalance] = useState<boolean>(false);
  const [aiReport, setAiReport] = useState<any>(null);
  const [aiLoading, setAiLoading] = useState<boolean>(false);
  const [aiQuestion, setAiQuestion] = useState<string>('');
  const [aiThinking, setAiThinking] = useState<boolean>(false);
  const [aiEngineInfo, setAiEngineInfo] = useState<{ provider?: string; model?: string; warning?: string }>({});
  const [miniChatOpen, setMiniChatOpen] = useState<boolean>(false);
  const chatEndRef = useRef<HTMLDivElement | null>(null);
  const miniChatEndRef = useRef<HTMLDivElement | null>(null);
  const [aiMessages, setAiMessages] = useState<{ role: 'ai' | 'user'; text: string }[]>([
    { role: 'ai', text: 'Xin chào! Mình là Trợ lý AI Phân tích Tài chính của HTR FinMind. Mình đã đọc dữ liệu thu chi, ví tiền và hạn mức của bạn. Bạn muốn hỏi gì về tình hình tài chính hiện tại?' }
  ]);

  useEffect(() => {
    chatEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    miniChatEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [aiMessages, aiThinking, miniChatOpen]);

  // State Modal Ví
  const [showWalletModal, setShowWalletModal] = useState(false);
  const [editingWalletId, setEditingWalletId] = useState<number | null>(null);
  const [walletName, setWalletName] = useState('');
  const [walletType, setWalletType] = useState<'CASH' | 'BANK' | 'EWALLET'>('BANK');
  const [walletBalance, setWalletBalance] = useState('');
  const [walletStatus, setWalletStatus] = useState<'ACTIVE' | 'INACTIVE'>('ACTIVE');

  const [showChangePassModal, setShowChangePassModal] = useState(false);
  const [showImportModal, setShowImportModal] = useState(false);
  const [showProfileModal, setShowProfileModal] = useState(false);
  const [catSubTab, setCatSubTab] = useState<'list' | 'rules'>('list');
  const [analyticsOverview, setAnalyticsOverview] = useState<any>(null);
  const [analyticsLoading, setAnalyticsLoading] = useState(false);

  // State Modal Giao dịch
  const [showTxModal, setShowTxModal] = useState(false);
  const [editingTxId, setEditingTxId] = useState<number | null>(null);
  const [txType, setTxType] = useState<'EXPENSE' | 'INCOME' | 'TRANSFER'>('EXPENSE');
  const [txWalletId, setTxWalletId] = useState<number>(0);
  const [txTargetWalletId, setTxTargetWalletId] = useState<number>(0);
  const [txCategoryId, setTxCategoryId] = useState<number>(0);
  const [txAmount, setTxAmount] = useState('');
  const [txDescription, setTxDescription] = useState('');

  // State Modal Danh mục & Hạn mức
  const [showCatModal, setShowCatModal] = useState(false);
  const [editingCatId, setEditingCatId] = useState<number | null>(null);
  const [catName, setCatName] = useState('');
  const [catType, setCatType] = useState<'EXPENSE' | 'INCOME'>('EXPENSE');
  const [catColor, setCatColor] = useState('#f43f5e');
  const [catBudgetLimit, setCatBudgetLimit] = useState('');

  // State Modal Mục tiêu tiết kiệm
  const [showGoalModal, setShowGoalModal] = useState(false);
  const [editingGoalId, setEditingGoalId] = useState<number | null>(null);
  const [goalName, setGoalName] = useState('');
  const [goalTarget, setGoalTarget] = useState('');
  const [goalCurrent, setGoalCurrent] = useState('');
  const [goalDeadline, setGoalDeadline] = useState('');
  const [goalColor, setGoalColor] = useState('#10b981');

  // State Modal Nạp / Rút mục tiêu tiết kiệm
  const [contribGoal, setContribGoal] = useState<any | null>(null);
  const [contribAction, setContribAction] = useState<'DEPOSIT' | 'WITHDRAW'>('DEPOSIT');
  const [contribWalletId, setContribWalletId] = useState<number>(0);
  const [contribAmount, setContribAmount] = useState('');

  // State Modal Giao dịch Định kỳ
  const [showRecModal, setShowRecModal] = useState(false);
  const [editingRecId, setEditingRecId] = useState<number | null>(null);
  const [recTitle, setRecTitle] = useState('');
  const [recType, setRecType] = useState<'EXPENSE' | 'INCOME'>('EXPENSE');
  const [recAmount, setRecAmount] = useState('');
  const [recFrequency, setRecFrequency] = useState<'DAILY' | 'WEEKLY' | 'MONTHLY' | 'YEARLY'>('MONTHLY');
  const [recNextDueDate, setRecNextDueDate] = useState('');
  const [recWalletId, setRecWalletId] = useState<number>(0);
  const [recCategoryId, setRecCategoryId] = useState<number>(0);

  const [filterType, setFilterType] = useState('');
  const [filterCategoryId, setFilterCategoryId] = useState<number>(0);
  const [filterKeyword, setFilterKeyword] = useState('');
  const [sortBy, setSortBy] = useState<'DATE_DESC' | 'DATE_ASC' | 'AMOUNT_DESC' | 'AMOUNT_ASC' | 'CAT_ASC' | 'CAT_DESC'>('DATE_DESC');

  const fetchAllData = async () => {
    try {
      const wRes = await axiosClient.get('/wallets');
      const walletList = Array.isArray(wRes.data) ? wRes.data : (wRes.data?.content || []);
      setWallets(walletList);
      if (walletList.length > 0 && !txWalletId) setTxWalletId(walletList[0].id);
    } catch (err) { console.error(err); }

    try {
      const cRes = await axiosClient.get('/categories');
      setCategories(Array.isArray(cRes.data) ? cRes.data : (cRes.data?.content || []));
    } catch (err) { console.error(err); }

    try {
      const gRes = await axiosClient.get('/goals');
      setGoals(Array.isArray(gRes.data) ? gRes.data : []);
    } catch (err) { console.error(err); }

    try {
      const rRes = await axiosClient.get('/recurring');
      setRecurrings(Array.isArray(rRes.data) ? rRes.data : []);
    } catch (err) { console.error(err); }

    try {
      const tRes = await axiosClient.get('/transactions');
      const rawTx = Array.isArray(tRes.data) ? tRes.data : (tRes.data?.content || []);
      const mappedTx = rawTx.map((t: any) => ({
        ...t,
        walletId: t.walletId ?? t.wallet?.id,
        walletName: t.walletName ?? t.wallet?.name ?? 'Ví mặc định',
        targetWalletId: t.targetWalletId ?? t.targetWallet?.id,
        targetWalletName: t.targetWalletName ?? t.targetWallet?.name,
        categoryId: t.categoryId ?? t.category?.id,
        categoryName: t.categoryName ?? t.category?.name ?? (t.type === 'INCOME' ? 'Thu nhập' : t.type === 'TRANSFER' ? 'Chuyển ví' : 'Tiết kiệm / Chi'),
        categoryColor: t.categoryColor ?? t.category?.color ?? (t.type === 'INCOME' ? '#10b981' : t.type === 'TRANSFER' ? '#6366f1' : '#f43f5e'),
      }));
      setTransactions(mappedTx);
    } catch (err) { console.error(err); }
  };

  const handleQuickExecuteRecurring = async (item: any) => {
    try {
      await axiosClient.post(`/recurring/${item.id}/execute`);
    } catch {
      const todayStr = new Date().toISOString().split('T')[0];
      await axiosClient.post('/transactions', {
        amount: item.amount,
        type: item.type || 'EXPENSE',
        description: `[Định kỳ] ${item.title || item.description || item.name || 'Khoản định kỳ'}`,
        transactionDate: todayStr,
        walletId: item.wallet?.id || item.walletId,
        categoryId: item.category?.id || item.categoryId
      });
    }
    await fetchAllData();
  };

  const fetchAiAnalysis = async (monthVal = selectedMonth) => {
    setAiLoading(true);
    try {
      const res = await axiosClient.get('/ai/analyze', { params: { month: monthVal } });
      setAiReport(res.data);
    } catch (err) {
      console.error(err);
    } finally {
      setAiLoading(false);
    }
  };

  const fetchAnalytics = async (monthVal = selectedMonth) => {
    setAnalyticsLoading(true);
    try {
      const params = monthVal && monthVal !== 'ALL' ? { month: monthVal } : {};
      const res = await axiosClient.get('/analytics/overview', { params });
      setAnalyticsOverview(res.data);
    } catch (err) {
      console.error(err);
    } finally {
      setAnalyticsLoading(false);
    }
  };

  useEffect(() => {
    if (user) {
      fetchAllData();
      fetchAiAnalysis(selectedMonth);
      fetchAnalytics(selectedMonth);
    }
  }, [user, selectedMonth]);

  const handleAskAi = async (customQ?: string) => {
    const q = (customQ ?? aiQuestion).trim();
    if (!q) return;
    setAiMessages((prev) => [...prev, { role: 'user', text: q }]);
    if (!customQ) setAiQuestion('');
    setAiThinking(true);
    try {
      const res = await axiosClient.post('/ai/ask', { question: q, month: selectedMonth, history: aiMessages.slice(-8) });
      setAiMessages((prev) => [...prev, { role: 'ai', text: res.data.answer }]);
      setAiEngineInfo({ provider: res.data.provider, model: res.data.model, warning: res.data.warning });
    } catch {
      setAiMessages((prev) => [...prev, { role: 'ai', text: 'Xin lỗi, không thể kết nối tới máy chủ phân tích lúc này.' }]);
    } finally {
      setAiThinking(false);
    }
  };

  useEffect(() => {
    if (activeTab === 'ai') {
      fetchAiAnalysis(selectedMonth);
    }
  }, [activeTab, selectedMonth]);

  const handleLogout = () => {
    localStorage.removeItem('token');
    localStorage.removeItem('user');
    setUser(null);
  };

  // --- CRUD VÍ ---
  const openCreateWallet = () => {
    setEditingWalletId(null);
    setWalletName('');
    setWalletType('BANK');
    setWalletBalance('');
    setWalletStatus('ACTIVE');
    setShowWalletModal(true);
  };

  const openEditWallet = (w: Wallet) => {
    setEditingWalletId(w.id);
    setWalletName(w.name);
    setWalletType(w.type || 'BANK');
    setWalletBalance(formatInputMoney(Math.round(w.balance)));
    setWalletStatus(w.status || 'ACTIVE');
    setShowWalletModal(true);
  };

  const handleSaveWallet = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      const payload = {
        name: walletName,
        type: walletType,
        balance: parseInputMoney(walletBalance),
        status: walletStatus,
      };
      if (editingWalletId) await axiosClient.put('/wallets/' + editingWalletId, payload);
      else await axiosClient.post('/wallets', payload);
      setShowWalletModal(false);
      fetchAllData();
    } catch (err: any) {
      alert(err.response?.data?.message || 'Không thể lưu thông tin ví');
    }
  };

  const handleDeleteWallet = async (id: number, name: string) => {
    if (!confirm(`Bạn có chắc chắn muốn xóa ví "${name}"?`)) return;
    try {
      await axiosClient.delete('/wallets/' + id);
      fetchAllData();
    } catch {
      alert('Không thể xóa ví này (có thể ví đang chứa giao dịch liên kết).');
    }
  };

  // --- CRUD GIAO DỊCH ---
  const openCreateTx = () => {
    if (wallets.length === 0) {
      alert('Bạn cần tạo ít nhất 1 ví trước khi ghi giao dịch!');
      openCreateWallet();
      return;
    }
    setEditingTxId(null);
    setTxType('EXPENSE');
    setTxWalletId(wallets[0].id);
    setTxTargetWalletId(wallets.length > 1 ? wallets[1].id : 0);
    const firstCat = categories.find((c) => c.type === 'EXPENSE');
    setTxCategoryId(firstCat ? firstCat.id : 0);
    setTxAmount('');
    setTxDescription('');
    setShowTxModal(true);
  };

  const openTransferFromWallet = (sourceWalletId: number) => {
    if (wallets.length < 2) {
      alert('Bạn cần có ít nhất 2 ví để thực hiện chuyển tiền nội bộ!');
      return;
    }
    setEditingTxId(null);
    setTxType('TRANSFER');
    setTxWalletId(sourceWalletId);
    const otherWallet = wallets.find((w) => w.id !== sourceWalletId);
    setTxTargetWalletId(otherWallet ? otherWallet.id : 0);
    setTxCategoryId(0);
    setTxAmount('');
    setTxDescription('');
    setShowTxModal(true);
  };

  const openEditTx = (t: any) => {
    setEditingTxId(t.id);
    setTxType(t.type || 'EXPENSE');
    setTxWalletId(t.walletId || (wallets[0]?.id ?? 0));
    setTxTargetWalletId(t.targetWalletId || 0);
    setTxCategoryId(t.categoryId || 0);
    setTxAmount(formatInputMoney(Math.round(t.amount)));
    setTxDescription(t.description || '');
    setShowTxModal(true);
  };

  const handleSaveTransaction = async (e: React.FormEvent) => {
    e.preventDefault();
    if (txType === 'TRANSFER' && (!txTargetWalletId || txTargetWalletId === txWalletId)) {
      alert('Vui lòng chọn ví nhận khác với ví nguồn!');
      return;
    }
    try {
      const payload = {
        walletId: Number(txWalletId),
        targetWalletId: txType === 'TRANSFER' ? Number(txTargetWalletId) : null,
        categoryId: txType !== 'TRANSFER' && txCategoryId ? Number(txCategoryId) : null,
        type: txType,
        amount: parseInputMoney(txAmount),
        description: txDescription,
      };
      if (editingTxId) await axiosClient.put('/transactions/' + editingTxId, payload);
      else await axiosClient.post('/transactions', payload);
      setShowTxModal(false);
      fetchAllData();
    } catch (err: any) {
      alert(err.response?.data?.message || 'Không thể lưu giao dịch');
    }
  };

  const handleDeleteTx = async (id: number) => {
    if (!confirm('Xóa giao dịch này và hoàn tác số dư ví?')) return;
    try {
      await axiosClient.delete('/transactions/' + id);
      fetchAllData();
    } catch {
      alert('Xóa thất bại');
    }
  };

  // --- CRUD DANH MỤC ---
  const openCreateCat = () => {
    setEditingCatId(null);
    setCatName('');
    setCatType('EXPENSE');
    setCatColor('#f43f5e');
    setCatBudgetLimit('');
    setShowCatModal(true);
  };

  const openEditCat = (c: any) => {
    setEditingCatId(c.id);
    setCatName(c.name);
    setCatType(c.type || 'EXPENSE');
    setCatColor(c.color || '#6366f1');
    setCatBudgetLimit(c.budgetLimit ? formatInputMoney(Math.round(c.budgetLimit)) : '');
    setShowCatModal(true);
  };

  const handleSaveCategory = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      const payload = {
        name: catName,
        type: catType,
        color: catColor,
        budgetLimit: catType === 'EXPENSE' ? parseInputMoney(catBudgetLimit) : 0,
      };
      if (editingCatId) await axiosClient.put('/categories/' + editingCatId, payload);
      else await axiosClient.post('/categories', payload);
      setShowCatModal(false);
      fetchAllData();
    } catch (err: any) {
      alert(err.response?.data?.message || 'Không thể lưu danh mục');
    }
  };

  const handleDeleteCat = async (id: number, name: string) => {
    if (!confirm(`Xóa danh mục "${name}"?`)) return;
    try {
      await axiosClient.delete('/categories/' + id);
      fetchAllData();
    } catch {
      alert('Không thể xóa danh mục đang có giao dịch sử dụng!');
    }
  };

  // --- CRUD MỤC TIÊU TIẾT KIỆM ---
  const openCreateGoal = () => {
    setEditingGoalId(null);
    setGoalName('');
    setGoalTarget('');
    setGoalCurrent('');
    setGoalDeadline('');
    setGoalColor('#10b981');
    setShowGoalModal(true);
  };

  const openEditGoal = (g: any) => {
    setEditingGoalId(g.id);
    setGoalName(g.name);
    setGoalTarget(formatInputMoney(Math.round(g.targetAmount)));
    setGoalCurrent(formatInputMoney(Math.round(g.currentAmount)));
    setGoalDeadline(g.deadline || '');
    setGoalColor(g.color || '#10b981');
    setShowGoalModal(true);
  };

  const handleSaveGoal = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      const payload = {
        name: goalName,
        targetAmount: parseInputMoney(goalTarget),
        currentAmount: parseInputMoney(goalCurrent),
        deadline: goalDeadline,
        color: goalColor,
      };
      if (editingGoalId) await axiosClient.put('/goals/' + editingGoalId, payload);
      else await axiosClient.post('/goals', payload);
      setShowGoalModal(false);
      fetchAllData();
    } catch {
      alert('Không thể lưu mục tiêu tiết kiệm');
    }
  };

  const handleDeleteGoal = async (id: number, name: string) => {
    if (!confirm(`Xóa mục tiêu "${name}"?`)) return;
    try {
      await axiosClient.delete('/goals/' + id);
      fetchAllData();
    } catch {
      alert('Xóa thất bại');
    }
  };

  const openContributeModal = (goal: any, action: 'DEPOSIT' | 'WITHDRAW') => {
    if (wallets.length === 0) {
      alert('Bạn cần có ít nhất 1 ví để thực hiện giao dịch này!');
      return;
    }
    setContribGoal(goal);
    setContribAction(action);
    setContribWalletId(wallets[0].id);
    setContribAmount('');
  };

  const handleContributeSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!contribGoal) return;
    try {
      await axiosClient.post(`/goals/${contribGoal.id}/contribute`, {
        walletId: Number(contribWalletId),
        amount: parseInputMoney(contribAmount),
        action: contribAction,
      });
      setContribGoal(null);
      fetchAllData();
    } catch (err: any) {
      alert(err.response?.data?.message || 'Giao dịch thất bại');
    }
  };

  // --- CRUD GIAO DỊCH ĐỊNH KỲ ---
  const openCreateRec = () => {
    if (wallets.length === 0) {
      alert('Bạn cần tạo ít nhất 1 ví trước!');
      return;
    }
    setEditingRecId(null);
    setRecTitle('');
    setRecType('EXPENSE');
    setRecAmount('');
    setRecFrequency('MONTHLY');
    setRecNextDueDate(new Date().toISOString().split('T')[0]);
    setRecWalletId(wallets[0].id);
    const firstCat = categories.find((c) => c.type === 'EXPENSE');
    setRecCategoryId(firstCat ? firstCat.id : 0);
    setShowRecModal(true);
  };

  const openEditRec = (r: any) => {
    setEditingRecId(r.id);
    setRecTitle(r.title);
    setRecType(r.type || 'EXPENSE');
    setRecAmount(formatInputMoney(Math.round(r.amount)));
    setRecFrequency(r.frequency || 'MONTHLY');
    setRecNextDueDate(r.nextDueDate || new Date().toISOString().split('T')[0]);
    setRecWalletId(r.wallet?.id || (wallets[0]?.id ?? 0));
    setRecCategoryId(r.category?.id || 0);
    setShowRecModal(true);
  };

  const handleSaveRec = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      const payload = {
        title: recTitle,
        type: recType,
        amount: parseInputMoney(recAmount),
        frequency: recFrequency,
        nextDueDate: recNextDueDate,
        walletId: Number(recWalletId),
        categoryId: recCategoryId ? Number(recCategoryId) : null,
      };
      if (editingRecId) await axiosClient.put('/recurring/' + editingRecId, payload);
      else await axiosClient.post('/recurring', payload);
      setShowRecModal(false);
      fetchAllData();
    } catch {
      alert('Không thể lưu khoản định kỳ');
    }
  };

  const handleExecuteRec = async (r: any) => {
    if (!confirm(`Ghi nhận khoản "${r.title}" (${formatVND(r.amount)}) vào ví ${r.wallet?.name || ''} ngay bây giờ?`)) return;
    try {
      await axiosClient.post(`/recurring/${r.id}/execute`);
      fetchAllData();
    } catch (err: any) {
      alert(err.response?.data?.message || 'Thực thi thất bại');
    }
  };

  const handleDeleteRec = async (id: number, title: string) => {
    if (!confirm(`Xóa lịch định kỳ "${title}"?`)) return;
    try {
      await axiosClient.delete('/recurring/' + id);
      fetchAllData();
    } catch {
      alert('Xóa thất bại');
    }
  };

  const availableMonths = useMemo(() => {
    const set = new Set<string>();
    transactions.forEach((t) => {
      if (t.transactionDate) set.add(t.transactionDate.slice(0, 7));
    });
    return Array.from(set).sort().reverse();
  }, [transactions]);

  const monthFilteredTx = useMemo(() => {
    if (selectedMonth === 'ALL') return transactions;
    return transactions.filter((t) => t.transactionDate && t.transactionDate.startsWith(selectedMonth));
  }, [transactions, selectedMonth]);

  const filteredTransactions = useMemo(() => {
    const filtered = monthFilteredTx.filter((t) => {
      if (filterType && t.type !== filterType) return false;
      if (filterCategoryId && t.categoryId !== filterCategoryId) return false;
      if (filterKeyword && !(t.description || '').toLowerCase().includes(filterKeyword.toLowerCase())) return false;
      return true;
    });

    return [...filtered].sort((a, b) => {
      switch (sortBy) {
        case 'DATE_ASC': return new Date(a.transactionDate).getTime() - new Date(b.transactionDate).getTime();
        case 'DATE_DESC': return new Date(b.transactionDate).getTime() - new Date(a.transactionDate).getTime();
        case 'AMOUNT_ASC': return (a.amount || 0) - (b.amount || 0);
        case 'AMOUNT_DESC': return (b.amount || 0) - (a.amount || 0);
        case 'CAT_ASC': return (a.categoryName || '').localeCompare(b.categoryName || '', 'vi');
        case 'CAT_DESC': return (b.categoryName || '').localeCompare(a.categoryName || '', 'vi');
        default: return 0;
      }
    });
  }, [monthFilteredTx, filterType, filterCategoryId, filterKeyword, sortBy]);

  const handleExportCsv = () => {
    const headers = ['ID', 'Thời gian', 'Loại', 'Danh mục', 'Ví nguồn', 'Ví nhận', 'Mô tả', 'Số tiền (VND)'];
    const rows = filteredTransactions.map((t) => [
      t.id,
      `"${new Date(t.transactionDate).toLocaleString('vi-VN')}"`,
      t.type === 'INCOME' ? 'Thu nhập' : t.type === 'EXPENSE' ? 'Chi tiêu' : 'Chuyển ví',
      t.categoryName || '',
      t.walletName || '',
      t.targetWalletName || '',
      `"${(t.description || '').replace(/"/g, '""')}"`,
      t.amount,
    ]);
    const csvContent = '\uFEFF' + [headers.join(','), ...rows.map((r) => r.join(','))].join('\n');
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `Bao_cao_tai_chinh_${selectedMonth}.csv`;
    link.click();
    URL.revokeObjectURL(url);
  };

  const stats = useMemo(() => {
    const totalBalance = wallets.filter((w) => w.status === 'ACTIVE').reduce((sum, w) => sum + w.balance, 0);
    const totalSaved = goals.reduce((sum, g) => sum + (g.currentAmount || 0), 0);
    const totalIncome = monthFilteredTx.filter((t) => t.type === 'INCOME').reduce((sum, t) => sum + t.amount, 0);
    const totalExpense = monthFilteredTx.filter((t) => t.type === 'EXPENSE').reduce((sum, t) => sum + t.amount, 0);

    const expenseByCatMap: Record<string, { name: string; value: number; color: string }> = {};
    const spentByCatId: Record<number, number> = {};

    monthFilteredTx
      .filter((t) => t.type === 'EXPENSE')
      .forEach((t) => {
        if (t.categoryId) spentByCatId[t.categoryId] = (spentByCatId[t.categoryId] || 0) + t.amount;
        if (t.categoryName) {
          const key = t.categoryName;
          if (!expenseByCatMap[key]) expenseByCatMap[key] = { name: key, value: 0, color: t.categoryColor || '#6366F1' };
          expenseByCatMap[key].value += t.amount;
        }
      });

    // Hạn mức tháng luôn đối chiếu với chi tiêu trong tháng hiện tại (nếu đang chọn ALL) hoặc tháng đang chọn
    const now = new Date();
    const currentYm = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
    const budgetTargetYm = selectedMonth === 'ALL' ? currentYm : selectedMonth;
    const budgetSpentByCatId: Record<number, number> = {};

    transactions
      .filter((t) => t.type === 'EXPENSE' && t.transactionDate && t.transactionDate.startsWith(budgetTargetYm))
      .forEach((t) => {
        if (t.categoryId) {
          budgetSpentByCatId[t.categoryId] = (budgetSpentByCatId[t.categoryId] || 0) + t.amount;
        }
      });

    const budgetProgress = categories
      .filter((c) => c.type === 'EXPENSE' && c.budgetLimit > 0)
      .map((c) => {
        const spent = budgetSpentByCatId[c.id] || 0;
        const percent = Math.round((spent / c.budgetLimit) * 100);
        return { ...c, spent, percent };
      })
      .sort((a, b) => b.percent - a.percent);

    return {
      totalBalance,
      totalSaved,
      totalIncome,
      totalExpense,
      spentByCatId,
      budgetSpentByCatId,
      budgetTargetYm,
      budgetProgress,
      expensePieData: Object.values(expenseByCatMap),
      overviewBarData: [
        { name: 'Tổng Thu', amount: totalIncome },
        { name: 'Tổng Chi', amount: totalExpense },
      ],
    };
  }, [wallets, goals, monthFilteredTx, transactions, selectedMonth, categories]);

  if (!user) return <AuthPage onLoginSuccess={(u) => setUser(u)} />;

  const filteredCategories = categories.filter((c) => c.type === txType);
  const recFilteredCategories = categories.filter((c) => c.type === recType);

  return (
    <div className="min-h-screen flex bg-transparent text-[#1d1d1f] relative overflow-x-clip">
      {/* Lớp phủ mờ khi mở menu trên điện thoại */}
      {mobileMenuOpen && (
        <div
          onClick={() => setMobileMenuOpen(false)}
          className="fixed inset-0 bg-slate-900/40 backdrop-blur-[1px] z-40 lg:hidden"
        />
      )}

      <aside
        className={
          'fixed lg:sticky top-0 left-0 z-50 h-screen apple-glass-strong border-r border-black/[0.06] flex flex-col justify-between transition-all duration-300 shrink-0 ' +
          (mobileMenuOpen ? 'translate-x-0 ' : '-translate-x-full lg:translate-x-0 ') +
          (sidebarCollapsed ? 'w-64 lg:w-20 p-4 lg:px-2.5' : 'w-64 p-5')
        }
      >
        <div className="overflow-y-auto overflow-x-hidden">
          <div className={'flex items-center justify-between mb-7 ' + (sidebarCollapsed ? 'lg:justify-center px-1' : 'px-1')}>
            <div className="flex items-center gap-3 min-w-0">
              <div className="w-9 h-9 rounded-[11px] bg-gradient-to-br from-[#0071e3] to-[#2997ff] text-white flex items-center justify-center font-bold shadow-sm shrink-0">
                F
              </div>
              <div className={sidebarCollapsed ? 'lg:hidden' : ''}>
                <div className="font-bold text-slate-900 truncate">HTR FinMind</div>
                <div className="text-[11px] text-slate-400 truncate">Smart Financial Management</div>
              </div>
            </div>

            {/* Nút đóng trên Mobile */}
            <button
              type="button"
              onClick={() => setMobileMenuOpen(false)}
              className="lg:hidden p-1.5 text-slate-500 hover:bg-slate-100 rounded-lg cursor-pointer"
              title="Đóng menu"
            >
              <X className="w-5 h-5" />
            </button>

            {/* Nút thu gọn / mở rộng trên Desktop */}
            {!sidebarCollapsed && (
              <button
                type="button"
                onClick={() => setSidebarCollapsed(true)}
                className="hidden lg:flex p-1.5 text-slate-400 hover:text-slate-700 hover:bg-slate-100 rounded-lg transition cursor-pointer"
                title="Thu nhỏ thanh bên"
              >
                <PanelLeftClose className="w-4 h-4" />
              </button>
            )}
          </div>

          {sidebarCollapsed && (
            <div className="hidden lg:flex justify-center mb-4">
              <button
                type="button"
                onClick={() => setSidebarCollapsed(false)}
                className="p-2 text-slate-400 hover:text-emerald-600 hover:bg-emerald-50 rounded-xl transition cursor-pointer"
                title="Mở rộng thanh bên"
              >
                <PanelLeftOpen className="w-4 h-4" />
              </button>
            </div>
          )}

          <nav className="space-y-1.5">
            <button
              onClick={() => setActiveTab('dashboard')}
              title="Tổng quan & Thống kê"
              className={'w-full flex items-center gap-3 px-3.5 py-2.5 rounded-xl text-sm font-medium transition cursor-pointer ' + (sidebarCollapsed ? 'lg:justify-center lg:px-0 ' : '') + (activeTab === 'dashboard' ? 'bg-[#1d1d1f] text-white font-semibold shadow-sm' : 'text-[#424245] hover:bg-black/[0.04]')}>
              <LayoutDashboard className="w-4 h-4 shrink-0" />
              <span className={sidebarCollapsed ? 'lg:hidden truncate' : 'truncate'}>Tổng quan & Thống kê</span>
            </button>

            <button
              onClick={() => setActiveTab('wallets')}
              title={`Quản lý Ví tiền (${wallets.length})`}
              className={'w-full flex items-center gap-3 px-3.5 py-2.5 rounded-xl text-sm font-medium transition cursor-pointer ' + (sidebarCollapsed ? 'lg:justify-center lg:px-0 ' : '') + (activeTab === 'wallets' ? 'bg-[#1d1d1f] text-white font-semibold shadow-sm' : 'text-[#424245] hover:bg-black/[0.04]')}>
              <WalletIcon className="w-4 h-4 shrink-0" />
              <span className={sidebarCollapsed ? 'lg:hidden truncate' : 'truncate'}>Quản lý Ví tiền ({wallets.length})</span>
            </button>

            <button
              onClick={() => setActiveTab('transactions')}
              title={`Sổ Giao dịch (${monthFilteredTx.length})`}
              className={'w-full flex items-center gap-3 px-3.5 py-2.5 rounded-xl text-sm font-medium transition cursor-pointer ' + (sidebarCollapsed ? 'lg:justify-center lg:px-0 ' : '') + (activeTab === 'transactions' ? 'bg-[#1d1d1f] text-white font-semibold shadow-sm' : 'text-[#424245] hover:bg-black/[0.04]')}>
              <ArrowLeftRight className="w-4 h-4 shrink-0" />
              <span className={sidebarCollapsed ? 'lg:hidden truncate' : 'truncate'}>Sổ Giao dịch ({monthFilteredTx.length})</span>
            </button>

            <button
              onClick={() => setActiveTab('categories')}
              title={`Danh mục & Ngân sách (${categories.length})`}
              className={'w-full flex items-center gap-3 px-3.5 py-2.5 rounded-xl text-sm font-medium transition cursor-pointer ' + (sidebarCollapsed ? 'lg:justify-center lg:px-0 ' : '') + (activeTab === 'categories' ? 'bg-[#1d1d1f] text-white font-semibold shadow-sm' : 'text-[#424245] hover:bg-black/[0.04]')}>
              <Tags className="w-4 h-4 shrink-0" />
              <span className={sidebarCollapsed ? 'lg:hidden truncate' : 'truncate'}>Danh mục & Ngân sách ({categories.length})</span>
            </button>

            <button
              onClick={() => setActiveTab('goals')}
              title={`Mục tiêu Tiết kiệm (${goals.length})`}
              className={'w-full flex items-center gap-3 px-3.5 py-2.5 rounded-xl text-sm font-medium transition cursor-pointer ' + (sidebarCollapsed ? 'lg:justify-center lg:px-0 ' : '') + (activeTab === 'goals' ? 'bg-[#1d1d1f] text-white font-semibold shadow-sm' : 'text-[#424245] hover:bg-black/[0.04]')}>
              <Target className="w-4 h-4 shrink-0" />
              <span className={sidebarCollapsed ? 'lg:hidden truncate' : 'truncate'}>Mục tiêu Tiết kiệm ({goals.length})</span>
            </button>

            <button
              onClick={() => setActiveTab('recurring')}
              title={`Giao dịch Định kỳ (${recurrings.length})`}
              className={'w-full flex items-center gap-3 px-3.5 py-2.5 rounded-xl text-sm font-medium transition cursor-pointer ' + (sidebarCollapsed ? 'lg:justify-center lg:px-0 ' : '') + (activeTab === 'recurring' ? 'bg-[#1d1d1f] text-white font-semibold shadow-sm' : 'text-[#424245] hover:bg-black/[0.04]')}>
              <Repeat className="w-4 h-4 shrink-0" />
              <span className={sidebarCollapsed ? 'lg:hidden truncate' : 'truncate'}>Giao dịch Định kỳ ({recurrings.length})</span>
            </button>

            <button
              onClick={() => { setActiveTab('ai'); fetchAiAnalysis(selectedMonth); }}
              title="AI Phân tích Tài chính"
              className={'w-full flex items-center gap-3 px-3.5 py-2.5 rounded-xl text-sm font-semibold transition cursor-pointer ' + (sidebarCollapsed ? 'lg:justify-center lg:px-0 ' : '') + (activeTab === 'ai' ? 'bg-gradient-to-r from-[#0071e3] via-[#5856d6] to-[#af52de] text-white shadow-sm' : 'text-[#5856d6] bg-[#5856d6]/[0.08] hover:bg-[#5856d6]/[0.14]')}>
              <Sparkles className="w-4 h-4 shrink-0" />
              <span className={sidebarCollapsed ? 'lg:hidden truncate' : 'truncate'}>AI Phân tích Tài chính</span>
            </button>
            {user.role?.toUpperCase() === 'ADMIN' && (
              <button
                onClick={() => setActiveTab('admin')}
                title="Quản trị Hệ thống"
                className={'w-full flex items-center gap-3 px-3.5 py-2.5 rounded-xl text-sm font-medium transition cursor-pointer ' + (sidebarCollapsed ? 'lg:justify-center lg:px-0 ' : '') + (activeTab === 'admin' ? 'bg-[#1d1d1f] text-white font-semibold shadow-sm' : 'text-[#424245] hover:bg-black/[0.04]')}>
                <Shield className="w-4 h-4 shrink-0" />
                <span className={sidebarCollapsed ? 'lg:hidden truncate' : 'truncate'}>Quản trị Hệ thống</span>
              </button>
            )}
          </nav>
        </div>

        <div className="pt-4 border-t border-slate-100">
          <button
            type="button"
            onClick={() => setShowProfileModal(true)}
            title="Hồ sơ & Ngân sách tổng"
            className={'w-full text-left px-2.5 py-2 mb-2 rounded-xl hover:bg-black/[0.04] transition cursor-pointer flex items-center gap-2.5 ' + (sidebarCollapsed ? 'lg:justify-center lg:px-0' : '')}>
            <div className="w-8 h-8 rounded-full bg-[#1d1d1f] text-white flex items-center justify-center text-xs font-bold shrink-0">
              <UserIcon className="w-4 h-4" />
            </div>
            <div className={'min-w-0 ' + (sidebarCollapsed ? 'lg:hidden' : '')}>
              <div className="text-sm font-semibold text-[#1d1d1f] truncate">{user.fullName}</div>
              <div className="text-xs text-[#6e6e73] truncate">{user.email}</div>
            </div>
          </button>

          <button
            onClick={() => setShowChangePassModal(true)}
            title="Đổi mật khẩu"
            className={'w-full flex items-center gap-2 px-3.5 py-2 mb-1 rounded-xl text-sm text-slate-600 hover:bg-slate-100 transition cursor-pointer ' + (sidebarCollapsed ? 'lg:justify-center lg:px-0' : '')}>
            <KeyRound className="w-4 h-4 shrink-0" />
            <span className={sidebarCollapsed ? 'lg:hidden' : ''}>Đổi mật khẩu</span>
          </button>

          <button
            onClick={handleLogout}
            title="Đăng xuất"
            className={'w-full flex items-center gap-2 px-3.5 py-2 rounded-xl text-sm text-rose-600 hover:bg-rose-50 transition cursor-pointer ' + (sidebarCollapsed ? 'lg:justify-center lg:px-0' : '')}>
            <LogOut className="w-4 h-4 shrink-0" />
            <span className={sidebarCollapsed ? 'lg:hidden' : ''}>Đăng xuất</span>
          </button>
        </div>
      </aside>

            <main className="flex-1 min-w-0 p-4 sm:p-6 lg:p-8">
        {/* Thanh trên cố định cho điện thoại */}
        <div className="lg:hidden sticky top-0 z-30 -mx-4 -mt-4 sm:-mx-6 sm:-mt-6 mb-5 px-4 py-3 apple-glass border-b border-black/[0.06] flex items-center justify-between">
          <div className="flex items-center gap-2.5 min-w-0">
            <button
              type="button"
              onClick={() => setMobileMenuOpen(true)}
              className="p-2 -ml-1 rounded-xl text-slate-700 hover:bg-slate-100 active:bg-slate-200 shrink-0 cursor-pointer"
              title="Mở danh mục"
            >
              <Menu className="w-5 h-5" />
            </button>
            <div className="w-8 h-8 rounded-[10px] bg-gradient-to-br from-[#0071e3] to-[#2997ff] text-white flex items-center justify-center font-bold text-sm shadow-xs shrink-0">
              F
            </div>
            <span className="font-bold text-slate-900 text-base truncate">HTR FinMind</span>
          </div>
        </div>
        <header className="flex flex-wrap items-center justify-between gap-3 sm:gap-4 mb-6 sm:mb-8">
          <div className="flex items-center gap-3 min-w-0 w-full sm:w-auto">
            <div className="min-w-0">
            <h1 className="text-xl sm:text-[26px] font-bold tracking-[-0.025em] text-[#1d1d1f] leading-tight sm:truncate">
              {activeTab === 'dashboard' && 'Tổng quan tài chính'}
              {activeTab === 'wallets' && 'Danh sách Ví & Tài khoản'}
              {activeTab === 'transactions' && 'Lịch sử Giao dịch'}
              {activeTab === 'categories' && 'Danh mục & Hạn mức Ngân sách'}
              {activeTab === 'goals' && 'Mục tiêu Tiết kiệm & Tích lũy'}
              {activeTab === 'recurring' && 'Khoản Thu / Chi Định kỳ'}
              {activeTab === 'ai' && 'Trợ lý AI Phân tích Tài chính'}
              {activeTab === 'admin' && 'Quản trị Hệ thống'}
            </h1>
            <p className="hidden sm:block text-xs sm:text-sm text-slate-500 truncate">Theo dõi dòng tiền và phân tích chi tiêu theo thời gian thực</p>
            </div>
          </div>

          <div className="flex flex-wrap items-center gap-2.5 sm:gap-3 w-full sm:w-auto">
            <div className="flex items-center gap-2 w-full sm:w-auto">
            <div
              onClick={() => setActiveTab('wallets')}
              title="Nhấp để xem chi tiết Quản lý Ví tiền"
              className="flex items-center gap-2.5 bg-white/90 hover:bg-white border border-black/[0.07] px-3.5 py-2 rounded-2xl shadow-xs transition cursor-pointer select-none flex-1 sm:flex-none min-w-0">
                <div className="w-7 h-7 rounded-xl bg-gradient-to-br from-[#34c759] to-[#248a3d] text-white flex items-center justify-center shrink-0 shadow-2xs">
                <WalletIcon className="w-4 h-4" />
              </div>
              <div>
                <div className="text-[11px] font-medium text-[#6e6e73] leading-none mb-0.5 truncate">Tổng số dư</div>
                <div className="text-sm font-bold text-[#1d1d1f] leading-none truncate">
                  {hideBalance ? '•••••••• ₫' : formatVND(stats.totalBalance)}
                </div>
              </div>
              <button
                type="button"
                onClick={(e) => {
                  e.stopPropagation();
                  setHideBalance(!hideBalance);
                }}
                className="ml-auto sm:ml-1 p-1 text-[#6e6e73] hover:text-[#1d1d1f] hover:bg-black/[0.05] rounded-lg transition cursor-pointer shrink-0"
                title={hideBalance ? 'Hiện số dư' : 'Ẩn số dư'}>
                {hideBalance ? <EyeOff className="w-3.5 h-3.5" /> : <Eye className="w-3.5 h-3.5" />}
              </button>
            </div>

            <div className="relative">
              <button
                type="button"
                onClick={() => setShowNotifDropdown(!showNotifDropdown)}
                title="Nhắc nhở giao dịch định kỳ sắp đến hạn"
                className="relative p-2.5 bg-white hover:bg-slate-50 border border-slate-200 rounded-xl text-slate-600 transition cursor-pointer">
                <Bell className="w-4 h-4" />
                {dueRecurrings.length > 0 && (
                  <span className="absolute -top-1.5 -right-1.5 min-w-[18px] h-[18px] px-1 rounded-full bg-rose-500 text-white text-[10px] font-bold flex items-center justify-center shadow-xs">
                    {dueRecurrings.length}
                  </span>
                )}
              </button>

              {showNotifDropdown && (
                <div className="fixed left-3 right-3 top-16 sm:absolute sm:left-auto sm:right-0 sm:top-full mt-2 sm:w-80 apple-glass-strong apple-popover-enter rounded-2xl shadow-2xl border border-black/[0.08] z-50 overflow-hidden">
                  <div className="px-4 py-3 bg-slate-50 border-b border-slate-100 flex items-center justify-between">
                    <span className="text-xs font-bold text-slate-800 uppercase tracking-wider">Nhắc lịch định kỳ ({dueRecurrings.length})</span>
                    <button type="button" onClick={() => setShowNotifDropdown(false)} className="text-xs text-slate-400 hover:text-slate-600 cursor-pointer">Đóng</button>
                  </div>
                  <div className="max-h-72 overflow-y-auto divide-y divide-slate-100">
                    {dueRecurrings.length === 0 ? (
                      <div className="p-4 text-center text-xs text-slate-500">Không có khoản định kỳ nào đến hạn trong 3 ngày tới.</div>
                    ) : (
                      dueRecurrings.map((item: any) => (
                        <div key={item.id} className="p-3.5 hover:bg-slate-50/80 transition flex items-center justify-between gap-2">
                          <div className="min-w-0">
                            <div className="text-sm font-semibold text-slate-800 truncate">{item.title || item.description || item.name || 'Khoản định kỳ'}</div>
                            <div className="text-xs text-slate-500 mt-0.5">
                              Hạn: <span className="font-medium text-amber-600">{item.nextDueDate || item.nextExecutionDate || item.nextDate || item.startDate}</span> ·{' '}
                              <span className={item.type === 'INCOME' ? 'text-emerald-600 font-semibold' : 'text-rose-600 font-semibold'}>
                                {formatVND(item.amount || 0)}
                              </span>
                            </div>
                          </div>
                          <button
                            type="button"
                            onClick={() => handleQuickExecuteRecurring(item)}
                            className="shrink-0 px-2.5 py-1.5 bg-emerald-600 hover:bg-emerald-700 text-white text-xs font-semibold rounded-lg transition cursor-pointer">
                            Ghi nhận
                          </button>
                        </div>
                      ))
                    )}
                  </div>
                </div>
              )}
            </div>

            </div>

            {(activeTab === 'dashboard' || activeTab === 'transactions' || activeTab === 'categories' || activeTab === 'ai') && (
              <div className="flex items-center gap-2 bg-white border border-slate-200 px-3 py-2.5 sm:py-2 rounded-xl text-sm w-full sm:w-auto">
                <Calendar className="w-4 h-4 text-slate-400 shrink-0" />
                <select
                  value={selectedMonth}
                  onChange={(e) => setSelectedMonth(e.target.value)}
                  className="bg-transparent focus:outline-none text-slate-700 font-medium cursor-pointer w-full sm:w-auto min-w-0">
                  <option value="ALL">Tất cả thời gian</option>
                  {availableMonths.map((m) => (
                    <option key={m} value={m}>
                      Tháng {m.split('-')[1]}/{m.split('-')[0]}
                    </option>
                  ))}
                </select>
              </div>
            )}

            {activeTab === 'categories' ? (
              <button onClick={openCreateCat} className="px-4 py-2.5 bg-[#0071e3] hover:bg-[#0077ed] text-white rounded-full text-sm font-medium flex items-center justify-center gap-2 shadow-sm shadow-[#0071e3]/20 cursor-pointer w-full sm:w-auto whitespace-nowrap">
                <Plus className="w-4 h-4" /> Thêm Danh mục
              </button>
            ) : activeTab === 'goals' ? (
              <button onClick={openCreateGoal} className="px-4 py-2.5 bg-[#0071e3] hover:bg-[#0077ed] text-white rounded-full text-sm font-medium flex items-center justify-center gap-2 shadow-sm shadow-[#0071e3]/20 cursor-pointer w-full sm:w-auto whitespace-nowrap">
                <Plus className="w-4 h-4" /> Tạo Mục tiêu mới
              </button>
            ) : activeTab === 'recurring' ? (
              <button onClick={openCreateRec} className="px-4 py-2.5 bg-[#0071e3] hover:bg-[#0077ed] text-white rounded-full text-sm font-medium flex items-center justify-center gap-2 shadow-sm shadow-[#0071e3]/20 cursor-pointer w-full sm:w-auto whitespace-nowrap">
                <Plus className="w-4 h-4" /> Thêm Khoản Định kỳ
              </button>
            ) : (
              <>
                <button onClick={openCreateWallet} className="px-4 py-2.5 bg-white border border-black/[0.09] hover:bg-black/[0.03] text-[#1d1d1f] rounded-full text-sm font-medium flex items-center justify-center gap-1.5 sm:gap-2 cursor-pointer flex-1 sm:flex-none whitespace-nowrap shadow-2xs">
                  <Plus className="w-4 h-4" /> Thêm Ví
                </button>
                <button onClick={openCreateTx} className="px-4 py-2.5 bg-[#1d1d1f] hover:bg-black text-white rounded-full text-sm font-medium flex items-center justify-center gap-1.5 sm:gap-2 shadow-sm cursor-pointer flex-1 sm:flex-none whitespace-nowrap">
                  <Plus className="w-4 h-4" /> Ghi Giao dịch
                </button>
              </>
            )}
          </div>
        </header>

        {activeTab === 'dashboard' && (
          <div key="tab-dashboard" className="space-y-6 apple-tab-enter">
            <QuickParseBar
              wallets={wallets}
              categories={categories}
              onCreated={async () => { await fetchAllData(); await fetchAnalytics(selectedMonth); }}
            />
            <div className="bg-white rounded-2xl border border-slate-200/80 p-4 sm:p-5 shadow-2xs">
              <div className="flex flex-wrap items-center justify-between gap-2 mb-5">
                <div>
                  <h3 className="text-base font-bold text-slate-800">Xu hướng Dòng tiền Thu – Chi (6 tháng gần nhất)</h3>
                  <p className="text-xs text-slate-500 mt-0.5">So sánh tổng thu nhập, chi tiêu thực tế và mức tích lũy ròng theo từng tháng</p>
                </div>
                <div className="flex items-center gap-4 text-xs font-medium">
                  <span className="flex items-center gap-1.5 text-slate-600">
                    <span className="w-2.5 h-2.5 rounded-xs bg-emerald-500 inline-block"></span> Thu nhập
                  </span>
                  <span className="flex items-center gap-1.5 text-slate-600">
                    <span className="w-2.5 h-2.5 rounded-xs bg-rose-400 inline-block"></span> Chi tiêu
                  </span>
                </div>
              </div>

              <div className="grid grid-cols-6 gap-1.5 sm:gap-3 items-end h-52 pt-6 pb-2 px-0 sm:px-2 border-b border-slate-100">
                {sixMonthCashflow.months.map((m) => {
                  const incPct = Math.round((m.income / sixMonthCashflow.maxVal) * 100);
                  const expPct = Math.round((m.expense / sixMonthCashflow.maxVal) * 100);
                  return (
                    <div key={m.key} className="group relative flex flex-col items-center h-full justify-end">
                      <div className="opacity-0 group-hover:opacity-100 pointer-events-none transition-opacity absolute -top-14 z-20 bg-slate-900 text-white text-[11px] rounded-xl px-2.5 py-1.5 shadow-lg whitespace-nowrap">
                        <div className="font-bold text-slate-200 mb-0.5">{m.label}</div>
                        <div className="text-emerald-400">Thu: {formatVND(m.income)}</div>
                        <div className="text-rose-300">Chi: {formatVND(m.expense)}</div>
                      </div>

                      <div className="w-full flex items-end justify-center gap-1.5 h-40">
                        <div
                          style={{ height: `${Math.max(incPct, m.income > 0 ? 6 : 2)}%` }}
                          className="w-3.5 sm:w-5 bg-emerald-500 hover:bg-emerald-600 rounded-t-md transition-all duration-300"
                        />
                        <div
                          style={{ height: `${Math.max(expPct, m.expense > 0 ? 6 : 2)}%` }}
                          className="w-3.5 sm:w-5 bg-rose-400 hover:bg-rose-500 rounded-t-md transition-all duration-300"
                        />
                      </div>
                    </div>
                  );
                })}
              </div>

              <div className="grid grid-cols-6 gap-1.5 sm:gap-3 pt-2.5 px-0 sm:px-2 text-center">
                {sixMonthCashflow.months.map((m) => (
                  <div key={m.key}>
                    <div className="text-xs font-bold text-slate-700">{m.label}</div>
                    <div className={`text-[9px] sm:text-[10px] font-semibold mt-0.5 truncate ${m.net >= 0 ? 'text-emerald-600' : 'text-rose-600'}`}>
                      {m.net >= 0 ? '+' : ''}{Math.round(m.net / 1000).toLocaleString('vi-VN')}k
                    </div>
                  </div>
                ))}
              </div>
            </div>

            <div className="grid grid-cols-1 lg:grid-cols-4 gap-4 sm:gap-5 apple-stagger">
              <div className="lg:col-span-2 apple-titanium-card p-6 text-white flex flex-col justify-between min-h-[168px]">
                <div className="flex items-center justify-between gap-2">
                  <div className="flex items-center gap-2">
                    <span className="w-2 h-2 rounded-full bg-[#34c759] shadow-[0_0_8px_#34c759]" />
                    <span className="text-xs font-semibold uppercase tracking-[0.12em] text-white/65">Tổng tài sản khả dụng</span>
                  </div>
                  <button
                    type="button"
                    onClick={() => setHideBalance(!hideBalance)}
                    className="p-2 rounded-full bg-white/10 hover:bg-white/20 text-white/80 hover:text-white backdrop-blur-md transition cursor-pointer"
                    title={hideBalance ? 'Hiện số dư' : 'Ẩn số dư'}>
                    {hideBalance ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                  </button>
                </div>

                <div className="my-4">
                  <div className="text-3xl sm:text-[34px] font-bold tracking-[-0.03em] text-white leading-tight">
                    {hideBalance ? '•••••••• ₫' : formatVND(stats.totalBalance)}
                  </div>
                  <div className="text-xs text-white/55 mt-1">Từ {wallets.length} ví đang hoạt động</div>
                </div>

                <div className="flex flex-wrap items-center gap-2 pt-3 border-t border-white/10 text-xs">
                  <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-white/10 backdrop-blur-md text-white/90 font-medium">
                    <PiggyBank className="w-3.5 h-3.5 text-[#bf5af2]" />
                    Tiết kiệm: <strong>{hideBalance ? '••••' : formatVND(stats.totalSaved)}</strong> ({goals.length})
                  </span>
                </div>
              </div>

              <div className="bg-white p-6 rounded-2xl border border-slate-100 shadow-sm flex flex-col justify-between">
                <div className="flex items-center justify-between mb-3">
                  <span className="text-xs font-semibold uppercase tracking-wider text-[#86868b]">Tổng Thu nhập</span>
                  <div className="w-9 h-9 rounded-full bg-[#34c759]/12 text-[#248a3d] flex items-center justify-center">
                    <TrendingUp className="w-4 h-4" />
                  </div>
                </div>
                <div>
                  <div className="text-2xl font-bold tracking-tight text-[#248a3d]">+{formatVND(stats.totalIncome)}</div>
                  <div className="text-xs text-[#86868b] mt-1.5">{selectedMonth === 'ALL' ? 'Tất cả thời gian' : `Trong tháng ${selectedMonth}`}</div>
                </div>
              </div>

              <div className="bg-white p-6 rounded-2xl border border-slate-100 shadow-sm flex flex-col justify-between">
                <div className="flex items-center justify-between mb-3">
                  <span className="text-xs font-semibold uppercase tracking-wider text-[#86868b]">Tổng Chi tiêu</span>
                  <div className="w-9 h-9 rounded-full bg-[#ff3b30]/12 text-[#d70015] flex items-center justify-center">
                    <TrendingDown className="w-4 h-4" />
                  </div>
                </div>
                <div>
                  <div className="text-2xl font-bold tracking-tight text-[#d70015]">-{formatVND(stats.totalExpense)}</div>
                  <div className="text-xs text-[#86868b] mt-1.5">{selectedMonth === 'ALL' ? 'Tất cả thời gian' : `Trong tháng ${selectedMonth}`}</div>
                </div>
              </div>
            </div>

            <AnalyticsWidgets
              analytics={analyticsOverview}
              loading={analyticsLoading}
              onOpenBudgetSettings={() => setShowProfileModal(true)}
            />

            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
              <div className="bg-white p-4 sm:p-6 rounded-2xl border border-slate-100 shadow-sm min-w-0">
                <h3 className="font-semibold text-slate-800 mb-4">So sánh Thu nhập & Chi tiêu</h3>
                <div className="h-72">
                  <ResponsiveContainer width="100%" height="100%">
                    <BarChart data={stats.overviewBarData}>
                      <XAxis dataKey="name" />
                      <YAxis />
                      <Tooltip formatter={(val: any) => formatVND(Number(val))} />
                      <Bar dataKey="amount" radius={[8, 8, 0, 0]}>
                        <Cell fill="#2563eb" />
                        <Cell fill="#e11d48" />
                      </Bar>
                    </BarChart>
                  </ResponsiveContainer>
                </div>
              </div>

              <div className="bg-white p-4 sm:p-6 rounded-2xl border border-slate-100 shadow-sm min-w-0">
                <h3 className="font-semibold text-slate-800 mb-4">Cơ cấu chi tiêu theo danh mục</h3>
                {stats.expensePieData.length === 0 ? (
                  <div className="h-72 flex items-center justify-center text-sm text-slate-400">Chưa có dữ liệu chi tiêu để vẽ biểu đồ</div>
                ) : (
                  <>
                  <div className="h-64 sm:h-72">
                    <ResponsiveContainer width="100%" height="100%">
                      <PieChart>
                        <Pie data={stats.expensePieData} dataKey="value" nameKey="name" cx="50%" cy="50%" innerRadius="46%" outerRadius="85%" paddingAngle={2}>
                          {stats.expensePieData.map((entry, idx) => <Cell key={idx} fill={entry.color} />)}
                        </Pie>
                        <Tooltip formatter={(val: any) => formatVND(Number(val))} />
                      </PieChart>
                    </ResponsiveContainer>
                  </div>
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 mt-4 pt-3 border-t border-slate-100">
                    {[...stats.expensePieData]
                      .sort((a, b) => b.value - a.value)
                      .map((entry, idx) => {
                        const total = stats.expensePieData.reduce((s, item) => s + (item.value || 0), 0) || 1;
                        const pct = ((entry.value / total) * 100).toFixed(1);
                        return (
                          <div key={idx} className="flex items-center justify-between gap-2 px-3 py-2 rounded-xl bg-slate-50/80 text-xs">
                            <div className="flex items-center gap-2 min-w-0">
                              <span className="w-2.5 h-2.5 rounded-full shrink-0" style={{ backgroundColor: entry.color }} />
                              <span className="font-medium text-slate-700 truncate">{entry.name}</span>
                            </div>
                            <div className="flex items-center gap-1.5 shrink-0">
                              <span className="font-semibold text-slate-900">{formatVND(entry.value)}</span>
                              <span className="text-[11px] text-slate-400">({pct}%)</span>
                            </div>
                          </div>
                        );
                      })}
                  </div>
                  </>
                )}
              </div>
            </div>

            {stats.budgetProgress.length > 0 && (
              <div className="bg-white p-4 sm:p-6 rounded-2xl border border-slate-100 shadow-sm">
                <div className="flex flex-wrap items-center justify-between gap-2 mb-4">
                  <h3 className="font-semibold text-slate-800">
                    Theo dõi Hạn mức Chi tiêu (Tháng {stats.budgetTargetYm.split('-')[1]}/{stats.budgetTargetYm.split('-')[0]})
                  </h3>
                  <button onClick={() => setActiveTab('categories')} className="text-xs text-emerald-600 font-semibold hover:underline cursor-pointer">
                    Điều chỉnh hạn mức →
                  </button>
                </div>
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  {stats.budgetProgress.map((b) => {
                    const isOver = b.spent > b.budgetLimit;
                    const overPct = isOver ? Math.round(((b.spent - b.budgetLimit) / b.budgetLimit) * 100) : 0;
                    const isWarn = !isOver && b.percent >= 80;
                    return (
                      <div key={b.id} className={'p-4 rounded-xl border ' + (isOver ? 'border-rose-200 bg-rose-50/60' : isWarn ? 'border-amber-200 bg-amber-50/40' : 'border-slate-100 bg-slate-50/50')}>
                        <div className="flex flex-wrap justify-between gap-x-2 gap-y-1 text-sm mb-1.5">
                          <span className="font-semibold text-slate-800 flex items-center gap-2">
                            <span className="w-3 h-3 rounded-full" style={{ backgroundColor: b.color }} />
                            {b.name}
                          </span>
                          <span className="text-xs font-medium text-slate-600">{formatVND(b.spent)} / {formatVND(b.budgetLimit)} ({b.percent}%)</span>
                        </div>
                        {isOver && (
                          <div className="mb-2 px-2.5 py-1.5 rounded-lg bg-rose-100 border border-rose-200 text-rose-700 text-xs font-semibold flex items-center gap-1.5">
                            <AlertTriangle className="w-4 h-4 shrink-0 text-rose-600" />
                            <span>Vượt quá <strong>{overPct}%</strong> hạn mức (+{formatVND(b.spent - b.budgetLimit)})</span>
                          </div>
                        )}
                        <div className="w-full h-2.5 bg-slate-200 rounded-full overflow-hidden">
                          <div className={'h-full rounded-full transition-all ' + (isOver ? 'bg-rose-600' : isWarn ? 'bg-amber-500' : 'bg-emerald-500')} style={{ width: `${Math.min(b.percent, 100)}%` }} />
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>
            )}
          </div>
        )}

        {activeTab === 'wallets' && (
          <div key="tab-wallets" className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 gap-4 sm:gap-6 apple-tab-enter">
            {wallets.length === 0 ? (
              <div className="col-span-full bg-white p-8 sm:p-12 rounded-2xl border border-dashed border-slate-300 text-center">
                <p className="text-slate-500 mb-4">Bạn chưa có ví nào. Hãy tạo ví đầu tiên!</p>
                <button onClick={openCreateWallet} className="px-4 py-2 bg-emerald-600 text-white rounded-xl text-sm font-medium cursor-pointer">+ Tạo Ví ngay</button>
              </div>
            ) : (
              wallets.map((w) => {
                const cardStyle =
                  w.type === 'BANK'
                    ? { background: 'radial-gradient(120% 100% at 100% 0%, rgba(56, 189, 248, 0.24) 0%, transparent 55%), linear-gradient(135deg, #1c1c1e 0%, #263147 55%, #111827 100%)' }
                    : w.type === 'CASH'
                    ? { background: 'radial-gradient(120% 100% at 100% 0%, rgba(52, 211, 153, 0.24) 0%, transparent 55%), linear-gradient(135deg, #0b3b2c 0%, #14634a 55%, #08291f 100%)' }
                    : { background: 'radial-gradient(120% 100% at 100% 0%, rgba(244, 114, 182, 0.26) 0%, transparent 55%), linear-gradient(135deg, #2e1f6b 0%, #5856d6 55%, #7c3aed 100%)' };
                return (
                <div key={w.id} style={cardStyle} className="apple-wallet-card p-6 text-white flex flex-col justify-between min-h-[196px]">
                  <div>
                    <div className="flex items-center justify-between mb-6">
                      <span className="px-3 py-1 rounded-full text-[11px] font-semibold bg-white/15 backdrop-blur-md text-white/90 tracking-wide">
                        {w.type === 'CASH' ? 'Tiền mặt' : w.type === 'BANK' ? 'Ngân hàng' : 'Ví điện tử'}
                      </span>
                      <div className="flex items-center gap-1">
                        <button onClick={() => openTransferFromWallet(w.id)} className="p-1.5 text-white/75 hover:text-white hover:bg-white/15 rounded-full transition cursor-pointer" title="Chuyển tiền từ ví này">
                          <ArrowLeftRight className="w-4 h-4" />
                        </button>
                        <button onClick={() => openEditWallet(w)} className="p-1.5 text-white/75 hover:text-white hover:bg-white/15 rounded-full transition cursor-pointer" title="Chỉnh sửa ví">
                          <Pencil className="w-4 h-4" />
                        </button>
                        <button onClick={() => handleDeleteWallet(w.id, w.name)} className="p-1.5 text-white/75 hover:text-rose-200 hover:bg-white/15 rounded-full transition cursor-pointer" title="Xóa ví">
                          <Trash2 className="w-4 h-4" />
                        </button>
                      </div>
                    </div>
                    <div className="flex items-center gap-2.5">
                      <div className="w-8 h-8 rounded-full bg-white/15 backdrop-blur-md flex items-center justify-center shrink-0">
                        {w.type === 'CASH' && <Banknote className="w-4 h-4 text-emerald-200" />}
                        {w.type === 'BANK' && <CreditCard className="w-4 h-4 text-sky-200" />}
                        {w.type === 'EWALLET' && <Smartphone className="w-4 h-4 text-pink-200" />}
                      </div>
                      <h3 className="text-lg font-semibold text-white/95 truncate">{w.name}</h3>
                    </div>
                    <div className="text-2xl sm:text-[26px] font-bold tracking-tight text-white mt-3">
                      {hideBalance ? '•••••••• ₫' : formatVND(w.balance)}
                    </div>
                  </div>
                  <div className="mt-5 pt-3 border-t border-white/15 text-[11px] text-white/65 flex justify-between">
                    <span>{w.status === 'ACTIVE' ? '● Đang sử dụng' : '○ Ngừng sử dụng'}</span>
                    <span className="font-mono opacity-80">#{String(w.id).padStart(4, '0')}</span>
                  </div>
                </div>
                );
              })
            )}
          </div>
        )}

        {activeTab === 'transactions' && (
          <div key="tab-transactions" className="space-y-4 apple-tab-enter">
            <QuickParseBar
              wallets={wallets}
              categories={categories}
              onCreated={async () => { await fetchAllData(); await fetchAnalytics(selectedMonth); }}
            />
          <div className="bg-white rounded-2xl border border-slate-100 shadow-sm overflow-hidden">
            <div className="p-4 border-b border-slate-100 flex flex-wrap items-center justify-between gap-3">
              <div className="flex flex-wrap items-center gap-2 sm:gap-3 flex-1 min-w-0 w-full sm:w-auto">
                <div className="relative w-full sm:flex-1 sm:max-w-xs">
                  <Search className="w-4 h-4 text-slate-400 absolute left-3 top-2.5" />
                  <input type="text" placeholder="Tìm mô tả giao dịch..." value={filterKeyword} onChange={(e) => setFilterKeyword(e.target.value)} className="w-full pl-9 pr-4 py-2 bg-[#f2f2f7] hover:bg-[#e8e8ed]/80 focus:bg-white border border-transparent rounded-full text-sm transition focus:outline-none" />
                </div>
                <select value={filterType} onChange={(e) => setFilterType(e.target.value)} className="flex-1 sm:flex-none min-w-0 px-3.5 py-2 border border-black/[0.08] rounded-full text-sm bg-white cursor-pointer">
                  <option value="">Tất cả loại</option>
                  <option value="INCOME">Thu nhập</option>
                  <option value="EXPENSE">Chi tiêu</option>
                  <option value="TRANSFER">Chuyển ví</option>
                </select>
                <select value={filterCategoryId} onChange={(e) => setFilterCategoryId(Number(e.target.value))} className="flex-1 sm:flex-none min-w-0 px-3.5 py-2 border border-black/[0.08] rounded-full text-sm bg-white cursor-pointer">
                  <option value={0}>Tất cả danh mục</option>
                  {categories.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
                </select>
                <div className="flex items-center gap-1.5 border border-black/[0.08] rounded-full px-3.5 py-2 bg-white flex-1 sm:flex-none min-w-0">
                  <ArrowUpDown className="w-4 h-4 text-slate-400 shrink-0" />
                  <select value={sortBy} onChange={(e) => setSortBy(e.target.value as any)} className="text-sm bg-transparent focus:outline-none cursor-pointer font-medium text-slate-700 w-full min-w-0">
                    <option value="DATE_DESC">Thời gian: Mới nhất trước</option>
                    <option value="DATE_ASC">Thời gian: Cũ nhất trước</option>
                    <option value="AMOUNT_DESC">Số tiền: Cao → Thấp</option>
                    <option value="AMOUNT_ASC">Số tiền: Thấp → Cao</option>
                    <option value="CAT_ASC">Danh mục: A → Z</option>
                    <option value="CAT_DESC">Danh mục: Z → A</option>
                  </select>
                </div>
              </div>
              <div className="flex items-center gap-2 w-full sm:w-auto"><button onClick={() => setShowImportModal(true)} className="px-4 py-2 bg-[#1d1d1f] hover:bg-black text-white rounded-full text-sm font-medium flex items-center justify-center gap-2 cursor-pointer flex-1 sm:flex-none whitespace-nowrap"><Upload className="w-4 h-4" /> Import CSV</button><button onClick={handleExportCsv} className="px-4 py-2 bg-[#f2f2f7] hover:bg-[#e5e5ea] text-[#1d1d1f] rounded-full text-sm font-medium flex items-center justify-center gap-2 cursor-pointer flex-1 sm:flex-none whitespace-nowrap">
                <Download className="w-4 h-4" /> Xuất CSV
              </button></div>
            </div>


            <div className="md:hidden divide-y divide-slate-100">
              {filteredTransactions.length === 0 ? (
                <div className="py-8 text-center text-sm text-slate-400">Chưa có giao dịch nào</div>
              ) : (
                filteredTransactions.map((t) => (
                  <div key={t.id} className="p-4 flex items-start gap-3">
                    <div className="min-w-0 flex-1">
                      <div className="flex items-start justify-between gap-2">
                        <div className="min-w-0">
                          {t.type === 'TRANSFER' ? (
                            <span className="inline-flex items-center px-2 py-0.5 rounded-full text-[11px] bg-indigo-50 text-indigo-600 font-semibold whitespace-nowrap">Chuyển ví nội bộ</span>
                          ) : (
                            <span className="inline-flex items-center gap-1.5 max-w-full px-2.5 py-1 rounded-full text-[11px] bg-[#f2f2f7] text-[#1d1d1f] font-medium truncate"><span className="w-2 h-2 rounded-full shrink-0" style={{ backgroundColor: t.categoryColor || '#64748b' }} />{t.categoryName}</span>
                          )}
                        </div>
                        <div className={'shrink-0 text-sm font-bold ' + (t.type === 'INCOME' ? 'text-emerald-600' : t.type === 'EXPENSE' ? 'text-rose-600' : 'text-indigo-600')}>
                          {t.type === 'INCOME' ? '+' : t.type === 'EXPENSE' ? '-' : ''}{formatVND(t.amount)}
                        </div>
                      </div>
                      <div className="mt-1.5 text-sm text-slate-700 break-words">{t.description || '-'}</div>
                      <div className="mt-1 flex items-center justify-between gap-2 text-xs text-slate-400">
                        <span className="truncate">{t.walletName}{t.targetWalletName ? ` → ${t.targetWalletName}` : ''} · {new Date(t.transactionDate).toLocaleString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' })}</span>
                        <div className="flex items-center gap-0.5 shrink-0 -mr-1.5">
                          <button onClick={() => openEditTx(t)} className="p-2 text-slate-400 hover:text-blue-600 hover:bg-blue-50 rounded-lg cursor-pointer" title="Chỉnh sửa giao dịch"><Pencil className="w-4 h-4" /></button>
                          <button onClick={() => handleDeleteTx(t.id)} className="p-2 text-slate-400 hover:text-rose-600 hover:bg-rose-50 rounded-lg cursor-pointer" title="Xóa giao dịch"><Trash2 className="w-4 h-4" /></button>
                        </div>
                      </div>
                    </div>
                  </div>
                ))
              )}
            </div>


            <div className="hidden md:block overflow-x-auto">
              <table className="w-full text-left border-collapse">
                <thead>
                  <tr className="bg-white text-[#86868b] text-[11px] tracking-wider uppercase border-b border-black/[0.06] select-none">
                    <th onClick={() => setSortBy(sortBy === 'DATE_DESC' ? 'DATE_ASC' : 'DATE_DESC')} className="py-3.5 px-4 cursor-pointer hover:text-emerald-600 transition">
                      <span className="inline-flex items-center gap-1">Thời gian <ArrowUpDown className="w-3 h-3" /></span>
                    </th>
                    <th onClick={() => setSortBy(sortBy === 'CAT_ASC' ? 'CAT_DESC' : 'CAT_ASC')} className="py-3.5 px-4 cursor-pointer hover:text-emerald-600 transition">
                      <span className="inline-flex items-center gap-1">Danh mục / Loại <ArrowUpDown className="w-3 h-3" /></span>
                    </th>
                    <th className="py-3.5 px-4">Ví</th>
                    <th className="py-3.5 px-4">Mô tả</th>
                    <th onClick={() => setSortBy(sortBy === 'AMOUNT_DESC' ? 'AMOUNT_ASC' : 'AMOUNT_DESC')} className="py-3.5 px-4 text-right cursor-pointer hover:text-emerald-600 transition">
                      <span className="inline-flex items-center justify-end gap-1">Số tiền <ArrowUpDown className="w-3 h-3" /></span>
                    </th>
                    <th className="py-3.5 px-4 text-center">Thao tác</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 text-sm">
                  {filteredTransactions.length === 0 ? (
                    <tr><td colSpan={6} className="py-8 text-center text-slate-400">Chưa có giao dịch nào</td></tr>
                  ) : (
                    filteredTransactions.map((t) => (
                      <tr key={t.id} className="hover:bg-[#f5f5f7]/70 transition-colors">
                        <td className="py-3.5 px-4 text-slate-500">{new Date(t.transactionDate).toLocaleString('vi-VN')}</td>
                        <td className="py-3.5 px-4 font-medium">
                          {t.type === 'TRANSFER' ? (
                            <span className="px-2.5 py-1 rounded-full text-xs bg-indigo-50 text-indigo-600 font-semibold">Chuyển ví nội bộ</span>
                          ) : (
                            <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs bg-[#f2f2f7] text-[#1d1d1f] font-medium"><span className="w-2 h-2 rounded-full shrink-0" style={{ backgroundColor: t.categoryColor || '#64748b' }} />{t.categoryName}</span>
                          )}
                        </td>
                        <td className="py-3.5 px-4 text-slate-700">{t.walletName}{t.targetWalletName ? ` → ${t.targetWalletName}` : ''}</td>
                        <td className="py-3.5 px-4 text-slate-600">{t.description || '-'}</td>
                        <td className={'py-3.5 px-4 text-right font-semibold ' + (t.type === 'INCOME' ? 'text-emerald-600' : t.type === 'EXPENSE' ? 'text-rose-600' : 'text-indigo-600')}>
                          {t.type === 'INCOME' ? '+' : t.type === 'EXPENSE' ? '-' : ''}{formatVND(t.amount)}
                        </td>
                        <td className="py-3.5 px-4 text-center">
                          <div className="flex items-center justify-center gap-1">
                            <button onClick={() => openEditTx(t)} className="p-1.5 text-slate-400 hover:text-blue-600 hover:bg-blue-50 rounded-lg cursor-pointer" title="Chỉnh sửa giao dịch"><Pencil className="w-4 h-4" /></button>
                            <button onClick={() => handleDeleteTx(t.id)} className="p-1.5 text-slate-400 hover:text-rose-600 hover:bg-rose-50 rounded-lg cursor-pointer" title="Xóa giao dịch"><Trash2 className="w-4 h-4" /></button>
                          </div>
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          </div>
          </div>
        )}

        {activeTab === 'categories' && (
          <div key="tab-categories" className="space-y-5 apple-tab-enter">
            <div className="inline-flex p-1 rounded-full bg-black/[0.06]">
              <button
                type="button"
                onClick={() => setCatSubTab('list')}
                className={'px-4 py-1.5 rounded-full text-xs font-semibold transition cursor-pointer ' + (catSubTab === 'list' ? 'bg-white text-[#1d1d1f] shadow-xs' : 'text-[#6e6e73] hover:text-[#1d1d1f]')}>
                Danh mục & Hạn mức ({categories.length})
              </button>
              <button
                type="button"
                onClick={() => setCatSubTab('rules')}
                className={'px-4 py-1.5 rounded-full text-xs font-semibold transition cursor-pointer ' + (catSubTab === 'rules' ? 'bg-white text-[#1d1d1f] shadow-xs' : 'text-[#6e6e73] hover:text-[#1d1d1f]')}>
                Quy tắc từ khóa tự động
              </button>
            </div>
            {catSubTab === 'rules' ? (
              <KeywordRulesPanel categories={categories} />
            ) : (
            <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 gap-4 sm:gap-6">
            {categories.map((c) => {
              const limit = c.budgetLimit || 0;
              const spent = limit > 0
                ? (stats.budgetSpentByCatId[c.id] || 0)
                : (stats.spentByCatId[c.id] || 0);
              const percent = limit > 0 ? Math.round((spent / limit) * 100) : 0;
              const targetLabel = `${stats.budgetTargetYm.split('-')[1]}/${stats.budgetTargetYm.split('-')[0]}`;
              return (
                <div key={c.id} className="bg-white p-6 rounded-2xl border border-slate-100 shadow-sm flex flex-col justify-between">
                  <div>
                    <div className="flex items-center justify-between mb-3">
                      <span className="px-3 py-1 rounded-full text-xs font-semibold text-white" style={{ backgroundColor: c.color || '#64748b' }}>
                        {c.type === 'EXPENSE' ? 'Khoản Chi' : 'Khoản Thu'}
                      </span>
                      <div className="flex items-center gap-1">
                        <button onClick={() => openEditCat(c)} className="p-1.5 text-slate-400 hover:text-blue-600 hover:bg-blue-50 rounded-lg cursor-pointer"><Pencil className="w-4 h-4" /></button>
                        <button onClick={() => handleDeleteCat(c.id, c.name)} className="p-1.5 text-slate-400 hover:text-rose-600 hover:bg-rose-50 rounded-lg cursor-pointer"><Trash2 className="w-4 h-4" /></button>
                      </div>
                    </div>
                    <h3 className="text-lg font-bold text-slate-800">{c.name}</h3>
                    {c.type === 'EXPENSE' && (
                      <div className="mt-3">
                        <div className="flex flex-wrap justify-between gap-x-2 gap-y-0.5 text-xs text-slate-500 mb-1.5">
                          <span>
                            Đã chi{limit > 0 ? ` (T${targetLabel})` : ''}: <strong className="text-slate-700">{formatVND(spent)}</strong>
                          </span>
                          <span>Hạn mức: {limit > 0 ? formatVND(limit) : 'Chưa đặt'}</span>
                        </div>
                        {limit > 0 && (
                          <>
                            <div className="w-full h-2.5 bg-slate-100 rounded-full overflow-hidden">
                              <div className={'h-full rounded-full ' + (spent > limit ? 'bg-rose-600' : percent >= 80 ? 'bg-amber-500' : 'bg-emerald-500')} style={{ width: `${Math.min(percent, 100)}%` }} />
                            </div>
                            {spent > limit ? (
                              <div className="mt-3 p-2.5 rounded-xl bg-rose-50 border border-rose-200 text-rose-700 text-xs flex items-start gap-2">
                                <AlertTriangle className="w-4 h-4 shrink-0 text-rose-600 mt-0.5" />
                                <div>
                                  <div className="font-bold">Vượt quá {Math.round(((spent - limit) / limit) * 100)}% hạn mức tháng {targetLabel}!</div>
                                  <div className="text-rose-600 mt-0.5">Chi vượt: +{formatVND(spent - limit)} (Tổng dùng {percent}%)</div>
                                </div>
                              </div>
                            ) : percent >= 80 ? (
                              <div className="mt-3 p-2 rounded-xl bg-amber-50 border border-amber-200 text-amber-800 text-xs font-medium">
                                Đã dùng {percent}% hạn mức, còn lại {formatVND(limit - spent)}
                              </div>
                            ) : (
                              <div className="mt-2 text-xs text-emerald-600 font-medium">An toàn • Còn lại {formatVND(limit - spent)} ({100 - percent}%)</div>
                            )}
                          </>
                        )}
                      </div>
                    )}
                  </div>
                </div>
              );
            })}
            </div>
            )}
          </div>
        )}

        {activeTab === 'goals' && (
          <div key="tab-goals" className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 gap-4 sm:gap-6 apple-tab-enter">
            {goals.length === 0 ? (
              <div className="col-span-full bg-white p-8 sm:p-12 rounded-2xl border border-dashed border-slate-300 text-center">
                <Target className="w-10 h-10 text-emerald-600 mx-auto mb-3" />
                <p className="text-slate-600 font-medium mb-1">Chưa có mục tiêu tiết kiệm nào</p>
                <p className="text-xs text-slate-400 mb-4">Hãy đặt mục tiêu tích lũy (VD: Mua Laptop, Quỹ dự phòng, Du lịch...)</p>
                <button onClick={openCreateGoal} className="px-4 py-2 bg-emerald-600 text-white rounded-xl text-sm font-medium cursor-pointer">+ Tạo Mục tiêu đầu tiên</button>
              </div>
            ) : (
              goals.map((g) => {
                const target = g.targetAmount || 1;
                const current = g.currentAmount || 0;
                const pct = Math.min(100, Math.round((current / target) * 100));
                const isDone = current >= g.targetAmount;
                return (
                  <div key={g.id} className="bg-white p-6 rounded-2xl border border-slate-100 shadow-sm flex flex-col justify-between">
                    <div>
                      <div className="flex items-center justify-between mb-3">
                        <span className="px-3 py-1 rounded-full text-xs font-semibold text-white" style={{ backgroundColor: g.color || '#10b981' }}>
                          {isDone ? 'Đã hoàn thành 🎉' : `Đạt ${pct}%`}
                        </span>
                        <div className="flex items-center gap-1">
                          <button onClick={() => openEditGoal(g)} className="p-1.5 text-slate-400 hover:text-blue-600 hover:bg-blue-50 rounded-lg cursor-pointer"><Pencil className="w-4 h-4" /></button>
                          <button onClick={() => handleDeleteGoal(g.id, g.name)} className="p-1.5 text-slate-400 hover:text-rose-600 hover:bg-rose-50 rounded-lg cursor-pointer"><Trash2 className="w-4 h-4" /></button>
                        </div>
                      </div>
                      <h3 className="text-lg font-bold text-slate-800">{g.name}</h3>
                      {g.deadline && (
                        <div className="text-xs text-slate-400 mt-0.5">Hạn hoàn thành: {new Date(g.deadline).toLocaleDateString('vi-VN')}</div>
                      )}
                      <div className="mt-4">
                        <div className="flex flex-wrap justify-between gap-x-2 gap-y-0.5 text-xs mb-1.5">
                          <span className="font-bold text-emerald-600">{formatVND(current)}</span>
                          <span className="text-slate-500">Mục tiêu: {formatVND(g.targetAmount)}</span>
                        </div>
                        <div className="w-full h-3 bg-slate-100 rounded-full overflow-hidden">
                          <div className="h-full rounded-full transition-all" style={{ width: `${pct}%`, backgroundColor: g.color || '#10b981' }} />
                        </div>
                      </div>
                    </div>
                    <div className="mt-5 pt-4 border-t border-slate-100 flex gap-2">
                      <button onClick={() => openContributeModal(g, 'DEPOSIT')} className="flex-1 py-2 px-3 bg-emerald-600 hover:bg-emerald-700 text-white rounded-xl text-xs font-semibold cursor-pointer">+ Nạp từ Ví</button>
                      <button onClick={() => openContributeModal(g, 'WITHDRAW')} className="py-2 px-3 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-xl text-xs font-semibold cursor-pointer">Rút về Ví</button>
                    </div>
                  </div>
                );
              })
            )}
          </div>
        )}

        {activeTab === 'recurring' && (
          <div key="tab-recurring" className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 gap-4 sm:gap-6 apple-tab-enter">
            {recurrings.length === 0 ? (
              <div className="col-span-full bg-white p-8 sm:p-12 rounded-2xl border border-dashed border-slate-300 text-center">
                <Repeat className="w-10 h-10 text-emerald-600 mx-auto mb-3" />
                <p className="text-slate-600 font-medium mb-1">Chưa có khoản thu/chi định kỳ nào</p>
                <p className="text-xs text-slate-400 mb-4">Thiết lập Tiền trọ, Internet, Netflix hoặc Lương tháng để thanh toán nhanh chỉ với 1 bấm</p>
                <button onClick={openCreateRec} className="px-4 py-2 bg-emerald-600 text-white rounded-xl text-sm font-medium cursor-pointer">+ Thêm Khoản Định kỳ</button>
              </div>
            ) : (
              recurrings.map((r) => {
                const isDue = r.nextDueDate && new Date(r.nextDueDate) <= new Date();
                const freqLabel = r.frequency === 'DAILY' ? 'Hàng ngày' : r.frequency === 'WEEKLY' ? 'Hàng tuần' : r.frequency === 'YEARLY' ? 'Hàng năm' : 'Hàng tháng';
                return (
                  <div key={r.id} className={'bg-white p-6 rounded-2xl border shadow-sm flex flex-col justify-between ' + (isDue ? 'border-amber-300' : 'border-slate-100')}>
                    <div>
                      <div className="flex items-center justify-between mb-3">
                        <span className={'px-2.5 py-1 rounded-lg text-xs font-semibold ' + (r.type === 'INCOME' ? 'bg-emerald-50 text-emerald-700' : 'bg-rose-50 text-rose-700')}>
                          {freqLabel} • {r.type === 'INCOME' ? 'Khoản Thu' : 'Khoản Chi'}
                        </span>
                        <div className="flex items-center gap-1">
                          <button onClick={() => openEditRec(r)} className="p-1.5 text-slate-400 hover:text-blue-600 hover:bg-blue-50 rounded-lg cursor-pointer"><Pencil className="w-4 h-4" /></button>
                          <button onClick={() => handleDeleteRec(r.id, r.title)} className="p-1.5 text-slate-400 hover:text-rose-600 hover:bg-rose-50 rounded-lg cursor-pointer"><Trash2 className="w-4 h-4" /></button>
                        </div>
                      </div>
                      <h3 className="text-lg font-bold text-slate-800">{r.title}</h3>
                      <div className={'text-2xl font-bold mt-1 ' + (r.type === 'INCOME' ? 'text-emerald-600' : 'text-rose-600')}>
                        {r.type === 'INCOME' ? '+' : '-'}{formatVND(r.amount)}
                      </div>
                      <div className="mt-3 space-y-1 text-xs text-slate-500">
                        <div>Ví áp dụng: <strong className="text-slate-700">{r.wallet?.name || 'Chưa chọn'}</strong></div>
                        {r.category && <div>Danh mục: <strong className="text-slate-700">{r.category.name}</strong></div>}
                        <div className={isDue ? 'text-amber-700 font-semibold' : ''}>
                          Kỳ hạn tiếp theo: {r.nextDueDate ? new Date(r.nextDueDate).toLocaleDateString('vi-VN') : '-'} {isDue ? '(Đã đến hạn)' : ''}
                        </div>
                      </div>
                    </div>
                    <div className="mt-5 pt-4 border-t border-slate-100">
                      <button
                        onClick={() => handleExecuteRec(r)}
                        className="w-full py-2.5 px-4 bg-emerald-600 hover:bg-emerald-700 text-white rounded-xl text-xs font-semibold flex items-center justify-center gap-2 cursor-pointer">
                        <Play className="w-3.5 h-3.5" /> Ghi nhận kỳ này & Chuyển kỳ tiếp theo
                      </button>
                    </div>
                  </div>
                );
              })
            )}
          </div>
        )}

        {activeTab === 'ai' && (
          <div key="tab-ai" className="space-y-6 apple-tab-enter">
            {aiLoading && !aiReport ? (
              <div className="bg-white p-12 rounded-2xl border border-slate-100 text-center text-slate-500">
                Đang phân tích dữ liệu tài chính của bạn...
              </div>
            ) : aiReport ? (
              <>
                <div className="grid grid-cols-1 md:grid-cols-3 gap-5">
                  <div className="bg-gradient-to-br from-[#0071e3] via-[#5856d6] to-[#af52de] text-white p-6 rounded-[22px] shadow-md shadow-[#5856d6]/20">
                    <div className="flex items-center justify-between mb-2">
                      <span className="text-xs uppercase tracking-wider text-indigo-100 font-semibold">Điểm Sức khỏe Tài chính</span>
                      <Sparkles className="w-5 h-5 text-amber-300" />
                    </div>
                    <div className="flex items-baseline gap-2 my-1">
                      <span className="text-4xl font-extrabold">{aiReport.healthScore}</span>
                      <span className="text-sm text-indigo-200">/ 100 điểm</span>
                    </div>
                    <div className="inline-block px-2.5 py-0.5 rounded-full bg-white/20 text-xs font-semibold mt-1">
                      Đánh giá: {aiReport.healthLabel}
                    </div>
                  </div>

                  <div className="bg-white p-6 rounded-2xl border border-slate-100 shadow-sm">
                    <div className="text-xs font-semibold text-slate-400 uppercase mb-1">Tỷ lệ Tiết kiệm ròng</div>
                    <div className={'text-3xl font-extrabold my-1 ' + (aiReport.savingsRate >= 20 ? 'text-emerald-600' : aiReport.savingsRate >= 0 ? 'text-amber-600' : 'text-rose-600')}>
                      {aiReport.savingsRate}%
                    </div>
                    <p className="text-xs text-slate-500">
                      Chuẩn quy tắc 50/30/20 khuyến nghị tối thiểu <strong>20%</strong> thu nhập dành cho tích lũy.
                    </p>
                  </div>

                  <div className="bg-white p-6 rounded-2xl border border-slate-100 shadow-sm">
                    <div className="text-xs font-semibold text-slate-400 uppercase mb-1">Điểm nghẽn chi tiêu lớn nhất</div>
                    <div className="text-xl font-bold text-slate-800 my-1 truncate">{aiReport.topCategoryName}</div>
                    <p className="text-xs text-slate-500">
                      Chiếm <strong className="text-rose-600">{aiReport.topCategoryPct}%</strong> tổng chi ({formatVND(aiReport.topCategoryAmount)})
                    </p>
                  </div>
                </div>

                <div className="grid grid-cols-1 lg:grid-cols-5 gap-6">
                  <div className="lg:col-span-3 bg-white p-4 sm:p-6 rounded-2xl border border-slate-100 shadow-sm space-y-4">
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <h3 className="font-bold text-slate-800 flex items-center gap-2">
                        <Bot className="w-5 h-5 text-indigo-600" /> Chẩn đoán & Khuyến nghị từ dữ liệu thực tế
                      </h3>
                      <button
                        type="button"
                        onClick={() => fetchAiAnalysis(selectedMonth)}
                        className="text-xs text-indigo-600 font-semibold hover:underline cursor-pointer"
                      >
                        Làm mới phân tích
                      </button>
                    </div>

                    <div className="space-y-3">
                      {(aiReport.insights || []).map((item: any, idx: number) => {
                        const isDanger = item.level === 'DANGER';
                        const isWarn = item.level === 'WARNING';
                        const isGood = item.level === 'GOOD';
                        return (
                          <div
                            key={idx}
                            className={
                              'p-4 rounded-xl border ' +
                              (isDanger
                                ? 'bg-rose-50/70 border-rose-200'
                                : isWarn
                                ? 'bg-amber-50/70 border-amber-200'
                                : isGood
                                ? 'bg-emerald-50/70 border-emerald-200'
                                : 'bg-slate-50 border-slate-200')
                            }
                          >
                            <div className="flex items-start gap-2.5">
                              {isDanger || isWarn ? (
                                <ShieldAlert className={'w-5 h-5 shrink-0 mt-0.5 ' + (isDanger ? 'text-rose-600' : 'text-amber-600')} />
                              ) : (
                                <CheckCircle2 className={'w-5 h-5 shrink-0 mt-0.5 ' + (isGood ? 'text-emerald-600' : 'text-indigo-600')} />
                              )}
                              <div>
                                <div className="font-bold text-sm text-slate-800">{item.title}</div>
                                <p className="text-xs text-slate-600 mt-1 leading-relaxed">{item.detail}</p>
                              </div>
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  </div>

                  <div className="lg:col-span-2 bg-white rounded-2xl border border-slate-200 shadow-sm flex flex-col h-[70dvh] max-h-[520px] min-h-[380px] lg:h-[460px] overflow-hidden">
                    <div className="px-4 py-3 bg-gradient-to-r from-indigo-50 via-purple-50/70 to-white border-b border-slate-100 shrink-0">
                      <div className="flex items-center justify-between mb-2">
                        <div className="flex items-center gap-2">
                          <div className="w-7 h-7 rounded-lg bg-indigo-600 text-white flex items-center justify-center">
                            <Bot className="w-4 h-4" />
                          </div>
                          <div>
                            <h3 className="font-bold text-xs text-slate-800">Trợ lý Tài chính HTR</h3>
                            <span className="text-[10px] text-emerald-600 font-medium flex items-center gap-1">
                              <span className={'w-1.5 h-1.5 rounded-full ' + (aiEngineInfo.provider === 'fallback' ? 'bg-amber-500' : 'bg-emerald-500')}></span>
                              {aiEngineInfo.provider === 'groq'
                                ? `Groq AI (${aiEngineInfo.model || 'Llama 3.3'})`
                                : aiEngineInfo.provider === 'fallback'
                                ? 'Phân tích nội bộ (Kiểm tra GROQ_API_KEY)'
                                : 'Groq AI • Trực tuyến'}
                            </span>
                          </div>
                        </div>
                        <button
                          type="button"
                          onClick={() => setAiMessages([{ role: 'ai', text: 'Xin chào HTR! Bạn muốn mình phân tích khoản chi, ví tiền hay mục tiêu tiết kiệm nào?' }])}
                          className="text-[11px] text-slate-400 hover:text-indigo-600 px-2 py-1 rounded-lg hover:bg-white transition cursor-pointer"
                        >
                          Làm mới
                        </button>
                      </div>

                      <div className="flex flex-wrap gap-1">
                        {['Mục nào chi quá tay?', 'Tiết kiệm ổn không?', 'Gợi ý cắt giảm chi?'].map((q) => (
                          <button
                            key={q}
                            type="button"
                            disabled={aiThinking}
                            onClick={() => handleAskAi(q)}
                            className="text-[11px] px-2.5 py-0.5 rounded-full bg-white border border-indigo-100 hover:border-indigo-300 hover:bg-indigo-50 text-indigo-700 font-medium cursor-pointer transition disabled:opacity-50"
                          >
                            {q}
                          </button>
                        ))}
                      </div>
                    </div>

                    <div className="flex-1 overflow-y-auto p-3.5 space-y-2.5 bg-slate-50/50">
                      {aiMessages.map((m: any, i: number) => (
                        <div
                          key={i}
                          style={{
                            display: 'flex',
                            justifyContent: m.role === 'user' ? 'flex-end' : 'flex-start',
                            alignItems: 'flex-end',
                            gap: '8px',
                          }}
                        >
                          {m.role === 'ai' && (
                            <div className="w-6 h-6 rounded-full bg-indigo-100 text-indigo-600 flex items-center justify-center shrink-0">
                              <Sparkles className="w-3.5 h-3.5" />
                            </div>
                          )}
                          <div
                            style={{
                              width: 'fit-content',
                              maxWidth: '78%',
                              wordBreak: 'break-word',
                            }}
                            className={
                              'px-3.5 py-2 rounded-2xl text-xs leading-relaxed whitespace-pre-line shadow-2xs ' +
                              (m.role === 'user'
                                ? 'bg-emerald-600 text-white rounded-br-sm'
                                : 'bg-white border border-slate-200/80 text-slate-700 rounded-bl-sm')
                            }
                          >
                            {m.role === 'ai' ? renderFormattedMessage(m.text) : m.text}
                          </div>
                        </div>
                      ))}

                      {aiThinking && (
                        <div style={{ display: 'flex', justifyContent: 'flex-start', alignItems: 'flex-end', gap: '8px' }}>
                          <div className="w-6 h-6 rounded-full bg-indigo-100 text-indigo-600 flex items-center justify-center shrink-0">
                            <Sparkles className="w-3.5 h-3.5 animate-pulse" />
                          </div>
                          <div
                            style={{ width: 'fit-content' }}
                            className="bg-white border border-indigo-100 text-indigo-700 px-3.5 py-2 rounded-2xl rounded-bl-sm inline-flex items-center gap-2 text-xs shadow-2xs"
                          >
                            <span className="font-medium">Đang phân tích</span>
                            <span className="flex items-center gap-1 pt-0.5">
                              <span className="w-1.5 h-1.5 bg-indigo-500 rounded-full animate-bounce [animation-delay:-0.3s]"></span>
                              <span className="w-1.5 h-1.5 bg-indigo-500 rounded-full animate-bounce [animation-delay:-0.15s]"></span>
                              <span className="w-1.5 h-1.5 bg-indigo-500 rounded-full animate-bounce"></span>
                            </span>
                          </div>
                        </div>
                      )}
                      <div ref={chatEndRef} />
                    </div>

                    <form
                      onSubmit={(e) => {
                        e.preventDefault();
                        handleAskAi();
                      }}
                      className="p-2.5 bg-white border-t border-slate-100 flex items-center gap-2 shrink-0"
                    >
                      <input
                        type="text"
                        value={aiQuestion}
                        onChange={(e) => setAiQuestion(e.target.value)}
                        disabled={aiThinking}
                        placeholder={aiThinking ? "AI đang phân tích..." : "Nhập câu hỏi của bạn..."}
                        className="flex-1 px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-xs focus:outline-none focus:bg-white focus:border-indigo-500 transition disabled:opacity-60"
                      />
                      <button
                        type="submit"
                        disabled={aiThinking || !aiQuestion.trim()}
                        className="p-2 bg-indigo-600 hover:bg-indigo-700 disabled:bg-slate-200 disabled:text-slate-400 text-white rounded-xl cursor-pointer transition"
                        title="Gửi câu hỏi"
                      >
                        <Send className="w-4 h-4" />
                      </button>
                    </form>
                  </div>
                </div>
              </>
            ) : (
              <div className="bg-white p-12 rounded-2xl border border-slate-100 text-center space-y-3">
                <p className="text-slate-600 text-sm">Chưa tải được dữ liệu phân tích từ máy chủ.</p>
                <button
                  type="button"
                  onClick={() => fetchAiAnalysis(selectedMonth)}
                  className="px-4 py-2 bg-indigo-600 text-white text-xs font-semibold rounded-xl cursor-pointer"
                >
                  Tải lại dữ liệu AI
                </button>
              </div>
            )}
          </div>
        )}
        {activeTab === 'admin' && <AdminPanel />}
      </main>

      {/* Modal Thêm / Sửa Ví */}
      {showWalletModal && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center p-4 z-50">
          <div className="bg-white rounded-2xl max-w-md w-full p-6">
            <h3 className="text-lg font-bold mb-4">{editingWalletId ? 'Chỉnh sửa thông tin Ví' : 'Thêm Ví / Tài khoản mới'}</h3>
            <form onSubmit={handleSaveWallet} className="space-y-4">
              <div>
                <label className="block text-sm font-medium mb-1">Tên ví</label>
                <input type="text" required value={walletName} onChange={(e) => setWalletName(e.target.value)} placeholder="VD: Vietcombank, Tiền mặt, MoMo..." className="w-full px-3.5 py-2 border border-slate-200 rounded-xl text-sm" />
              </div>

              <div>
                <label className="block text-sm font-medium mb-1">Loại ví</label>
                <select value={walletType} onChange={(e) => setWalletType(e.target.value as any)} className="w-full px-3.5 py-2 border border-slate-200 rounded-xl text-sm bg-white">
                  <option value="BANK">Tài khoản Ngân hàng</option>
                  <option value="CASH">Tiền mặt</option>
                  <option value="EWALLET">Ví điện tử</option>
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium mb-1">Số dư (VNĐ)</label>
                <input type="text" inputMode="numeric" required value={walletBalance} onChange={(e) => setWalletBalance(formatInputMoney(e.target.value))} placeholder="5.000.000" className="w-full px-3.5 py-2 border border-slate-200 rounded-xl text-sm font-semibold" />
              </div>
              {editingWalletId && (
                <div>
                  <label className="block text-sm font-medium mb-1">Trạng thái</label>
                  <select value={walletStatus} onChange={(e) => setWalletStatus(e.target.value as any)} className="w-full px-3.5 py-2 border border-slate-200 rounded-xl text-sm bg-white">
                    <option value="ACTIVE">Đang sử dụng</option>
                    <option value="INACTIVE">Ngừng sử dụng</option>
                  </select>
                </div>
              )}
              <div className="flex justify-end gap-2 pt-2">
                <button type="button" onClick={() => setShowWalletModal(false)} className="px-4 py-2 text-sm text-slate-600 hover:bg-slate-100 rounded-xl cursor-pointer">Hủy</button>
                <button type="submit" className="px-4 py-2 text-sm bg-emerald-600 text-white rounded-xl font-medium cursor-pointer">{editingWalletId ? 'Cập nhật Ví' : 'Lưu Ví'}</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Modal Thêm / Sửa Giao dịch */}
      {showTxModal && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center p-4 z-50">
          <div className="bg-white rounded-2xl max-w-md w-full p-6">
            <h3 className="text-lg font-bold mb-4">{editingTxId ? 'Chỉnh sửa Giao dịch' : 'Ghi chép Giao dịch mới'}</h3>
            <form onSubmit={handleSaveTransaction} className="space-y-4">
              <div className="grid grid-cols-3 gap-2 p-1 bg-slate-100 rounded-xl">
                {(['EXPENSE', 'INCOME', 'TRANSFER'] as const).map((t) => (
                  <button
                    key={t}
                    type="button"
                    onClick={() => { setTxType(t); const first = categories.find((c) => c.type === t); if (first) setTxCategoryId(first.id); }}
                    className={'py-2 text-xs font-semibold rounded-lg transition cursor-pointer ' + (txType === t ? 'bg-white shadow text-emerald-700' : 'text-slate-500')}>
                    {t === 'EXPENSE' ? 'Chi tiêu' : t === 'INCOME' ? 'Thu nhập' : 'Chuyển ví'}
                  </button>
                ))}
              </div>
              <div>
                <label className="block text-sm font-medium mb-1">Số tiền (VNĐ)</label>
                <input type="text" inputMode="numeric" required value={txAmount} onChange={(e) => setTxAmount(formatInputMoney(e.target.value))} placeholder="50.000" className="w-full px-3.5 py-2 border border-slate-200 rounded-xl text-sm font-semibold" />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1">{txType === 'TRANSFER' ? 'Từ ví nguồn' : 'Ví thanh toán'}</label>
                <select value={txWalletId} onChange={(e) => setTxWalletId(Number(e.target.value))} className="w-full px-3.5 py-2 border border-slate-200 rounded-xl text-sm bg-white">
                  {wallets.map((w) => <option key={w.id} value={w.id}>{w.name} ({formatVND(w.balance)})</option>)}
                </select>
              </div>
              {txType === 'TRANSFER' ? (
                <div>
                  <label className="block text-sm font-medium mb-1">Đến ví nhận</label>
                  <select required value={txTargetWalletId} onChange={(e) => setTxTargetWalletId(Number(e.target.value))} className="w-full px-3.5 py-2 border border-slate-200 rounded-xl text-sm bg-white">
                    <option value={0}>-- Chọn ví nhận --</option>
                    {wallets.filter((w) => w.id !== txWalletId).map((w) => <option key={w.id} value={w.id}>{w.name} ({formatVND(w.balance)})</option>)}
                  </select>
                </div>
              ) : (
                <div>
                  <label className="block text-sm font-medium mb-1">Danh mục</label>
                  <select value={txCategoryId} onChange={(e) => setTxCategoryId(Number(e.target.value))} className="w-full px-3.5 py-2 border border-slate-200 rounded-xl text-sm bg-white">
                    <option value={0}>-- Chọn danh mục --</option>
                    {filteredCategories.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
                  </select>
                </div>
              )}
              <div>
                <label className="block text-sm font-medium mb-1">Mô tả ngắn</label>
                <input type="text" value={txDescription} onChange={(e) => setTxDescription(e.target.value)} placeholder="VD: Ăn trưa cơm tấm, Chuyển tiền sang MoMo..." className="w-full px-3.5 py-2 border border-slate-200 rounded-xl text-sm" />
              </div>
              <div className="flex justify-end gap-2 pt-2">
                <button type="button" onClick={() => setShowTxModal(false)} className="px-4 py-2 text-sm text-slate-600 hover:bg-slate-100 rounded-xl cursor-pointer">Hủy</button>
                <button type="submit" className="px-4 py-2 text-sm bg-emerald-600 text-white rounded-xl font-medium cursor-pointer">{editingTxId ? 'Cập nhật Giao dịch' : 'Lưu Giao dịch'}</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Modal Thêm / Sửa Danh mục & Ngân sách */}
      {showCatModal && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center p-4 z-50">
          <div className="bg-white rounded-2xl max-w-md w-full p-6">
            <h3 className="text-lg font-bold mb-4">{editingCatId ? 'Chỉnh sửa Danh mục & Ngân sách' : 'Thêm Danh mục mới'}</h3>
            <form onSubmit={handleSaveCategory} className="space-y-4">
              <div>
                <label className="block text-sm font-medium mb-1">Tên danh mục</label>
                <input type="text" required value={catName} onChange={(e) => setCatName(e.target.value)} placeholder="VD: Tiền nhà, Thể thao, Freelance..." className="w-full px-3.5 py-2 border border-slate-200 rounded-xl text-sm" />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1">Phân loại</label>
                <select value={catType} onChange={(e) => setCatType(e.target.value as any)} className="w-full px-3.5 py-2 border border-slate-200 rounded-xl text-sm bg-white">
                  <option value="EXPENSE">Khoản Chi tiêu</option>
                  <option value="INCOME">Khoản Thu nhập</option>
                </select>
              </div>
              {catType === 'EXPENSE' && (
                <div>
                  <label className="block text-sm font-medium mb-1">Hạn mức chi tiêu tháng (VNĐ)</label>
                  <input type="text" inputMode="numeric" value={catBudgetLimit} onChange={(e) => setCatBudgetLimit(formatInputMoney(e.target.value))} placeholder="3.000.000 (Để trống nếu không giới hạn)" className="w-full px-3.5 py-2 border border-slate-200 rounded-xl text-sm font-semibold" />
                </div>
              )}
              <div>
                <label className="block text-sm font-medium mb-2">Màu hiển thị trên biểu đồ</label>
                <div className="flex flex-wrap gap-2">
                  {PRESET_COLORS.map((color) => (
                    <button key={color} type="button" onClick={() => setCatColor(color)} className={'w-7 h-7 rounded-full transition cursor-pointer ' + (catColor === color ? 'ring-2 ring-offset-2 ring-slate-800 scale-110' : '')} style={{ backgroundColor: color }} />
                  ))}
                </div>
              </div>
              <div className="flex justify-end gap-2 pt-2">
                <button type="button" onClick={() => setShowCatModal(false)} className="px-4 py-2 text-sm text-slate-600 hover:bg-slate-100 rounded-xl cursor-pointer">Hủy</button>
                <button type="submit" className="px-4 py-2 text-sm bg-emerald-600 text-white rounded-xl font-medium cursor-pointer">{editingCatId ? 'Cập nhật Danh mục' : 'Lưu Danh mục'}</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Modal Tạo / Sửa Mục tiêu Tiết kiệm */}
      {showGoalModal && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center p-4 z-50">
          <div className="bg-white rounded-2xl max-w-md w-full p-6">
            <h3 className="text-lg font-bold mb-4">{editingGoalId ? 'Chỉnh sửa Mục tiêu Tiết kiệm' : 'Tạo Mục tiêu Tiết kiệm mới'}</h3>
            <form onSubmit={handleSaveGoal} className="space-y-4">
              <div>
                <label className="block text-sm font-medium mb-1">Tên mục tiêu</label>
                <input type="text" required value={goalName} onChange={(e) => setGoalName(e.target.value)} placeholder="VD: Mua Laptop mới, Quỹ khẩn cấp..." className="w-full px-3.5 py-2 border border-slate-200 rounded-xl text-sm" />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1">Số tiền mục tiêu (VNĐ)</label>
                <input type="text" inputMode="numeric" required value={goalTarget} onChange={(e) => setGoalTarget(formatInputMoney(e.target.value))} placeholder="20.000.000" className="w-full px-3.5 py-2 border border-slate-200 rounded-xl text-sm font-semibold" />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1">Số tiền đã có ban đầu (VNĐ)</label>
                <input type="text" inputMode="numeric" value={goalCurrent} onChange={(e) => setGoalCurrent(formatInputMoney(e.target.value))} placeholder="0" className="w-full px-3.5 py-2 border border-slate-200 rounded-xl text-sm font-semibold" />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1.5">Hạn hoàn thành (Tùy chọn)</label>
                <div className="grid grid-cols-2 sm:grid-cols-4 gap-1.5 mb-2">
                  {[{ label: '+1 tháng', m: 1 }, { label: '+3 tháng', m: 3 }, { label: '+6 tháng', m: 6 }, { label: '+1 năm', m: 12 }].map((p) => {
                    const d = new Date(); d.setMonth(d.getMonth() + p.m);
                    const iso = d.toISOString().split('T')[0];
                    return (
                      <button key={p.m} type="button" onClick={() => setGoalDeadline(iso)} className={'py-1.5 rounded-lg text-xs font-medium border cursor-pointer ' + (goalDeadline === iso ? 'bg-emerald-50 border-emerald-500 text-emerald-700 font-semibold' : 'bg-slate-50 border-slate-200 text-slate-600')}>
                        {p.label}
                      </button>
                    );
                  })}
                </div>
                <CustomDateSelect value={goalDeadline} onChange={setGoalDeadline} allowClear />
              </div>
              <div>
                <label className="block text-sm font-medium mb-2">Màu sắc nhận diện</label>
                <div className="flex flex-wrap gap-2">
                  {PRESET_COLORS.map((color) => (
                    <button key={color} type="button" onClick={() => setGoalColor(color)} className={'w-7 h-7 rounded-full transition cursor-pointer ' + (goalColor === color ? 'ring-2 ring-offset-2 ring-slate-800 scale-110' : '')} style={{ backgroundColor: color }} />
                  ))}
                </div>
              </div>
              <div className="flex justify-end gap-2 pt-2">
                <button type="button" onClick={() => setShowGoalModal(false)} className="px-4 py-2 text-sm text-slate-600 hover:bg-slate-100 rounded-xl cursor-pointer">Hủy</button>
                <button type="submit" className="px-4 py-2 text-sm bg-emerald-600 text-white rounded-xl font-medium cursor-pointer">{editingGoalId ? 'Cập nhật Mục tiêu' : 'Lưu Mục tiêu'}</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Modal Nạp / Rút tiền Mục tiêu Tiết kiệm */}
      {contribGoal && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center p-4 z-50">
          <div className="bg-white rounded-2xl max-w-md w-full p-6">
            <h3 className="text-lg font-bold mb-1">{contribAction === 'DEPOSIT' ? 'Nạp tiền vào Mục tiêu' : 'Rút tiền từ Mục tiêu về Ví'}</h3>
            <p className="text-xs text-slate-500 mb-4">Mục tiêu: <strong>{contribGoal.name}</strong> (Hiện có: {formatVND(contribGoal.currentAmount)})</p>
            <form onSubmit={handleContributeSubmit} className="space-y-4">
              <div>
                <label className="block text-sm font-medium mb-1">{contribAction === 'DEPOSIT' ? 'Trích từ ví nào?' : 'Chuyển về ví nào?'}</label>
                <select value={contribWalletId} onChange={(e) => setContribWalletId(Number(e.target.value))} className="w-full px-3.5 py-2 border border-slate-200 rounded-xl text-sm bg-white">
                  {wallets.map((w) => <option key={w.id} value={w.id}>{w.name} ({formatVND(w.balance)})</option>)}
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium mb-1">Số tiền (VNĐ)</label>
                <input type="text" inputMode="numeric" required value={contribAmount} onChange={(e) => setContribAmount(formatInputMoney(e.target.value))} placeholder="1.000.000" className="w-full px-3.5 py-2 border border-slate-200 rounded-xl text-sm font-semibold" />
              </div>
              <div className="flex justify-end gap-2 pt-2">
                <button type="button" onClick={() => setContribGoal(null)} className="px-4 py-2 text-sm text-slate-600 hover:bg-slate-100 rounded-xl cursor-pointer">Hủy</button>
                <button type="submit" className="px-4 py-2 text-sm bg-emerald-600 text-white rounded-xl font-medium cursor-pointer">Xác nhận</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Modal Thêm / Sửa Giao dịch Định kỳ */}
      {showRecModal && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center p-4 z-50">
          <div className="bg-white rounded-2xl max-w-md w-full p-6">
            <h3 className="text-lg font-bold mb-4">{editingRecId ? 'Chỉnh sửa Khoản Định kỳ' : 'Thêm Khoản Thu / Chi Định kỳ'}</h3>
            <form onSubmit={handleSaveRec} className="space-y-4">
              <div className="grid grid-cols-2 gap-2 p-1 bg-slate-100 rounded-xl">
                {(['EXPENSE', 'INCOME'] as const).map((t) => (
                  <button
                    key={t}
                    type="button"
                    onClick={() => { setRecType(t); const first = categories.find((c) => c.type === t); if (first) setRecCategoryId(first.id); }}
                    className={'py-2 text-xs font-semibold rounded-lg transition cursor-pointer ' + (recType === t ? 'bg-white shadow text-emerald-700' : 'text-slate-500')}>
                    {t === 'EXPENSE' ? 'Khoản Chi cố định' : 'Khoản Thu cố định'}
                  </button>
                ))}
              </div>
              <div>
                <label className="block text-sm font-medium mb-1">Tên khoản định kỳ</label>
                <input type="text" required value={recTitle} onChange={(e) => setRecTitle(e.target.value)} placeholder="VD: Tiền thuê trọ, Cước Internet, Lương cứng..." className="w-full px-3.5 py-2 border border-slate-200 rounded-xl text-sm" />
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-sm font-medium mb-1">Số tiền (VNĐ)</label>
                  <input type="text" inputMode="numeric" required value={recAmount} onChange={(e) => setRecAmount(formatInputMoney(e.target.value))} placeholder="3.500.000" className="w-full px-3.5 py-2 border border-slate-200 rounded-xl text-sm font-semibold" />
                </div>
                <div>
                  <label className="block text-sm font-medium mb-1">Chu kỳ lặp lại</label>
                  <select value={recFrequency} onChange={(e) => setRecFrequency(e.target.value as any)} className="w-full px-3.5 py-2 border border-slate-200 rounded-xl text-sm bg-white">
                    <option value="MONTHLY">Hàng tháng</option>
                    <option value="WEEKLY">Hàng tuần</option>
                    <option value="DAILY">Hàng ngày</option>
                    <option value="YEARLY">Hàng năm</option>
                  </select>
                </div>
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-sm font-medium mb-1">Ví áp dụng</label>
                  <select value={recWalletId} onChange={(e) => setRecWalletId(Number(e.target.value))} className="w-full px-3.5 py-2 border border-slate-200 rounded-xl text-sm bg-white">
                    {wallets.map((w) => <option key={w.id} value={w.id}>{w.name}</option>)}
                  </select>
                </div>
                <div>
                  <label className="block text-sm font-medium mb-1">Danh mục</label>
                  <select value={recCategoryId} onChange={(e) => setRecCategoryId(Number(e.target.value))} className="w-full px-3.5 py-2 border border-slate-200 rounded-xl text-sm bg-white">
                    <option value={0}>-- Không chọn --</option>
                    {recFilteredCategories.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
                  </select>
                </div>
              </div>
              <div>
                <label className="block text-sm font-medium mb-1.5">Ngày đến hạn tiếp theo</label>
                <CustomDateSelect value={recNextDueDate} onChange={setRecNextDueDate} />
              </div>
              <div className="flex justify-end gap-2 pt-2">
                <button type="button" onClick={() => setShowRecModal(false)} className="px-4 py-2 text-sm text-slate-600 hover:bg-slate-100 rounded-xl cursor-pointer">Hủy</button>
                <button type="submit" className="px-4 py-2 text-sm bg-emerald-600 text-white rounded-xl font-medium cursor-pointer">{editingRecId ? 'Cập nhật' : 'Lưu Khoản Định kỳ'}</button>
              </div>
            </form>
          </div>
        </div>
      )}

      <ChangePasswordModal
        isOpen={showChangePassModal}
        onClose={() => setShowChangePassModal(false)}
      />

      <CsvImportModal
        isOpen={showImportModal}
        onClose={() => setShowImportModal(false)}
        wallets={wallets}
        categories={categories}
        onImported={async () => { await fetchAllData(); await fetchAnalytics(selectedMonth); }}
      />

      <ProfileModal
        isOpen={showProfileModal}
        onClose={() => setShowProfileModal(false)}
        user={user}
        onUpdated={(nextUser) => { setUser(nextUser); fetchAnalytics(selectedMonth); }}
      />

      {activeTab !== 'ai' && !mobileMenuOpen && !showWalletModal && !showTxModal && !showCatModal && !showGoalModal && !showRecModal && !contribGoal && !showChangePassModal && !showImportModal && !showProfileModal && (
      <div style={{ zIndex: 9999 }} className="fixed bottom-4 right-4 sm:bottom-6 sm:right-6 flex flex-col items-end">
        {miniChatOpen && (
          <div className="mb-3 w-[calc(100vw-32px)] sm:w-[370px] h-[min(440px,calc(100dvh-110px))] apple-glass-strong apple-chat-enter rounded-[24px] border border-black/[0.08] shadow-2xl flex flex-col overflow-hidden">
            <div className="px-4 py-3 bg-gradient-to-r from-[#0071e3] via-[#5856d6] to-[#af52de] text-white flex items-center justify-between shrink-0">
              <div className="flex items-center gap-2">
                <div className="w-7 h-7 rounded-lg bg-white/20 flex items-center justify-center">
                  <Bot className="w-4 h-4 text-white" />
                </div>
                <div>
                  <div className="font-bold text-xs leading-tight">Trợ lý Tài chính HTR</div>
                  <div className="text-[10px] text-indigo-100 flex items-center gap-1">
                    <span className={'w-1.5 h-1.5 rounded-full ' + (aiEngineInfo.provider === 'fallback' ? 'bg-amber-300' : 'bg-emerald-400')}></span>
                    {aiEngineInfo.provider === 'groq'
                      ? `Groq AI • ${aiEngineInfo.model || 'Llama 3.3'}`
                      : aiEngineInfo.provider === 'fallback'
                      ? 'Nội bộ (Kiểm tra GROQ_API_KEY)'
                      : 'Groq AI • Trực tuyến'}
                  </div>
                </div>
              </div>
              <div className="flex items-center gap-1">
                <button
                  type="button"
                  onClick={() => {
                    setMiniChatOpen(false);
                    setActiveTab('ai');
                  }}
                  className="text-[10px] px-2 py-1 rounded-lg bg-white/15 hover:bg-white/25 text-white transition cursor-pointer"
                >
                  Mở rộng
                </button>
                <button
                  type="button"
                  onClick={() => setMiniChatOpen(false)}
                  className="w-6 h-6 rounded-lg hover:bg-white/20 flex items-center justify-center text-white text-base font-bold cursor-pointer"
                  title="Thu gọn"
                >
                  ×
                </button>
              </div>
            </div>

            <div className="px-3 py-2 bg-indigo-50/70 border-b border-slate-100 flex gap-1.5 overflow-x-auto shrink-0">
              {['Chi tiêu tháng này?', 'Mục nào vượt hạn mức?', 'Gợi ý tiết kiệm?'].map((q) => (
                <button
                  key={q}
                  type="button"
                  disabled={aiThinking}
                  onClick={() => handleAskAi(q)}
                  className="text-[10px] px-2.5 py-1 rounded-full bg-white border border-indigo-100 hover:border-indigo-300 text-indigo-700 font-medium whitespace-nowrap cursor-pointer transition disabled:opacity-50"
                >
                  {q}
                </button>
              ))}
            </div>

            <div className="flex-1 overflow-y-auto p-3 space-y-2.5 bg-slate-50/60">
              {aiMessages.map((m: any, i: number) => (
                <div
                  key={i}
                  style={{
                    display: 'flex',
                    justifyContent: m.role === 'user' ? 'flex-end' : 'flex-start',
                    alignItems: 'flex-end',
                    gap: '6px',
                  }}
                >
                  {m.role === 'ai' && (
                    <div className="w-5 h-5 rounded-full bg-indigo-100 text-indigo-600 flex items-center justify-center shrink-0">
                      <Sparkles className="w-3 h-3" />
                    </div>
                  )}
                  <div
                    style={{
                      width: 'fit-content',
                      maxWidth: '80%',
                      wordBreak: 'break-word',
                    }}
                    className={
                      'px-3 py-2 rounded-2xl text-xs leading-relaxed whitespace-pre-line shadow-2xs ' +
                      (m.role === 'user'
                        ? 'bg-emerald-600 text-white rounded-br-sm'
                        : 'bg-white border border-slate-200/80 text-slate-700 rounded-bl-sm')
                    }
                  >
                    {m.role === 'ai' ? renderFormattedMessage(m.text) : m.text}
                  </div>
                </div>
              ))}

              {aiThinking && (
                <div style={{ display: 'flex', justifyContent: 'flex-start', alignItems: 'flex-end', gap: '6px' }}>
                  <div className="w-5 h-5 rounded-full bg-indigo-100 text-indigo-600 flex items-center justify-center shrink-0">
                    <Sparkles className="w-3 h-3 animate-pulse" />
                  </div>
                  <div
                    style={{ width: 'fit-content' }}
                    className="bg-white border border-indigo-100 text-indigo-700 px-3 py-2 rounded-2xl rounded-bl-sm inline-flex items-center gap-1.5 text-xs shadow-2xs"
                  >
                    <span className="font-medium">Đang phân tích</span>
                    <span className="flex items-center gap-1 pt-0.5">
                      <span className="w-1.5 h-1.5 bg-indigo-500 rounded-full animate-bounce [animation-delay:-0.3s]"></span>
                      <span className="w-1.5 h-1.5 bg-indigo-500 rounded-full animate-bounce [animation-delay:-0.15s]"></span>
                      <span className="w-1.5 h-1.5 bg-indigo-500 rounded-full animate-bounce"></span>
                    </span>
                  </div>
                </div>
              )}
              <div ref={miniChatEndRef} />
            </div>

            <form
              onSubmit={(e) => {
                e.preventDefault();
                handleAskAi();
              }}
              className="p-2.5 bg-white border-t border-slate-100 flex items-center gap-2 shrink-0"
            >
              <input
                type="text"
                value={aiQuestion}
                onChange={(e) => setAiQuestion(e.target.value)}
                disabled={aiThinking}
                placeholder={aiThinking ? "AI đang phân tích..." : "Hỏi nhanh trợ lý AI..."}
                className="flex-1 px-3 py-1.5 bg-slate-50 border border-slate-200 rounded-xl text-xs focus:outline-none focus:bg-white focus:border-indigo-500 transition disabled:opacity-60"
              />
              <button
                type="submit"
                disabled={aiThinking || !aiQuestion.trim()}
                className="p-2 bg-indigo-600 hover:bg-indigo-700 disabled:bg-slate-200 disabled:text-slate-400 text-white rounded-xl cursor-pointer transition"
                title="Gửi"
              >
                <Send className="w-3.5 h-3.5" />
              </button>
            </form>
          </div>
        )}

        <button
          type="button"
          onClick={() => setMiniChatOpen((prev) => !prev)}
          className="w-12 h-12 sm:w-14 sm:h-14 rounded-full bg-gradient-to-br from-[#0071e3] via-[#5856d6] to-[#af52de] hover:brightness-110 text-white shadow-xl shadow-[#5856d6]/30 flex items-center justify-center cursor-pointer transition-all hover:scale-105 active:scale-95 relative"
          title="Mở Trợ lý Tài chính AI"
        >
          <Bot className="w-5 h-5 sm:w-6 sm:h-6" />
          <span className="absolute top-0 right-0 sm:top-0.5 sm:right-0.5 w-3 h-3 sm:w-3.5 sm:h-3.5 rounded-full bg-emerald-400 border-2 border-white"></span>
        </button>
      </div>
      )}

    </div>
  );
}