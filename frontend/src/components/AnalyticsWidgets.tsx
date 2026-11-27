import { Flame, ShieldCheck, TrendingUp, AlertTriangle, Clock, SlidersHorizontal } from 'lucide-react';

interface AnalyticsWidgetsProps {
  analytics: any;
  loading?: boolean;
  onOpenBudgetSettings: () => void;
}

const formatVND = (n: number) =>
  new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(n || 0);

export default function AnalyticsWidgets({ analytics, loading, onOpenBudgetSettings }: AnalyticsWidgetsProps) {
  if (loading && !analytics) {
    return (
      <div className="bg-white p-6 rounded-2xl border border-slate-100 text-xs text-[#6e6e73]">
        Đang tính toán tốc độ tiêu dùng & biến động kỳ...
      </div>
    );
  }
  if (!analytics) return null;

  const status = analytics.overallBurnStatus || 'NO_BUDGET';
  const statusStyles: Record<string, { badge: string; bar: string; label: string }> = {
    SAFE: { badge: 'bg-[#34c759]/15 text-[#248a3d]', bar: 'bg-[#34c759]', label: 'Nhịp chi an toàn' },
    WARNING: { badge: 'bg-[#ff9500]/15 text-[#b25000]', bar: 'bg-[#ff9500]', label: 'Cảnh báo tốc độ' },
    DANGER: { badge: 'bg-[#ff3b30]/15 text-[#d70015]', bar: 'bg-[#ff3b30]', label: 'Burn-rate cao' },
    EXCEEDED: { badge: 'bg-[#ff3b30]/20 text-[#b00012]', bar: 'bg-[#d70015]', label: 'Vượt ngân sách' },
    NO_BUDGET: { badge: 'bg-black/[0.06] text-[#6e6e73]', bar: 'bg-[#86868b]', label: 'Chưa đặt ngân sách' },
  };
  const st = statusStyles[status] || statusStyles.NO_BUDGET;

  const anomalies = (analytics.categoryAnomalies || []).filter((a: any) => a.currentSpent > 0);
  const spikes = anomalies.filter((a: any) => a.spike);

  return (
    <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 apple-stagger">
      <div className="bg-white p-5 sm:p-6 rounded-2xl border border-slate-100 shadow-sm space-y-4">
        <div className="flex flex-wrap items-start justify-between gap-2">
          <div>
            <div className="flex items-center gap-2">
              <Flame className="w-4 h-4 text-[#ff9500]" />
              <h3 className="font-bold text-[#1d1d1f] text-base">Ngân sách chủ động & Burn-rate</h3>
            </div>
            <p className="text-xs text-[#6e6e73] mt-0.5">
              Tháng {analytics.month} • Đã qua {analytics.daysElapsed}/{analytics.daysInMonth} ngày ({analytics.timeElapsedPct}%)
            </p>
          </div>
          <div className="flex items-center gap-2">
            <span className={'px-2.5 py-1 rounded-full text-[11px] font-semibold ' + st.badge}>
              {st.label}
            </span>
            <button
              type="button"
              onClick={onOpenBudgetSettings}
              className="p-1.5 rounded-full bg-[#f2f2f7] hover:bg-[#e5e5ea] text-[#1d1d1f] cursor-pointer"
              title="Đặt ngân sách tổng tháng"
            >
              <SlidersHorizontal className="w-3.5 h-3.5" />
            </button>
          </div>
        </div>

        <div className="grid grid-cols-2 gap-3">
          <div className="p-4 rounded-2xl bg-[#f5f5f7]">
            <div className="text-[11px] font-semibold uppercase tracking-wider text-[#6e6e73]">An toàn mỗi ngày</div>
            <div className="text-xl sm:text-2xl font-bold text-[#1d1d1f] mt-1 tabular-nums">
              {analytics.overallBudgetLimit > 0 ? formatVND(analytics.safeToSpendPerDay) : '—'}
            </div>
            <div className="text-[11px] text-[#6e6e73] mt-1 flex items-center gap-1">
              <Clock className="w-3 h-3" /> Còn {analytics.daysRemaining} ngày trong tháng
            </div>
          </div>

          <div className="p-4 rounded-2xl bg-[#f5f5f7]">
            <div className="text-[11px] font-semibold uppercase tracking-wider text-[#6e6e73]">Tốc độ tiêu dùng</div>
            <div className="text-xl sm:text-2xl font-bold text-[#1d1d1f] mt-1 tabular-nums">
              {analytics.overallBudgetLimit > 0 ? `${analytics.overallBurnRateRatio}x` : '—'}
            </div>
            <div className="text-[11px] text-[#6e6e73] mt-1">
              Đã dùng <strong>{analytics.overallSpentPct}%</strong> / {analytics.timeElapsedPct}% thời gian
            </div>
          </div>
        </div>

        {analytics.overallBudgetLimit > 0 && (
          <div className="space-y-1.5">
            <div className="flex justify-between text-xs text-[#6e6e73]">
              <span>Đã chi: <strong className="text-[#1d1d1f]">{formatVND(analytics.totalExpense)}</strong></span>
              <span>Ngân sách: <strong className="text-[#1d1d1f]">{formatVND(analytics.overallBudgetLimit)}</strong></span>
            </div>
            <div className="relative w-full h-3 bg-[#e8e8ed] rounded-full overflow-hidden">
              <div
                className={'h-full rounded-full transition-all duration-700 ' + st.bar}
                style={{ width: `${Math.min(analytics.overallSpentPct, 100)}%` }}
              />
              <div
                title={`Mốc thời gian đã trôi qua: ${analytics.timeElapsedPct}%`}
                style={{ left: `${Math.min(analytics.timeElapsedPct, 99)}%` }}
                className="absolute top-0 bottom-0 w-0.5 bg-[#1d1d1f]"
              />
            </div>
            <div className="text-[11px] text-[#6e6e73] leading-relaxed">
              {analytics.overallBurnMessage}
            </div>
          </div>
        )}

        {(analytics.categoryBurnRates || []).length > 0 && (
          <div className="pt-3 border-t border-black/[0.06] space-y-2">
            <div className="text-[11px] font-bold uppercase tracking-wider text-[#86868b]">Tốc độ theo danh mục có hạn mức</div>
            <div className="space-y-2 max-h-44 overflow-y-auto pr-1">
              {analytics.categoryBurnRates.map((b: any) => (
                <div key={b.categoryId} className="flex items-center justify-between gap-2 text-xs px-3 py-2 rounded-xl bg-[#f5f5f7]/80">
                  <div className="flex items-center gap-2 min-w-0">
                    <span className="w-2.5 h-2.5 rounded-full shrink-0" style={{ backgroundColor: b.color || '#6366f1' }} />
                    <span className="font-semibold text-[#1d1d1f] truncate">{b.categoryName}</span>
                  </div>
                  <div className="flex items-center gap-2 shrink-0">
                    <span className="text-[11px] text-[#6e6e73] hidden sm:inline">{b.message}</span>
                    <span className={
                      'px-2 py-0.5 rounded-full text-[10px] font-bold ' +
                      (b.status === 'EXCEEDED' || b.status === 'DANGER'
                        ? 'bg-[#ff3b30]/15 text-[#d70015]'
                        : b.status === 'WARNING'
                        ? 'bg-[#ff9500]/15 text-[#b25000]'
                        : 'bg-[#34c759]/15 text-[#248a3d]')
                    }>
                      {b.spentPct}%
                    </span>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>

      <div className="bg-white p-5 sm:p-6 rounded-2xl border border-slate-100 shadow-sm space-y-4">
        <div className="flex flex-wrap items-start justify-between gap-2">
          <div>
            <div className="flex items-center gap-2">
              <TrendingUp className="w-4 h-4 text-[#0071e3]" />
              <h3 className="font-bold text-[#1d1d1f] text-base">Biến động chi tiêu giữa các kỳ</h3>
            </div>
            <p className="text-xs text-[#6e6e73] mt-0.5">
              So sánh tháng {analytics.month} với tháng {analytics.previousMonth} & trung bình 3 tháng
            </p>
          </div>
          <span className={'px-2.5 py-1 rounded-full text-[11px] font-semibold ' + (analytics.expenseDeltaVsPrevPct > 15 ? 'bg-[#ff3b30]/12 text-[#d70015]' : 'bg-[#34c759]/12 text-[#248a3d]')}>
            {analytics.expenseDeltaVsPrevPct >= 0 ? '+' : ''}{analytics.expenseDeltaVsPrevPct}% vs kỳ trước
          </span>
        </div>

        <div className="grid grid-cols-2 gap-3 text-xs">
          <div className="p-3.5 rounded-2xl bg-[#f5f5f7]">
            <div className="text-[#6e6e73]">Kỳ trước ({analytics.previousMonth})</div>
            <div className="text-base font-bold text-[#1d1d1f] mt-0.5 tabular-nums">{formatVND(analytics.previousMonthExpense)}</div>
          </div>
          <div className="p-3.5 rounded-2xl bg-[#f5f5f7]">
            <div className="text-[#6e6e73]">Trung bình 3 tháng</div>
            <div className="text-base font-bold text-[#1d1d1f] mt-0.5 tabular-nums">{formatVND(analytics.avg3MonthExpense)}</div>
          </div>
        </div>

        {spikes.length === 0 ? (
          <div className="p-4 rounded-2xl bg-[#34c759]/[0.08] border border-[#34c759]/20 flex items-center gap-2.5 text-xs text-[#248a3d] font-medium">
            <ShieldCheck className="w-5 h-5 shrink-0" />
            <span>Không phát hiện danh mục nào tăng đột biến so với kỳ trước và trung bình 3 tháng gần nhất.</span>
          </div>
        ) : (
          <div className="space-y-2.5 max-h-60 overflow-y-auto pr-1">
            {spikes.slice(0, 4).map((item: any) => (
              <div key={item.categoryId} className="p-3.5 rounded-2xl bg-[#ff3b30]/[0.04] border border-[#ff3b30]/15 space-y-2">
                <div className="flex items-center justify-between gap-2">
                  <div className="flex items-center gap-2 min-w-0">
                    <AlertTriangle className="w-4 h-4 text-[#d70015] shrink-0" />
                    <span className="font-bold text-xs text-[#1d1d1f] truncate">{item.categoryName}</span>
                  </div>
                  <span className="text-xs font-bold text-[#d70015] shrink-0 tabular-nums">
                    +{formatVND(item.deltaVsPrev)} ({item.deltaVsPrevPct >= 0 ? '+' : ''}{item.deltaVsPrevPct}%)
                  </span>
                </div>
                <div className="text-[11px] text-[#6e6e73]">
                  Kỳ này: <strong>{formatVND(item.currentSpent)}</strong> • Kỳ trước: {formatVND(item.previousSpent)} • TB 3T: {formatVND(item.avg3MonthsSpent)}
                </div>
                {(item.topTransactions || []).length > 0 && (
                  <div className="pt-1.5 border-t border-[#ff3b30]/10 space-y-1">
                    <div className="text-[10px] font-semibold uppercase tracking-wider text-[#86868b]">Nguyên nhân từ giao dịch lớn nhất:</div>
                    {item.topTransactions.map((tx: any) => (
                      <div key={tx.id} className="flex items-center justify-between text-[11px] text-[#424245]">
                        <span className="truncate pr-2">• {tx.description} ({tx.date})</span>
                        <span className="font-semibold text-[#1d1d1f] shrink-0 tabular-nums">{formatVND(tx.amount)}</span>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
