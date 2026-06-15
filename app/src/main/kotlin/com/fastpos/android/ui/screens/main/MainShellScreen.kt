package com.fastpos.android.ui.screens.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.fastpos.android.ui.navigation.Screen
import com.fastpos.android.ui.theme.GreenSuccess

data class BottomNavItem(val label: String, val icon: ImageVector, val route: String)

private val bottomNavItems = listOf(
    BottomNavItem("Home",    Icons.Default.Home,             Screen.Dashboard.route),
    BottomNavItem("POS",     Icons.Default.PointOfSale,      Screen.Pos.route),
    BottomNavItem("Kitchen", Icons.Default.Kitchen,           Screen.Kitchen.route),
    BottomNavItem("Orders",  Icons.Default.ListAlt,          Screen.Orders.route),
    BottomNavItem("More",    Icons.Default.MoreHoriz,        Screen.More.route)
)

@Composable
fun MainBottomBar(navController: NavHostController) {
    val backStack by navController.currentBackStackEntryAsState()
    val current   = backStack?.destination?.route

    fun navigate(route: String) {
        if (current != route) {
            navController.navigate(route) {
                popUpTo(Screen.Dashboard.route) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(78.dp)
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            BottomNavButton(bottomNavItems[0], current == Screen.Dashboard.route, Modifier.weight(1f)) { navigate(Screen.Dashboard.route) }
            BottomNavButton(bottomNavItems[1], current == Screen.Pos.route, Modifier.weight(1f)) { navigate(Screen.Pos.route) }
            BottomNavButton(bottomNavItems[2], current == Screen.Kitchen.route, Modifier.weight(1f)) { navigate(Screen.Kitchen.route) }
            BottomNavButton(bottomNavItems[3], current == Screen.Orders.route, Modifier.weight(1f)) { navigate(Screen.Orders.route) }
            BottomNavButton(bottomNavItems[4], current == Screen.More.route, Modifier.weight(1f)) { navigate(Screen.More.route) }
        }
    }
}

@Composable
private fun BottomNavButton(
    item: BottomNavItem,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val color = if (selected) GreenSuccess else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = modifier.fillMaxHeight()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(item.icon, contentDescription = item.label, tint = color, modifier = Modifier.size(26.dp))
            Spacer(Modifier.height(4.dp))
            Text(item.label, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .height(3.dp)
                    .width(if (selected) 44.dp else 0.dp)
                    .background(GreenSuccess, CircleShape)
            )
        }
    }
}
