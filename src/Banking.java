import java.util.concurrent.locks.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.Random;

class Bank {
    public static final int N = 10;
    private int[] balances = new int[N];
    private Lock[] locks = new Lock[N];

    public Bank() {
        for (int i = 0; i < locks.length; ++i) {
            locks[i] = new ReentrantLock();
        }
    }

    public void deposit(int accountId, int amount) {
        balances[accountId] += amount;
    }

    public int getBalance(int accountId) {
        return balances[accountId];
    }

    public boolean transfer(int fromAccount, int toAccount, int amount) {
        if (fromAccount == toAccount) {
            return false;
        }

        // sort account IDs to avoid deadlocks
        int minAccountId = Math.min(fromAccount, toAccount);
        int maxAccountId = Math.max(fromAccount, toAccount);

        locks[minAccountId].lock();
        locks[maxAccountId].lock();

        //checking accounts balances
        try {
            if (balances[fromAccount] < amount) {
                return false;
            }

            balances[fromAccount] -= amount; // reduce balance
            balances[toAccount] += amount; //increase balance
            return true;
        } finally {
            locks[maxAccountId].unlock();  //unlock blocads from accounts
            locks[minAccountId].unlock();
        }
    }

    public void equalize(int accountA, int accountB) {
        if (accountA == accountB) { //check if transfer is to another account
            return;
        }

        Lock lockA = locks[accountA];
        Lock lockB = locks[accountB];

        // sort account locks to avoid deadlocks
        if (accountA > accountB) {
            lockA = locks[accountB];
            lockB = locks[accountA];
        }

        while (true) { //locking acconuts
            boolean successA = lockA.tryLock();
            boolean successB = lockB.tryLock();

            if (successA && successB) { //if succes during locking -> equalize balances
                try {
                    int total = balances[accountA] + balances[accountB];
                    balances[accountA] = total / 2 + total % 2;
                    balances[accountB] = total / 2;
                    return;
                } finally {  //if not success during locking a and b
                    lockB.unlock();
                    lockA.unlock();
                }
            } else {
                if (successA) {
                    lockA.unlock();
                }
                if (successB) {
                    lockB.unlock();
                }
            }
        }
    }
}

class Accountant extends Thread {
    Bank bank;

    public Accountant(Bank bank) {
        this.bank = bank;
    }

    @Override
    public void run() {
        Random rng = ThreadLocalRandom.current();
        for (int i = 0; i < 1000; ++i) {
            // Try to transfer a random amount between a pair of accounts
            // The accounts numbers (ids) are also selected randomly
            int fromAccount = rng.nextInt(Bank.N);
            int toAccount = rng.nextInt(Bank.N);
            while (toAccount == fromAccount) { // Source should differ from
                // the target
                toAccount = rng.nextInt(Bank.N); // Try again
            }
            if (rng.nextBoolean()) { // 50% of the time we transfer...
                bank.transfer(fromAccount, toAccount, rng.nextInt(100));
            } else { // ...the remaining 50% of the time we equalize
                bank.equalize(fromAccount, toAccount);
            }
        }
    }
}

public class Banking {
    public static void main(String [] args) throws InterruptedException {
        Bank bank = new Bank();
        for (int i = 0; i < Bank.N; ++i) {
            bank.deposit(i, 100);
        }
        Thread [] threads = new Thread[10];
        for (int i = 0; i < threads.length; ++i) {
            threads[i] = new Accountant(bank);
        }
        for (Thread t : threads) { t.start(); }
        for (Thread t : threads) { t.join(); }
        int total = 0;
        for (int i = 0; i < Bank.N; ++i) {
            int b = bank.getBalance(i);
            total += b;
            System.out.printf("Account [%d] balance: %d\n", i, b);
        }
       // System.out.printf("Total balance is %d\tvalid value is %d\n", total, Bank.N * 100);
        System.out.printf("Total balance equals %d.\n", total);
    }
}
