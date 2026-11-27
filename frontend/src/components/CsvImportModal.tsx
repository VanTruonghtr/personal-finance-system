import React, { useState } from 'react';
import * as XLSX from 'xlsx';
import axiosClient from '../api/axiosClient';
import { Upload, X, FileSpreadsheet, AlertTriangle, CheckCircle2, Download } from 'lucide-react';
import type { Wallet } from '../types/finance';

interface CsvImportModalProps {
  isOpen: boolean;
  onClose: () => void;
  wallets: Wallet[];
  categories: any[];
  onImported: () => Promise<void> | void;
}

interface PreviewRow {
  rowIndex: number;
  date: string;
  amount: number;
  type: string;
  description: string;
  walletId: number | null;
  categoryId: number | null;
  categoryName: string | null;
  categoryColor: string | null;
  duplicate: boolean;
  duplicateReason: string | null;
  valid: boolean;
  error: string | null;
}

const formatVND = (n: number) =>
  new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(n || 0);

function parseCsvText(raw: string): string[][] {
  const clean = raw.replace(/^\uFEFF/, '');
  const firstLine = clean.split(/\r?\n/)[0] || '';
  const delimiter = (firstLine.match(/;/g)?.length || 0) > (firstLine.match(/,/g)?.length || 0) ? ';' : ',';

  const rows: string[][] = [];
  let row: string[] = [];
  let field = '';
  let inQuotes = false;

  for (let i = 0; i < clean.length; i++) {
    const ch = clean[i];
    if (inQuotes) {
      if (ch === '"') {
        if (clean[i + 1] === '"') {
          field += '"';
          i++;
        } else {
          inQuotes = false;
        }
      } else {
        field += ch;
      }
    } else {
      if (ch === '"') {
        inQuotes = true;
      } else if (ch === delimiter) {
        row.push(field.trim());
        field = '';
      } else if (ch === '\n' || ch === '\r') {
        if (ch === '\r' && clean[i + 1] === '\n') i++;
        row.push(field.trim());
        if (row.some((cell) => cell !== '')) rows.push(row);
        row = [];
        field = '';
      } else {
        field += ch;
      }
    }
  }
  row.push(field.trim());
  if (row.some((cell) => cell !== '')) rows.push(row);
  return rows;
}

function parseAmountCell(raw: string): number {
  if (!raw) return 0;
  const s = raw.replace(/\s+/g, '').replace(/[₫đVNDvnd]/g, '');
  const isNeg = s.startsWith('-');
  const unsigned = s.replace(/^[+-]/, '');
  const normalized = unsigned.replace(/[.,](?=\d{3}(?:\D|$))/g, '').replace(',', '.');
  const val = Number(normalized);
  if (Number.isNaN(val)) return 0;
  return isNeg ? -Math.abs(val) : val;
}

export default function CsvImportModal({ isOpen, onClose, wallets, categories, onImported }: CsvImportModalProps) {
  const [step, setStep] = useState<'upload' | 'preview' | 'done'>('upload');
  const [headers, setHeaders] = useState<string[]>([]);
  const [rawRows, setRawRows] = useState<string[][]>([]);
  const [fileName, setFileName] = useState('');

  const [walletId, setWalletId] = useState<number>(wallets[0]?.id || 0);
  const [dateCol, setDateCol] = useState<number>(0);
  const [descCol, setDescCol] = useState<number>(1);
  const [amountCol, setAmountCol] = useState<number>(2);
  const [typeCol, setTypeCol] = useState<number>(-1);

  const [previewRows, setPreviewRows] = useState<PreviewRow[]>([]);
  const [skipDuplicates, setSkipDuplicates] = useState(true);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [commitResult, setCommitResult] = useState<any>(null);

  if (!isOpen) return null;

  const resetAll = () => {
    setStep('upload');
    setHeaders([]);
    setRawRows([]);
    setFileName('');
    setPreviewRows([]);
    setError('');
    setCommitResult(null);
    setWalletId(wallets[0]?.id || 0);
  };

  const handleClose = () => {
    resetAll();
    onClose();
  };

  const handleDownloadSample = () => {
    const sample = [
      'Ngay,Mo ta,So tien,Loai',
      '2026-11-25,GrabBike di hoc,-45000,EXPENSE',
      '2026-11-25,Com trua van phong,-55000,EXPENSE',
      '2026-11-26,Shopee mua phu kien,-230000,EXPENSE',
      '2026-11-26,Thuong du an Freelance,2500000,INCOME',
      '2026-11-26,GrabBike di hoc,-45000,EXPENSE',
    ].join('\n');
    const blob = new Blob(['\uFEFF' + sample], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'sao_ke_mau_HTR_FinMind.csv';
    a.click();
    URL.revokeObjectURL(url);
  };

  const applyParsedTable = (parsed: string[][]) => {
    if (parsed.length < 2) {
      setError('Tệp cần có ít nhất 1 dòng tiêu đề và 1 dòng dữ liệu.');
      return;
    }
    const hdr = parsed[0].map((c) => String(c ?? '').trim());
    const body = parsed.slice(1).filter((r) => r.some((cell) => String(cell ?? '').trim() !== ''));
    setHeaders(hdr);
    setRawRows(body);

    const findCol = (patterns: string[], fallback: number) => {
      const idx = hdr.findIndex((h) => patterns.some((p) => h.toLowerCase().includes(p)));
      return idx >= 0 ? idx : fallback;
    };
    setDateCol(findCol(['ngay', 'ngày', 'date', 'time', 'thời gian'], 0));
    setDescCol(findCol(['mo ta', 'mô tả', 'noi dung', 'nội dung', 'desc', 'memo', 'diễn giải'], Math.min(1, hdr.length - 1)));
    setAmountCol(findCol(['so tien', 'số tiền', 'amount', 'tien', 'tiền', 'debit', 'credit'], Math.min(2, hdr.length - 1)));
    setTypeCol(findCol(['loai', 'loại', 'type', 'thu/chi'], -1));
    if (!walletId && wallets[0]) setWalletId(wallets[0].id);
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setError('');
    setFileName(file.name);
    const lowerName = file.name.toLowerCase();

    if (lowerName.endsWith('.xlsx') || lowerName.endsWith('.xls')) {
      const reader = new FileReader();
      reader.onload = () => {
        try {
          const wb = XLSX.read(reader.result, { type: 'array', cellDates: false });
          const firstSheet = wb.Sheets[wb.SheetNames[0]];
          const sheetRows = XLSX.utils.sheet_to_json<any[]>(firstSheet, { header: 1, raw: false, dateNF: 'yyyy-mm-dd' });
          const strRows = (sheetRows || []).map((r) => (r || []).map((cell) => String(cell ?? '').trim()));
          applyParsedTable(strRows);
        } catch {
          setError('Không đọc được tệp Excel. Vui lòng kiểm tra lại định dạng .xlsx / .xls.');
        }
      };
      reader.readAsArrayBuffer(file);
      return;
    }

    const reader = new FileReader();
    reader.onload = () => {
      const text = String(reader.result || '');
      applyParsedTable(parseCsvText(text));
    };
    reader.readAsText(file, 'utf-8');
  };

  const handlePreview = async () => {
    if (!walletId) {
      setError('Vui lòng chọn ví đích trước khi đối soát.');
      return;
    }
    setLoading(true);
    setError('');
    try {
      const mapped = rawRows.map((r) => {
        const rawAmt = parseAmountCell(r[amountCol] || '');
        const rawTypeCell = typeCol >= 0 ? (r[typeCol] || '').toUpperCase() : '';
        let type = '';
        if (rawTypeCell.includes('CHUYỂN') || rawTypeCell.includes('CHUYEN') || rawTypeCell.includes('TRANSFER')) {
          type = 'TRANSFER';
        } else if (rawTypeCell.includes('THU') || rawTypeCell.includes('INCOME') || rawTypeCell.includes('CREDIT')) {
          type = 'INCOME';
        } else if (rawTypeCell.includes('CHI') || rawTypeCell.includes('EXPENSE') || rawTypeCell.includes('DEBIT')) {
          type = 'EXPENSE';
        }
        return {
          date: r[dateCol] || '',
          description: r[descCol] || '',
          amount: rawAmt,
          type,
          walletId,
        };
      });

      const res = await axiosClient.post('/transactions/import/preview', {
        defaultWalletId: walletId,
        skipDuplicates,
        rows: mapped,
      });
      setPreviewRows(res.data?.rows || []);
      setStep('preview');
    } catch (err: any) {
      setError(err?.response?.data?.message || 'Lỗi khi đối soát tệp sao kê');
    } finally {
      setLoading(false);
    }
  };

  const updateRowCategory = (rowIndex: number, newCatId: number) => {
    setPreviewRows((prev) =>
      prev.map((r) => {
        if (r.rowIndex !== rowIndex) return r;
        const cat = categories.find((c) => c.id === newCatId);
        return {
          ...r,
          categoryId: newCatId || null,
          categoryName: cat?.name || null,
          categoryColor: cat?.color || null,
        };
      })
    );
  };

  const handleCommit = async () => {
    setLoading(true);
    setError('');
    try {
      const payloadRows = previewRows
        .filter((r) => r.valid)
        .map((r) => ({
          date: r.date,
          amount: r.amount,
          type: r.type,
          description: r.description,
          walletId: r.walletId || walletId,
          categoryId: r.categoryId,
        }));

      const res = await axiosClient.post('/transactions/import/commit', {
        defaultWalletId: walletId,
        skipDuplicates,
        rows: payloadRows,
      });
      setCommitResult(res.data);
      setStep('done');
      await onImported();
    } catch (err: any) {
      setError(err?.response?.data?.message || 'Lỗi khi lưu giao dịch từ sao kê');
    } finally {
      setLoading(false);
    }
  };

  const validNonDupCount = previewRows.filter((r) => r.valid && (!skipDuplicates || !r.duplicate)).length;
  const duplicateCount = previewRows.filter((r) => r.duplicate).length;

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center p-4 z-50">
      <div className="bg-white rounded-[28px] max-w-3xl w-full p-6 max-h-[90dvh] flex flex-col overflow-hidden">
        <div className="flex items-center justify-between pb-4 border-b border-black/[0.06] shrink-0">
          <div className="flex items-center gap-2.5">
            <div className="w-9 h-9 rounded-2xl bg-[#1d1d1f] text-white flex items-center justify-center">
              <FileSpreadsheet className="w-4 h-4" />
            </div>
            <div>
              <h3 className="text-lg font-bold text-[#1d1d1f]">Import & Đối soát Sao kê CSV / Excel</h3>
              <p className="text-xs text-[#6e6e73]">Tự động ánh xạ cột, gán danh mục theo từ khóa và phát hiện trùng lặp</p>
            </div>
          </div>
          <button type="button" onClick={handleClose} className="p-1.5 text-[#86868b] hover:text-[#1d1d1f] hover:bg-black/[0.05] rounded-full cursor-pointer">
            <X className="w-5 h-5" />
          </button>
        </div>

        <div className="flex-1 overflow-y-auto py-4 space-y-4">
          {error && (
            <div className="p-3 rounded-2xl bg-[#ff3b30]/10 border border-[#ff3b30]/20 text-[#d70015] text-xs font-medium">
              {error}
            </div>
          )}

          {step === 'upload' && (
            <div className="space-y-4">
              <div className="p-6 rounded-2xl border-2 border-dashed border-black/[0.12] bg-[#f5f5f7]/70 text-center space-y-3">
                <Upload className="w-8 h-8 text-[#0071e3] mx-auto" />
                <div>
                  <div className="text-sm font-semibold text-[#1d1d1f]">
                    {fileName ? `Đã chọn: ${fileName} (${rawRows.length} dòng)` : 'Chọn tệp sao kê .CSV hoặc Excel (.XLSX / .XLS)'}
                  </div>
                  <p className="text-xs text-[#6e6e73] mt-0.5">Hỗ trợ tệp CSV UTF-8 và bảng tính Excel xuất trực tiếp từ ngân hàng / ví điện tử</p>
                </div>
                <div className="flex flex-wrap items-center justify-center gap-2">
                  <label className="px-4 py-2 rounded-full bg-[#1d1d1f] hover:bg-black text-white text-xs font-semibold cursor-pointer">
                    Chọn tệp CSV / Excel
                    <input type="file" accept=".csv,.xlsx,.xls,.txt" onChange={handleFileChange} className="hidden" />
                  </label>
                  <button
                    type="button"
                    onClick={handleDownloadSample}
                    className="px-4 py-2 rounded-full bg-white border border-black/[0.1] hover:bg-black/[0.04] text-[#1d1d1f] text-xs font-semibold inline-flex items-center gap-1.5 cursor-pointer"
                  >
                    <Download className="w-3.5 h-3.5" /> Tải file CSV mẫu
                  </button>
                </div>
              </div>

              {headers.length > 0 && (
                <div className="p-4 rounded-2xl bg-[#f5f5f7] space-y-3">
                  <div className="text-xs font-bold uppercase tracking-wider text-[#6e6e73]">Cấu hình ánh xạ cột & Ví đích</div>
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs">
                    <div>
                      <label className="block font-medium text-[#1d1d1f] mb-1">Ví áp dụng</label>
                      <select value={walletId} onChange={(e) => setWalletId(Number(e.target.value))} className="w-full px-3 py-2 rounded-xl border border-black/[0.1] bg-white">
                        {wallets.map((w) => <option key={w.id} value={w.id}>{w.name}</option>)}
                      </select>
                    </div>
                    <div>
                      <label className="block font-medium text-[#1d1d1f] mb-1">Cột Ngày giao dịch</label>
                      <select value={dateCol} onChange={(e) => setDateCol(Number(e.target.value))} className="w-full px-3 py-2 rounded-xl border border-black/[0.1] bg-white">
                        {headers.map((h, i) => <option key={i} value={i}>{h || `Cột ${i + 1}`}</option>)}
                      </select>
                    </div>
                    <div>
                      <label className="block font-medium text-[#1d1d1f] mb-1">Cột Mô tả / Diễn giải</label>
                      <select value={descCol} onChange={(e) => setDescCol(Number(e.target.value))} className="w-full px-3 py-2 rounded-xl border border-black/[0.1] bg-white">
                        {headers.map((h, i) => <option key={i} value={i}>{h || `Cột ${i + 1}`}</option>)}
                      </select>
                    </div>
                    <div>
                      <label className="block font-medium text-[#1d1d1f] mb-1">Cột Số tiền (âm = Chi, dương = Thu)</label>
                      <select value={amountCol} onChange={(e) => setAmountCol(Number(e.target.value))} className="w-full px-3 py-2 rounded-xl border border-black/[0.1] bg-white">
                        {headers.map((h, i) => <option key={i} value={i}>{h || `Cột ${i + 1}`}</option>)}
                      </select>
                    </div>
                    <div className="sm:col-span-2">
                      <label className="block font-medium text-[#1d1d1f] mb-1">Cột Loại giao dịch (Tùy chọn)</label>
                      <select value={typeCol} onChange={(e) => setTypeCol(Number(e.target.value))} className="w-full px-3 py-2 rounded-xl border border-black/[0.1] bg-white">
                        <option value={-1}>-- Tự suy luận từ dấu (+/-) số tiền --</option>
                        {headers.map((h, i) => <option key={i} value={i}>{h || `Cột ${i + 1}`}</option>)}
                      </select>
                    </div>
                  </div>
                </div>
              )}
            </div>
          )}

          {step === 'preview' && (
            <div className="space-y-3">
              <div className="flex flex-wrap items-center justify-between gap-2 p-3.5 rounded-2xl bg-[#f5f5f7] text-xs">
                <div className="flex flex-wrap items-center gap-3">
                  <span className="font-semibold text-[#1d1d1f]">Tổng: {previewRows.length} dòng</span>
                  <span className="text-[#248a3d] font-semibold">Hợp lệ: {validNonDupCount}</span>
                  {duplicateCount > 0 && (
                    <span className="inline-flex items-center gap-1 text-[#d70015] font-semibold">
                      <AlertTriangle className="w-3.5 h-3.5" /> Trùng lặp: {duplicateCount}
                    </span>
                  )}
                </div>
                <label className="inline-flex items-center gap-2 font-medium text-[#1d1d1f] cursor-pointer">
                  <input type="checkbox" checked={skipDuplicates} onChange={(e) => setSkipDuplicates(e.target.checked)} className="rounded" />
                  Tự động bỏ qua dòng trùng lặp
                </label>
              </div>

              <div className="space-y-2 max-h-[340px] overflow-y-auto pr-1">
                {previewRows.map((row) => (
                  <div
                    key={row.rowIndex}
                    className={
                      'p-3 rounded-2xl border text-xs flex flex-col sm:flex-row sm:items-center justify-between gap-2 ' +
                      (row.duplicate ? 'bg-[#ff3b30]/[0.05] border-[#ff3b30]/25 opacity-80' : 'bg-white border-black/[0.07]')
                    }
                  >
                    <div className="min-w-0 flex-1">
                      <div className="flex items-center gap-2 flex-wrap">
                        <span className="font-semibold text-[#1d1d1f] truncate">{row.description}</span>
                        {row.duplicate && (
                          <span className="px-2 py-0.5 rounded-full bg-[#ff3b30]/15 text-[#d70015] text-[10px] font-bold">
                            TRÙNG LẶP
                          </span>
                        )}
                      </div>
                      <div className="text-[11px] text-[#6e6e73] mt-0.5">
                        {row.date?.slice(0, 10)} {row.duplicateReason ? `• ${row.duplicateReason}` : ''}
                      </div>
                    </div>

                    <div className="flex items-center gap-2 shrink-0">
                      <select
                        value={row.categoryId || 0}
                        onChange={(e) => updateRowCategory(row.rowIndex, Number(e.target.value))}
                        className="px-2.5 py-1.5 rounded-full border border-black/[0.1] bg-[#f5f5f7] text-xs max-w-[140px]"
                      >
                        <option value={0}>Chưa phân loại</option>
                        {categories.filter((c) => c.type === row.type).map((c) => (
                          <option key={c.id} value={c.id}>{c.name}</option>
                        ))}
                      </select>
                      <span className={'font-bold tabular-nums min-w-[92px] text-right ' + (row.type === 'INCOME' ? 'text-[#248a3d]' : 'text-[#d70015]')}>
                        {row.type === 'INCOME' ? '+' : '-'}{formatVND(row.amount)}
                      </span>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {step === 'done' && commitResult && (
            <div className="py-8 text-center space-y-3">
              <CheckCircle2 className="w-12 h-12 text-[#248a3d] mx-auto" />
              <div className="text-lg font-bold text-[#1d1d1f]">Đối soát & Nhập sao kê thành công!</div>
              <div className="text-sm text-[#6e6e73]">
                Đã nhập <strong className="text-[#248a3d]">{commitResult.importedCount}</strong> giao dịch ({formatVND(commitResult.totalImportedAmount)})
                {commitResult.skippedDuplicates > 0 && ` • Đã loại bỏ ${commitResult.skippedDuplicates} giao dịch trùng lặp`}.
              </div>
            </div>
          )}
        </div>

        <div className="pt-4 border-t border-black/[0.06] flex justify-end gap-2 shrink-0">
          {step === 'upload' && (
            <>
              <button type="button" onClick={handleClose} className="px-4 py-2 rounded-full text-sm text-[#6e6e73] hover:bg-black/[0.05] cursor-pointer">Hủy</button>
              <button
                type="button"
                disabled={loading || rawRows.length === 0}
                onClick={handlePreview}
                className="px-5 py-2 rounded-full bg-[#1d1d1f] hover:bg-black disabled:opacity-40 text-white text-sm font-medium cursor-pointer"
              >
                {loading ? 'Đang đối soát...' : 'Tiếp tục & Kiểm tra trùng lặp'}
              </button>
            </>
          )}
          {step === 'preview' && (
            <>
              <button type="button" onClick={() => setStep('upload')} className="px-4 py-2 rounded-full text-sm text-[#6e6e73] hover:bg-black/[0.05] cursor-pointer">Quay lại</button>
              <button
                type="button"
                disabled={loading || validNonDupCount === 0}
                onClick={handleCommit}
                className="px-5 py-2 rounded-full bg-[#1d1d1f] hover:bg-black disabled:opacity-40 text-white text-sm font-medium cursor-pointer"
              >
                {loading ? 'Đang lưu...' : `Nhập ${validNonDupCount} giao dịch hợp lệ`}
              </button>
            </>
          )}
          {step === 'done' && (
            <button type="button" onClick={handleClose} className="px-5 py-2 rounded-full bg-[#1d1d1f] text-white text-sm font-medium cursor-pointer">Hoàn tất</button>
          )}
        </div>
      </div>
    </div>
  );
}
