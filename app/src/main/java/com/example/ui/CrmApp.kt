package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.example.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.FileOutputStream
import java.io.File
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.data.Customer
import com.example.data.FollowUpTask
import com.example.data.Transaction
import com.example.data.InventoryItem
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

// Helper formatters
fun formatPrice(amount: Double): String {
    val formatter = NumberFormat.getNumberInstance(Locale("fa", "IR"))
    return "${formatter.format(amount)} تومان"
}

fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("yyyy/MM/dd", Locale("fa", "IR"))
    return sdf.format(Date(timestamp))
}

fun exportCustomersToCsv(context: Context, customers: List<Customer>) {
    try {
        val builder = java.lang.StringBuilder()
        builder.append('\ufeff') // Excel UTF-8 BOM
        builder.append("شناسه,نام مشتری,تلفن,ایمیل,نام فروشگاه/کارگاه,نوع مشتری,تاریخ ثبت,کل خرید (تومان),یادداشت\n")
        
        val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale("fa", "IR"))
        customers.forEach { customer ->
            val dateStr = sdf.format(Date(customer.registrationDate))
            val notesClean = customer.notes.replace("\n", " ").replace(",", " ")
            builder.append("${customer.id},${customer.name},${customer.phone},${customer.email},${customer.company},${customer.type},$dateStr,${customer.totalSpent},$notesClean\n")
        }
        
        val file = File(context.cacheDir, "customers_palex.csv")
        FileOutputStream(file).use { out ->
            out.write(builder.toString().toByteArray(Charsets.UTF_8))
        }
        
        InvoiceExporter.shareFile(context, file, "text/csv", "اشتراک‌گذاری خروجی اکسل مشتریان")
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "خطا در خروجی فایل: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

fun exportTransactionsToCsv(context: Context, transactions: List<Transaction>) {
    try {
        val builder = java.lang.StringBuilder()
        builder.append('\ufeff') // Excel UTF-8 BOM
        builder.append("شناسه معامله,نام مشتری,عنوان معامله,مبلغ کل (تومان),نوع,وضعیت,تاریخ معامله,تعداد,قیمت واحد,تخفیف,مالیات,توضیحات\n")
        
        val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale("fa", "IR"))
        transactions.forEach { trans ->
            val dateStr = sdf.format(Date(trans.date))
            val descClean = trans.description.replace("\n", " ").replace(",", " ")
            builder.append("${trans.id},${trans.customerName},${trans.title},${trans.amount},${trans.type},${trans.status},$dateStr,${trans.quantity},${trans.unitPrice},${trans.discount},${trans.tax},$descClean\n")
        }
        
        val file = File(context.cacheDir, "transactions_palex.csv")
        FileOutputStream(file).use { out ->
            out.write(builder.toString().toByteArray(Charsets.UTF_8))
        }
        
        InvoiceExporter.shareFile(context, file, "text/csv", "اشتراک‌گذاری خروجی اکسل معاملات")
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "خطا در خروجی فایل: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrmApp(viewModel: CrmViewModel = viewModel()) {
    val context = LocalContext.current
    
    // UI states from database
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val pendingTasks by viewModel.pendingTasks.collectAsStateWithLifecycle()
    val inventoryItems by viewModel.inventoryItems.collectAsStateWithLifecycle()

    // Navigation state
    var currentTab by remember { mutableStateOf("dashboard") }

    // Dialog & Detail states
    var selectedCustomerForDetail by remember { mutableStateOf<Customer?>(null) }
    var isAddCustomerOpen by remember { mutableStateOf(false) }
    var isAddTransactionOpen by remember { mutableStateOf(false) }
    var isAddTaskOpen by remember { mutableStateOf(false) }
    var isAddInventoryItemOpen by remember { mutableStateOf(false) }
    var customerToEdit by remember { mutableStateOf<Customer?>(null) }
    var inventoryItemToEdit by remember { mutableStateOf<InventoryItem?>(null) }

    // User Profile and Invoice customization triggers
    var isProfileOpen by remember { mutableStateOf(false) }
    var isSettingsOpen by remember { mutableStateOf(false) }

    val profileUsername by viewModel.profileUsername.collectAsStateWithLifecycle()
    val profileRole by viewModel.profileRole.collectAsStateWithLifecycle()
    val profilePhone by viewModel.profilePhone.collectAsStateWithLifecycle()
    val profileBio by viewModel.profileBio.collectAsStateWithLifecycle()

    val invoiceWorkshopName by viewModel.invoiceWorkshopName.collectAsStateWithLifecycle()
    val invoicePhone by viewModel.invoicePhone.collectAsStateWithLifecycle()
    val invoiceAddress by viewModel.invoiceAddress.collectAsStateWithLifecycle()
    val invoiceHeaderColor by viewModel.invoiceHeaderColor.collectAsStateWithLifecycle()
    val invoiceShowLogo by viewModel.invoiceShowLogo.collectAsStateWithLifecycle()
    val invoiceShowTerms by viewModel.invoiceShowTerms.collectAsStateWithLifecycle()
    val invoiceTerms1 by viewModel.invoiceTerms1.collectAsStateWithLifecycle()
    val invoiceTerms2 by viewModel.invoiceTerms2.collectAsStateWithLifecycle()
    val invoiceTerms3 by viewModel.invoiceTerms3.collectAsStateWithLifecycle()
    val invoiceTerms4 by viewModel.invoiceTerms4.collectAsStateWithLifecycle()
    val invoiceBorderStyle by viewModel.invoiceBorderStyle.collectAsStateWithLifecycle()
    val invoiceLogoPath by viewModel.invoiceLogoPath.collectAsStateWithLifecycle()
    val invoiceUseCustomLogo by viewModel.invoiceUseCustomLogo.collectAsStateWithLifecycle()
    val invoiceWatermarkText by viewModel.invoiceWatermarkText.collectAsStateWithLifecycle()
    val invoiceShowWatermark by viewModel.invoiceShowWatermark.collectAsStateWithLifecycle()
    val invoiceUseSignature by viewModel.invoiceUseSignature.collectAsStateWithLifecycle()
    val invoiceSignaturePath by viewModel.invoiceSignaturePath.collectAsStateWithLifecycle()
    val invoiceFontStyle by viewModel.invoiceFontStyle.collectAsStateWithLifecycle()
    val invoiceDefaultTaxRate by viewModel.invoiceDefaultTaxRate.collectAsStateWithLifecycle()
    val invoiceDefaultDiscountRate by viewModel.invoiceDefaultDiscountRate.collectAsStateWithLifecycle()

    // We force Persian RTL direction layout
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            topBar = {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.app_logo_custom_1783271400562),
                                contentDescription = "لوگو برنامه",
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            )
                            Column {
                                Text(
                                    text = "مدیریت کارگاه و مشتریان",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "نسخه مدرن CRM و انبارداری ویژه",
                                    fontSize = 10.sp,
                                    color = Color.Gray,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // User Profile Icon Button
                            IconButton(
                                onClick = { isProfileOpen = true },
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                        shape = CircleShape
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "پروفایل کاربری",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Settings Customization Icon Button
                            IconButton(
                                onClick = { isSettingsOpen = true },
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                        shape = CircleShape
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "تنظیمات فاکتور",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Theme switch
                            IconButton(
                                onClick = { viewModel.toggleTheme() },
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                        shape = CircleShape
                                    )
                            ) {
                                Icon(
                                    imageVector = if (viewModel.isDarkMode.collectAsStateWithLifecycle().value) Icons.Default.LightMode else Icons.Default.DarkMode,
                                    contentDescription = "تغییر تم برنامه",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            },
            bottomBar = {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .testTag("bottom_nav_bar"),
                    shape = RoundedCornerShape(24.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                ) {
                    NavigationBar(
                        containerColor = Color.Transparent,
                        tonalElevation = 0.dp,
                        modifier = Modifier.height(72.dp)
                    ) {
                        NavigationBarItem(
                            selected = currentTab == "dashboard",
                            onClick = { currentTab = "dashboard" },
                            icon = { Icon(if (currentTab == "dashboard") Icons.Filled.Dashboard else Icons.Outlined.Dashboard, contentDescription = "پیشخوان") },
                            label = { Text("پیشخوان", fontSize = 10.sp, fontWeight = if (currentTab == "dashboard") FontWeight.Bold else FontWeight.Normal) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        )
                        NavigationBarItem(
                            selected = currentTab == "customers",
                            onClick = { currentTab = "customers" },
                            icon = { Icon(if (currentTab == "customers") Icons.Filled.People else Icons.Outlined.People, contentDescription = "مشتریان") },
                            label = { Text("مشتریان", fontSize = 10.sp, fontWeight = if (currentTab == "customers") FontWeight.Bold else FontWeight.Normal) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        )
                        NavigationBarItem(
                            selected = currentTab == "deals",
                            onClick = { currentTab = "deals" },
                            icon = { Icon(if (currentTab == "deals") Icons.Filled.MonetizationOn else Icons.Outlined.MonetizationOn, contentDescription = "فروش‌ها") },
                            label = { Text("فروش‌ها", fontSize = 10.sp, fontWeight = if (currentTab == "deals") FontWeight.Bold else FontWeight.Normal) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        )
                        NavigationBarItem(
                            selected = currentTab == "inventory",
                            onClick = { currentTab = "inventory" },
                            icon = { Icon(if (currentTab == "inventory") Icons.Filled.Inventory else Icons.Outlined.Inventory, contentDescription = "انبارداری") },
                            label = { Text("انبارداری", fontSize = 10.sp, fontWeight = if (currentTab == "inventory") FontWeight.Bold else FontWeight.Normal) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        )
                        NavigationBarItem(
                            selected = currentTab == "tasks",
                            onClick = { currentTab = "tasks" },
                            icon = { Icon(if (currentTab == "tasks") Icons.Filled.Assignment else Icons.Outlined.Assignment, contentDescription = "یادآورها") },
                            label = { Text("یادآورها", fontSize = 10.sp, fontWeight = if (currentTab == "tasks") FontWeight.Bold else FontWeight.Normal) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        )
                    }
                }
            },
            floatingActionButton = {
                when (currentTab) {
                    "customers" -> {
                        ExtendedFloatingActionButton(
                            onClick = { isAddCustomerOpen = true },
                            icon = { Icon(Icons.Default.PersonAdd, contentDescription = "افزودن مشتری") },
                            text = { Text("مشتری جدید") },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.testTag("add_customer_fab")
                        )
                    }
                    "deals" -> {
                        ExtendedFloatingActionButton(
                            onClick = { isAddTransactionOpen = true },
                            icon = { Icon(Icons.Default.Add, contentDescription = "ثبت فروش") },
                            text = { Text("فروش جدید") },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.testTag("add_transaction_fab")
                        )
                    }
                    "inventory" -> {
                        ExtendedFloatingActionButton(
                            onClick = { isAddInventoryItemOpen = true },
                            icon = { Icon(Icons.Default.Add, contentDescription = "افزودن کالا") },
                            text = { Text("کالای جدید") },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.testTag("add_inventory_fab")
                        )
                    }
                    "tasks" -> {
                        ExtendedFloatingActionButton(
                            onClick = { isAddTaskOpen = true },
                            icon = { Icon(Icons.Default.NotificationAdd, contentDescription = "افزودن یادآور") },
                            text = { Text("یادآور جدید") },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.testTag("add_task_fab")
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.background,
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                            )
                        )
                    )
            ) {
                AnimatedContent(
                    targetState = currentTab,
                    transitionSpec = {
                        fadeIn(animationSpec = spring()) togetherWith fadeOut(animationSpec = spring())
                    },
                    label = "TabTransition"
                ) { targetTab ->
                    when (targetTab) {
                        "dashboard" -> DashboardScreen(
                            customers = customers,
                            transactions = transactions,
                            pendingTasks = pendingTasks,
                            viewModel = viewModel,
                            onTabRequest = { currentTab = it }
                        )
                        "customers" -> CustomersScreen(
                            customers = customers,
                            onCustomerClick = { selectedCustomerForDetail = it },
                            onEditCustomer = { customerToEdit = it }
                        )
                        "deals" -> DealsScreen(
                            transactions = transactions,
                            customers = customers,
                            onDeleteTransaction = { viewModel.deleteTransaction(it) },
                            onUpdateStatus = { id, status -> viewModel.updateTransactionStatus(id, status) }
                        )
                        "tasks" -> TasksScreen(
                            tasks = tasks,
                            onToggleStatus = { id, isComplete -> viewModel.toggleTaskStatus(id, isComplete) },
                            onDeleteTask = { viewModel.deleteTask(it) }
                        )
                        "inventory" -> InventoryScreen(
                            inventoryItems = inventoryItems,
                            onEditItem = { inventoryItemToEdit = it },
                            onDeleteItem = { viewModel.deleteInventoryItem(it) },
                            onUpdateStock = { id, quantity -> viewModel.updateStockQuantity(id, quantity) }
                        )
                    }
                }
            }
        }

        // --- Dialogs ---

        // Customer Detail Dialog (Sleek Bottom Sheet UI style inside a dialog for universal support)
        selectedCustomerForDetail?.let { customer ->
            CustomerDetailDialog(
                customer = customer,
                customerTransactions = transactions.filter { it.customerId == customer.id },
                customerTasks = tasks.filter { it.customerId == customer.id },
                onDismiss = { selectedCustomerForDetail = null },
                onDelete = {
                    viewModel.deleteCustomer(customer.id)
                    selectedCustomerForDetail = null
                    Toast.makeText(context, "مشتری حذف شد", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // Add / Edit Customer Dialog
        if (isAddCustomerOpen || customerToEdit != null) {
            val editingCustomer = customerToEdit
            AddEditCustomerDialog(
                customer = editingCustomer,
                onDismiss = {
                    isAddCustomerOpen = false
                    customerToEdit = null
                },
                onConfirm = { name, phone, email, company, type, notes ->
                    if (editingCustomer != null) {
                        viewModel.updateCustomer(
                            editingCustomer.copy(
                                name = name,
                                phone = phone,
                                email = email,
                                company = company,
                                type = type,
                                notes = notes
                            )
                        )
                        Toast.makeText(context, "اطلاعات مشتری بروزرسانی شد", Toast.LENGTH_SHORT).show()
                    } else {
                        viewModel.addCustomer(
                            Customer(
                                name = name,
                                phone = phone,
                                email = email,
                                company = company,
                                type = type,
                                notes = notes
                            )
                        )
                        Toast.makeText(context, "مشتری جدید با موفقیت ثبت شد", Toast.LENGTH_SHORT).show()
                    }
                    isAddCustomerOpen = false
                    customerToEdit = null
                }
            )
        }

        // Add Transaction Dialog
        if (isAddTransactionOpen) {
            AddTransactionDialog(
                customers = customers,
                defaultTaxPercent = invoiceDefaultTaxRate,
                defaultDiscountPercent = invoiceDefaultDiscountRate,
                onDismiss = { isAddTransactionOpen = false },
                onConfirm = { customerId, customerName, title, amount, type, status, description, quantity, unitPrice, discount, tax ->
                    viewModel.addTransaction(
                        Transaction(
                            customerId = customerId,
                            customerName = customerName,
                            title = title,
                            amount = amount,
                            type = type,
                            status = status,
                            description = description,
                            quantity = quantity,
                            unitPrice = unitPrice,
                            discount = discount,
                            tax = tax
                        )
                    )
                    Toast.makeText(context, "معامله جدید ثبت شد", Toast.LENGTH_SHORT).show()
                    isAddTransactionOpen = false
                }
            )
        }

        // Add Task Dialog
        if (isAddTaskOpen) {
            AddTaskDialog(
                customers = customers,
                onDismiss = { isAddTaskOpen = false },
                onConfirm = { customerId, customerName, title, priority, dueDate ->
                    viewModel.addTask(
                        FollowUpTask(
                            customerId = customerId,
                            customerName = customerName,
                            title = title,
                            priority = priority,
                            dueDate = dueDate
                        )
                    )
                    Toast.makeText(context, "یادآور جدید ثبت شد", Toast.LENGTH_SHORT).show()
                    isAddTaskOpen = false
                }
            )
        }

        // Add/Edit Inventory Item Dialog
        if (isAddInventoryItemOpen || inventoryItemToEdit != null) {
            AddInventoryItemDialog(
                editingItem = inventoryItemToEdit,
                onDismiss = {
                    isAddInventoryItemOpen = false
                    inventoryItemToEdit = null
                },
                onConfirm = { name, category, sku, stockQuantity, minStockLimit, unitPrice, location, description ->
                    if (inventoryItemToEdit != null) {
                        viewModel.updateInventoryItem(
                            inventoryItemToEdit!!.copy(
                                name = name,
                                category = category,
                                sku = sku,
                                stockQuantity = stockQuantity,
                                minStockLimit = minStockLimit,
                                unitPrice = unitPrice,
                                location = location,
                                description = description
                            )
                        )
                        Toast.makeText(context, "کالا بروزرسانی شد", Toast.LENGTH_SHORT).show()
                    } else {
                        viewModel.addInventoryItem(
                            InventoryItem(
                                name = name,
                                category = category,
                                sku = sku,
                                stockQuantity = stockQuantity,
                                minStockLimit = minStockLimit,
                                unitPrice = unitPrice,
                                location = location,
                                description = description
                            )
                        )
                        Toast.makeText(context, "کالای جدید ثبت شد", Toast.LENGTH_SHORT).show()
                    }
                    isAddInventoryItemOpen = false
                    inventoryItemToEdit = null
                }
            )
        }

        // User Profile Dialog instance
        if (isProfileOpen) {
            UserProfileDialog(
                currentUsername = profileUsername,
                currentRole = profileRole,
                currentPhone = profilePhone,
                currentBio = profileBio,
                onDismiss = { isProfileOpen = false },
                onConfirm = { username, role, phone, bio ->
                    viewModel.updateProfile(username, role, phone, bio)
                    Toast.makeText(context, "پروفایل با موفقیت بروزرسانی شد", Toast.LENGTH_SHORT).show()
                    isProfileOpen = false
                }
            )
        }

        // Invoice Settings Customization Dialog instance
        if (isSettingsOpen) {
            InvoiceSettingsDialog(
                currentWorkshopName = invoiceWorkshopName,
                currentPhone = invoicePhone,
                currentAddress = invoiceAddress,
                currentHeaderColor = invoiceHeaderColor,
                currentShowLogo = invoiceShowLogo,
                currentShowTerms = invoiceShowTerms,
                currentTerms1 = invoiceTerms1,
                currentTerms2 = invoiceTerms2,
                currentTerms3 = invoiceTerms3,
                currentTerms4 = invoiceTerms4,
                currentBorderStyle = invoiceBorderStyle,
                currentLogoPath = invoiceLogoPath,
                currentUseCustomLogo = invoiceUseCustomLogo,
                currentWatermarkText = invoiceWatermarkText,
                currentShowWatermark = invoiceShowWatermark,
                currentUseSignature = invoiceUseSignature,
                currentSignaturePath = invoiceSignaturePath,
                currentFontStyle = invoiceFontStyle,
                currentDefaultTaxRate = invoiceDefaultTaxRate,
                currentDefaultDiscountRate = invoiceDefaultDiscountRate,
                onDismiss = { isSettingsOpen = false },
                onConfirm = { wsName, ph, addr, hColor, sLogo, sTerms, t1, t2, t3, t4, bStyle, lPath, uCustomL, wText, sWMark, uSig, sPath, fStyle, dTax, dDiscount ->
                    viewModel.updateInvoiceSettings(
                        wsName, ph, addr, hColor, sLogo, sTerms, t1, t2, t3, t4, bStyle,
                        lPath, uCustomL, wText, sWMark, uSig, sPath, fStyle, dTax, dDiscount
                    )
                    Toast.makeText(context, "تنظیمات اختصاصی فاکتور ذخیره شد", Toast.LENGTH_SHORT).show()
                    isSettingsOpen = false
                }
            )
        }
    }
}

// ==================== DASHBOARD SCREEN ====================

@Composable
fun DashboardScreen(
    customers: List<Customer>,
    transactions: List<Transaction>,
    pendingTasks: List<FollowUpTask>,
    viewModel: CrmViewModel,
    onTabRequest: (String) -> Unit
) {
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val totalSales = transactions.filter { it.status == "Paid" }.sumOf { it.amount }
    val activeCount = customers.filter { it.type != "Inactive" }.size
    val totalTransactionsCount = transactions.size
    val pendingTasksCount = pendingTasks.size

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Header Section
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "پیشخوان کارگاه و فروشگاه",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "روند مشتریان و فروش امروز",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { viewModel.toggleTheme() },
                            modifier = Modifier
                                .background(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .size(40.dp)
                        ) {
                            Icon(
                                imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = "تغییر تم برنامه",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Button(
                            onClick = { viewModel.seedSampleData() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                contentColor = MaterialTheme.colorScheme.primary
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(40.dp)
                        ) {
                            Text("داده نمونه", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Metrics Grid (2x2)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard(
                        modifier = Modifier.weight(1f),
                        title = "کل فروش موفق",
                        value = formatPrice(totalSales),
                        icon = Icons.Default.MonetizationOn,
                        iconColor = Color(0xFF4CAF50),
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                    MetricCard(
                        modifier = Modifier.weight(1f),
                        title = "مشتریان فعال",
                        value = "$activeCount نفر",
                        icon = Icons.Default.People,
                        iconColor = Color(0xFF2196F3),
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard(
                        modifier = Modifier.weight(1f),
                        title = "کل قراردادها",
                        value = "$totalTransactionsCount معامله",
                        icon = Icons.Default.Handshake,
                        iconColor = Color(0xFF9C27B0),
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                    MetricCard(
                        modifier = Modifier.weight(1f),
                        title = "یادآورهای فعال",
                        value = "$pendingTasksCount کار",
                        icon = Icons.Default.NotificationImportant,
                        iconColor = Color(0xFFFF9800),
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                }
            }
        }

        // Monthly Sales Target Goal Progress Tracker Widget
        item {
            val monthlyGoal = 100000000.0 // 100 Million Tomans
            // Current month paid sales (last 30 days)
            val currentMonthSales = transactions.filter { 
                it.status == "Paid" && (System.currentTimeMillis() - it.date) < 30 * 24 * 60 * 60 * 1000L 
            }.sumOf { it.amount }
            
            val progress = (currentMonthSales / monthlyGoal).toFloat().coerceIn(0f, 1f)
            val animatedProgress by animateFloatAsState(
                targetValue = progress,
                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
                label = "SalesProgress"
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("monthly_sales_target_widget"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "هدف فروش ماهانه کارگاه",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = when {
                                    progress < 0.3f -> "شروع پرقدرت در این ماه! 🚀"
                                    progress < 0.7f -> "فروش عالی در جریان است، پرقدرت ادامه دهید! 💪"
                                    progress < 1.0f -> "تقریباً به هدف فروش رسیدیم! فقط کمی دیگر... 🔥"
                                    else -> "تبریک! هدف فروش ماهانه شکسته شد! 🎉🏆"
                                },
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Animated Progress Bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(5.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(animatedProgress)
                                .fillMaxHeight()
                                .background(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.tertiary
                                        )
                                    ),
                                    shape = RoundedCornerShape(5.dp)
                                )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "فروش ۳۰ روز اخیر: ${formatPrice(currentMonthSales)}",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "هدف: ${formatPrice(monthlyGoal)}",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Advanced Reporting: Custom Canvas Line Chart for Sales Trend
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "تحلیل پیشرفته فروش (۶ ماه اخیر)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Icon(
                            Icons.Default.TrendingUp,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    // Line Chart component drawn on Custom Canvas
                    val lineChartData = computeMonthlySales(transactions)
                    if (lineChartData.isNotEmpty()) {
                        SalesLineChart(data = lineChartData)
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("داده‌ای برای رسم نمودار وجود ندارد", color = Color.Gray, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Advanced Reporting: Custom Canvas Donut Chart for Customer Distribution
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "ترکیب و دسته بندی مشتریان",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    val segmentCounts = computeCustomerSegments(customers)
                    if (customers.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(140.dp)) {
                                CustomerDonutChart(segments = segmentCounts)
                            }
                            Spacer(modifier = Modifier.width(20.dp))
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                SegmentLegendItem(color = Color(0xFFFFD700), label = "VIP: ${segmentCounts["VIP"] ?: 0} نفر")
                                SegmentLegendItem(color = Color(0xFF4CAF50), label = "عادی: ${segmentCounts["Regular"] ?: 0} نفر")
                                SegmentLegendItem(color = Color(0xFF2196F3), label = "بالقوه (سرنخ): ${segmentCounts["Lead"] ?: 0} نفر")
                                SegmentLegendItem(color = Color(0xFF9E9E9E), label = "غیرفعال: ${segmentCounts["Inactive"] ?: 0} نفر")
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("داده‌ای برای تحلیل مشتریان موجود نیست", color = Color.Gray, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // پالکس: جعبه‌ابزار حسابداری و صادرات فایل اکسل و ماشین حساب هوشمند
        item {
            var grossAmountText by remember { mutableStateOf("") }
            var discountRate by remember { mutableStateOf(0f) }
            var taxRate by remember { mutableStateOf(9f) }

            val grossAmount = grossAmountText.toDoubleOrNull() ?: 0.0
            val calculatedDiscount = grossAmount * (discountRate / 100f)
            val calculatedTax = (grossAmount - calculatedDiscount) * (taxRate / 100f)
            val finalPayable = grossAmount - calculatedDiscount + calculatedTax

            val context = LocalContext.current

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "جعبه‌ابزار حسابداری و صادرات داده‌ها",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Export Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { exportCustomersToCsv(context, customers) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("خروجی اکسل مشتریان", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { exportTransactionsToCsv(context, transactions) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f),
                                contentColor = MaterialTheme.colorScheme.tertiary
                            )
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("خروجی اکسل معاملات", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    // Accounting Quick Calculator
                    Text(
                        text = "🧮 محاسبه‌گر سریع و هوشمند قیمت (تخفیف و مالیات)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )

                    OutlinedTextField(
                        value = grossAmountText,
                        onValueChange = { grossAmountText = it },
                        label = { Text("مبلغ ناخالص (تومان)", fontSize = 11.sp) },
                        placeholder = { Text("مثلاً ۱,۰۰۰,۰۰۰", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp),
                        trailingIcon = {
                            if (grossAmountText.isNotEmpty()) {
                                IconButton(onClick = { grossAmountText = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "پاک کردن", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    )

                    // Discount Presets
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("میزان درصد تخفیف: ${discountRate.toInt()}%", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(0, 5, 10, 15, 20).forEach { pct ->
                                OutlinedButton(
                                    onClick = { discountRate = pct.toFloat() },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(0.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (discountRate == pct.toFloat()) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("$pct%", fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    // Tax Presets
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("درصد مالیات (ارزش افزوده): ${taxRate.toInt()}%", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(0, 5, 9, 10, 15).forEach { pct ->
                                OutlinedButton(
                                    onClick = { taxRate = pct.toFloat() },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(0.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (taxRate == pct.toFloat()) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("$pct%", fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    // Calculation Results
                    if (grossAmount > 0.0) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("مبلغ ناخالص اولیه:", fontSize = 11.sp, color = Color.Gray)
                                    Text(formatPrice(grossAmount), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("تخفیف کسر شده (${discountRate.toInt()}%):", fontSize = 11.sp, color = Color.Gray)
                                    Text("- ${formatPrice(calculatedDiscount)}", fontSize = 11.sp, color = Color.Red, fontWeight = FontWeight.Bold)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("مالیات افزوده (${taxRate.toInt()}%):", fontSize = 11.sp, color = Color.Gray)
                                    Text("+ ${formatPrice(calculatedTax)}", fontSize = 11.sp, color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                                }
                                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("مبلغ خالص نهایی قابل دریافت:", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                                    Text(formatPrice(finalPayable), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Recent Pending Tasks Title
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "پیگیری‌های فوری امروز",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                TextButton(onClick = { onTabRequest("tasks") }) {
                    Text("مشاهده همه", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Task preview list
        if (pendingTasks.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("تبریک! کار پیگیری عقب‌مانده‌ای ندارید 🎉", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        } else {
            items(pendingTasks.take(3)) { task ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        color = when (task.priority) {
                                            "High" -> Color.Red
                                            "Medium" -> Color(0xFFFF9800)
                                            else -> Color.Blue
                                        },
                                        shape = CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = task.title,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "مشتری: ${task.customerName}",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                        Text(
                            text = formatDate(task.dueDate),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MetricCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: ImageVector,
    iconColor: Color,
    containerColor: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = title, fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(iconColor.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// Custom drawn Line Chart
@Composable
fun SalesLineChart(data: List<Pair<String, Double>>) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val maxVal = data.maxOfOrNull { it.second } ?: 1.0
    val gridLinesCount = 4

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .padding(top = 10.dp, bottom = 20.dp, start = 30.dp, end = 10.dp)
    ) {
        val width = size.width
        val height = size.height

        // Draw horizontal grid lines and value indicators
        val stepHeight = height / gridLinesCount
        for (i in 0..gridLinesCount) {
            val y = i * stepHeight
            drawLine(
                color = Color.LightGray.copy(alpha = 0.4f),
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 1f
            )
        }

        // Coordinate calculations
        val points = mutableListOf<Offset>()
        val stepWidth = width / (data.size - 1).coerceAtLeast(1)

        data.forEachIndexed { index, pair ->
            val x = index * stepWidth
            // Normalize y coordinate (invert because canvas coordinates start top-left)
            val normalizedY = height - ((pair.second / maxVal) * height * 0.85f).toFloat()
            points.add(Offset(x, normalizedY))
        }

        // Draw smooth path
        val path = Path().apply {
            if (points.isNotEmpty()) {
                moveTo(points[0].x, points[0].y)
                for (i in 1 until points.size) {
                    val p0 = points[i - 1]
                    val p1 = points[i]
                    // Bezier curves control points
                    val cp1X = p0.x + (p1.x - p0.x) / 2
                    val cp1Y = p0.y
                    val cp2X = p0.x + (p1.x - p0.x) / 2
                    val cp2Y = p1.y
                    cubicTo(cp1X, cp1Y, cp2X, cp2Y, p1.x, p1.y)
                }
            }
        }

        // Fill below path gradient
        val fillPath = Path().apply {
            addPath(path)
            if (points.isNotEmpty()) {
                lineTo(points.last().x, height)
                lineTo(points.first().x, height)
                close()
            }
        }
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(primaryColor.copy(alpha = 0.3f), Color.Transparent),
                startY = 0f,
                endY = height
            )
        )

        // Draw line
        drawPath(
            path = path,
            color = primaryColor,
            style = Stroke(width = 4f, cap = StrokeCap.Round)
        )

        // Draw data circles and labels
        points.forEachIndexed { index, offset ->
            drawCircle(
                color = secondaryColor,
                radius = 6f,
                center = offset
            )
            drawCircle(
                color = primaryColor,
                radius = 3f,
                center = offset
            )
        }
    }
    
    // Labels row
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 30.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        data.forEach { pair ->
            Text(
                text = pair.first,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
        }
    }
}

// Custom drawn Customer segments donut chart
@Composable
fun CustomerDonutChart(segments: Map<String, Int>) {
    val total = segments.values.sum().toFloat().coerceAtLeast(1f)
    
    val colors = listOf(
        Color(0xFFFFD700), // VIP
        Color(0xFF4CAF50), // Regular
        Color(0xFF2196F3), // Lead
        Color(0xFF9E9E9E)  // Inactive
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        var startAngle = 0f
        
        val segmentsList = listOf(
            segments["VIP"] ?: 0,
            segments["Regular"] ?: 0,
            segments["Lead"] ?: 0,
            segments["Inactive"] ?: 0
        )

        segmentsList.forEachIndexed { index, count ->
            if (count > 0) {
                val sweepAngle = (count / total) * 360f
                drawArc(
                    color = colors[index],
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    size = size,
                    style = Stroke(width = 24.dp.toPx(), cap = StrokeCap.Round)
                )
                startAngle += sweepAngle
            }
        }

        // Draw decorative center text if needed
        if (total > 1f) {
            drawCircle(
                color = Color.Transparent,
                radius = (size.width / 2) - 15.dp.toPx()
            )
        }
    }
}

@Composable
fun SegmentLegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(text = label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// Data processors for canvas charts
private fun computeMonthlySales(transactions: List<Transaction>): List<Pair<String, Double>> {
    // We group by month and display last 5-6 Persian months
    // Here we sort transactions, find labels, mock monthly buckets based on sample dates
    val now = System.currentTimeMillis()
    val oneMonth = 30 * 24 * 60 * 60 * 1000L
    
    val labels = listOf("اسفند", "فروردین", "اردیبهشت", "خرداد", "تیر")
    val sales = DoubleArray(5)

    transactions.filter { it.status == "Paid" }.forEach { tx ->
        val diff = now - tx.date
        val monthIdx = (diff / oneMonth).toInt()
        val index = 4 - monthIdx
        if (index in 0..4) {
            sales[index] += tx.amount
        }
    }

    return labels.mapIndexed { index, label ->
        label to sales[index]
    }
}

private fun computeCustomerSegments(customers: List<Customer>): Map<String, Int> {
    val map = mutableMapOf("VIP" to 0, "Regular" to 0, "Lead" to 0, "Inactive" to 0)
    customers.forEach {
        val type = it.type
        if (map.containsKey(type)) {
            map[type] = (map[type] ?: 0) + 1
        }
    }
    return map
}


// ==================== CUSTOMERS SCREEN ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomersScreen(
    customers: List<Customer>,
    onCustomerClick: (Customer) -> Unit,
    onEditCustomer: (Customer) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Search & Minimal Header
        Text(
            text = "بانک مشتریان فروشگاه",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("جستجوی مشتری بر اساس نام یا کارگاه...", fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("customer_search_input"),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            ),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Horizontal filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedFilter == "All",
                onClick = { selectedFilter = "All" },
                label = { Text("همه", fontSize = 12.sp) }
            )
            FilterChip(
                selected = selectedFilter == "VIP",
                onClick = { selectedFilter = "VIP" },
                label = { Text("VIP ⭐", fontSize = 12.sp) }
            )
            FilterChip(
                selected = selectedFilter == "Regular",
                onClick = { selectedFilter = "Regular" },
                label = { Text("عادی", fontSize = 12.sp) }
            )
            FilterChip(
                selected = selectedFilter == "Lead",
                onClick = { selectedFilter = "Lead" },
                label = { Text("بالقوه", fontSize = 12.sp) }
            )
            FilterChip(
                selected = selectedFilter == "Inactive",
                onClick = { selectedFilter = "Inactive" },
                label = { Text("غیرفعال", fontSize = 12.sp) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Customers list
        val filteredCustomers = customers.filter {
            val matchesSearch = it.name.contains(searchQuery, ignoreCase = true) || 
                                it.company.contains(searchQuery, ignoreCase = true)
            val matchesFilter = selectedFilter == "All" || it.type == selectedFilter
            matchesSearch && matchesFilter
        }

        if (filteredCustomers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Group,
                        contentDescription = null,
                        modifier = Modifier.size(60.dp),
                        tint = Color.LightGray
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("هیچ مشتری یافت نشد", color = Color.Gray, fontSize = 14.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredCustomers, key = { it.id }) { customer ->
                    CustomerListItem(
                        customer = customer,
                        onClick = { onCustomerClick(customer) },
                        onEdit = { onEditCustomer(customer) }
                    )
                }
            }
        }
    }
}

@Composable
fun CustomerListItem(
    customer: Customer,
    onClick: () -> Unit,
    onEdit: () -> Unit
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("customer_item_${customer.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Circle Initials Avatar
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(
                        color = when (customer.type) {
                            "VIP" -> Color(0xFFFFD700).copy(alpha = 0.15f)
                            "Regular" -> Color(0xFF4CAF50).copy(alpha = 0.15f)
                            "Lead" -> Color(0xFF2196F3).copy(alpha = 0.15f)
                            else -> Color.Gray.copy(alpha = 0.15f)
                        },
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = customer.name.take(1),
                    color = when (customer.type) {
                        "VIP" -> Color(0xFFD4AF37)
                        "Regular" -> Color(0xFF2E7D32)
                        "Lead" -> Color(0xFF1565C0)
                        else -> Color.DarkGray
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Text Info
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = customer.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    CustomerBadge(type = customer.type)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (customer.company.isNotEmpty()) customer.company else "کارگاه/فروشگاه شخصی",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Quick Call & Message Actions
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${customer.phone}"))
                        context.startActivity(intent)
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Call,
                        contentDescription = "تماس تلفنی",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${customer.phone}"))
                        context.startActivity(intent)
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Sms,
                        contentDescription = "ارسال پیامک",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "ویرایش مشتری",
                        tint = Color.Gray,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CustomerBadge(type: String) {
    val text = when (type) {
        "VIP" -> "VIP ⭐"
        "Regular" -> "عادی"
        "Lead" -> "بالقوه"
        else -> "غیرفعال"
    }
    val badgeColor = when (type) {
        "VIP" -> Color(0xFFFFD700)
        "Regular" -> Color(0xFF4CAF50)
        "Lead" -> Color(0xFF2196F3)
        else -> Color.Gray
    }

    Box(
        modifier = Modifier
            .background(badgeColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text = text, color = badgeColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}


// ==================== DEALS SCREEN ====================

@Composable
fun PreInvoiceDialog(
    deal: Transaction,
    customer: Customer?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val config = remember { InvoiceExporter.loadConfig(context) }
    val subtotal = deal.quantity * deal.unitPrice

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "پیش‌فاکتور فروش رسمی",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "بستن")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header Design
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("کارگاه و فروشگاه مدرن CRM", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("پیش‌فاکتور", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("تاریخ صدور: ${formatDate(deal.date)}", fontSize = 11.sp, color = Color.Gray)
                            Text("شماره فاکتور: #${deal.id + 1000}", fontSize = 11.sp, color = Color.Gray)
                        }
                    }
                }

                // Customer Details Section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("مشخصات خریدار / کارفرما", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("نام مشتری: ${deal.customerName}", fontSize = 11.sp)
                        Text("شرکت/کارگاه: ${customer?.company ?: "شخصی"}", fontSize = 11.sp)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("تلفن تماس: ${customer?.phone ?: "نامشخص"}", fontSize = 11.sp)
                        Text("نوع مشتری: ${customer?.type ?: "عادی"}", fontSize = 11.sp)
                    }
                }

                // Invoice Table List
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                ) {
                    // Table Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("شرح کالا یا خدمات", modifier = Modifier.weight(2f), fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Right)
                        Text("تعداد", modifier = Modifier.weight(0.7f), fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        Text("قیمت واحد", modifier = Modifier.weight(1.5f), fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        Text("جمع نهایی", modifier = Modifier.weight(1.5f), fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Left)
                    }
                    
                    // Table Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(deal.title, modifier = Modifier.weight(2f), fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(deal.quantity.toString(), modifier = Modifier.weight(0.7f), fontSize = 11.sp, textAlign = TextAlign.Center)
                        Text(formatPrice(deal.unitPrice), modifier = Modifier.weight(1.5f), fontSize = 10.sp, textAlign = TextAlign.Center)
                        Text(formatPrice(subtotal), modifier = Modifier.weight(1.5f), fontSize = 10.sp, textAlign = TextAlign.Left)
                    }
                }

                // Billing calculations
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("جمع ناخالص کالاها:", fontSize = 11.sp, color = Color.Gray)
                        Text(formatPrice(subtotal), fontSize = 11.sp)
                    }
                    if (deal.discount > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("تخفیف ویژه:", fontSize = 11.sp, color = Color(0xFFD84315))
                            Text("- ${formatPrice(deal.discount)}", fontSize = 11.sp, color = Color(0xFFD84315))
                        }
                    }
                    if (deal.tax > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("مالیات بر ارزش افزوده:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("+ ${formatPrice(deal.tax)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                            .padding(vertical = 4.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("مبلغ خالص نهایی:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text(
                            formatPrice(deal.amount),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Standard Note Footer
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text("بندها و توضیحات قرارداد:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    Text(
                        text = deal.description.ifEmpty { "این پیش‌فاکتور جهت برآورد مالی صادر شده و فاقد اعتبار معاملاتی تعهدآور فوری است." },
                        fontSize = 10.sp,
                        color = Color.Gray,
                        lineHeight = 14.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Divider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "اشتراک‌گذاری و گرفتن خروجی:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // PDF Button
                    Button(
                        onClick = {
                            try {
                                val file = InvoiceExporter.generatePdf(context, deal, customer)
                                InvoiceExporter.shareFile(context, file, "application/pdf", "پیش‌فاکتور رسمی - #${deal.id + 1000}")
                            } catch (e: Exception) {
                                Toast.makeText(context, "خطا در ساخت PDF: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)), // Red for PDF
                        modifier = Modifier.weight(1f).testTag("export_pdf_button"),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                            Text("خروجی PDF", fontSize = 11.sp, color = Color.White)
                        }
                    }

                    // Image Button
                    Button(
                        onClick = {
                            try {
                                val file = InvoiceExporter.generateImage(context, deal, customer)
                                InvoiceExporter.shareFile(context, file, "image/jpeg", "پیش‌فاکتور رسمی - #${deal.id + 1000}")
                            } catch (e: Exception) {
                                Toast.makeText(context, "خطا در ساخت عکس: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)), // Blue for Image
                        modifier = Modifier.weight(1f).testTag("export_image_button"),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                            Text("خروجی عکس", fontSize = 11.sp, color = Color.White)
                        }
                    }

                    // Text Share Button
                    Button(
                        onClick = {
                            val shareText = """
                                🧾 *پیش‌فاکتور فروش رسمی ${config.workshopName}*
                                ----------------------------------
                                تاریخ صدور: ${formatDate(deal.date)}
                                شماره فاکتور: #${deal.id + 1000}
                                خریدار: ${deal.customerName}
                                تلفن خریدار: ${customer?.phone ?: "-"}
                                شرکت: ${customer?.company ?: "شخصی"}
                                ----------------------------------
                                شرح: ${deal.title}
                                تعداد: ${deal.quantity} عدد
                                قیمت واحد: ${formatPrice(deal.unitPrice)}
                                ----------------------------------
                                جمع ناخالص: ${formatPrice(subtotal)}
                                تخفیف: ${formatPrice(deal.discount)}
                                مالیات: ${formatPrice(deal.tax)}
                                *مبلغ نهایی قابل پرداخت: ${formatPrice(deal.amount)}*
                                ----------------------------------
                                وضعیت فاکتور: ${when (deal.status) {
                                    "Paid" -> "تسویه کامل شده"
                                    "Pending" -> "در انتظار پرداخت"
                                    else -> "خرید قسطی"
                                }}
                                ----------------------------------
                                با تشکر از همکاری شما 🙏✨
                            """.trimIndent()

                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "پیش فاکتور")
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            }
                            context.startActivity(Intent.createChooser(intent, "ارسال پیش فاکتور"))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.weight(1f).testTag("export_text_button"),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("اشتراک متنی", fontSize = 11.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().testTag("close_invoice_dialog_button")
            ) {
                Text("بستن پیش‌فاکتور")
            }
        }
    )
}

@Composable
fun DealsScreen(
    transactions: List<Transaction>,
    customers: List<Customer>,
    onDeleteTransaction: (Transaction) -> Unit,
    onUpdateStatus: (Int, String) -> Unit
) {
    var selectedDealForInvoice by remember { mutableStateOf<Transaction?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "فروش‌ها و معاملات مالی",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(14.dp))

        if (transactions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.AssignmentLate,
                        contentDescription = null,
                        modifier = Modifier.size(60.dp),
                        tint = Color.LightGray
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("هنوز معامله‌ای ثبت نشده است", color = Color.Gray, fontSize = 14.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(transactions, key = { it.id }) { deal ->
                    val matchingCustomer = customers.find { it.id == deal.customerId }
                    DealListItem(
                        deal = deal,
                        customer = matchingCustomer,
                        onDelete = { onDeleteTransaction(deal) },
                        onUpdateStatus = onUpdateStatus,
                        onInvoiceClick = { selectedDealForInvoice = deal }
                    )
                }
            }
        }
    }

    selectedDealForInvoice?.let { deal ->
        val matchingCustomer = customers.find { it.id == deal.customerId }
        PreInvoiceDialog(
            deal = deal,
            customer = matchingCustomer,
            onDismiss = { selectedDealForInvoice = null }
        )
    }
}

@Composable
fun DealListItem(
    deal: Transaction,
    customer: Customer?,
    onDelete: () -> Unit,
    onUpdateStatus: (Int, String) -> Unit,
    onInvoiceClick: () -> Unit
) {
    var isConfirmDeleteOpen by remember { mutableStateOf(false) }
    var isStatusMenuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("deal_item_${deal.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        color = when (deal.status) {
                            "Paid" -> Color(0xFF4CAF50).copy(alpha = 0.12f)
                            "Pending" -> Color(0xFFFF9800).copy(alpha = 0.12f)
                            else -> Color(0xFF9C27B0).copy(alpha = 0.12f)
                        },
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (deal.type) {
                        "PreOrder" -> Icons.Default.PendingActions
                        else -> Icons.Default.MonetizationOn
                    },
                    contentDescription = null,
                    tint = when (deal.status) {
                        "Paid" -> Color(0xFF4CAF50)
                        "Pending" -> Color(0xFFFF9800)
                        else -> Color(0xFF9C27B0)
                    },
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = deal.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "مشتری: ${deal.customerName}",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                    Box(
                        modifier = Modifier
                            .size(3.dp)
                            .background(Color.Gray, CircleShape)
                    )
                    Text(
                        text = formatDate(deal.date),
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                    Box(
                        modifier = Modifier
                            .size(3.dp)
                            .background(Color.Gray, CircleShape)
                    )
                    Text(
                        text = "${deal.quantity} عدد",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatPrice(deal.amount),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = when (deal.status) {
                        "Paid" -> Color(0xFF2E7D32)
                        else -> Color(0xFFD84315)
                    }
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Quick-Action Status Click Dropdown Toggle
                    Box {
                        Box(
                            modifier = Modifier
                                .background(
                                    color = when (deal.status) {
                                        "Paid" -> Color(0xFF4CAF50).copy(alpha = 0.15f)
                                        "Pending" -> Color(0xFFFF9800).copy(alpha = 0.15f)
                                        else -> Color(0xFF9C27B0).copy(alpha = 0.15f)
                                    },
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .clickable { isStatusMenuExpanded = true }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = when (deal.status) {
                                        "Paid" -> "تسویه شده"
                                        "Pending" -> "در انتظار پرداخت"
                                        else -> "قسطی"
                                    },
                                    color = when (deal.status) {
                                        "Paid" -> Color(0xFF4CAF50)
                                        "Pending" -> Color(0xFFFF9800)
                                        else -> Color(0xFF9C27B0)
                                    },
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "تغییر وضعیت فاکتور",
                                    tint = when (deal.status) {
                                        "Paid" -> Color(0xFF4CAF50)
                                        "Pending" -> Color(0xFFFF9800)
                                        else -> Color(0xFF9C27B0)
                                    },
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = isStatusMenuExpanded,
                            onDismissRequest = { isStatusMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("تسویه کامل شده", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50)) },
                                onClick = {
                                    onUpdateStatus(deal.id, "Paid")
                                    isStatusMenuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("در انتظار پرداخت", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF9800)) },
                                onClick = {
                                    onUpdateStatus(deal.id, "Pending")
                                    isStatusMenuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("خرید قسطی / تعهدی", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF9C27B0)) },
                                onClick = {
                                    onUpdateStatus(deal.id, "Installment")
                                    isStatusMenuExpanded = false
                                }
                            )
                        }
                    }

                    // Pre-invoice Issuance Icon Button
                    IconButton(
                        onClick = onInvoiceClick,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f), CircleShape)
                            .size(26.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ReceiptLong,
                            contentDescription = "صدور پیش فاکتور",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    // Delete Button
                    IconButton(
                        onClick = { isConfirmDeleteOpen = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "حذف معامله",
                            tint = Color.LightGray,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }

    if (isConfirmDeleteOpen) {
        AlertDialog(
            onDismissRequest = { isConfirmDeleteOpen = false },
            title = { Text("حذف معامله") },
            text = { Text("آیا از حذف این معامله مالی اطمینان دارید؟ کل هزینه‌های پرداختی مشتری دوباره محاسبه می‌شود.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    isConfirmDeleteOpen = false
                }) {
                    Text("بله، حذف شود", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { isConfirmDeleteOpen = false }) {
                    Text("انصراف")
                }
            }
        )
    }
}


// ==================== TASKS SCREEN ====================

@Composable
fun TasksScreen(
    tasks: List<FollowUpTask>,
    onToggleStatus: (Int, Boolean) -> Unit,
    onDeleteTask: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "کارها و پیگیری‌های زمان‌بندی‌شده",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(14.dp))

        if (tasks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.PlaylistAddCheck,
                        contentDescription = null,
                        modifier = Modifier.size(60.dp),
                        tint = Color.LightGray
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("هیچ یادآوری تعریف نشده است", color = Color.Gray, fontSize = 14.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(tasks, key = { it.id }) { task ->
                    TaskListItem(
                        task = task,
                        onCheckChanged = { onToggleStatus(task.id, it) },
                        onDelete = { onDeleteTask(task.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun TaskListItem(
    task: FollowUpTask,
    onCheckChanged: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("task_item_${task.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (task.isCompleted) MaterialTheme.colorScheme.surface.copy(alpha = 0.5f) 
                             else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = task.isCompleted,
                onCheckedChange = onCheckChanged,
                modifier = Modifier.testTag("task_checkbox_${task.id}")
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = if (task.isCompleted) Color.Gray else MaterialTheme.colorScheme.onSurface,
                    style = if (task.isCompleted) LocalTextStyle.current.copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough)
                            else LocalTextStyle.current
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "مشتری: ${task.customerName}",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .background(
                                color = when (task.priority) {
                                    "High" -> Color.Red.copy(alpha = 0.15f)
                                    "Medium" -> Color(0xFFFF9800).copy(alpha = 0.15f)
                                    else -> Color.Blue.copy(alpha = 0.15f)
                                },
                                shape = RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = when (task.priority) {
                                "High" -> "فوری"
                                "Medium" -> "متوسط"
                                else -> "کم"
                            },
                            color = when (task.priority) {
                                "High" -> Color.Red
                                "Medium" -> Color(0xFFFF9800)
                                else -> Color.Blue
                            },
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatDate(task.dueDate),
                    fontSize = 11.sp,
                    color = if (task.isCompleted) Color.Gray else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "حذف کار",
                        tint = Color.LightGray,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}


// ==================== DIALOG COMPONENT IMPLEMENTATIONS ====================

// Elegant Customer Details Bottom Sheet / Dialog
@Composable
fun CustomerDetailDialog(
    customer: Customer,
    customerTransactions: List<Transaction>,
    customerTasks: List<FollowUpTask>,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    var isConfirmDeleteOpen by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = customer.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                CustomerBadge(type = customer.type)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Phone, Email, Company
                Text("📞 شماره تماس: ${customer.phone}", fontSize = 13.sp)
                Text("✉️ ایمیل: ${if (customer.email.isNotEmpty()) customer.email else "ثبت نشده"}", fontSize = 13.sp)
                Text("🏭 کارگاه/فروشگاه: ${if (customer.company.isNotEmpty()) customer.company else "شخصی"}", fontSize = 13.sp)
                Text("💰 کل خرید‌های تسویه شده: ${formatPrice(customer.totalSpent)}", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                
                if (customer.notes.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "📝 توضیحات پرونده:\n${customer.notes}",
                        fontSize = 12.sp,
                        color = Color.DarkGray,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.LightGray.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    )
                }

                // Recent Transaction History
                Spacer(modifier = Modifier.height(8.dp))
                Text("📊 سابقه تراکنش‌ها و خریدها:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                if (customerTransactions.isEmpty()) {
                    Text("هیچ تراکنشی برای این مشتری یافت نشد.", fontSize = 12.sp, color = Color.Gray)
                } else {
                    customerTransactions.forEach { tx ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "• ${tx.title}", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text(
                                text = formatPrice(tx.amount),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (tx.status == "Paid") Color(0xFF2E7D32) else Color(0xFFD84315)
                            )
                        }
                    }
                }

                // Upcoming Tasks
                Spacer(modifier = Modifier.height(8.dp))
                Text("🔔 یادآورها و کارهای آینده:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary)
                if (customerTasks.isEmpty()) {
                    Text("هیچ پیگیری زمان‌بندی‌شده‌ای تعریف نشده است.", fontSize = 12.sp, color = Color.Gray)
                } else {
                    customerTasks.forEach { task ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${if (task.isCompleted) "✓" else "•"} ${task.title}",
                                fontSize = 12.sp,
                                color = if (task.isCompleted) Color.Gray else Color.Black,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(text = formatDate(task.dueDate), fontSize = 11.sp, color = Color.Gray)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { isConfirmDeleteOpen = true }) {
                Text("حذف پرونده مشتری", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("بستن")
            }
        }
    )

    if (isConfirmDeleteOpen) {
        AlertDialog(
            onDismissRequest = { isConfirmDeleteOpen = false },
            title = { Text("حذف مشتری") },
            text = { Text("آیا مطمئن هستید که می‌خواهید پرونده مشتری را حذف کنید؟ این عمل غیرقابل بازگشت است.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    isConfirmDeleteOpen = false
                }) {
                    Text("حذف کامل", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { isConfirmDeleteOpen = false }) {
                    Text("انصراف")
                }
            }
        )
    }
}

// Add or Edit Customer Form Dialog
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditCustomerDialog(
    customer: Customer? = null,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String, String, String) -> Unit
) {
    var name by remember { mutableStateOf(customer?.name ?: "") }
    var phone by remember { mutableStateOf(customer?.phone ?: "") }
    var email by remember { mutableStateOf(customer?.email ?: "") }
    var company by remember { mutableStateOf(customer?.company ?: "") }
    var type by remember { mutableStateOf(customer?.type ?: "Regular") }
    var notes by remember { mutableStateOf(customer?.notes ?: "") }

    var isTypeMenuExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (customer != null) "ویرایش پرونده مشتری" else "افزودن مشتری جدید", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("نام و نام خانوادگی *", fontSize = 12.sp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("customer_form_name"),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("شماره همراه *", fontSize = 12.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("customer_form_phone"),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = company,
                    onValueChange = { company = it },
                    label = { Text("نام فروشگاه یا کارگاه", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("ایمیل", fontSize = 12.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                // Custom Dropdown Menu for Customer Type Select
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { isTypeMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "گروه مشتری: " + when (type) {
                                "VIP" -> "عالی (VIP) ⭐"
                                "Regular" -> "عادی"
                                "Lead" -> "بالقوه (سرنخ)"
                                else -> "غیرفعال"
                            },
                            fontSize = 12.sp
                        )
                    }
                    DropdownMenu(
                        expanded = isTypeMenuExpanded,
                        onDismissRequest = { isTypeMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("عادی") },
                            onClick = { type = "Regular"; isTypeMenuExpanded = false }
                        )
                        DropdownMenuItem(
                            text = { Text("عالی (VIP) ⭐") },
                            onClick = { type = "VIP"; isTypeMenuExpanded = false }
                        )
                        DropdownMenuItem(
                            text = { Text("بالقوه (سرنخ)") },
                            onClick = { type = "Lead"; isTypeMenuExpanded = false }
                        )
                        DropdownMenuItem(
                            text = { Text("غیرفعال") },
                            onClick = { type = "Inactive"; isTypeMenuExpanded = false }
                        )
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("توضیحات تکمیلی", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotEmpty() && phone.isNotEmpty()) {
                        onConfirm(name, phone, email, company, type, notes)
                    }
                },
                modifier = Modifier.testTag("customer_form_submit")
            ) {
                Text("ثبت اطلاعات")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("انصراف")
            }
        }
    )
}

// Add Transaction Form Dialog
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionDialog(
    customers: List<Customer>,
    defaultTaxPercent: Float,
    defaultDiscountPercent: Float,
    onDismiss: () -> Unit,
    onConfirm: (Int, String, String, Double, String, String, String, Int, Double, Double, Double) -> Unit
) {
    val items = remember { mutableStateListOf<InvoiceItemDetail>() }

    // Current item inputs
    var itemTitle by remember { mutableStateOf("") }
    var itemUnitPriceStr by remember { mutableStateOf("") }
    var itemQuantity by remember { mutableIntStateOf(1) }

    var discountStr by remember { mutableStateOf("") }
    var taxStr by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("Sale") } // Sale or PreOrder
    var status by remember { mutableStateOf("Paid") } // Paid, Pending, Installment
    var description by remember { mutableStateOf("") }

    var selectedCustomerIndex by remember { mutableIntStateOf(0) }
    var isCustomerMenuExpanded by remember { mutableStateOf(false) }
    var isStatusMenuExpanded by remember { mutableStateOf(false) }

    // Live calculations
    val subtotal = items.sumOf { it.quantity * it.unitPrice }
    val discount = discountStr.toDoubleOrNull() ?: 0.0
    val tax = taxStr.toDoubleOrNull() ?: 0.0
    val totalAmount = (subtotal - discount + tax).coerceAtLeast(0.0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ثبت فاکتور و معامله جدید (چندکالایی)", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (customers.isEmpty()) {
                    Text("ابتدا باید حداقل یک مشتری در سیستم ثبت کنید.", color = Color.Red, fontSize = 12.sp)
                } else {
                    // Dropdown for Customer Choice
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { isCustomerMenuExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(text = "مشتری: ${customers[selectedCustomerIndex].name}", fontSize = 12.sp)
                        }
                        DropdownMenu(
                            expanded = isCustomerMenuExpanded,
                            onDismissRequest = { isCustomerMenuExpanded = false }
                        ) {
                            customers.forEachIndexed { index, cust ->
                                DropdownMenuItem(
                                    text = { Text(cust.name + " (" + cust.company + ")") },
                                    onClick = {
                                        selectedCustomerIndex = index
                                        isCustomerMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Header for Invoice Items List
                Text(
                    text = "لیست اقلام فاکتور (${items.size} قلم)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                // Items list display inside a clean bordered box
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                ) {
                    if (items.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "هیچ کالایی اضافه نشده است.\nلطفاً از فرم زیر کالا را پر کرده و اضافه کنید.",
                                fontSize = 11.sp,
                                color = Color.Gray,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items.forEachIndexed { index, item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            MaterialTheme.colorScheme.surface,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${index + 1}. ${item.title}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                        Text(
                                            text = "تعداد: ${item.quantity} | قیمت واحد: ${formatPrice(item.unitPrice)}",
                                            fontSize = 10.sp,
                                            color = Color.Gray
                                        )
                                    }
                                    IconButton(
                                        onClick = { items.removeAt(index) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "حذف کالا",
                                            tint = Color.Red,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Add New Item Fields Box
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "افزودن کالا یا خدمات جدید",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary
                        )

                        OutlinedTextField(
                            value = itemTitle,
                            onValueChange = { itemTitle = it },
                            label = { Text("عنوان کالا یا خدمات جدید", fontSize = 11.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = itemUnitPriceStr,
                                onValueChange = { itemUnitPriceStr = it },
                                label = { Text("قیمت واحد (تومان)", fontSize = 11.sp) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1.2f),
                                shape = RoundedCornerShape(8.dp)
                            )

                            // Numerical Stepper for quantity
                            Row(
                                modifier = Modifier
                                    .background(
                                        MaterialTheme.colorScheme.surface,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outline,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                IconButton(
                                    onClick = { if (itemQuantity > 1) itemQuantity-- },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Remove, "کاهش", modifier = Modifier.size(12.dp))
                                }
                                Text(
                                    text = itemQuantity.toString(),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                IconButton(
                                    onClick = { itemQuantity++ },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Add, "افزایش", modifier = Modifier.size(12.dp))
                                }
                            }
                        }

                        Button(
                            onClick = {
                                val price = itemUnitPriceStr.toDoubleOrNull() ?: 0.0
                                if (itemTitle.isNotBlank() && price > 0.0) {
                                    items.add(InvoiceItemDetail(itemTitle, itemQuantity, price))
                                    // Reset fields
                                    itemTitle = ""
                                    itemUnitPriceStr = ""
                                    itemQuantity = 1
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Add, "افزودن کالا", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("افزودن به لیست اقلام", fontSize = 11.sp)
                        }
                    }
                }

                // Discount and Tax Fields
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = discountStr,
                            onValueChange = { discountStr = it },
                            label = { Text("تخفیف کل (تومان)", fontSize = 11.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        if (defaultDiscountPercent > 0f) {
                            Spacer(modifier = Modifier.height(2.dp))
                            TextButton(
                                onClick = {
                                    val calcDiscount = (subtotal * (defaultDiscountPercent / 100f)).toInt()
                                    discountStr = calcDiscount.toString()
                                },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("محاسبه ${defaultDiscountPercent.toInt()}% تخفیف پیش‌فرض", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = taxStr,
                            onValueChange = { taxStr = it },
                            label = { Text("مالیات کل (تومان)", fontSize = 11.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        TextButton(
                            onClick = {
                                val rate = if (defaultTaxPercent > 0f) defaultTaxPercent else 9f
                                val calcTax = (subtotal * (rate / 100f)).toInt()
                                taxStr = calcTax.toString()
                            },
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            val rate = if (defaultTaxPercent > 0f) defaultTaxPercent else 9f
                            Text("محاسبه ${rate.toInt()}% مالیات", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Dynamic Live Calculation Box
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("مبلغ ناخالص اقلام:", fontSize = 11.sp, color = Color.Gray)
                            Text(formatPrice(subtotal), fontSize = 11.sp, color = Color.Gray)
                        }
                        if (discount > 0) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("تخفیف اعمال‌شده:", fontSize = 11.sp, color = Color(0xFFD84315))
                                Text("- ${formatPrice(discount)}", fontSize = 11.sp, color = Color(0xFFD84315))
                            }
                        }
                        if (tax > 0) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("مالیات:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("+ ${formatPrice(tax)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                .padding(vertical = 4.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("جمع کل نهایی:", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(
                                formatPrice(totalAmount),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Dropdown for Status Choice
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { isStatusMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "وضعیت تسویه: " + when (status) {
                                "Paid" -> "تسویه کامل شده"
                                "Pending" -> "در انتظار پرداخت / پس‌کرایه"
                                else -> "خرید قسطی / تعهدی"
                            },
                            fontSize = 12.sp
                        )
                    }
                    DropdownMenu(
                        expanded = isStatusMenuExpanded,
                        onDismissRequest = { isStatusMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("تسویه کامل شده") },
                            onClick = { status = "Paid"; isStatusMenuExpanded = false }
                        )
                        DropdownMenuItem(
                            text = { Text("در انتظار پرداخت") },
                            onClick = { status = "Pending"; isStatusMenuExpanded = false }
                        )
                        DropdownMenuItem(
                            text = { Text("خرید قسطی / تعهدی") },
                            onClick = { status = "Installment"; isStatusMenuExpanded = false }
                        )
                    }
                }

                // Choose Type (Direct Sale or PreOrder)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = type == "Sale", onClick = { type = "Sale" })
                        Text("تحویل فوری کالا", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = type == "PreOrder", onClick = { type = "PreOrder" })
                        Text("سفارش کارگاهی (پیش‌خرید)", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("شرح و بندهای قرارداد / توضیحات فاکتور", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (items.isNotEmpty() && customers.isNotEmpty()) {
                        val cust = customers[selectedCustomerIndex]
                        
                        // Construct aggregated fields for db backward compatibility
                        val finalTitle = if (items.size == 1) {
                            items[0].title
                        } else {
                            val joined = items.joinToString(", ") { it.title }
                            if (joined.length > 50) joined.take(47) + "..." else joined
                        }
                        
                        val finalQty = 1
                        val finalUnitPrice = subtotal
                        
                        // Serialize list to description
                        val serializedDescription = InvoiceExporter.serializeItems(items.toList(), description)
                        
                        onConfirm(
                            cust.id,
                            cust.name,
                            finalTitle,
                            totalAmount,
                            type,
                            status,
                            serializedDescription,
                            finalQty,
                            finalUnitPrice,
                            discount,
                            tax
                        )
                    }
                },
                enabled = items.isNotEmpty() && customers.isNotEmpty(),
                modifier = Modifier.testTag("transaction_form_submit")
            ) {
                Text("ثبت در حسابداری")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("انصراف")
            }
        }
    )
}

// User Profile management Dialog
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileDialog(
    currentUsername: String,
    currentRole: String,
    currentPhone: String,
    currentBio: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String) -> Unit
) {
    var username by remember { mutableStateOf(currentUsername) }
    var role by remember { mutableStateOf(currentRole) }
    var phone by remember { mutableStateOf(currentPhone) }
    var bio by remember { mutableStateOf(currentBio) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("پروفایل کاربری من", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // User avatar placeholder
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape)
                        .align(Alignment.CenterHorizontally),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "آواتار",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("نام کاربری / نام و نام خانوادگی", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )

                OutlinedTextField(
                    value = role,
                    onValueChange = { role = it },
                    label = { Text("سمت / عنوان شغلی", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("شماره همراه", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )

                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it },
                    label = { Text("درباره کارگاه / شعار برند", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = { Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(username, role, phone, bio)
                }
            ) {
                Text("ذخیره تغییرات")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("انصراف")
            }
        }
    )
}

fun saveBitmapToCache(context: Context, bitmap: Bitmap, fileName: String): String? {
    return try {
        val file = File(context.cacheDir, fileName)
        val stream = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        stream.flush()
        stream.close()
        file.absolutePath
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

// In-Dialog Signature Canvas Component
@Composable
fun SignaturePad(
    modifier: Modifier = Modifier,
    onSignatureCaptured: (Bitmap) -> Unit,
    onClear: () -> Unit
) {
    var pathState by remember { mutableStateOf(listOf<Offset>()) }
    
    Box(
        modifier = modifier
            .background(Color.White, RoundedCornerShape(8.dp))
            .border(1.5.dp, Color(0xFFCBD5E1), RoundedCornerShape(8.dp))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        pathState = pathState + offset
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        pathState = pathState + change.position
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (pathState.size > 1) {
                for (i in 0 until pathState.size - 1) {
                    val p1 = pathState[i]
                    val p2 = pathState[i + 1]
                    if ((p1 - p2).getDistance() < 150f) {
                        drawLine(
                            color = Color.Black,
                            start = p1,
                            end = p2,
                            strokeWidth = 6f,
                            cap = StrokeCap.Round
                        )
                    }
                }
            }
        }
        
        // Buttons to clear or save signature
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(
                onClick = { 
                    pathState = emptyList()
                    onClear()
                },
                modifier = Modifier.height(32.dp)
            ) {
                Text("پاک کردن", fontSize = 11.sp, color = Color.Red, fontWeight = FontWeight.Bold)
            }
            
            Button(
                onClick = {
                    if (pathState.isNotEmpty()) {
                        val bitmap = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888)
                        val canvas = android.graphics.Canvas(bitmap)
                        canvas.drawColor(android.graphics.Color.TRANSPARENT)
                        val paint = android.graphics.Paint().apply {
                            color = android.graphics.Color.BLACK
                            strokeWidth = 8f
                            style = android.graphics.Paint.Style.STROKE
                            strokeCap = android.graphics.Paint.Cap.ROUND
                            isAntiAlias = true
                        }
                        
                        if (pathState.size > 1) {
                            val path = android.graphics.Path()
                            path.moveTo(pathState[0].x, pathState[0].y)
                            for (i in 1 until pathState.size) {
                                val p1 = pathState[i-1]
                                val p2 = pathState[i]
                                if ((p1 - p2).getDistance() < 150f) {
                                    path.lineTo(p2.x, p2.y)
                                } else {
                                    path.moveTo(p2.x, p2.y)
                                }
                            }
                            canvas.drawPath(path, paint)
                        }
                        onSignatureCaptured(bitmap)
                    }
                },
                modifier = Modifier.height(32.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text("تایید امضا", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// Invoice Personalization & Customization Settings Dialog
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceSettingsDialog(
    currentWorkshopName: String,
    currentPhone: String,
    currentAddress: String,
    currentHeaderColor: String,
    currentShowLogo: Boolean,
    currentShowTerms: Boolean,
    currentTerms1: String,
    currentTerms2: String,
    currentTerms3: String,
    currentTerms4: String,
    currentBorderStyle: String,
    currentLogoPath: String?,
    currentUseCustomLogo: Boolean,
    currentWatermarkText: String,
    currentShowWatermark: Boolean,
    currentUseSignature: Boolean,
    currentSignaturePath: String?,
    currentFontStyle: String,
    currentDefaultTaxRate: Float,
    currentDefaultDiscountRate: Float,
    onDismiss: () -> Unit,
    onConfirm: (
        String, String, String, String, Boolean, Boolean, String, String, String, String, String,
        String?, Boolean, String, Boolean, Boolean, String?, String, Float, Float
    ) -> Unit
) {
    var workshopName by remember { mutableStateOf(currentWorkshopName) }
    var phone by remember { mutableStateOf(currentPhone) }
    var address by remember { mutableStateOf(currentAddress) }
    var headerColor by remember { mutableStateOf(currentHeaderColor) }
    var showLogo by remember { mutableStateOf(currentShowLogo) }
    var showTerms by remember { mutableStateOf(currentShowTerms) }
    var terms1 by remember { mutableStateOf(currentTerms1) }
    var terms2 by remember { mutableStateOf(currentTerms2) }
    var terms3 by remember { mutableStateOf(currentTerms3) }
    var terms4 by remember { mutableStateOf(currentTerms4) }
    var borderStyle by remember { mutableStateOf(currentBorderStyle) }

    // New state variables
    var logoPath by remember { mutableStateOf(currentLogoPath) }
    var useCustomLogo by remember { mutableStateOf(currentUseCustomLogo) }
    var watermarkText by remember { mutableStateOf(currentWatermarkText) }
    var showWatermark by remember { mutableStateOf(currentShowWatermark) }
    var useSignature by remember { mutableStateOf(currentUseSignature) }
    var signaturePath by remember { mutableStateOf(currentSignaturePath) }
    var fontStyle by remember { mutableStateOf(currentFontStyle) }
    var defaultTaxRate by remember { mutableStateOf(currentDefaultTaxRate) }
    var defaultDiscountRate by remember { mutableStateOf(currentDefaultDiscountRate) }

    var isColorMenuExpanded by remember { mutableStateOf(false) }
    var isBorderMenuExpanded by remember { mutableStateOf(false) }
    var isFontMenuExpanded by remember { mutableStateOf(false) }
    var isPresetLogoMenuExpanded by remember { mutableStateOf(false) }
    var showSignaturePad by remember { mutableStateOf(false) }

    val context = LocalContext.current

    val logoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    val savedPath = saveBitmapToCache(context, bitmap, "invoice_custom_uploaded_logo.png")
                    if (savedPath != null) {
                        logoPath = savedPath
                        useCustomLogo = true
                        Toast.makeText(context, "لوگوی اختصاصی شما با موفقیت بارگذاری شد", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "خطا در بارگذاری تصویر لوگو", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val colorOptions = listOf(
        Pair("سرمه‌ای تیره (Slate)", "#0F172A"),
        Pair("برنز طلایی (Gold)", "#78350F"),
        Pair("یاقوتی (Ruby)", "#881337"),
        Pair("یشمی (Green)", "#064E3B"),
        Pair("آبی سلطنتی (Royal Blue)", "#1E3A8A")
    )

    val borderOptions = listOf(
        Pair("حاشیه طلایی مدرن", "GOLD_ACCENT"),
        Pair("حاشیه خاکستری ساده", "SIMPLE"),
        Pair("حاشیه دوخطه ضخیم", "DOUBLE"),
        Pair("بدون حاشیه", "NONE")
    )

    val fontOptions = listOf(
        Pair("یکان مدرن (Sans Serif)", "SANS_SERIF"),
        Pair("نازلی سنتی (Serif)", "SERIF"),
        Pair("ماشین تحریر (Monospace)", "MONOSPACE")
    )

    val presetLogoOptions = listOf(
        Pair("طرح صنایع چوب (pine tree)", "preset_wood"),
        Pair("طرح مبل و راحتی (sofa design)", "preset_sofa"),
        Pair("طرح سلطنتی پرنسس (crown logo)", "preset_crown"),
        Pair("طرح مونوگرام مدرن (monogram M)", "preset_minimal"),
        Pair("طرح ستاره درخشان (star design)", "preset_star")
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("پنل شخصی‌سازی و تنظیمات پیشرفته فاکتور", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "اطلاعات واحد صنفی (فروشنده)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                OutlinedTextField(
                    value = workshopName,
                    onValueChange = { workshopName = it },
                    label = { Text("نام کارگاه / واحد تجاری", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("شماره تلفن کارگاه", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("آدرس فیزیکی کارگاه", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text(
                    text = "استایل ظاهری و نوع قلم فاکتور",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                // Color Selector dropdown
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { isColorMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        val currentLabel = colorOptions.find { it.second == headerColor }?.first ?: "سفارشی"
                        Text(text = "رنگ هدر فاکتور: $currentLabel", fontSize = 12.sp)
                    }
                    DropdownMenu(
                        expanded = isColorMenuExpanded,
                        onDismissRequest = { isColorMenuExpanded = false }
                    ) {
                        colorOptions.forEach { opt ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(16.dp)
                                                .background(Color(android.graphics.Color.parseColor(opt.second)), RoundedCornerShape(4.dp))
                                        )
                                        Text(opt.first)
                                    }
                                },
                                onClick = {
                                    headerColor = opt.second
                                    isColorMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                // Border style dropdown
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { isBorderMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        val currentLabel = borderOptions.find { it.second == borderStyle }?.first ?: "سفارشی"
                        Text(text = "حاشیه فاکتور: $currentLabel", fontSize = 12.sp)
                    }
                    DropdownMenu(
                        expanded = isBorderMenuExpanded,
                        onDismissRequest = { isBorderMenuExpanded = false }
                    ) {
                        borderOptions.forEach { opt ->
                            DropdownMenuItem(
                                text = { Text(opt.first) },
                                onClick = {
                                    borderStyle = opt.second
                                    isBorderMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                // Typography style dropdown
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { isFontMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        val currentLabel = fontOptions.find { it.second == fontStyle }?.first ?: "سفارشی"
                        Text(text = "نوع قلم (Typography): $currentLabel", fontSize = 12.sp)
                    }
                    DropdownMenu(
                        expanded = isFontMenuExpanded,
                        onDismissRequest = { isFontMenuExpanded = false }
                    ) {
                        fontOptions.forEach { opt ->
                            DropdownMenuItem(
                                text = { Text(opt.first) },
                                onClick = {
                                    fontStyle = opt.second
                                    isFontMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text(
                    text = "تنظیمات لوگوی اختصاصی فاکتور",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("نمایش لوگو در بالای فاکتور", fontSize = 12.sp)
                    Switch(checked = showLogo, onCheckedChange = { showLogo = it })
                }

                if (showLogo) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("استفاده از لوگوی سفارشی آماده نرم‌افزار", fontSize = 12.sp)
                        Switch(checked = useCustomLogo && (logoPath?.startsWith("preset_") == true), onCheckedChange = { isChecked ->
                            if (isChecked) {
                                useCustomLogo = true
                                logoPath = "preset_wood"
                            } else {
                                if (logoPath?.startsWith("preset_") == true) {
                                    logoPath = null
                                }
                            }
                        })
                    }

                    if (useCustomLogo && (logoPath?.startsWith("preset_") == true)) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { isPresetLogoMenuExpanded = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                val currentPreset = presetLogoOptions.find { it.second == logoPath }?.first ?: "بدون طرح (مونوگرام متنی)"
                                Text(text = "لوگوی پیش‌ساخته: $currentPreset", fontSize = 12.sp)
                            }
                            DropdownMenu(
                                expanded = isPresetLogoMenuExpanded,
                                onDismissRequest = { isPresetLogoMenuExpanded = false }
                            ) {
                                presetLogoOptions.forEach { opt ->
                                    DropdownMenuItem(
                                        text = { Text(opt.first) },
                                        onClick = {
                                            logoPath = opt.second
                                            isPresetLogoMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    val isUploadedLogo = useCustomLogo && logoPath != null && !logoPath!!.startsWith("preset_")
                    
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "آپلود و تنظیم لوگوی شخصی شما",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (isUploadedLogo) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text("تصویر لوگوی اختصاصی شما تنظیم شده است", fontSize = 11.sp)
                                }
                                TextButton(onClick = { 
                                    logoPath = null
                                    useCustomLogo = false
                                }) {
                                    Text("حذف لوگو", color = Color.Red, fontSize = 11.sp)
                                }
                            }
                        } else {
                            Button(
                                onClick = { logoPickerLauncher.launch("image/*") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                            ) {
                                Icon(Icons.Default.AddCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("بارگذاری لوگو از گالری دستگاه", fontSize = 11.sp)
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text(
                    text = "متن واترمارک بک‌گراند (امنیت فاکتور)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("نمایش متن واترمارک مورب", fontSize = 12.sp)
                    Switch(checked = showWatermark, onCheckedChange = { showWatermark = it })
                }

                if (showWatermark) {
                    OutlinedTextField(
                        value = watermarkText,
                        onValueChange = { watermarkText = it },
                        label = { Text("متن واترمارک (مثال: پیش‌نویس، تایید نهایی)", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text(
                    text = "درصد مالیات و تخفیف پیش‌فرض معاملات",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = if (defaultTaxRate == 0f) "" else defaultTaxRate.toString(),
                        onValueChange = { defaultTaxRate = it.toFloatOrNull() ?: 0f },
                        label = { Text("مالیات پیش‌فرض (%)", fontSize = 10.sp) },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = if (defaultDiscountRate == 0f) "" else defaultDiscountRate.toString(),
                        onValueChange = { defaultDiscountRate = it.toFloatOrNull() ?: 0f },
                        label = { Text("تخفیف پیش‌فرض (%)", fontSize = 10.sp) },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text(
                    text = "امضای دیجیتال رسمی فروشگاه",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("درج امضای دیجیتال شما در فاکتور", fontSize = 12.sp)
                    Switch(checked = useSignature, onCheckedChange = { useSignature = it })
                }

                if (useSignature) {
                    if (signaturePath != null) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("امضای دیجیتال ذخیره شده است.", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                TextButton(onClick = { signaturePath = null }) {
                                    Text("حذف امضا", color = Color.Red, fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    Button(
                        onClick = { showSignaturePad = !showSignaturePad },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(if (showSignaturePad) "پنهان کردن صفحه امضا" else "ترسیم امضای دیجیتال جدید", fontSize = 11.sp)
                    }

                    if (showSignaturePad) {
                        Text(
                            "محدوده زیر را لمس کرده و با انگشت امضا بکشید:",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        SignaturePad(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            onSignatureCaptured = { bitmap ->
                                val saved = saveBitmapToCache(context, bitmap, "invoice_sig.png")
                                if (saved != null) {
                                    signaturePath = saved
                                    showSignaturePad = false
                                    Toast.makeText(context, "امضای دیجیتال با موفقیت تایید و ذخیره شد", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onClear = {
                                signaturePath = null
                            }
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("نمایش بخش شرایط و ضوابط پیش‌فاکتور", fontSize = 12.sp)
                    Switch(checked = showTerms, onCheckedChange = { showTerms = it })
                }

                if (showTerms) {
                    Text(
                        text = "متن بندهای شرایط و قوانین پیش‌فاکتور",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    OutlinedTextField(
                        value = terms1,
                        onValueChange = { terms1 = it },
                        label = { Text("شرط بند ۱", fontSize = 10.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    OutlinedTextField(
                        value = terms2,
                        onValueChange = { terms2 = it },
                        label = { Text("شرط بند ۲", fontSize = 10.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    OutlinedTextField(
                        value = terms3,
                        onValueChange = { terms3 = it },
                        label = { Text("شرط بند ۳", fontSize = 10.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    OutlinedTextField(
                        value = terms4,
                        onValueChange = { terms4 = it },
                        label = { Text("شرط بند ۴", fontSize = 10.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        workshopName, phone, address, headerColor, showLogo, showTerms,
                        terms1, terms2, terms3, terms4, borderStyle, logoPath, useCustomLogo,
                        watermarkText, showWatermark, useSignature, signaturePath, fontStyle,
                        defaultTaxRate, defaultDiscountRate
                    )
                }
            ) {
                Text("ذخیره و اعمال")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("انصراف")
            }
        }
    )
}

// Add Task / Follow-up Dialog
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskDialog(
    customers: List<Customer>,
    onDismiss: () -> Unit,
    onConfirm: (Int, String, String, String, Long) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf("Medium") } // High, Medium, Low
    var dueDateOffsetDays by remember { mutableIntStateOf(1) } // Default tomorrow

    var selectedCustomerIndex by remember { mutableIntStateOf(0) }
    var isCustomerMenuExpanded by remember { mutableStateOf(false) }
    var isPriorityMenuExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ثبت یادآور و پیگیری مشتری", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (customers.isEmpty()) {
                    Text("ابتدا باید حداقل یک مشتری ثبت کنید.", color = Color.Red, fontSize = 12.sp)
                } else {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { isCustomerMenuExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(text = "پیگیری مشتری: ${customers[selectedCustomerIndex].name}", fontSize = 12.sp)
                        }
                        DropdownMenu(
                            expanded = isCustomerMenuExpanded,
                            onDismissRequest = { isCustomerMenuExpanded = false }
                        ) {
                            customers.forEachIndexed { index, cust ->
                                DropdownMenuItem(
                                    text = { Text(cust.name) },
                                    onClick = {
                                        selectedCustomerIndex = index
                                        isCustomerMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("موضوع پیگیری (مثلاً: تماس جهت تمدید فاکتور) *", fontSize = 12.sp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("task_form_title"),
                    shape = RoundedCornerShape(12.dp)
                )

                // Priority Selection
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { isPriorityMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "اولویت کار: " + when (priority) {
                                "High" -> "فوری (قرمز)"
                                "Medium" -> "متوسط (نارنجی)"
                                else -> "کم‌اهمیت (آبی)"
                            },
                            fontSize = 12.sp
                        )
                    }
                    DropdownMenu(
                        expanded = isPriorityMenuExpanded,
                        onDismissRequest = { isPriorityMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("فوری (قرمز)") },
                            onClick = { priority = "High"; isPriorityMenuExpanded = false }
                        )
                        DropdownMenuItem(
                            text = { Text("متوسط (نارنجی)") },
                            onClick = { priority = "Medium"; isPriorityMenuExpanded = false }
                        )
                        DropdownMenuItem(
                            text = { Text("کم‌اهمیت (آبی)") },
                            onClick = { priority = "Low"; isPriorityMenuExpanded = false }
                        )
                    }
                }

                // Choose Due Date (Time offsets)
                Text("زمان انجام کار یادآور:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = dueDateOffsetDays == 0,
                        onClick = { dueDateOffsetDays = 0 },
                        label = { Text("امروز", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = dueDateOffsetDays == 1,
                        onClick = { dueDateOffsetDays = 1 },
                        label = { Text("فردا", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = dueDateOffsetDays == 3,
                        onClick = { dueDateOffsetDays = 3 },
                        label = { Text("۳ روز بعد", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = dueDateOffsetDays == 7,
                        onClick = { dueDateOffsetDays = 7 },
                        label = { Text("هفته آینده", fontSize = 11.sp) }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotEmpty() && customers.isNotEmpty()) {
                        val cust = customers[selectedCustomerIndex]
                        val computedDueTime = System.currentTimeMillis() + (dueDateOffsetDays * 24 * 60 * 60 * 1000L)
                        onConfirm(cust.id, cust.name, title, priority, computedDueTime)
                    }
                },
                modifier = Modifier.testTag("task_form_submit")
            ) {
                Text("ثبت یادآور")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("انصراف")
            }
        }
    )
}

// ==================== WAREHOUSE INVENTORY SCREEN ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    inventoryItems: List<InventoryItem>,
    onEditItem: (InventoryItem) -> Unit,
    onDeleteItem: (Int) -> Unit,
    onUpdateStock: (Int, Int) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("همه") }

    val categories = listOf("همه", "مواد اولیه", "محصول نهایی", "سایر")

    val filteredItems = inventoryItems.filter { item ->
        val matchesSearch = item.name.contains(searchQuery, ignoreCase = true) || item.sku.contains(searchQuery, ignoreCase = true)
        val matchesCategory = selectedCategory == "همه" || item.category == selectedCategory
        matchesSearch && matchesCategory
    }

    val totalItems = inventoryItems.size
    val lowStockCount = inventoryItems.count { it.stockQuantity <= it.minStockLimit }
    val totalInventoryValue = inventoryItems.sumOf { it.stockQuantity * it.unitPrice }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Title block
        item {
            Column(modifier = Modifier.padding(bottom = 8.dp)) {
                Text(
                    text = "مدیریت انبار کارگاه",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "کنترل موجودی مواد اولیه و فرآورده‌های مبلمان",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }

        // Stats Summary Cards
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Total Items card
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("کل اقلام", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("$totalItems قلم", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }

                // Low Stock Warning Card
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (lowStockCount > 0) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f) 
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("کسری انبار", fontSize = 11.sp, color = if (lowStockCount > 0) MaterialTheme.colorScheme.error else Color.Gray, fontWeight = FontWeight.Bold)
                            if (lowStockCount > 0) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(12.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "$lowStockCount کالا", 
                            fontSize = 16.sp, 
                            fontWeight = FontWeight.ExtraBold, 
                            color = if (lowStockCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground
                        )
                    }
                }

                // Total inventory Value
                Card(
                    modifier = Modifier.weight(1.2f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("ارزش انبار", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(formatPrice(totalInventoryValue), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        // Search Bar and Filters
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("جستجو بر اساس نام یا بارکد SKU...", fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        categories.forEach { cat ->
                            val selected = selectedCategory == cat
                            FilterChip(
                                selected = selected,
                                onClick = { selectedCategory = cat },
                                label = { Text(cat, fontSize = 11.sp) },
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }
                }
            }
        }

        // Inventory Items List
        if (filteredItems.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.Inventory,
                            contentDescription = null,
                            tint = Color.Gray.copy(alpha = 0.5f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("هیچ کالایی در این انبار یافت نشد", color = Color.Gray, fontSize = 14.sp)
                    }
                }
            }
        } else {
            items(filteredItems, key = { it.id }) { item ->
                InventoryItemCard(
                    item = item,
                    onEdit = { onEditItem(item) },
                    onDelete = { onDeleteItem(item.id) },
                    onIncrease = { onUpdateStock(item.id, item.stockQuantity + 1) },
                    onDecrease = { 
                        if (item.stockQuantity > 0) {
                            onUpdateStock(item.id, item.stockQuantity - 1) 
                        }
                    }
                )
            }
        }

        // Padding under list so floating nav bar doesn't cover items
        item {
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

@Composable
fun InventoryItemCard(
    item: InventoryItem,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit
) {
    val isLowStock = item.stockQuantity <= item.minStockLimit
    val hasNoStock = item.stockQuantity == 0

    val statusColor = when {
        hasNoStock -> MaterialTheme.colorScheme.error
        isLowStock -> Color(0xFFE65100) // Dark orange
        else -> Color(0xFF2E7D32) // Green
    }

    val statusText = when {
        hasNoStock -> "ناموجود"
        isLowStock -> "رو به اتمام"
        else -> "موجود سالم"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = if (isLowStock) BorderStroke(1.dp, statusColor.copy(alpha = 0.5f)) else null
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Category & SKU & Actions Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(statusColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = statusText,
                            color = statusColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "دسته: ${item.category}",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "ویرایش", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "حذف", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Item Name
            Text(
                text = item.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // SKU
            Text(
                text = "کد انبار: ${item.sku}",
                fontSize = 11.sp,
                color = Color.Gray,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Price and Location row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("قیمت واحد:", fontSize = 10.sp, color = Color.Gray)
                    Text(formatPrice(item.unitPrice), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text("محل نگهداری:", fontSize = 10.sp, color = Color.Gray)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(12.dp))
                        Text(item.location, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Divider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp)

            Spacer(modifier = Modifier.height(12.dp))

            // Stock Control Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Stock Info
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "موجودی فعلی:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "${item.stockQuantity}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = statusColor
                        )
                    }
                    Text(
                        text = "حد بحرانی انبار: ${item.minStockLimit}",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }

                // Plus / Minus Buttons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    IconButton(
                        onClick = onDecrease,
                        modifier = Modifier
                            .size(32.dp)
                            .background(MaterialTheme.colorScheme.surface, CircleShape)
                    ) {
                        Text("-", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                    }

                    Text(
                        text = "${item.stockQuantity}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )

                    IconButton(
                        onClick = onIncrease,
                        modifier = Modifier
                            .size(32.dp)
                            .background(MaterialTheme.colorScheme.surface, CircleShape)
                    ) {
                        Text("+", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // Description
            if (item.description.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = item.description,
                    fontSize = 11.sp,
                    color = Color.Gray.copy(alpha = 0.8f),
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddInventoryItemDialog(
    editingItem: InventoryItem? = null,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, Int, Int, Double, String, String) -> Unit
) {
    var name by remember { mutableStateOf(editingItem?.name ?: "") }
    var category by remember { mutableStateOf(editingItem?.category ?: "مواد اولیه") }
    var sku by remember { mutableStateOf(editingItem?.sku ?: "") }
    var stockQuantityStr by remember { mutableStateOf(editingItem?.stockQuantity?.toString() ?: "") }
    var minStockLimitStr by remember { mutableStateOf(editingItem?.minStockLimit?.toString() ?: "5") }
    var unitPriceStr by remember { mutableStateOf(editingItem?.unitPrice?.toLong()?.toString() ?: "") }
    var location by remember { mutableStateOf(editingItem?.location ?: "") }
    var description by remember { mutableStateOf(editingItem?.description ?: "") }

    var isCategoryMenuExpanded by remember { mutableStateOf(false) }
    val categories = listOf("مواد اولیه", "محصول نهایی", "سایر")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (editingItem != null) "ویرایش کالای انبار" else "ثبت کالای جدید در انبار",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Right
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("نام کالا *", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                // Category selection dropdown
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { isCategoryMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("دسته‌بندی: $category", fontSize = 12.sp)
                    }
                    DropdownMenu(
                        expanded = isCategoryMenuExpanded,
                        onDismissRequest = { isCategoryMenuExpanded = false }
                    ) {
                        categories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat) },
                                onClick = {
                                    category = cat
                                    isCategoryMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = sku,
                    onValueChange = { sku = it },
                    label = { Text("بارکد / شناسه کالا (SKU) *", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = stockQuantityStr,
                        onValueChange = { stockQuantityStr = it },
                        label = { Text("موجودی اولیه *", fontSize = 12.sp) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )

                    OutlinedTextField(
                        value = minStockLimitStr,
                        onValueChange = { minStockLimitStr = it },
                        label = { Text("حد بحرانی انبار", fontSize = 12.sp) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }

                OutlinedTextField(
                    value = unitPriceStr,
                    onValueChange = { unitPriceStr = it },
                    label = { Text("قیمت واحد کالا (تومان) *", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("محل نگهداری (مثلاً: انبار A، قفسه ۳)", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("توضیحات و مشخصات فنی کالا", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val stock = stockQuantityStr.toIntOrNull() ?: 0
                    val minLimit = minStockLimitStr.toIntOrNull() ?: 5
                    val price = unitPriceStr.toDoubleOrNull() ?: 0.0

                    if (name.isNotEmpty() && sku.isNotEmpty()) {
                        onConfirm(name, category, sku, stock, minLimit, price, location.ifEmpty { "نا مشخص" }, description)
                    }
                },
                modifier = Modifier.testTag("inventory_form_submit")
            ) {
                Text("تایید و ثبت")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("انصراف")
            }
        }
    )
}
