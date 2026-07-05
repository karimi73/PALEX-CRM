package com.example.data

import kotlinx.coroutines.flow.Flow

class CrmRepository(private val database: AppDatabase) {
    private val customerDao = database.customerDao()
    private val transactionDao = database.transactionDao()
    private val followUpTaskDao = database.followUpTaskDao()
    private val inventoryItemDao = database.inventoryItemDao()

    // Customer operations
    val allCustomers: Flow<List<Customer>> = customerDao.getAllCustomers()

    suspend fun getCustomerById(id: Int): Customer? = customerDao.getCustomerById(id)

    suspend fun insertCustomer(customer: Customer): Long = customerDao.insertCustomer(customer)

    suspend fun updateCustomer(customer: Customer) = customerDao.updateCustomer(customer)

    suspend fun deleteCustomer(customerId: Int) {
        customerDao.deleteCustomerById(customerId)
    }

    // Transaction operations
    val allTransactions: Flow<List<Transaction>> = transactionDao.getAllTransactions()

    fun getTransactionsForCustomer(customerId: Int): Flow<List<Transaction>> = 
        transactionDao.getTransactionsForCustomer(customerId)

    suspend fun insertTransaction(transaction: Transaction) {
        transactionDao.insertTransaction(transaction)
        // Recalculate customer's total spent
        updateCustomerSpent(transaction.customerId)
    }

    suspend fun deleteTransaction(transaction: Transaction) {
        transactionDao.deleteTransactionById(transaction.id)
        // Recalculate customer's total spent
        updateCustomerSpent(transaction.customerId)
    }

    suspend fun updateTransactionStatus(id: Int, status: String) {
        val tx = transactionDao.getTransactionById(id) ?: return
        transactionDao.updateTransactionStatus(id, status)
        // Recalculate customer's total spent
        updateCustomerSpent(tx.customerId)
    }

    private suspend fun updateCustomerSpent(customerId: Int) {
        val customer = customerDao.getCustomerById(customerId) ?: return
        val totalSpent = transactionDao.getCustomerTotalSpent(customerId) ?: 0.0
        customerDao.updateCustomer(customer.copy(totalSpent = totalSpent))
    }

    // FollowUpTask operations
    val allTasks: Flow<List<FollowUpTask>> = followUpTaskDao.getAllTasks()
    val pendingTasks: Flow<List<FollowUpTask>> = followUpTaskDao.getPendingTasks()

    suspend fun insertTask(task: FollowUpTask) = followUpTaskDao.insertTask(task)

    suspend fun updateTaskStatus(id: Int, isCompleted: Boolean) = 
        followUpTaskDao.updateTaskStatus(id, isCompleted)

    suspend fun deleteTask(id: Int) = followUpTaskDao.deleteTaskById(id)

    // Inventory operations
    val allInventoryItems: Flow<List<InventoryItem>> = inventoryItemDao.getAllInventoryItems()

    suspend fun insertInventoryItem(item: InventoryItem): Long = inventoryItemDao.insertInventoryItem(item)

    suspend fun updateInventoryItem(item: InventoryItem) = inventoryItemDao.updateInventoryItem(item)

    suspend fun deleteInventoryItem(id: Int) = inventoryItemDao.deleteInventoryItemById(id)

    suspend fun updateStockQuantity(id: Int, quantity: Int) = inventoryItemDao.updateStockQuantity(id, quantity)
}
