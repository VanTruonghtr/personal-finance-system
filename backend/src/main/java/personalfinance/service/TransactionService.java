package personalfinance.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import personalfinance.entity.Category;
import personalfinance.entity.Transaction;
import personalfinance.entity.User;
import personalfinance.entity.Wallet;
import personalfinance.repository.CategoryRepository;
import personalfinance.repository.TransactionRepository;
import personalfinance.repository.UserRepository;
import personalfinance.repository.WalletRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TransactionService {
    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;

    public List<Transaction> getTransactionsByUser(Long userId) {
        return transactionRepository.findByUserIdOrderByTransactionDateDescIdDesc(userId);
    }

    @Transactional
    public Transaction createTransaction(Long walletId, Long targetWalletId, Long categoryId, String type, Double amount, String description, Long userId) {
        return createTransaction(walletId, targetWalletId, categoryId, type, amount, description, LocalDateTime.now(), userId);
    }

    @Transactional
    public Transaction createTransaction(Long walletId, Long targetWalletId, Long categoryId, String type, Double amount, String description, LocalDateTime transactionDate, Long userId) {
        Wallet wallet = walletRepository.findByIdAndUserId(walletId, userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy ví nguồn"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng"));
        Category category = (categoryId != null && categoryId > 0)
                ? categoryRepository.findById(categoryId)
                    .filter(c -> c.getUser() == null || c.getUser().getId().equals(userId))
                    .orElse(null)
                : null;
        Wallet targetWallet = null;

        double val = amount != null ? amount : 0.0;
        double currentBal = wallet.getBalance() != null ? wallet.getBalance() : 0.0;

        if ("INCOME".equalsIgnoreCase(type)) {
            wallet.setBalance(currentBal + val);
        } else if ("EXPENSE".equalsIgnoreCase(type)) {
            wallet.setBalance(currentBal - val);
        } else if ("TRANSFER".equalsIgnoreCase(type)) {
            if (targetWalletId == null || targetWalletId.equals(walletId)) {
                throw new RuntimeException("Ví nhận phải khác ví nguồn");
            }
            targetWallet = walletRepository.findByIdAndUserId(targetWalletId, userId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy ví nhận"));
            wallet.setBalance(currentBal - val);
            double targetBal = targetWallet.getBalance() != null ? targetWallet.getBalance() : 0.0;
            targetWallet.setBalance(targetBal + val);
            walletRepository.save(targetWallet);
        }
        walletRepository.save(wallet);

        Transaction tx = Transaction.builder()
                .type(type)
                .amount(val)
                .description(description)
                .transactionDate(transactionDate != null ? transactionDate : LocalDateTime.now())
                .wallet(wallet)
                .targetWallet(targetWallet)
                .category(category)
                .user(user)
                .build();

        return transactionRepository.save(tx);
    }

    @Transactional
    public Transaction updateTransaction(Long txId, Long newWalletId, Long newTargetWalletId, Long newCategoryId, String newType, Double newAmount, String newDescription, Long userId) {
        Transaction tx = transactionRepository.findByIdAndUserId(txId, userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy giao dịch"));

        // 1. Hoàn tác số dư cũ
        Wallet oldWallet = tx.getWallet();
        double oldVal = tx.getAmount() != null ? tx.getAmount() : 0.0;
        if (oldWallet != null) {
            double oldBal = oldWallet.getBalance() != null ? oldWallet.getBalance() : 0.0;
            if ("INCOME".equalsIgnoreCase(tx.getType())) {
                oldWallet.setBalance(oldBal - oldVal);
            } else if ("EXPENSE".equalsIgnoreCase(tx.getType())) {
                oldWallet.setBalance(oldBal + oldVal);
            } else if ("TRANSFER".equalsIgnoreCase(tx.getType())) {
                oldWallet.setBalance(oldBal + oldVal);
                Wallet oldTarget = tx.getTargetWallet();
                if (oldTarget != null) {
                    double oldTargetBal = oldTarget.getBalance() != null ? oldTarget.getBalance() : 0.0;
                    oldTarget.setBalance(oldTargetBal - oldVal);
                    walletRepository.save(oldTarget);
                }
            }
            walletRepository.save(oldWallet);
        }

        // 2. Áp dụng giao dịch mới
        Wallet sourceWallet = (newWalletId != null)
                ? walletRepository.findByIdAndUserId(newWalletId, userId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy ví nguồn"))
                : oldWallet;
        Wallet destWallet = null;
        double val = newAmount != null ? newAmount : 0.0;

        if (sourceWallet != null) {
            double curBal = sourceWallet.getBalance() != null ? sourceWallet.getBalance() : 0.0;
            if ("INCOME".equalsIgnoreCase(newType)) {
                sourceWallet.setBalance(curBal + val);
            } else if ("EXPENSE".equalsIgnoreCase(newType)) {
                sourceWallet.setBalance(curBal - val);
            } else if ("TRANSFER".equalsIgnoreCase(newType)) {
                if (newTargetWalletId == null || newTargetWalletId.equals(sourceWallet.getId())) {
                    throw new RuntimeException("Ví nhận phải khác ví nguồn");
                }
                destWallet = walletRepository.findByIdAndUserId(newTargetWalletId, userId)
                        .orElseThrow(() -> new RuntimeException("Không tìm thấy ví nhận"));
                sourceWallet.setBalance(curBal - val);
                double destBal = destWallet.getBalance() != null ? destWallet.getBalance() : 0.0;
                destWallet.setBalance(destBal + val);
                walletRepository.save(destWallet);
            }
            walletRepository.save(sourceWallet);
        }

        Category category = (newCategoryId != null && newCategoryId > 0)
                ? categoryRepository.findById(newCategoryId)
                    .filter(c -> c.getUser() == null || c.getUser().getId().equals(userId))
                    .orElse(null)
                : null;

        tx.setWallet(sourceWallet);
        tx.setTargetWallet(destWallet);
        tx.setCategory(category);
        tx.setType(newType);
        tx.setAmount(val);
        tx.setDescription(newDescription);

        return transactionRepository.save(tx);
    }

    @Transactional
    public void deleteTransaction(Long txId, Long userId) {
        Transaction tx = transactionRepository.findByIdAndUserId(txId, userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy giao dịch"));

        Wallet wallet = tx.getWallet();
        double val = tx.getAmount() != null ? tx.getAmount() : 0.0;
        if (wallet != null) {
            double curBal = wallet.getBalance() != null ? wallet.getBalance() : 0.0;
            if ("INCOME".equalsIgnoreCase(tx.getType())) {
                wallet.setBalance(curBal - val);
            } else if ("EXPENSE".equalsIgnoreCase(tx.getType())) {
                wallet.setBalance(curBal + val);
            } else if ("TRANSFER".equalsIgnoreCase(tx.getType())) {
                wallet.setBalance(curBal + val);
                Wallet target = tx.getTargetWallet();
                if (target != null) {
                    double targetBal = target.getBalance() != null ? target.getBalance() : 0.0;
                    target.setBalance(targetBal - val);
                    walletRepository.save(target);
                }
            }
            walletRepository.save(wallet);
        }

        transactionRepository.delete(tx);
    }
}