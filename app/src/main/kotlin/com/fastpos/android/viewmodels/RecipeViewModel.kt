package com.fastpos.android.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastpos.android.data.models.ProductSize
import com.fastpos.android.data.models.RawMaterial
import com.fastpos.android.data.models.RecipeHeader
import com.fastpos.android.data.models.RecipeItem
import com.fastpos.android.data.repositories.RawMaterialRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecipeViewModel @Inject constructor(
    private val repo: RawMaterialRepository
) : ViewModel() {

    private val _recipes        = MutableStateFlow<List<RecipeHeader>>(emptyList())
    private val _selectedRecipe = MutableStateFlow<RecipeHeader?>(null)
    private val _recipeItems    = MutableStateFlow<List<RecipeItem>>(emptyList())
    private val _recipeCost     = MutableStateFlow(0.0)
    private val _salePrice      = MutableStateFlow(0.0)
    private val _products       = MutableStateFlow<List<Triple<Int, String, String>>>(emptyList())
    private val _productSizes   = MutableStateFlow<List<ProductSize>>(emptyList())
    private val _rawMaterials   = MutableStateFlow<List<RawMaterial>>(emptyList())
    private val _isLoading      = MutableStateFlow(false)
    private val _message        = MutableStateFlow<String?>(null)

    val recipes:        StateFlow<List<RecipeHeader>>               = _recipes
    val selectedRecipe: StateFlow<RecipeHeader?>                    = _selectedRecipe
    val recipeItems:    StateFlow<List<RecipeItem>>                 = _recipeItems
    val recipeCost:     StateFlow<Double>                           = _recipeCost
    val salePrice:      StateFlow<Double>                           = _salePrice
    val products:       StateFlow<List<Triple<Int, String, String>>> = _products
    val productSizes:   StateFlow<List<ProductSize>>                = _productSizes
    val rawMaterials:   StateFlow<List<RawMaterial>>                = _rawMaterials
    val isLoading:      StateFlow<Boolean>                          = _isLoading
    val message:        StateFlow<String?>                          = _message

    init { load() }

    fun load() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _recipes.value      = repo.getAllRecipes()
                _products.value     = repo.getProductsWithRecipes()
                _rawMaterials.value = repo.getAllMaterials()
            } catch (e: Exception) { _message.value = e.message }
            finally { _isLoading.value = false }
        }
    }

    fun selectRecipe(recipe: RecipeHeader) {
        _selectedRecipe.value = recipe
        loadItems(recipe.recipeId)
    }

    fun clearSelection() {
        _selectedRecipe.value = null
        _recipeItems.value    = emptyList()
        _recipeCost.value     = 0.0
        _salePrice.value      = 0.0
    }

    fun loadSizesForProduct(productId: Int) {
        viewModelScope.launch {
            try { _productSizes.value = repo.getSizesForProduct(productId) }
            catch (_: Exception) { _productSizes.value = emptyList() }
        }
    }

    fun clearProductSizes() { _productSizes.value = emptyList() }

    // addRecipe mirrors WPF RecipeViewModel.SaveAsync — auto-names per WPF convention
    // and guards against duplicate product+size combinations
    fun addRecipe(productId: Int, sizeId: Int?, recipeName: String) {
        viewModelScope.launch {
            try {
                val duplicate = _recipes.value.any {
                    it.productId == productId && it.sizeId == sizeId
                }
                if (duplicate) {
                    _message.value = "A recipe for this ${if (sizeId != null) "product/size" else "product"} already exists — tap it to edit."
                    return@launch
                }
                val id = repo.createRecipe(productId, sizeId, recipeName)
                val updated = repo.getAllRecipes()
                _recipes.value = updated
                updated.find { it.recipeId == id }?.let { selectRecipe(it) }
            } catch (e: Exception) { _message.value = e.message }
        }
    }

    fun deleteRecipe(recipeId: Int) {
        viewModelScope.launch {
            try {
                repo.deleteRecipe(recipeId)
                if (_selectedRecipe.value?.recipeId == recipeId) clearSelection()
                _recipes.value = repo.getAllRecipes()
                _message.value = "Recipe deleted"
            } catch (e: Exception) { _message.value = e.message }
        }
    }

    private fun loadItems(recipeId: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _recipeItems.value = repo.getRecipeItems(recipeId)
                _recipeCost.value  = repo.getRecipeCostById(recipeId)
                _selectedRecipe.value?.let { r ->
                    _salePrice.value = repo.getSalePrice(r.productId, r.sizeId)
                }
            } catch (e: Exception) { _message.value = e.message }
            finally { _isLoading.value = false }
        }
    }

    fun addIngredient(materialId: Int, quantity: Double) {
        val recipe = _selectedRecipe.value ?: return
        if (quantity <= 0) return
        viewModelScope.launch {
            try {
                repo.addIngredientToRecipe(recipe.recipeId, recipe.productId, materialId, quantity)
                loadItems(recipe.recipeId)
                _recipes.value = repo.getAllRecipes()
                _message.value = "Ingredient added"
            } catch (e: Exception) { _message.value = e.message }
        }
    }

    fun updateIngredient(recipeItemId: Int, materialId: Int, quantity: Double) {
        val recipe = _selectedRecipe.value ?: return
        if (quantity <= 0) { deleteIngredient(recipeItemId); return }
        viewModelScope.launch {
            try {
                repo.addIngredientToRecipe(recipe.recipeId, recipe.productId, materialId, quantity)
                loadItems(recipe.recipeId)
            } catch (e: Exception) { _message.value = e.message }
        }
    }

    fun deleteIngredient(recipeItemId: Int) {
        val recipeId = _selectedRecipe.value?.recipeId ?: return
        viewModelScope.launch {
            try {
                repo.deleteRecipeItem(recipeItemId)
                loadItems(recipeId)
                _recipes.value = repo.getAllRecipes()
                _message.value = "Ingredient removed"
            } catch (e: Exception) { _message.value = e.message }
        }
    }

    fun clearMessage() { _message.value = null }
}
