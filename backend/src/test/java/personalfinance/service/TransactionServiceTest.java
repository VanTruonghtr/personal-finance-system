package personalfinance.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import personalfinance.entity.Transaction;
import personalfinance.entity.User;
import personalfinance.entity.Wallet;
import personalfinance.repository.CategoryRepository;
import personalfinance.repository.TransactionRepository;
import personalfinance.repository.UserRepository;
import personalfinance.repository.WalletRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private WalletRepository walletRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks private TransactionService transactionService;

    @Test
    void shouldUpdateBalancesAccuratelyOnInternalTransfer() {
        User user = User.builder().id(1L).email("test@uit.edu.vn").build();
        Wallet vcb = Wallet.builder().id(10L).name("VCB").balance(5_000_000.0).user(user).build();
        Wallet momo = Wallet.builder().id(20L).name("Momo").balance(500_000.0).user(user).build();

        when(walletRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(vcb));
        when(walletRepository.findByIdAndUserId(20L, 1L)).thenReturn(Optional.of(momo));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        Transaction tx = transactionService.createTransaction(10L, 20L, null, "TRANSFER", 1_200_000.0, "Nạp ví Momo", 1L);

        assertEquals("TRANSFER", tx.getType());
        assertEquals(3_800_000.0, vcb.getBalance());
        assertEquals(1_700_000.0, momo.getBalance());
        verify(walletRepository).save(vcb);
        verify(walletRepository).save(momo);
    }

    @Test
    void shouldRevertWalletBalanceWhenDeletingExpense() {
        Wallet cash = Wallet.builder().id(10L).name("Tiền mặt").balance(800_000.0).build();
        Transaction expense = Transaction.builder().id(99L).type("EXPENSE").amount(200_000.0).wallet(cash).build();

        when(transactionRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.of(expense));

        transactionService.deleteTransaction(99L, 1L);

        assertEquals(1_000_000.0, cash.getBalance());
        verify(walletRepository).save(cash);
        verify(transactionRepository).delete(expense);
    }
}
