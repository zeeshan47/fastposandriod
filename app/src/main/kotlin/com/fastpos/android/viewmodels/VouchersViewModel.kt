package com.fastpos.android.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastpos.android.data.models.Voucher
import com.fastpos.android.data.repositories.VoucherRepository
import com.fastpos.android.utils.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class VouchersViewModel @Inject constructor(
    private val repo: VoucherRepository,
    val session: SessionManager
) : ViewModel() {

    private val _vouchers   = MutableStateFlow<List<Voucher>>(emptyList())
    private val _isLoading  = MutableStateFlow(false)
    private val _error      = MutableStateFlow<String?>(null)
    private val _saveResult = MutableStateFlow<String?>(null)

    val vouchers:   StateFlow<List<Voucher>> = _vouchers
    val isLoading:  StateFlow<Boolean>       = _isLoading
    val error:      StateFlow<String?>       = _error
    val saveResult: StateFlow<String?>       = _saveResult

    init { load() }

    fun load() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                _vouchers.value = repo.getAllVouchers()
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun createVoucher(
        code:           String,
        description:    String,
        discountType:   String,
        discountValue:  Double,
        minOrderAmount: Double,
        maxUses:        Int,
        expiryDate:     Date?
    ) {
        if (code.isBlank()) { _saveResult.value = "Voucher code cannot be empty"; return }
        if (discountValue <= 0) { _saveResult.value = "Discount value must be > 0"; return }
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repo.createVoucher(code, description, discountType, discountValue, minOrderAmount, maxUses, expiryDate)
                _vouchers.value = repo.getAllVouchers()
                _saveResult.value = "Voucher created"
            } catch (e: Exception) {
                _saveResult.value = e.message ?: "Error creating voucher"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateVoucher(
        voucherId:      Int,
        code:           String,
        description:    String,
        discountType:   String,
        discountValue:  Double,
        minOrderAmount: Double,
        maxUses:        Int,
        expiryDate:     Date?,
        isActive:       Boolean
    ) {
        if (code.isBlank()) { _saveResult.value = "Voucher code cannot be empty"; return }
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repo.updateVoucher(voucherId, code, description, discountType, discountValue, minOrderAmount, maxUses, expiryDate, isActive)
                _vouchers.value = repo.getAllVouchers()
                _saveResult.value = "Voucher updated"
            } catch (e: Exception) {
                _saveResult.value = e.message ?: "Error updating voucher"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun toggleActive(voucher: Voucher) {
        updateVoucher(
            voucherId      = voucher.voucherId,
            code           = voucher.voucherCode,
            description    = voucher.description,
            discountType   = voucher.discountType,
            discountValue  = voucher.discountValue,
            minOrderAmount = voucher.minOrderAmount,
            maxUses        = voucher.maxUses,
            expiryDate     = voucher.expiryDate,
            isActive       = !voucher.isActive
        )
    }

    fun deleteVoucher(voucherId: Int) {
        viewModelScope.launch {
            try {
                repo.deleteVoucher(voucherId)
                _vouchers.value = repo.getAllVouchers()
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun clearResult() { _saveResult.value = null }
}
