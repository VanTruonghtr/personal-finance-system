export interface UserProfile {
  id: number;
  fullName: string;
  email: string;
  role: string;
  monthlyBudgetLimit?: number;
}

export interface Wallet {
  id: number;
  name: string;
  type: 'CASH' | 'BANK' | 'EWALLET';
  balance: number;
  status: 'ACTIVE' | 'INACTIVE';
}

export interface Category {
  id: number;
  name: string;
  type: 'INCOME' | 'EXPENSE';
  icon: string;
  color: string;
}

export interface TransactionItem {
  id: number;
  type: 'INCOME' | 'EXPENSE' | 'TRANSFER';
  amount: number;
  transactionDate: string;
  description: string;
  note: string;
  walletId: number;
  walletName: string;
  targetWalletId?: number;
  targetWalletName?: string;
  categoryId?: number;
  categoryName?: string;
  categoryColor?: string;
}