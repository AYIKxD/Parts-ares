# Material 3 Expressive — Agent Build Guide
### For Android Apps (Java/Kotlin + Jetpack Compose)

> **Audience:** AI coding agents (Claude Code, Cursor, etc.) building immersive, research-backed Material 3 Expressive UIs on Android.  
> **Stack:** Jetpack Compose (Kotlin) with optional View interop for legacy Java screens.  
> **Source research:** M3 Expressive blog (Google I/O 2025), `androidx.compose.material3` API reference, 46-study/18,000-participant design research corpus.

---

## Table of Contents

1. [What M3 Expressive Is (and Isn't)](#1-what-m3-expressive-is-and-isnt)
2. [Project Setup & Dependencies](#2-project-setup--dependencies)
3. [Theme: MaterialExpressiveTheme](#3-theme-materialexpressivetheme)
4. [Motion System — Springs](#4-motion-system--springs)
5. [Shape Library — MaterialShapes](#5-shape-library--materialshapes)
6. [Color System](#6-color-system)
7. [Typography — Emphasized Scales](#7-typography--emphasized-scales)
8. [New & Updated Components](#8-new--updated-components)
   - 8.1 LoadingIndicator / ContainedLoadingIndicator
   - 8.2 ButtonGroup
   - 8.3 SplitButtonLayout
   - 8.4 FloatingActionButtonMenu
   - 8.5 HorizontalFloatingToolbar / VerticalFloatingToolbar
   - 8.6 Updated App Bars
   - 8.7 Updated Navigation Bar / Rail
   - 8.8 Updated FAB & Extended FAB
   - 8.9 Progress Indicators (Wavy)
   - 8.10 Sliders
9. [Seven Expressive Design Tactics](#9-seven-expressive-design-tactics)
10. [Hero Moments — Pattern](#10-hero-moments--pattern)
11. [Java/View Interop Strategy](#11-javaview-interop-strategy)
12. [Accessibility & Reduced Motion](#12-accessibility--reduced-motion)
13. [Agent Decision Rules (Quick Reference)](#13-agent-decision-rules-quick-reference)
14. [Anti-Patterns to Avoid](#14-anti-patterns-to-avoid)

---

## 1. What M3 Expressive Is (and Isn't)

**IS:** An evolutionary layer on top of Material 3 — new components, motion physics, color expansions, and shape library added to the existing M3 system. Backed by 46 research studies with 18,000+ participants proving expressive UIs are preferred across all age groups and are up to **4× faster to navigate**.

**IS NOT:** A new design system, M4, or a breaking change. M3 is not deprecated. All existing M3 components remain valid. You layer expressiveness on top.

**Key stat for decision-making:** Expressive designs score higher on playfulness, energy, creativity, friendliness — and users are significantly more likely to switch to apps that adopt them.

---

## 2. Project Setup & Dependencies

### 2.1 Gradle (libs.versions.toml)

```toml
[versions]
# Use the latest alpha that includes Expressive APIs
material3 = "1.4.0-alpha17"
compose-bom = "2025.06.00"

[libraries]
androidx-material3 = { group = "androidx.compose.material3", name = "material3", version.ref = "material3" }
# OR use the BOM approach:
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "compose-bom" }
```

### 2.2 build.gradle.kts (app module)

```kotlin
android {
    compileSdk = 35
    defaultConfig { minSdk = 24 }

    buildFeatures { compose = true }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2025.06.00")
    implementation(bom)

    // Material 3 — pin to alpha for Expressive APIs
    implementation("androidx.compose.material3:material3:1.4.0-alpha17")

    // Standard Compose
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.0")

    // For View interop (Java screens)
    implementation("androidx.compose.ui:ui-viewbinding")
}
```

### 2.3 Opt-in annotation

Every Expressive API is behind `@ExperimentalMaterial3ExpressiveApi`. Apply at file, function, or module level:

```kotlin
// Per file (recommended during development)
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

// Per composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable fun MyScreen() { ... }

// Module-wide (gradle) — add to composeOptions:
// freeCompilerArgs += ["-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi"]
```

---

## 3. Theme: MaterialExpressiveTheme

Replace or wrap your existing `MaterialTheme` with `MaterialExpressiveTheme`. It is a superset — all existing `MaterialTheme` slots still work.

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && darkTheme  -> dynamicDarkColorScheme(LocalContext.current)
        dynamicColor && !darkTheme -> dynamicLightColorScheme(LocalContext.current)
        darkTheme  -> expressiveDarkColorScheme()   // NEW: expressive palette
        else       -> expressiveLightColorScheme()  // NEW: expressive palette
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(), // KEY: enables spring physics
        shapes = Shapes(
            // Example: push large shapes rounder for expressive feel
            largeIncreased = RoundedCornerShape(36.dp)
        ),
        typography = AppTypography, // your custom type scale
        content = content
    )
}
```

**Accessing theme values in composables:**

```kotlin
// Motion
val motionScheme = MaterialTheme.motionScheme

// Shapes
val shapes = MaterialTheme.shapes

// Colors
val primary = MaterialTheme.colorScheme.primary
```

---

## 4. Motion System — Springs

M3 Expressive replaces duration-based easing curves with **spring physics**. Two spring families:

| Family | Use Case |
|---|---|
| **Spatial springs** | Position, size, layout changes — mirror real physics |
| **Effects springs** | Color, opacity, blur — seamless fade/transition |

### 4.1 MotionScheme variants

```kotlin
// Standard — familiar, predictable
MotionScheme.standard()

// Expressive — fluid, playful, alive (USE THIS for M3 Expressive)
MotionScheme.expressive()
```

### 4.2 Using springs in animations

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SpringyCard(selected: Boolean) {
    val motionScheme = MaterialTheme.motionScheme

    val scale by animateFloatAsState(
        targetValue = if (selected) 1.05f else 1f,
        animationSpec = motionScheme.fastSpatialSpec() // spatial spring
    )
    val alpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0.7f,
        animationSpec = motionScheme.fastEffectsSpec() // effects spring
    )

    Card(modifier = Modifier.scale(scale).alpha(alpha)) { ... }
}
```

### 4.3 Available spring specs from MotionScheme

```kotlin
motionScheme.defaultSpatialSpec()      // General position/size
motionScheme.fastSpatialSpec()         // Quick snappy spatial
motionScheme.slowSpatialSpec()         // Deliberate spatial
motionScheme.defaultEffectsSpec()      // Color/opacity transitions
motionScheme.fastEffectsSpec()         // Quick color/opacity
motionScheme.slowEffectsSpec()         // Slow fade effects
```

### 4.4 Shape morphing animation

```kotlin
val shape by animateIntAsState(
    targetValue = if (expanded) MaterialShapes.Circle else MaterialShapes.RoundedSquare,
    animationSpec = motionScheme.defaultSpatialSpec()
)
// Use with Modifier.clip(shape.toShape())
```

---

## 5. Shape Library — MaterialShapes

35 iconic shapes available via `MaterialShapes`. These go beyond `RoundedCornerShape` — they are polygonal, architectural shapes for avatars, image crops, containers, and decorative elements.

### 5.1 Available shapes (selected)

```kotlin
MaterialShapes.Circle
MaterialShapes.Square
MaterialShapes.Pill
MaterialShapes.Oval
MaterialShapes.RoundedSquare
MaterialShapes.Pentagon
MaterialShapes.Hexagon
MaterialShapes.Octagon
MaterialShapes.Star4
MaterialShapes.Star6
MaterialShapes.Cookie4Sided
MaterialShapes.Cookie9Sided
MaterialShapes.SoftBurst
MaterialShapes.Sunny
MaterialShapes.Clover4Leaf
MaterialShapes.Puffy
MaterialShapes.PuffyDiamond
// ... 35 total
```

### 5.2 Using shapes as Compose modifiers

```kotlin
// As clip shape
Image(
    painter = painterResource(R.drawable.avatar),
    contentDescription = null,
    modifier = Modifier
        .size(72.dp)
        .clip(MaterialShapes.Cookie9Sided.toShape())
)

// Shape morphing between states
@Composable
fun MorphingFab(expanded: Boolean) {
    val motionScheme = MaterialTheme.motionScheme
    val shapeProgress by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = motionScheme.defaultSpatialSpec()
    )
    val morphShape = MaterialShapes.Circle
        .lerp(MaterialShapes.Pill, shapeProgress)

    FloatingActionButton(
        onClick = {},
        shape = morphShape.toShape()
    ) { Icon(Icons.Default.Add, null) }
}
```

### 5.3 In LoadingIndicator (custom polygons)

```kotlin
LoadingIndicator(
    polygons = listOf(
        MaterialShapes.Pill,
        MaterialShapes.Sunny,
        MaterialShapes.Cookie4Sided,
        MaterialShapes.Oval
    )
)
```

---

## 6. Color System

### 6.1 Expressive color schemes

```kotlin
// Light — broader, more vivid palette than standard M3
val lightScheme = expressiveLightColorScheme()

// Dark
val darkScheme = expressiveDarkColorScheme()

// Dynamic (Android 12+ / API 31+) — takes wallpaper colors
val dynamicLight = dynamicLightColorScheme(context)
val dynamicDark  = dynamicDarkColorScheme(context)
```

### 6.2 Using color roles for hierarchy

The expressive system uses ALL color roles deliberately for visual hierarchy. Don't flatten everything to `primary`:

```kotlin
// Primary action — highest visual weight
Button(colors = ButtonDefaults.buttonColors(
    containerColor = MaterialTheme.colorScheme.primary
)) { ... }

// Secondary / supporting actions
Button(colors = ButtonDefaults.buttonColors(
    containerColor = MaterialTheme.colorScheme.secondary
)) { ... }

// Tertiary — decorative, accent
Surface(color = MaterialTheme.colorScheme.tertiaryContainer) { ... }

// Surface tones for hierarchy in cards/containers
Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest) { ... }
Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) { ... }
```

### 6.3 Vibrant surface mapping rule

Give the most important content/action the **brightest / highest-contrast** surface. Use `surfaceContainerHighest` for hero cards, `surfaceContainerLowest` for background content.

---

## 7. Typography — Emphasized Scales

### 7.1 New emphasized type styles

M3 Expressive adds **emphasized** variants to the type scale for editorial hierarchy:

```kotlin
// Standard scale (unchanged)
MaterialTheme.typography.displayLarge
MaterialTheme.typography.headlineMedium
MaterialTheme.typography.bodyLarge

// NEW emphasized variants (ExperimentalMaterial3ExpressiveApi)
MaterialTheme.typography.displayLargeEmphasized    // Heavy weight, editorial
MaterialTheme.typography.headlineMediumEmphasized  // Draw attention to key info
MaterialTheme.typography.titleLargeEmphasized      // Section headers
MaterialTheme.typography.labelLargeEmphasized      // Emphasized action labels
```

### 7.2 Defining your type scale

```kotlin
val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily(Font(R.font.your_display_font, FontWeight.Black)),
        fontWeight = FontWeight.Black,
        fontSize = 57.sp,
        lineHeight = 64.sp
    ),
    // Emphasized styles — heavier weight, tighter tracking
    displayLargeEmphasized = TextStyle(
        fontFamily = FontFamily(Font(R.font.your_display_font, FontWeight.ExtraBold)),
        fontWeight = FontWeight.ExtraBold,
        fontSize = 57.sp,
        letterSpacing = (-0.25).sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily(Font(R.font.your_body_font, FontWeight.Normal)),
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    )
)
```

### 7.3 Typography for hierarchy in practice

```kotlin
// Hero moment — use emphasized display
Text(
    text = "Begin Recording",
    style = MaterialTheme.typography.displayMediumEmphasized,
    color = MaterialTheme.colorScheme.primary
)

// Supporting info — regular body
Text(
    text = "Tap to start your session",
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant
)
```

---

## 8. New & Updated Components

> All components in this section require `@OptIn(ExperimentalMaterial3ExpressiveApi::class)`.

---

### 8.1 LoadingIndicator / ContainedLoadingIndicator

**Replace `CircularProgressIndicator` for waits under 5 seconds.** These morph between shapes fluidly — far more engaging.

```kotlin
// Basic — morphs through default shapes
LoadingIndicator()

// Sized and colored
LoadingIndicator(
    modifier = Modifier.size(60.dp),
    color = MaterialTheme.colorScheme.primary
)

// Custom shape sequence
LoadingIndicator(
    color = MaterialTheme.colorScheme.secondary,
    polygons = listOf(
        MaterialShapes.Pill,
        MaterialShapes.Sunny,
        MaterialShapes.Cookie9Sided,
        MaterialShapes.Oval
    )
)

// Contained variant — draws animation inside a colored container
ContainedLoadingIndicator(
    containerShape = MaterialShapes.Pill.toShape(),
    color = MaterialTheme.colorScheme.onPrimary,
    containerColor = MaterialTheme.colorScheme.primary
)

// Determinate — shows progress (0f..1f)
LoadingIndicator(
    progress = { loadProgress }, // () -> Float
    polygons = LoadingIndicatorDefaults.DeterminateIndicatorPolygons
)
```

**When to use:**
- API calls, data fetching, file operations
- Use `ContainedLoadingIndicator` when placed on white/neutral backgrounds for contrast
- Use plain `LoadingIndicator` when placed on colored surfaces

---

### 8.2 ButtonGroup

Organizes related toggle buttons with animated width changes — selected item expands, neighbors compress.

```kotlin
// Multi-select button group
val options = listOf("Work", "Restaurant", "Coffee")
val icons = listOf(Icons.Outlined.Work, Icons.Outlined.Restaurant, Icons.Outlined.Coffee)
val checkedIcons = listOf(Icons.Filled.Work, Icons.Filled.Restaurant, Icons.Filled.Coffee)
val checked = remember { mutableStateListOf(false, false, false) }

ButtonGroup(
    modifier = Modifier.padding(horizontal = 8.dp),
    overflowIndicator = {} // handle overflow if needed
) {
    options.forEachIndexed { index, label ->
        toggleableItem(
            checked = checked[index],
            onCheckedChange = { checked[index] = it },
            label = label,
            icon = {
                Icon(
                    if (checked[index]) checkedIcons[index] else icons[index],
                    contentDescription = label
                )
            }
        )
    }
}
```

```kotlin
// Single-select connected button group (using Row + ButtonGroupDefaults)
var selectedIndex by remember { mutableIntStateOf(0) }

Row(
    modifier = Modifier.padding(horizontal = 8.dp),
    horizontalArrangement = Arrangement.spacedBy(
        ButtonGroupDefaults.ConnectedSpaceBetween
    )
) {
    options.forEachIndexed { index, label ->
        ToggleButton(
            checked = selectedIndex == index,
            onCheckedChange = { selectedIndex = index },
            shapes = ButtonGroupDefaults.connectedLeadingButtonShapes() // or trailing/middle
        ) {
            Icon(if (selectedIndex == index) checkedIcons[index] else icons[index], null)
            Spacer(Modifier.size(ToggleButtonDefaults.IconSpacing))
            Text(label)
        }
    }
}
```

---

### 8.3 SplitButtonLayout

Two-part button: leading performs a primary action, trailing opens a secondary action (e.g., a dropdown).

```kotlin
var expanded by remember { mutableStateOf(false) }

SplitButtonLayout(
    leadingButton = {
        SplitButtonDefaults.LeadingButton(onClick = { /* primary action */ }) {
            Icon(
                Icons.Filled.Edit,
                contentDescription = "Edit",
                modifier = Modifier.size(SplitButtonDefaults.LeadingIconSize)
            )
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text("Edit")
        }
    },
    trailingButton = {
        SplitButtonDefaults.TrailingButton(
            onClick = { expanded = !expanded },
            checked = expanded
        ) {
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = "More options"
            )
        }
    }
)
// Shape morphs automatically on press — no extra code needed
```

---

### 8.4 FloatingActionButtonMenu

Expandable FAB that reveals a menu of related actions. Toggle FAB animates between add/close icon.

```kotlin
val listState = rememberLazyListState()
val fabVisible by remember {
    derivedStateOf { listState.firstVisibleItemIndex == 0 }
}
var fabMenuExpanded by rememberSaveable { mutableStateOf(false) }

BackHandler(enabled = fabMenuExpanded) { fabMenuExpanded = false }

Box(modifier = Modifier.fillMaxSize()) {
    LazyColumn(state = listState) { /* content */ }

    FloatingActionButtonMenu(
        modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        expanded = fabMenuExpanded,
        button = {
            ToggleFloatingActionButton(
                modifier = Modifier.animateFloatingActionButton(
                    visible = fabVisible || fabMenuExpanded,
                    alignment = Alignment.BottomEnd
                ),
                checked = fabMenuExpanded,
                containerSize = ToggleFloatingActionButtonDefaults.containerSizeLarge(),
                onCheckedChange = { fabMenuExpanded = it }
            ) {
                val rotation by animateFloatAsState(
                    targetValue = if (fabMenuExpanded) 45f else 0f,
                    animationSpec = MaterialTheme.motionScheme.fastSpatialSpec()
                )
                Icon(
                    Icons.Filled.Add,
                    contentDescription = if (fabMenuExpanded) "Close menu" else "Open menu",
                    modifier = Modifier.rotate(rotation)
                )
            }
        }
    ) {
        // Menu items (shown when expanded)
        listOf(
            Icons.Filled.PhotoCamera to "Camera",
            Icons.Filled.Videocam to "Video",
            Icons.Filled.Archive to "Archive"
        ).forEach { (icon, label) ->
            FloatingActionButtonMenuItem(
                onClick = { fabMenuExpanded = false },
                icon = { Icon(icon, contentDescription = null) },
                text = { Text(label) }
            )
        }
    }
}
```

---

### 8.5 HorizontalFloatingToolbar / VerticalFloatingToolbar

Floating toolbars that expand/collapse based on scroll. New in M3 Expressive.

```kotlin
// Horizontal (bottom of screen)
var expanded by rememberSaveable { mutableStateOf(true) }

Scaffold { innerPadding ->
    Box(Modifier.padding(innerPadding)) {
        LazyColumn(
            modifier = Modifier.floatingToolbarVerticalNestedScroll(
                expanded = expanded,
                onExpand = { expanded = true },
                onCollapse = { expanded = false }
            ),
            state = rememberLazyListState()
        ) { /* items */ }

        HorizontalFloatingToolbar(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-16).dp),
            expanded = expanded,
            floatingActionButton = {
                FloatingToolbarDefaults.VibrantFloatingActionButton(
                    onClick = { /* primary action */ }
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add")
                }
            },
            content = {
                IconButton(onClick = { /* */ }) {
                    Icon(Icons.Filled.Search, null)
                }
                IconButton(onClick = { /* */ }) {
                    Icon(Icons.Filled.FilterList, null)
                }
                IconButton(onClick = { /* */ }) {
                    Icon(Icons.Filled.Sort, null)
                }
            }
        )
    }
}
```

```kotlin
// Vertical (side of screen — great for tablets/foldables)
VerticalFloatingToolbar(
    modifier = Modifier
        .align(Alignment.CenterEnd)
        .offset(x = (-16).dp),
    expanded = expanded,
    leadingContent = {
        IconButton(onClick = {}) {
            Icon(Icons.Filled.MoreVert, null)
        }
    },
    trailingContent = {
        IconButton(onClick = {}) {
            Icon(Icons.Filled.Settings, null)
        }
    },
    content = {
        IconButton(onClick = {}) { Icon(Icons.Filled.Edit, null) }
        IconButton(onClick = {}) { Icon(Icons.Filled.Share, null) }
        IconButton(onClick = {}) { Icon(Icons.Filled.Delete, null) }
    }
)
```

---

### 8.6 Updated App Bars

App bars now support shape, emphasized titles, and scroll behaviors. Use `LargeTopAppBar` for editorial emphasis.

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpressiveTopBar(scrollBehavior: TopAppBarScrollBehavior) {
    LargeTopAppBar(
        title = {
            Text(
                "Discover",
                style = MaterialTheme.typography.headlineLargeEmphasized
            )
        },
        navigationIcon = {
            IconButton(onClick = { /* nav */ }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
            }
        },
        actions = {
            IconButton(onClick = { /* search */ }) {
                Icon(Icons.Filled.Search, "Search")
            }
        },
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.largeTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )
    )
}
```

---

### 8.7 Updated Navigation Bar / Rail

Navigation components now support shape morphing on selected items.

```kotlin
var selectedItem by remember { mutableIntStateOf(0) }
val items = listOf("Home", "Explore", "Profile")
val icons = listOf(Icons.Filled.Home, Icons.Filled.Explore, Icons.Filled.Person)

NavigationBar {
    items.forEachIndexed { index, item ->
        NavigationBarItem(
            selected = selectedItem == index,
            onClick = { selectedItem = index },
            icon = { Icon(icons[index], contentDescription = item) },
            label = { Text(item) }
            // Shape indicator morphs automatically with MotionScheme.expressive()
        )
    }
}
```

---

### 8.8 Updated FAB & Extended FAB

FABs now support `animateFloatingActionButton` modifier and shape options.

```kotlin
// Animated show/hide on scroll
val listState = rememberLazyListState()
val fabVisible by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }

FloatingActionButton(
    onClick = { /* */ },
    modifier = Modifier.animateFloatingActionButton(
        visible = fabVisible,
        alignment = Alignment.BottomEnd
    )
) {
    Icon(Icons.Filled.Add, "Add")
}

// Extended FAB with emphasized style
ExtendedFloatingActionButton(
    onClick = { /* */ },
    icon = { Icon(Icons.Filled.Create, null) },
    text = { Text("Create", style = MaterialTheme.typography.labelLargeEmphasized) },
    expanded = fabVisible
)
```

---

### 8.9 Progress Indicators (Wavy)

New wavy variants replace flat linear progress bars.

```kotlin
// Indeterminate wavy linear
LinearWavyProgressIndicator()

// Determinate wavy linear
LinearWavyProgressIndicator(
    progress = { uploadProgress },
    amplitude = 1f,        // wave height (0f = flat)
    waveSpeed = 1f         // animation speed multiplier
)

// Wavy circular
CircularWavyProgressIndicator(
    progress = { downloadProgress }
)
```

---

### 8.10 Sliders

Sliders now have expressive thumb shapes and track styling.

```kotlin
var sliderValue by remember { mutableFloatStateOf(0.5f) }

Slider(
    value = sliderValue,
    onValueChange = { sliderValue = it },
    track = { sliderState ->
        SliderDefaults.Track(
            sliderState = sliderState,
            thumbTrackGapSize = 4.dp,
            trackInsideCornerSize = 2.dp
        )
    }
)
```

---

## 9. Seven Expressive Design Tactics

These are the core design principles distilled from Google's M3 Expressive research. Agents should apply these when designing any screen.

### Tactic 1 — Variety of Shapes

Mix shapes to create visual tension, cohesion, and hierarchy. Break from the surrounding shape style to draw attention to key elements.

```kotlin
// DO: Mix round and architectural shapes
Row {
    // Avatar — abstract decorative shape
    Image(modifier = Modifier.clip(MaterialShapes.Cookie9Sided.toShape()))

    // Card — standard rounded
    Card(shape = RoundedCornerShape(12.dp)) { }

    // CTA button — pill for prominence
    Button(shape = MaterialShapes.Pill.toShape()) { Text("Start") }
}

// DON'T: All RoundedCornerShape(8.dp) everywhere — blends together
```

### Tactic 2 — Rich and Nuanced Colors

Use contrast between primary, secondary, and tertiary roles to create hierarchy. Don't use one color role for everything.

```kotlin
// DO: Contrast-based hierarchy
Column {
    // Primary — dominant action
    Button(
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary
        )
    ) { Text("Book Now") }

    // Secondary — supporting
    OutlinedButton(
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.secondary
        )
    ) { Text("Save for Later") }

    // Tertiary surface for decorative info
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer) {
        Text("Members save 20%")
    }
}
```

### Tactic 3 — Guide Attention with Typography

Use emphasized styles to draw attention to important elements. Create editorial moments.

```kotlin
// DO: Multi-weight editorial hierarchy
Column {
    Text(
        "Your Balance",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(
        "$1,240.00",
        style = MaterialTheme.typography.displayMediumEmphasized, // BIG & BOLD
        color = MaterialTheme.colorScheme.primary
    )
    Text(
        "+$120 this week",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.tertiary
    )
}
```

### Tactic 4 — Contain Content for Emphasis

Organize into logical containers. Give the most important content the most visual space and brightest surface.

```kotlin
// DO: Hero card with high-surface, generous padding
Card(
    colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer
    ),
    modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp)
) {
    Column(Modifier.padding(24.dp)) { // Generous padding = visual prominence
        Text("Priority Task", style = MaterialTheme.typography.headlineSmallEmphasized)
        Spacer(Modifier.height(8.dp))
        Text("Review Q3 report before Friday", style = MaterialTheme.typography.bodyLarge)
    }
}

// Secondary content — lower surface, tighter padding
Card(
    colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    )
) {
    Column(Modifier.padding(12.dp)) {
        Text("3 other tasks", style = MaterialTheme.typography.bodySmall)
    }
}
```

### Tactic 5 — Fluid and Natural Motion

Use spring physics for all state transitions. Apply shape morphing for interactive elements.

```kotlin
// DO: Spring-animated selection state
@Composable
fun SelectableChip(selected: Boolean, label: String, onClick: () -> Unit) {
    val motionScheme = MaterialTheme.motionScheme

    val containerColor by animateColorAsState(
        targetValue = if (selected)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = motionScheme.defaultEffectsSpec()
    )
    val elevation by animateDpAsState(
        targetValue = if (selected) 4.dp else 0.dp,
        animationSpec = motionScheme.fastSpatialSpec()
    )

    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = containerColor
        ),
        elevation = FilterChipDefaults.filterChipElevation(elevation)
    )
}
```

### Tactic 6 — Leverage Component Flexibility

Adapt layout to context. Use `HorizontalFloatingToolbar` on phones, `VerticalFloatingToolbar` on tablets/foldables.

```kotlin
@Composable
fun AdaptiveToolbar(windowSizeClass: WindowSizeClass) {
    if (windowSizeClass.widthSizeClass >= WindowWidthSizeClass.Medium) {
        VerticalFloatingToolbar(/* ... */)
    } else {
        HorizontalFloatingToolbar(/* ... */)
    }
}
```

### Tactic 7 — Hero Moments

Combine multiple tactics for 1–2 key interactions. These are the emotional highlights of your app.

See [Section 10](#10-hero-moments--pattern) below.

---

## 10. Hero Moments — Pattern

A hero moment combines typography emphasis + shape contrast + spring motion + color hierarchy. Use **maximum 2** hero moments per app to avoid overwhelming the user.

### Identifying your hero moment
Ask:
1. Is this interaction emotionally impactful?
2. Is this a key interaction users repeat often?
3. Can a design detail here emphasize clarity or familiarity?

### Example: Recording Start Screen

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RecordingHeroMoment(isRecording: Boolean, onToggle: () -> Unit) {
    val motionScheme = MaterialTheme.motionScheme

    // Shape morphs from square to circle on record start
    val shapeProgress by animateFloatAsState(
        targetValue = if (isRecording) 1f else 0f,
        animationSpec = motionScheme.defaultSpatialSpec()
    )
    val buttonShape = MaterialShapes.RoundedSquare.lerp(MaterialShapes.Circle, shapeProgress)

    // Color surges to primary on record
    val buttonColor by animateColorAsState(
        targetValue = if (isRecording)
            MaterialTheme.colorScheme.error
        else
            MaterialTheme.colorScheme.primary,
        animationSpec = motionScheme.defaultEffectsSpec()
    )

    // Scale pulse when recording
    val scale by animateFloatAsState(
        targetValue = if (isRecording) 1.1f else 1f,
        animationSpec = motionScheme.fastSpatialSpec()
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Emphasized typography — the hero label
        Text(
            text = if (isRecording) "Recording..." else "Begin Recording",
            style = MaterialTheme.typography.displaySmallEmphasized,
            color = if (isRecording)
                MaterialTheme.colorScheme.error
            else
                MaterialTheme.colorScheme.onSurface
        )

        // Shape-morphing button — the hero action
        Button(
            onClick = onToggle,
            shape = buttonShape.toShape(),
            colors = ButtonDefaults.buttonColors(containerColor = buttonColor),
            modifier = Modifier
                .size(120.dp)
                .scale(scale)
        ) {
            Icon(
                if (isRecording) Icons.Filled.Stop else Icons.Filled.Mic,
                contentDescription = null,
                modifier = Modifier.size(48.dp)
            )
        }

        // Contained loading indicator when recording
        if (isRecording) {
            ContainedLoadingIndicator(
                containerShape = MaterialShapes.Pill.toShape(),
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        }
    }
}
```

---

## 11. Java/View Interop Strategy

For existing Java/XML screens, embed Compose for new expressive elements without full rewrites.

### 11.1 ComposeView in XML layout

```xml
<!-- res/layout/fragment_dashboard.xml -->
<LinearLayout ...>
    <!-- Legacy views stay -->
    <TextView android:id="@+id/title" ... />

    <!-- Compose island for expressive new components -->
    <androidx.compose.ui.platform.ComposeView
        android:id="@+id/compose_toolbar"
        android:layout_width="match_parent"
        android:layout_height="wrap_content" />
</LinearLayout>
```

```java
// Java fragment
@Override
public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
    ComposeView composeView = view.findViewById(R.id.compose_toolbar);
    composeView.setContent(new Function0<Unit>() {
        @Override
        public Unit invoke() {
            // Set Compose content via Kotlin companion or extension
            ExpressiveComponentsKt.ExpressiveToolbar();
            return Unit.INSTANCE;
        }
    });
}
```

```kotlin
// Kotlin composables exposed to Java
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveToolbar() {
    AppTheme {
        HorizontalFloatingToolbar(
            expanded = true,
            floatingActionButton = {
                FloatingToolbarDefaults.VibrantFloatingActionButton(onClick = {}) {
                    Icon(Icons.Filled.Add, null)
                }
            },
            content = { /* icons */ }
        )
    }
}
```

### 11.2 AbstractComposeView for reusable View subclass

```kotlin
class ExpressiveLoadingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : AbstractComposeView(context, attrs, defStyle) {

    var isLoading: Boolean by mutableStateOf(false)

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    override fun Content() {
        AppTheme {
            AnimatedVisibility(visible = isLoading) {
                LoadingIndicator(
                    modifier = Modifier.size(48.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
```

---

## 12. Accessibility & Reduced Motion

M3 Expressive motion must respect system accessibility settings.

```kotlin
@Composable
fun AccessibleSpringAnimation(targetValue: Float): Float {
    val reducedMotion = LocalAccessibilityManager.current
        ?.isEnabled == true // simplified check

    // OR use the official API:
    val disableAnimations = LocalReduceMotion.current

    return animateFloatAsState(
        targetValue = targetValue,
        animationSpec = if (disableAnimations) {
            snap() // Instant — no animation
        } else {
            MaterialTheme.motionScheme.defaultSpatialSpec()
        }
    ).value
}
```

### Reduce motion system-wide in theme

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val reduceMotion = LocalReduceMotion.current

    MaterialExpressiveTheme(
        motionScheme = if (reduceMotion) {
            MotionScheme.standard() // Less springy
        } else {
            MotionScheme.expressive()
        },
        content = content
    )
}
```

### Content descriptions are always required

```kotlin
// Every Icon in a clickable context MUST have contentDescription
Icon(Icons.Filled.Add, contentDescription = "Add new item") // ✅
Icon(Icons.Filled.Add, contentDescription = null) // ✅ only if parent has semantics
Icon(Icons.Filled.Add, contentDescription = "") // ❌ empty string — screen reader reads nothing
```

---

## 13. Agent Decision Rules (Quick Reference)

Use these rules to make fast, consistent UI decisions.

| Situation | Decision |
|---|---|
| Short loading state (<5s) | `LoadingIndicator` or `ContainedLoadingIndicator` |
| Long progress (>5s or determinate) | `LinearWavyProgressIndicator` with progress |
| Group of 2–5 related toggleable actions | `ButtonGroup` with `toggleableItem` |
| Primary + secondary action in one button | `SplitButtonLayout` |
| Multiple FAB actions | `FloatingActionButtonMenu` |
| Context actions tied to content | `HorizontalFloatingToolbar` |
| Tablet/foldable context actions | `VerticalFloatingToolbar` |
| Avatar / decorative image crop | `MaterialShapes.Cookie9Sided` or `SoftBurst` |
| Primary CTA button | `MaterialShapes.Pill` shape |
| Key interaction to emotionally highlight | Apply Hero Moment pattern (Section 10) |
| Animation for position/size change | `motionScheme.defaultSpatialSpec()` |
| Animation for color/opacity change | `motionScheme.defaultEffectsSpec()` |
| Quick snap animation | `motionScheme.fastSpatialSpec()` |
| User has reduce motion enabled | Use `snap()` or `MotionScheme.standard()` |
| Single dominant action on screen | `primary` color, `displayEmphasized` type, largest shape |
| Supporting / secondary action | `secondary` color, `titleMedium` type |
| Background/decorative surface | `surfaceContainerLow`, `tertiaryContainer` |

---

## 14. Anti-Patterns to Avoid

These are the research-backed failure modes identified in the M3 Expressive study.

| Anti-Pattern | Problem | Fix |
|---|---|---|
| All buttons same size and shape | Essential actions look unimportant | Vary size and shape by action importance |
| Single color role everywhere | Elements blend together, no hierarchy | Use primary/secondary/tertiary contrast |
| Flat motion (instant state changes) | Feels lifeless, jarring | Apply `MotionScheme.expressive()` springs |
| More than 2 hero moments per screen | Overwhelming, no clear focus | Limit to 1–2 hero moments |
| Empty screen with no guidance | Users feel lost | Treat empty states as calls to action with inviting typography |
| `CircularProgressIndicator` for short waits | Dated, low engagement | Replace with `LoadingIndicator` |
| Uniform `RoundedCornerShape(8.dp)` for everything | Visually monotone, no personality | Mix shapes using `MaterialShapes` library |
| Plain `MaterialTheme` without `MaterialExpressiveTheme` | Springs and expressive color won't activate | Wrap root with `MaterialExpressiveTheme` |
| Skipping `@OptIn` annotation | Compilation errors | Add per-file or module-wide opt-in |
| `contentDescription = ""` on interactive icons | Accessibility failure | Always provide meaningful descriptions |
| Applying springs to every element | Chaotic, no hierarchy of motion | Reserve springs for key interactions |

---

## References

- [M3 Expressive Blog — Google I/O 2025](https://m3.material.io/blog/building-with-m3-expressive)
- [androidx.compose.material3 API Reference](https://developer.android.com/reference/kotlin/androidx/compose/material3/package-summary)
- [Compose Material 3 Release Notes](https://developer.android.com/jetpack/androidx/releases/compose-material3)
- [MaterialExpressiveTheme — Composables.com](https://composables.com/material3/materialexpressivetheme)
- [M3 Expressive Design — ProAndroidDev Part 1](https://proandroiddev.com/material-3-expressive-design-a-new-era-9ea77959a262)
- [M3 Expressive Design — ProAndroidDev Part 2](https://proandroiddev.com/material-3-expressive-design-a-new-era-part-2-6a93483c98b0)
- [Express Yourself in Compose — Nav Singh](https://navczydev.medium.com/express-yourself-designing-with-material-3-expressive-in-compose-215818c18e91)

---

*Last updated: June 2026 | Material3 version: `1.4.0-alpha17` | Min SDK: 24*
