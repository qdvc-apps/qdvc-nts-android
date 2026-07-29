package qdvc.notetoself.android.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import qdvc.notetoself.android.app.model.FontContext
import qdvc.notetoself.android.app.model.ThemeMode
import qdvc.notetoself.android.app.model.ThemeSpec
import qdvc.notetoself.android.app.ui.components.ListRow
import qdvc.notetoself.android.app.ui.components.hierarchySlide

private enum class Page { ROOT, APPEARANCE, LIGHT_THEME, DARK_THEME, FONT_SIZE }

private fun Page.depth() = ordinal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    lightThemeId: String,
    darkThemeId: String,
    lightThemes: List<ThemeSpec>,
    darkThemes: List<ThemeSpec>,
    viewFontSize: Float,
    editFontSize: Float,
    onThemeMode: (ThemeMode) -> Unit,
    onLightTheme: (String) -> Unit,
    onDarkTheme: (String) -> Unit,
    onFontSize: (FontContext, Float) -> Unit,
    onClose: () -> Unit,
) {
    var page by remember { mutableStateOf(Page.ROOT) }
    val goBack: () -> Unit = { if (page == Page.ROOT) onClose() else page = Page.ROOT }
    BackHandler { goBack() }

    val title = when (page) {
        Page.ROOT -> "Settings"
        Page.APPEARANCE -> "Appearance"
        Page.LIGHT_THEME -> "Light Mode Style"
        Page.DARK_THEME -> "Dark Mode Style"
        Page.FONT_SIZE -> "Font size"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = goBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            AnimatedContent(
                targetState = page,
                transitionSpec = { hierarchySlide(targetState.depth(), initialState.depth()) },
                label = "settings",
            ) { p ->
                when (p) {
                    Page.ROOT -> RootPage(themeMode, lightThemeId, darkThemeId, { page = it })
                    Page.APPEARANCE -> AppearancePage(themeMode, onThemeMode)
                    Page.LIGHT_THEME -> ThemeChoicePage(lightThemes, lightThemeId, onLightTheme)
                    Page.DARK_THEME -> ThemeChoicePage(darkThemes, darkThemeId, onDarkTheme)
                    Page.FONT_SIZE -> FontSizePage(viewFontSize, editFontSize, onFontSize)
                }
            }
        }
    }
}

@Composable
private fun RootPage(
    themeMode: ThemeMode,
    lightThemeId: String,
    darkThemeId: String,
    navigate: (Page) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            ListRow(Icons.Filled.Brightness6, "Appearance", themeMode.name.lowercase().replaceFirstChar { it.uppercase() },
                showChevron = true, onClick = { navigate(Page.APPEARANCE) })
        }
        item {
            ListRow(Icons.Filled.LightMode, "Light Mode Style", lightThemeId,
                showChevron = true, onClick = { navigate(Page.LIGHT_THEME) })
        }
        item {
            ListRow(Icons.Filled.DarkMode, "Dark Mode Style", darkThemeId,
                showChevron = true, onClick = { navigate(Page.DARK_THEME) })
        }
        item {
            ListRow(Icons.Filled.FormatSize, "Font size", "Independent size for View and Edit",
                showChevron = true, onClick = { navigate(Page.FONT_SIZE) })
        }
    }
}

@Composable
private fun AppearancePage(mode: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(ThemeMode.values().toList()) { m ->
            ChoiceRow(
                label = m.name.lowercase().replaceFirstChar { it.uppercase() },
                selected = m == mode,
                onClick = { onSelect(m) },
            )
        }
    }
}

@Composable
private fun ThemeChoicePage(themes: List<ThemeSpec>, selectedId: String, onSelect: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(themes) { t ->
            ChoiceRow(label = t.name, selected = t.id == selectedId, onClick = { onSelect(t.id) })
        }
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    ListRow(
        icon = if (selected) Icons.Filled.Check else Icons.Filled.Brightness6,
        title = label,
        onClick = onClick,
        trailing = {
            if (selected) Icon(Icons.Filled.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
        },
    )
}

@Composable
private fun FontSizePage(view: Float, edit: Float, onFontSize: (FontContext, Float) -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        item { StepperRow("View text size", view) { onFontSize(FontContext.VIEW, it) } }
        item { StepperRow("Edit text size", edit) { onFontSize(FontContext.EDIT, it) } }
    }
}

@Composable
private fun StepperRow(label: String, value: Float, onChange: (Float) -> Unit) {
    val min = 12f; val max = 28f; val step = 1f
    Row(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        IconButton(onClick = { onChange((value - step).coerceAtLeast(min)) }) {
            Icon(Icons.Filled.Remove, contentDescription = "Decrease")
        }
        Text("${value.toInt()}", style = MaterialTheme.typography.bodyLarge)
        IconButton(onClick = { onChange((value + step).coerceAtMost(max)) }) {
            Icon(Icons.Filled.Add, contentDescription = "Increase")
        }
        TextButton(onClick = { onChange(16f) }) { Text("Reset") }
    }
}
