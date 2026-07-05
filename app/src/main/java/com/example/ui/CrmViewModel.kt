package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class CrmViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val repository = CrmRepository(database)

    private val _isDarkMode = MutableStateFlow(true)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    fun toggleTheme() {
        _isDarkMode.value = !_isDarkMode.value
    }

    private val prefs = application.getSharedPreferences("crm_settings", android.content.Context.MODE_PRIVATE)

    // Profile States
    private val _profileUsername = MutableStateFlow(prefs.getString("profileUsername", "امیر کریمی") ?: "امیر کریمی")
    val profileUsername = _profileUsername.asStateFlow()

    private val _profileRole = MutableStateFlow(prefs.getString("profileRole", "مدیر تولید و فروش") ?: "مدیر تولید و فروش")
    val profileRole = _profileRole.asStateFlow()

    private val _profilePhone = MutableStateFlow(prefs.getString("profilePhone", "۰۹۱۲۱۱۱۱۱۱۱") ?: "۰۹۱۲۱۱۱۱۱۱۱")
    val profilePhone = _profilePhone.asStateFlow()

    private val _profileBio = MutableStateFlow(prefs.getString("profileBio", "طراح دکوراسیون و صنایع چوب افرا") ?: "طراح دکوراسیون و صنایع چوب افرا")
    val profileBio = _profileBio.asStateFlow()

    // Invoice Customization Settings
    private val _invoiceWorkshopName = MutableStateFlow(prefs.getString("invoiceWorkshopName", "صنایع چوب، مبلمان و دکوراسیون کارگاه") ?: "صنایع چوب، مبلمان و دکوراسیون کارگاه")
    val invoiceWorkshopName = _invoiceWorkshopName.asStateFlow()

    private val _invoicePhone = MutableStateFlow(prefs.getString("invoicePhone", "۰۹۱۲۳۴۵۶۷۸۹") ?: "۰۹۱۲۳۴۵۶۷۸۹")
    val invoicePhone = _invoicePhone.asStateFlow()

    private val _invoiceAddress = MutableStateFlow(prefs.getString("invoiceAddress", "تهران، شهرک صنعتی خاوران، بازار مبل") ?: "تهران، شهرک صنعتی خاوران، بازار مبل")
    val invoiceAddress = _invoiceAddress.asStateFlow()

    private val _invoiceHeaderColor = MutableStateFlow(prefs.getString("invoiceHeaderColor", "#0F172A") ?: "#0F172A")
    val invoiceHeaderColor = _invoiceHeaderColor.asStateFlow()

    private val _invoiceShowLogo = MutableStateFlow(prefs.getBoolean("invoiceShowLogo", true))
    val invoiceShowLogo = _invoiceShowLogo.asStateFlow()

    private val _invoiceShowTerms = MutableStateFlow(prefs.getBoolean("invoiceShowTerms", true))
    val invoiceShowTerms = _invoiceShowTerms.asStateFlow()

    private val _invoiceTerms1 = MutableStateFlow(prefs.getString("invoiceTerms1", "۱. کلیه کالاها شامل گارانتی ۱۲ ماهه کلاف و اتصالات می‌باشند.") ?: "۱. کلیه کالاها شامل گارانتی ۱۲ ماهه کلاف و اتصالات می‌باشند.")
    val invoiceTerms1 = _invoiceTerms1.asStateFlow()

    private val _invoiceTerms2 = MutableStateFlow(prefs.getString("invoiceTerms2", "۲. تحویل نهایی مبل حداکثر تا ۲۵ روز کاری پس از واریز بیعانه خواهد بود.") ?: "۲. تحویل نهایی مبل حداکثر تا ۲۵ روز کاری پس از واریز بیعانه خواهد بود.")
    val invoiceTerms2 = _invoiceTerms2.asStateFlow()

    private val _invoiceTerms3 = MutableStateFlow(prefs.getString("invoiceTerms3", "۳. پرداخت نهایی بلافاصله پس از نصب و تایید فنی در کارگاه الزامی است.") ?: "۳. پرداخت نهایی بلافاصله پس از نصب و تایید فنی در کارگاه الزامی است.")
    val invoiceTerms3 = _invoiceTerms3.asStateFlow()

    private val _invoiceTerms4 = MutableStateFlow(prefs.getString("invoiceTerms4", "۴. این پیش‌فاکتور فاقد مهر برجسته رسمی، به عنوان سند برآورد است.") ?: "۴. این پیش‌فاکتور فاقد مهر برجسته رسمی، به عنوان سند برآورد است.")
    val invoiceTerms4 = _invoiceTerms4.asStateFlow()

    private val _invoiceBorderStyle = MutableStateFlow(prefs.getString("invoiceBorderStyle", "GOLD_ACCENT") ?: "GOLD_ACCENT")
    val invoiceBorderStyle = _invoiceBorderStyle.asStateFlow()

    private val _invoiceLogoPath = MutableStateFlow(prefs.getString("invoiceLogoPath", null))
    val invoiceLogoPath = _invoiceLogoPath.asStateFlow()

    private val _invoiceUseCustomLogo = MutableStateFlow(prefs.getBoolean("invoiceUseCustomLogo", false))
    val invoiceUseCustomLogo = _invoiceUseCustomLogo.asStateFlow()

    private val _invoiceWatermarkText = MutableStateFlow(prefs.getString("invoiceWatermarkText", "") ?: "")
    val invoiceWatermarkText = _invoiceWatermarkText.asStateFlow()

    private val _invoiceShowWatermark = MutableStateFlow(prefs.getBoolean("invoiceShowWatermark", false))
    val invoiceShowWatermark = _invoiceShowWatermark.asStateFlow()

    private val _invoiceUseSignature = MutableStateFlow(prefs.getBoolean("invoiceUseSignature", false))
    val invoiceUseSignature = _invoiceUseSignature.asStateFlow()

    private val _invoiceSignaturePath = MutableStateFlow(prefs.getString("invoiceSignaturePath", null))
    val invoiceSignaturePath = _invoiceSignaturePath.asStateFlow()

    private val _invoiceFontStyle = MutableStateFlow(prefs.getString("invoiceFontStyle", "SANS_SERIF") ?: "SANS_SERIF")
    val invoiceFontStyle = _invoiceFontStyle.asStateFlow()

    private val _invoiceDefaultTaxRate = MutableStateFlow(prefs.getFloat("invoiceDefaultTaxRate", 0f))
    val invoiceDefaultTaxRate = _invoiceDefaultTaxRate.asStateFlow()

    private val _invoiceDefaultDiscountRate = MutableStateFlow(prefs.getFloat("invoiceDefaultDiscountRate", 0f))
    val invoiceDefaultDiscountRate = _invoiceDefaultDiscountRate.asStateFlow()

    fun updateProfile(username: String, role: String, phone: String, bio: String) {
        _profileUsername.value = username
        _profileRole.value = role
        _profilePhone.value = phone
        _profileBio.value = bio
        prefs.edit().apply {
            putString("profileUsername", username)
            putString("profileRole", role)
            putString("profilePhone", phone)
            putString("profileBio", bio)
            apply()
        }
    }

    fun updateInvoiceSettings(
        workshopName: String,
        phone: String,
        address: String,
        headerColor: String,
        showLogo: Boolean,
        showTerms: Boolean,
        terms1: String,
        terms2: String,
        terms3: String,
        terms4: String,
        borderStyle: String,
        logoPath: String?,
        useCustomLogo: Boolean,
        watermarkText: String,
        showWatermark: Boolean,
        useSignature: Boolean,
        signaturePath: String?,
        fontStyle: String,
        defaultTaxRate: Float,
        defaultDiscountRate: Float
    ) {
        _invoiceWorkshopName.value = workshopName
        _invoicePhone.value = phone
        _invoiceAddress.value = address
        _invoiceHeaderColor.value = headerColor
        _invoiceShowLogo.value = showLogo
        _invoiceShowTerms.value = showTerms
        _invoiceTerms1.value = terms1
        _invoiceTerms2.value = terms2
        _invoiceTerms3.value = terms3
        _invoiceTerms4.value = terms4
        _invoiceBorderStyle.value = borderStyle
        _invoiceLogoPath.value = logoPath
        _invoiceUseCustomLogo.value = useCustomLogo
        _invoiceWatermarkText.value = watermarkText
        _invoiceShowWatermark.value = showWatermark
        _invoiceUseSignature.value = useSignature
        _invoiceSignaturePath.value = signaturePath
        _invoiceFontStyle.value = fontStyle
        _invoiceDefaultTaxRate.value = defaultTaxRate
        _invoiceDefaultDiscountRate.value = defaultDiscountRate

        prefs.edit().apply {
            putString("invoiceWorkshopName", workshopName)
            putString("invoicePhone", phone)
            putString("invoiceAddress", address)
            putString("invoiceHeaderColor", headerColor)
            putBoolean("invoiceShowLogo", showLogo)
            putBoolean("invoiceShowTerms", showTerms)
            putString("invoiceTerms1", terms1)
            putString("invoiceTerms2", terms2)
            putString("invoiceTerms3", terms3)
            putString("invoiceTerms4", terms4)
            putString("invoiceBorderStyle", borderStyle)
            putString("invoiceLogoPath", logoPath)
            putBoolean("invoiceUseCustomLogo", useCustomLogo)
            putString("invoiceWatermarkText", watermarkText)
            putBoolean("invoiceShowWatermark", showWatermark)
            putBoolean("invoiceUseSignature", useSignature)
            putString("invoiceSignaturePath", signaturePath)
            putString("invoiceFontStyle", fontStyle)
            putFloat("invoiceDefaultTaxRate", defaultTaxRate)
            putFloat("invoiceDefaultDiscountRate", defaultDiscountRate)
            apply()
        }
    }

    val customers: StateFlow<List<Customer>> = repository.allCustomers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val transactions: StateFlow<List<Transaction>> = repository.allTransactions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val tasks: StateFlow<List<FollowUpTask>> = repository.allTasks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingTasks: StateFlow<List<FollowUpTask>> = repository.pendingTasks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val inventoryItems: StateFlow<List<InventoryItem>> = repository.allInventoryItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Automatically seed sample data if empty
        viewModelScope.launch {
            customers.take(1).collect { list ->
                if (list.isEmpty()) {
                    seedSampleData()
                }
            }
        }
    }

    fun addCustomer(customer: Customer) {
        viewModelScope.launch {
            repository.insertCustomer(customer)
        }
    }

    fun updateCustomer(customer: Customer) {
        viewModelScope.launch {
            repository.updateCustomer(customer)
        }
    }

    fun deleteCustomer(id: Int) {
        viewModelScope.launch {
            repository.deleteCustomer(id)
        }
    }

    fun addTransaction(transaction: Transaction) {
        viewModelScope.launch {
            repository.insertTransaction(transaction)
        }
    }

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch {
            repository.deleteTransaction(transaction)
        }
    }

    fun updateTransactionStatus(id: Int, status: String) {
        viewModelScope.launch {
            repository.updateTransactionStatus(id, status)
        }
    }

    fun addTask(task: FollowUpTask) {
        viewModelScope.launch {
            repository.insertTask(task)
        }
    }

    fun toggleTaskStatus(id: Int, isCompleted: Boolean) {
        viewModelScope.launch {
            repository.updateTaskStatus(id, isCompleted)
        }
    }

    fun deleteTask(id: Int) {
        viewModelScope.launch {
            repository.deleteTask(id)
        }
    }

    fun addInventoryItem(item: InventoryItem) {
        viewModelScope.launch {
            repository.insertInventoryItem(item)
        }
    }

    fun updateInventoryItem(item: InventoryItem) {
        viewModelScope.launch {
            repository.updateInventoryItem(item)
        }
    }

    fun deleteInventoryItem(id: Int) {
        viewModelScope.launch {
            repository.deleteInventoryItem(id)
        }
    }

    fun updateStockQuantity(id: Int, quantity: Int) {
        viewModelScope.launch {
            repository.updateStockQuantity(id, quantity)
        }
    }

    fun seedSampleData() {
        viewModelScope.launch {
            // Seed Customers
            val c1Id = repository.insertCustomer(Customer(
                name = "امیر کریمی",
                phone = "09121111111",
                email = "amir@workshop.com",
                company = "کارگاه مبلمان افرا",
                type = "VIP",
                notes = "مشتری قدیمی - پرداخت‌های به موقع"
            ))
            
            val c2Id = repository.insertCustomer(Customer(
                name = "زهرا حسینی",
                phone = "09122222222",
                email = "zahra@store.com",
                company = "فروشگاه پوشاک زرین",
                type = "Regular",
                notes = "به دنبال خرید عمده زمستانه"
            ))

            val c3Id = repository.insertCustomer(Customer(
                name = "علیرضا نوری",
                phone = "09123333333",
                email = "nouri@workshop.com",
                company = "آتلیه طراحی نوریا",
                type = "Lead",
                notes = "مذاکره اولیه برای دکوراسیون داخلی"
            ))

            val c4Id = repository.insertCustomer(Customer(
                name = "صبا احمدی",
                phone = "09124444444",
                email = "saba@crafts.ir",
                company = "کارگاه صنایع دستی مهر",
                type = "Inactive",
                notes = "خرید سال گذشته انجام شده، نیاز به تماس مجدد"
            ))

            // Seed Transactions
            val now = System.currentTimeMillis()
            val oneDay = 24 * 60 * 60 * 1000L
            val oneMonth = 30 * oneDay

            repository.insertTransaction(Transaction(
                customerId = c1Id.toInt(),
                customerName = "امیر کریمی",
                title = "سفارش چوب راش و مبل سلطنتی",
                amount = 45000000.0,
                type = "Sale",
                status = "Paid",
                date = now - 5 * oneDay,
                description = "تسویه کامل فاز اول پروژه",
                quantity = 3,
                unitPrice = 15000000.0,
                discount = 1000000.0,
                tax = 1000000.0
            ))

            repository.insertTransaction(Transaction(
                customerId = c1Id.toInt(),
                customerName = "امیر کریمی",
                title = "طراحی و برش‌کاری CNC",
                amount = 12000000.0,
                type = "Sale",
                status = "Paid",
                date = now - 35 * oneDay,
                description = "خدمات مبل‌های سفارشی",
                quantity = 2,
                unitPrice = 6000000.0,
                discount = 0.0,
                tax = 0.0
            ))

            repository.insertTransaction(Transaction(
                customerId = c2Id.toInt(),
                customerName = "زهرا حسینی",
                title = "تولید کت و شلوار پاییزه",
                amount = 28000000.0,
                type = "Sale",
                status = "Paid",
                date = now - 15 * oneDay,
                description = "پیش‌پرداخت ۵۰ درصد انجام شد",
                quantity = 7,
                unitPrice = 4000000.0,
                discount = 500000.0,
                tax = 500000.0
            ))

            repository.insertTransaction(Transaction(
                customerId = c2Id.toInt(),
                customerName = "زهرا حسینی",
                title = "بسته‌بندی و ارسال سفارشات دفتری",
                amount = 3500000.0,
                type = "Sale",
                status = "Pending",
                date = now - 2 * oneDay,
                description = "در انتظار تسویه پس از تحویل بار",
                quantity = 1,
                unitPrice = 3500000.0,
                discount = 0.0,
                tax = 0.0
            ))

            repository.insertTransaction(Transaction(
                customerId = c3Id.toInt(),
                customerName = "علیرضا نوری",
                title = "مشاوره دکوراسیون و متریال دیزاین",
                amount = 7500000.0,
                type = "PreOrder",
                status = "Installment",
                date = now - 65 * oneDay,
                description = "قسط اول پرداخت شد",
                quantity = 1,
                unitPrice = 7500000.0,
                discount = 0.0,
                tax = 0.0
            ))

            repository.insertTransaction(Transaction(
                customerId = c1Id.toInt(),
                customerName = "امیر کریمی",
                title = "تهیه یراق‌آلات پیشرفته",
                amount = 15000000.0,
                type = "Sale",
                status = "Paid",
                date = now - 95 * oneDay,
                description = "خرید سال قبل",
                quantity = 10,
                unitPrice = 1500000.0,
                discount = 0.0,
                tax = 0.0
            ))

            // Seed FollowUpTasks
            repository.insertTask(FollowUpTask(
                customerId = c3Id.toInt(),
                customerName = "علیرضا نوری",
                title = "تماس برای ارسال کاتالوگ متریال‌ها",
                dueDate = now + oneDay,
                priority = "High"
            ))

            repository.insertTask(FollowUpTask(
                customerId = c2Id.toInt(),
                customerName = "زهرا حسینی",
                title = "پیگیری فاکتور تسویه ارسال کت و شلوار",
                dueDate = now + 2 * oneDay,
                priority = "Medium"
            ))

            repository.insertTask(FollowUpTask(
                customerId = c4Id.toInt(),
                customerName = "صبا احمدی",
                title = "تماس جهت آفر ویژه تابستانه",
                dueDate = now + 4 * oneDay,
                priority = "Low"
            ))

            repository.insertTask(FollowUpTask(
                customerId = c1Id.toInt(),
                customerName = "امیر کریمی",
                title = "ارسال لوح تقدیر مشتریان برتر فاز یک",
                dueDate = now - oneDay,
                isCompleted = true,
                priority = "Low"
            ))

            // Seed Inventory Items
            repository.insertInventoryItem(InventoryItem(
                name = "چوب راش گرجستان (مترمکعب)",
                category = "مواد اولیه",
                sku = "WD-BEECH-01",
                stockQuantity = 45,
                minStockLimit = 10,
                unitPrice = 18000000.0,
                location = "انبار A - قفسه ۱",
                description = "چوب راش وارداتی با کیفیت گرید A خشک‌کن رفته"
            ))

            repository.insertInventoryItem(InventoryItem(
                name = "پارچه مبلی چرم ترک (طاقه)",
                category = "مواد اولیه",
                sku = "TX-LEATH-04",
                stockQuantity = 12,
                minStockLimit = 5,
                unitPrice = 8500000.0,
                location = "انبار B - بخش طاقه‌ها",
                description = "چرم تنفسی ضد خش رنگ قهوه‌ای تیره"
            ))

            repository.insertInventoryItem(InventoryItem(
                name = "مبل تک‌نفره سلطنتی افرا",
                category = "محصول نهایی",
                sku = "FR-SOFA-01",
                stockQuantity = 3,
                minStockLimit = 4,
                unitPrice = 22000000.0,
                location = "سالن نمایش محصولات",
                description = "کلاف چوب راش رنگ استخوانی پارچه طلایی برجسته"
            ))

            repository.insertInventoryItem(InventoryItem(
                name = "اسفنج ۳۵ کیلویی ویژه (بلوک)",
                category = "مواد اولیه",
                sku = "SP-FOAM-35",
                stockQuantity = 8,
                minStockLimit = 15,
                unitPrice = 1200000.0,
                location = "انبار مواد شیمیایی و اسفنج",
                description = "اسفنج دانسیته بالا مناسب نشیمن مبل"
            ))

            repository.insertInventoryItem(InventoryItem(
                name = "میز ناهارخوری ۶ نفره کلاسیک",
                category = "محصول نهایی",
                sku = "FR-TAB-06",
                stockQuantity = 2,
                minStockLimit = 2,
                unitPrice = 35000000.0,
                location = "سالن نمایش محصولات",
                description = "صفحه روکش معرق پایه گلدانی دوبل"
            ))
        }
    }
}
