package com.fastpos.android.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastpos.android.data.models.Branch
import com.fastpos.android.data.repositories.BranchRepository
import com.fastpos.android.utils.PreferencesManager
import com.fastpos.android.utils.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BranchViewModel @Inject constructor(
    private val branchRepo: BranchRepository,
    private val prefs:      PreferencesManager,
    val session:            SessionManager
) : ViewModel() {

    private val _branches  = MutableStateFlow<List<Branch>>(emptyList())
    private val _isLoading = MutableStateFlow(false)
    private val _message   = MutableStateFlow<String?>(null)

    val branches:  StateFlow<List<Branch>> = _branches
    val isLoading: StateFlow<Boolean>      = _isLoading
    val message:   StateFlow<String?>      = _message

    init { loadBranches() }

    fun loadBranches() {
        viewModelScope.launch {
            _isLoading.value = true
            try { _branches.value = branchRepo.getBranches() }
            catch (e: Exception) { _message.value = e.message }
            finally { _isLoading.value = false }
        }
    }

    fun saveBranch(branch: Branch) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val ok = branchRepo.saveBranch(branch, session.userId)
                if (ok) {
                    _message.value = if (branch.branchId == 0) "Branch created." else "Branch updated."
                    loadBranches()
                } else {
                    _message.value = "Failed to save branch."
                }
            } catch (e: Exception) {
                _message.value = "Error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteBranch(branchId: Int) {
        if (branchId == 1) { _message.value = "Cannot delete the main branch."; return }
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val ok = branchRepo.deleteBranch(branchId, session.userId)
                if (ok) {
                    if (session.currentBranchId.value == branchId) {
                        session.setBranch(1, "Main Branch")
                        prefs.saveActiveBranch(1, "Main Branch")
                    }
                    _message.value = "Branch deleted."
                    loadBranches()
                } else {
                    _message.value = "Cannot delete — branch has existing orders."
                }
            } catch (e: Exception) {
                _message.value = "Error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun switchBranch(branch: Branch) {
        viewModelScope.launch {
            session.setBranch(branch.branchId, branch.branchName)
            prefs.saveActiveBranch(branch.branchId, branch.branchName)
            _message.value = "Switched to ${branch.branchName}."
        }
    }

    fun clearMessage() { _message.value = null }
}
